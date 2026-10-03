import { nextTick, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { chatWithMioBot, chatWithStream } from '@/api/chat'
import { getChatHistory } from '@/api/chatMemory'
import type { ChatMessage, MessageBlock, PlanStep } from '@/types'
import {
  generateConversationId,
  generateMessageId,
  type ChatMessagesApi
} from './useChatMessages'

// 看门狗：连续该时长收不到任何事件（含心跳）才判定连接挂死。
// 后端每 15s 发一次 heartbeat，长工具执行期间也会被持续喂狗。
const STREAM_WATCHDOG_TIMEOUT_MS = 60000

// 断线自愈：连接被浏览器/代理掐断时后端仍在执行并落库，
// 静默轮询会话历史把内容补回界面；连续无增长视为已结束。
const RECOVERY_POLL_INTERVAL_MS = 4000
const RECOVERY_STABLE_POLLS = 8
const RECOVERY_MAX_WAIT_MS = 240000

/**
 * 消息发送与流式接收：智能体 SSE（ZCode 风格事件流）与本地 Ollama 直连两种通道。
 * <p>事件按到达顺序写入消息的内容块（blocks）：文本/思考增量合并到同类末块、
 * tool_use 追加工具块、tool_args 流式补参数、tool_result 按 id 配对收尾——
 * 渲染时顺着块顺序显示，过程信息不再堆在回答上方。
 * <p>滚动：发送时跳到底部一次；流式期间仅在用户本就贴底时跟随，不强制拉滚动条。
 * 看门狗在每个事件到达时重挂，只在真正静默超时时触发异常收尾。
 */
export function useChatStream(options: {
  agentId: Ref<number>
  messagesApi: ChatMessagesApi
  ensureSession: (chatId: string) => void
  updateTitle: (userContent: string, aiContent: string, chatId: string) => void
  scrollToBottom: () => void
  /** 流式期间的贴底跟随：用户已滚离底部时不应拉动滚动条（由页面实现判断） */
  followStream: () => void
  scrollToChatListTop: () => void
}) {
  const router = useRouter()
  const userStore = useUserStore()
  const { messagesApi } = options

  let eventSource: EventSource | null = null
  let currentEventSourceChatId = ''
  let activeStream: { close: () => void } | null = null
  let currentStreamChatId = ''
  let streamWatchdogTimer: ReturnType<typeof setTimeout> | null = null

  /** 事件驱动的静默看门狗：每收到任何事件重挂；超时仍未收到才触发异常收尾 */
  function rearmStreamWatchdog(chatId: string, onStall: () => void): void {
    if (streamWatchdogTimer) {
      clearTimeout(streamWatchdogTimer)
    }
    streamWatchdogTimer = setTimeout(() => {
      streamWatchdogTimer = null
      if (messagesApi.chatLoadingMap.value.get(chatId)) {
        onStall()
      }
    }, STREAM_WATCHDOG_TIMEOUT_MS)
  }

  function disarmStreamWatchdog(): void {
    if (streamWatchdogTimer) {
      clearTimeout(streamWatchdogTimer)
      streamWatchdogTimer = null
    }
  }

  function sendMessage(content: string): void {
    if (!content || messagesApi.isLoading.value) return

    const isNewChat = !messagesApi.currentChatId.value
    if (isNewChat) {
      messagesApi.currentChatId.value = userStore.isLoggedIn
        ? generateConversationId()
        : 'temp_' + Date.now()
    }
    const chatId = messagesApi.currentChatId.value

    const existingMessages = messagesApi.getChatMessages(chatId)
    const userMessage: ChatMessage = {
      id: generateMessageId(),
      role: 'user',
      content,
      createTime: new Date()
    }
    const chatMessages = [...existingMessages, userMessage]
    const aiMessageIndex = chatMessages.length
    messagesApi.setChatMessages(chatId, chatMessages)

    if (userStore.isLoggedIn) {
      options.ensureSession(chatId)
    }
    if (isNewChat && userStore.isLoggedIn) {
      router.push(`/chat/${options.agentId.value}/${chatId}`)
    }

    nextTick(() => {
      options.scrollToBottom()
      options.scrollToChatListTop()
    })

    messagesApi.setLoading(chatId, true)

    const aiMessage: ChatMessage = {
      id: generateMessageId(),
      role: 'assistant',
      content: '',
      blocks: [],
      createTime: new Date()
    }
    messagesApi.setChatMessages(chatId, [...chatMessages, aiMessage])

    const token: string = localStorage.getItem('token') || ''

    // 本地大模型：Ollama 直连（仅正文流）
    const provider = localStorage.getItem('ai-model-provider') || 'dashscope'
    if (provider === 'ollama') {
      if (activeStream) {
        activeStream.close()
        activeStream = null
      }
      currentStreamChatId = chatId

      const history = chatMessages
        .filter(m => m.role === 'user' || (m.role === 'assistant' && m.content))
        .map(m => ({ role: m.role, content: m.content }))

      activeStream = chatWithStream(
        content,
        chatId,
        options.agentId.value,
        token,
        history,
        (rawData) => {
          if (currentStreamChatId !== chatId) return
          if (rawData && rawData !== '[DONE]') {
            patchMessage(chatId, aiMessageIndex, (msg) => appendTextDelta(msg, rawData))
            followIfCurrent(chatId)
          }
          if (rawData === '[DONE]') {
            messagesApi.setLoading(chatId, false)
            activeStream = null
            currentStreamChatId = ''
          }
        },
        (error) => {
          console.error('Ollama 错误:', error)
          handleEmptyOrPartial(chatId, aiMessageIndex)
          message.error('本地模型连接失败，请启动 Ollama 或在「个人设置」切换回在线模型')
          messagesApi.setLoading(chatId, false)
          activeStream = null
          currentStreamChatId = ''
        }
      )
      return
    }

    // 关闭旧的 EventSource
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    currentEventSourceChatId = chatId

    eventSource = chatWithMioBot(content, chatId, options.agentId.value, token)
    const es = eventSource

    // 流式异常处理：连接被掐断时后端通常仍在执行——启动静默自愈而不是报错
    const handleStreamError = (error: Event) => {
      console.error('SSE连接错误:', error)

      // 不是当前会话的消息则忽略
      if (currentEventSourceChatId !== chatId) {
        return
      }
      // 该会话已不在加载中，说明消息已完成接收，忽略此错误
      if (!messagesApi.chatLoadingMap.value.get(chatId)) {
        return
      }

      disarmStreamWatchdog()
      es.close()
      eventSource = null
      currentEventSourceChatId = ''

      const partial = messagesApi.getChatMessages(chatId)[aiMessageIndex]
      const empty = !partial || (!partial.content && !(partial.blocks && partial.blocks.length))
      if (empty) {
        messagesApi.setChatMessages(chatId, messagesApi.getChatMessages(chatId)
          .filter((_, i) => i !== aiMessageIndex))
        message.error('连接中断，请重试')
        messagesApi.setLoading(chatId, false)
        return
      }

      startRecoveryPolling(chatId, aiMessageIndex, () => {
        // 自愈失败（后端真挂了/始终无新内容）：保留已有内容并标记中断
        patchMessage(chatId, aiMessageIndex, (msg) => ({ ...msg, interrupted: true }))
        if (isNewChat && userStore.isLoggedIn) {
          options.updateTitle(content, messagesApi.getChatMessages(chatId)[aiMessageIndex]?.content || '', chatId)
        }
        messagesApi.setLoading(chatId, false)
      }, (finalText) => {
        // 自愈完成：以后端落库的完整内容收尾，用户无感
        patchMessage(chatId, aiMessageIndex, (msg) => ({
          ...msg,
          content: finalText,
          blocks: undefined
        }))
        if (messagesApi.currentChatId.value === chatId) {
          nextTick(() => options.followStream())
        }
        if (isNewChat && userStore.isLoggedIn) {
          options.updateTitle(content, finalText, chatId)
        }
        messagesApi.setLoading(chatId, false)
      })
    }

    // 初始挂一次看门狗；此后每个事件（含心跳）到达都会重挂
    rearmStreamWatchdog(chatId, () => handleStreamError(new Event('stream-watchdog')))

    es.onmessage = (event: MessageEvent) => {
      const rawData = event.data

      // 不是当前会话的消息则忽略
      if (currentEventSourceChatId !== chatId) {
        return
      }
      // 任何事件都证明连接活着：喂狗
      rearmStreamWatchdog(chatId, () => handleStreamError(new Event('stream-watchdog')))

      let parsed: Record<string, any> | null = null
      try {
        const obj = JSON.parse(rawData)
        if (obj && typeof obj === 'object') parsed = obj
      } catch {
        parsed = null
      }
      if (!parsed) {
        return
      }

      let finished = false
      patchMessage(chatId, aiMessageIndex, (msg) => applyEnvelope(msg, parsed!, () => {
        finished = true
      }))

      if (finished) {
        finishStream()
      } else {
        followIfCurrent(chatId)
      }
    }

    /** 流完成：生成标题、复位加载状态、关闭连接 */
    function finishStream(): void {
      disarmStreamWatchdog()
      const finalMessages = messagesApi.getChatMessages(chatId)
      if (isNewChat && userStore.isLoggedIn) {
        options.updateTitle(content, finalMessages[aiMessageIndex]?.content || '', chatId)
      }
      messagesApi.setLoading(chatId, false)
      es.close()
      eventSource = null
      currentEventSourceChatId = ''
    }

    es.onerror = handleStreamError
  }

  /** 文本增量：content 累积 + 合并进同类末块（块序即显示序） */
  function appendTextDelta(msg: ChatMessage, delta: string): ChatMessage {
    return {
      ...msg,
      content: msg.content + delta,
      blocks: appendBlockDelta(msg.blocks, 'text', delta)
    }
  }

  /** 向同类末块合并增量；末块类型不同或无块时新开一块 */
  function appendBlockDelta(blocks: MessageBlock[] | undefined, type: 'text' | 'thinking', delta: string): MessageBlock[] {
    const next = [...(blocks ?? [])]
    const last = next[next.length - 1]
    if (last && last.type === type) {
      next[next.length - 1] = { ...last, text: last.text + delta } as MessageBlock
    } else {
      next.push({ type, text: delta })
    }
    return next
  }

  /** 把一条信封事件应用到 AI 消息上，返回新消息对象 */
  function applyEnvelope(
    msg: ChatMessage,
    parsed: Record<string, any>,
    markFinished: () => void
  ): ChatMessage {
    switch (String(parsed.type ?? '')) {
      case 'answer':
        return appendTextDelta(msg, String(parsed.delta ?? parsed.content ?? ''))
      case 'thinking':
        return { ...msg, blocks: appendBlockDelta(msg.blocks, 'thinking', String(parsed.delta ?? parsed.content ?? '')) }
      case 'tool_use': {
        const blocks = [...(msg.blocks ?? [])]
        blocks.push({
          type: 'tool',
          id: parsed.id ? String(parsed.id) : undefined,
          tool: String(parsed.tool ?? ''),
          args: '',
          status: 'running'
        })
        return { ...msg, blocks }
      }
      case 'tool_args': {
        const id = parsed.id ? String(parsed.id) : ''
        const delta = String(parsed.delta ?? '')
        const blocks = (msg.blocks ?? []).map(b =>
          b.type === 'tool' && b.id && b.id === id ? { ...b, args: (b.args || '') + delta } : b)
        return { ...msg, blocks }
      }
      case 'tool_result': {
        const blocks = matchToolResult(msg.blocks ?? [], parsed)
        return { ...msg, blocks }
      }
      case 'plan':
        return { ...msg, plan: (parsed.steps ?? []) as PlanStep[] }
      case 'usage':
        return {
          ...msg,
          durationMs: Number(parsed.durationMs) || undefined,
          usage: {
            inputTokens: Number(parsed.inputTokens) || undefined,
            outputTokens: Number(parsed.outputTokens) || undefined,
            durationMs: Number(parsed.durationMs) || undefined
          }
        }
      case 'heartbeat':
        return msg
      case 'done':
        markFinished()
        return msg
      case 'error':
        markFinished()
        return { ...msg, interrupted: true }
      default:
        return msg
    }
  }

  /** tool_result 配对：优先按 id，其次按同名工具中最后一个 running，最后按最后一个 running */
  function matchToolResult(blocks: MessageBlock[], parsed: Record<string, any>): MessageBlock[] {
    type ToolBlock = Extract<MessageBlock, { type: 'tool' }>
    const id = parsed.id ? String(parsed.id) : ''
    const tool = String(parsed.tool ?? '')
    const toolBlocks = blocks
      .map((b, i) => ({ b, i }))
      .filter((x): x is { b: ToolBlock; i: number } => x.b.type === 'tool')
    let targetIndex = -1
    if (id) {
      const byId = toolBlocks.find(x => x.b.id === id)
      if (byId) targetIndex = byId.i
    }
    if (targetIndex === -1) {
      const running = toolBlocks.filter(x => x.b.status === 'running')
      const byName = running.filter(x => x.b.tool === tool)
      const picked = byName.length > 0 ? byName[byName.length - 1] : running[running.length - 1]
      if (picked) targetIndex = picked.i
    }
    if (targetIndex === -1) {
      // 没有配对的调用：补一张已完成卡片，保证结果不丢
      return [...blocks, {
        type: 'tool',
        tool,
        result: parsed.content !== undefined ? String(parsed.content) : undefined,
        status: 'done'
      }]
    }
    return blocks.map((b, i) => {
      if (i !== targetIndex || b.type !== 'tool') return b
      return {
        ...b,
        status: 'done',
        result: parsed.content !== undefined ? String(parsed.content) : b.result
      }
    })
  }

  /** 局部更新某条消息，避免整表深拷贝 */
  function patchMessage(
    chatId: string,
    index: number,
    patch: (msg: ChatMessage) => ChatMessage
  ): void {
    const msgs = messagesApi.getChatMessages(chatId)
    if (index < 0 || index >= msgs.length) {
      return
    }
    const updated = [...msgs]
    updated[index] = patch(updated[index])
    messagesApi.setChatMessages(chatId, updated)
  }

  function followIfCurrent(chatId: string): void {
    if (messagesApi.currentChatId.value === chatId) {
      nextTick(() => options.followStream())
    }
  }

  /** 异常收尾：空 AI 消息移除；已有内容则保留并标记中断 */
  function handleEmptyOrPartial(chatId: string, aiMessageIndex: number): void {
    const msgs = messagesApi.getChatMessages(chatId)
    const last = msgs[aiMessageIndex]
    if (!last) return
    const empty = !last.content && !(last.blocks && last.blocks.length)
    if (empty) {
      messagesApi.setChatMessages(chatId, msgs.filter((_, i) => i !== aiMessageIndex))
      message.error('连接中断，请重试')
    } else {
      patchMessage(chatId, aiMessageIndex, (msg) => ({ ...msg, interrupted: true }))
    }
  }

  /**
   * 断线自愈：连接被掐断后后端仍在执行并持续落库，
   * 轮询会话历史把内容静默补回；连续无增长视为执行结束，超时放弃才提示中断。
   */
  function startRecoveryPolling(
    chatId: string,
    aiMessageIndex: number,
    onGiveUp: () => void,
    onRecovered: (finalText: string) => void
  ): void {
    const startTime = Date.now()
    let stableCount = 0
    let lastLen = -1
    let lastText = ''
    let everGrew = false

    const timer = setInterval(async () => {
      // 加载状态被其他路径复位（如用户切走/删除）则停止
      if (!messagesApi.chatLoadingMap.value.get(chatId)) {
        clearInterval(timer)
        return
      }
      const elapsed = Date.now() - startTime
      try {
        const history = await getChatHistory(chatId, { skipErrorMessage: true })
        const text = assistantTextOfLastTurn(history ?? [])
        if (text.length > lastLen) {
          if (text.length > 0) {
            everGrew = true
            lastText = text
            patchMessage(chatId, aiMessageIndex, (msg) => ({
              ...msg,
              content: text,
              blocks: undefined
            }))
            if (messagesApi.currentChatId.value === chatId) {
              nextTick(() => options.followStream())
            }
          }
          lastLen = text.length
          stableCount = 0
        } else {
          stableCount++
        }

        if (stableCount >= RECOVERY_STABLE_POLLS
            && elapsed > RECOVERY_STABLE_POLLS * RECOVERY_POLL_INTERVAL_MS) {
          clearInterval(timer)
          if (everGrew) onRecovered(lastText)
          else onGiveUp()
          return
        }
        if (elapsed > RECOVERY_MAX_WAIT_MS) {
          clearInterval(timer)
          if (everGrew) onRecovered(lastText)
          else onGiveUp()
        }
      } catch {
        // 轮询失败（后端暂不可达）：继续尝试直到超时
        if (elapsed > RECOVERY_MAX_WAIT_MS) {
          clearInterval(timer)
          onGiveUp()
        }
      }
    }, RECOVERY_POLL_INTERVAL_MS)
  }

  /** 取历史里最后一个用户消息之后的全部助手文本（与本轮流式消息对应） */
  function assistantTextOfLastTurn(history: Array<{ role: string; content: string }>): string {
    let lastUser = -1
    for (let i = history.length - 1; i >= 0; i--) {
      if (history[i].role === 'user') {
        lastUser = i
        break
      }
    }
    return history
      .slice(lastUser + 1)
      .filter(m => m.role === 'assistant')
      .map(m => m.content)
      .join('\n\n')
  }

  /** 组件卸载前关闭所有流式连接 */
  function cleanup(): void {
    disarmStreamWatchdog()
    if (eventSource) {
      eventSource.close()
      eventSource = null
      currentEventSourceChatId = ''
    }
    if (activeStream) {
      activeStream.close()
      activeStream = null
      currentStreamChatId = ''
    }
  }

  return { sendMessage, cleanup }
}
