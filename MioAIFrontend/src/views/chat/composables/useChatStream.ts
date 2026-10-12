import { nextTick, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { chatWithMioBot, chatWithStream, isChatActive, reconnectChatStream, stopChat } from '@/api/chat'
import { getBotMessages, type BotMessageRow } from '@/api/botMessages'
import { historyFromVersions } from './useChatMessages'
import type {
  AttachmentItem,
  ChatMessage,
  MessageBlock,
  PlanStep,
  QuestionAnswer,
  QuestionItem
} from '@/types'
import {
  generateConversationId,
  generateMessageId,
  textOfBlocks,
  type ChatMessagesApi
} from './useChatMessages'

// 看门狗：连续该时长收不到任何事件（含心跳）才判定连接挂死。
// 后端每 15s 发一次 heartbeat，长工具执行期间也会被持续喂狗。
const STREAM_WATCHDOG_TIMEOUT_MS = 60000

// 断线重连回放：携 lastSeq 重连 /bot/chat/stream 续播；连续失败到该次数
// （后端不在执行/网络持续不通）才回退到恢复轮询。每次收到事件即恢复预算
const MAX_RECONNECT_ATTEMPTS = 3

// 断线自愈：连接被浏览器/代理掐断时后端仍在执行，
// 结束时会把完整工作过程一次性落库到 agent_message——静默轮询该表，
// 本轮 assistant 行出现即代表执行完成，取整行还原界面；超时放弃才提示中断。
// 长任务 + 标签页休眠场景可能很久才完成，窗口放宽到 10 分钟。
const RECOVERY_POLL_INTERVAL_MS = 4000
const RECOVERY_MAX_WAIT_MS = 600000

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
  ensureSession: (chatId: string, firstMessage?: string) => void
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

  /** 恢复中的会话（重连回放/恢复轮询全程占位）：loadMessages 并发触发时防止重复接管开双流 */
  const recoveringChatIds = new Set<string>()
  /** 恢复流的清理回调（关闭回放连接/停表）：组件卸载时统一执行 */
  const recoveryCleanups = new Set<() => void>()

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

  /** 发送选项：重新生成场景复用已在列表里的用户消息（本地截断后调用），后端也不重复落库 */
  interface SendOptions {
    skipUserMessage?: boolean
    skipUserPersist?: boolean
    /** 思考强度（none/low/medium/high 等），透传到模型 */
    reasoningEffort?: string
    /** 已上传到沙箱的附件引用（选中文件时即已上传，发送只引用路径） */
    attachments?: AttachmentItem[]
    /** 编辑/重新生成时被替换掉的旧回复版本（挂到新回复上供 <n/n> 切换） */
    history?: ChatMessage[]
    /** 编辑重发：本轮版本组锚（沿用被编辑轮次，后端据此持久化版本组） */
    groupSeq?: number
  }

  /** 首次选中附件时即锁定会话 id（附件上传目录随之确定）；新会话 id 记入 freshChatIds，发送时据此判定 isNewChat */
  const freshChatIds = new Set<string>()

  function prepareChatId(): string {
    const existing = messagesApi.currentChatId.value
    if (existing) {
      return existing
    }
    const id = userStore.isLoggedIn ? generateConversationId() : 'temp_' + Date.now()
    messagesApi.currentChatId.value = id
    freshChatIds.add(id)
    return id
  }

  function sendMessage(content: string, opts?: SendOptions): void {
    // 守卫按"本会话"的 loading 判断（其他会话的执行/恢复轮询不应挡住当前会话）；
    // 拦截必须给出提示——静默 return 正是"点发送没反应"的元凶
    if (!content) return
    const guardChatId = messagesApi.currentChatId.value
    if (guardChatId && messagesApi.chatLoadingMap.value.get(guardChatId)) {
      message.warning('当前会话有任务正在执行，请等完成后再发送')
      return
    }
    const skipUserMessage = opts?.skipUserMessage ?? false

    if (!messagesApi.currentChatId.value) {
      prepareChatId()
    }
    const chatId = messagesApi.currentChatId.value
    const isNewChat = !skipUserMessage && freshChatIds.has(chatId)
    freshChatIds.delete(chatId)

    const uploaded = opts?.attachments ?? []

    const existingMessages = messagesApi.getChatMessages(chatId)
    const chatMessages = skipUserMessage
      ? [...existingMessages]
      : [...existingMessages, {
          id: generateMessageId(),
          role: 'user' as const,
          content,
          createTime: new Date(),
          ...(uploaded.length
            ? {
                blocks: [{
                  type: 'attachments' as const,
                  side: 'input' as const,
                  items: uploaded
                }]
              }
            : {})
        }]
    const aiMessageIndex = chatMessages.length
    messagesApi.setChatMessages(chatId, chatMessages)

    if (!skipUserMessage && userStore.isLoggedIn) {
      options.ensureSession(chatId, content)
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
      createTime: new Date(),
      history: opts?.history,
      activeVersion: (opts?.history?.length ?? 0) + 1
    }
    messagesApi.setChatMessages(chatId, [...chatMessages, aiMessage])

    const token: string = localStorage.getItem('token') || ''

    // 本地大模型：Ollama 直连（仅正文流）
    const provider = localStorage.getItem('ai-model-provider') || 'dashscope'
    if (provider === 'ollama') {
      if (uploaded.length) {
        message.info('本地模型暂不支持读取附件，仅发送文字')
      }
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

    eventSource = chatWithMioBot(content, chatId, options.agentId.value, token, {
      skipUserPersist: opts?.skipUserPersist ?? false,
      reasoningEffort: opts?.reasoningEffort,
      groupSeq: opts?.groupSeq,
      attachments: uploaded
    })

    // 断点续播：服务端为执行中的回合缓冲了全部事件（seq 递增），
    // 连接断开时携 lastSeq 重连 /bot/chat/stream——先回放缺口再续播直播；
    // 重连预算耗尽（后端不在执行/网络持续不通）才回退到恢复轮询（落库还原）
    let lastSeq = 0
    let reconnectAttempts = MAX_RECONNECT_ATTEMPTS

    // 流式异常处理：先尝试重连回放，失败再走静默自愈
    const handleStreamError = (error: Event) => {
      // 不是当前会话的消息则忽略
      if (currentEventSourceChatId !== chatId) {
        return
      }
      // 该会话已不在加载中，说明消息已完成接收，忽略此错误
      if (!messagesApi.chatLoadingMap.value.get(chatId)) {
        return
      }

      if (reconnectAttempts > 0) {
        reconnectAttempts--
        console.warn('SSE 断开，尝试重连回放:', error)
        disarmStreamWatchdog()
        if (eventSource) {
          eventSource.close()
        }
        eventSource = reconnectChatStream(chatId, lastSeq, token)
        currentEventSourceChatId = chatId
        eventSource.onmessage = onStreamMessage
        eventSource.onerror = handleStreamError
        return
      }

      console.error('SSE连接错误（重连回放不可用）:', error)
      disarmStreamWatchdog()
      if (eventSource) {
        eventSource.close()
        eventSource = null
        currentEventSourceChatId = ''
      }

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
        if (!earlyTitleDone && isNewChat && userStore.isLoggedIn) {
          options.updateTitle(content, messagesApi.getChatMessages(chatId)[aiMessageIndex]?.content || '', chatId)
        }
        messagesApi.setLoading(chatId, false)
      }, (row) => {
        // 自愈完成：以后端落库的完整工作过程收尾，用户无感（被中断的轮次转为"已停止"态）
        patchMessage(chatId, aiMessageIndex, (msg) => recoveredRowPatch(row, msg))
        if (messagesApi.currentChatId.value === chatId) {
          nextTick(() => options.followStream())
        }
        if (!earlyTitleDone && isNewChat && userStore.isLoggedIn) {
          options.updateTitle(content, textOfBlocks((row.blocks ?? []) as MessageBlock[]), chatId)
        }
        messagesApi.setLoading(chatId, false)
      })
    }

    // 标题不必等回复结束：首答累计够多字或首个工具调用出现时即可总结
    let earlyTitleDone = false
    const maybeTitleEarly = (): void => {
      if (earlyTitleDone || !isNewChat || !userStore.isLoggedIn) return
      const msg = messagesApi.getChatMessages(chatId)[aiMessageIndex]
      if (!msg) return
      const hasTool = (msg.blocks ?? []).some(b => b.type === 'tool')
      if (msg.content.length >= 60 || hasTool) {
        earlyTitleDone = true
        options.updateTitle(content, msg.content, chatId)
      }
    }

    /** 原流与重连流共用的事件处理：喂狗、追踪 seq 断点、应用信封 */
    const onStreamMessage = (event: MessageEvent): void => {
      // 不是当前会话的消息则忽略
      if (currentEventSourceChatId !== chatId) {
        return
      }

      let parsed: Record<string, any> | null = null
      try {
        const obj = JSON.parse(event.data)
        if (obj && typeof obj === 'object') parsed = obj
      } catch {
        parsed = null
      }
      if (!parsed) {
        return
      }
      // 服务端明确不可回放（回合已结束/缓冲停用）或本连接已被更新的接管顶掉：
      // 不再重连，交给 onerror 走落库还原
      if (parsed.type === 'replay_unavailable' || parsed.type === 'superseded') {
        reconnectAttempts = 0
        return
      }

      // 任何事件都证明连接活着：喂狗 + 恢复重连预算 + 记录断点
      rearmStreamWatchdog(chatId, () => handleStreamError(new Event('stream-watchdog')))
      reconnectAttempts = MAX_RECONNECT_ATTEMPTS
      const seqNum = Number(parsed.seq)
      if (Number.isFinite(seqNum) && seqNum > 0) {
        lastSeq = seqNum
      }

      let finished = false
      patchMessage(chatId, aiMessageIndex, (msg) => applyEnvelope(msg, parsed!, () => {
        finished = true
      }))

      if (finished) {
        finishStream()
      } else {
        maybeTitleEarly()
        followIfCurrent(chatId)
      }
    }

    /** 流完成：生成标题（早触发过则跳过）、复位加载状态、关闭连接 */
    function finishStream(): void {
      disarmStreamWatchdog()
      const finalMessages = messagesApi.getChatMessages(chatId)
      if (!earlyTitleDone && isNewChat && userStore.isLoggedIn) {
        options.updateTitle(content, finalMessages[aiMessageIndex]?.content || '', chatId)
      }
      messagesApi.setLoading(chatId, false)
      if (eventSource) {
        eventSource.close()
        eventSource = null
      }
      currentEventSourceChatId = ''
    }

    eventSource.onmessage = onStreamMessage
    eventSource.onerror = handleStreamError
    // 初始挂一次看门狗；此后每个事件（含心跳）到达都会重挂
    rearmStreamWatchdog(chatId, () => handleStreamError(new Event('stream-watchdog')))
  }

  /** 重连成功（内容恢复流动）：活动重试行转入历史——保留静态行标记该轮经历过重连，动画取消 */
  function flushRetry(msg: ChatMessage): ChatMessage {
    if (!msg.retryNotice) return msg
    return {
      ...msg,
      retryHistory: [...(msg.retryHistory ?? []), msg.retryNotice],
      retryNotice: undefined
    }
  }

  /** 文本增量：content 累积 + 合并进同类末块（块序即显示序）；重试提示随之转入历史 */
  function appendTextDelta(msg: ChatMessage, delta: string): ChatMessage {
    return flushRetry({
      ...msg,
      content: msg.content + delta,
      blocks: appendBlockDelta(closeOpenThinking(msg.blocks), 'text', delta)
    })
  }

  /** 向同类末块合并增量；末块类型不同或无块时新开一块（思考块开块时记起点） */
  function appendBlockDelta(blocks: MessageBlock[] | undefined, type: 'text' | 'thinking', delta: string): MessageBlock[] {
    const next = [...(blocks ?? [])]
    const last = next[next.length - 1]
    if (last && last.type === type) {
      next[next.length - 1] = { ...last, text: last.text + delta } as MessageBlock
    } else {
      next.push(type === 'thinking'
        ? { type, text: delta, startedAt: Date.now() }
        : { type, text: delta })
    }
    return next
  }

  /** 进行中的思考块收尾：换块/流结束时记上时长（收起态显示"思考 · 持续了X秒"） */
  function closeOpenThinking(blocks: MessageBlock[] | undefined): MessageBlock[] {
    const next = blocks ?? []
    const last = next[next.length - 1]
    if (last && last.type === 'thinking' && last.startedAt != null && last.durationMs == null) {
      const copy = [...next]
      copy[copy.length - 1] = { ...last, durationMs: Date.now() - last.startedAt }
      return copy
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
        return flushRetry({
          ...msg,
          blocks: appendBlockDelta(msg.blocks, 'thinking', String(parsed.delta ?? parsed.content ?? ''))
        })
      case 'tool_use': {
        const blocks = [...closeOpenThinking(msg.blocks)]
        blocks.push({
          type: 'tool',
          id: parsed.id ? String(parsed.id) : undefined,
          tool: String(parsed.tool ?? ''),
          args: '',
          status: 'running'
        })
        return flushRetry({ ...msg, blocks })
      }
      case 'tool_args': {
        const id = parsed.id ? String(parsed.id) : ''
        const delta = String(parsed.delta ?? '')
        if (!delta) return msg
        const blocks = [...(msg.blocks ?? [])]
        // 优先按 id 配对；无 id 的兼容端点落到最后一个 running 工具块
        let targetIndex = -1
        if (id) {
          for (let i = blocks.length - 1; i >= 0; i--) {
            const b = blocks[i]
            if (b.type === 'tool' && b.id === id) { targetIndex = i; break }
          }
        }
        if (targetIndex === -1) {
          for (let i = blocks.length - 1; i >= 0; i--) {
            if (blocks[i].type === 'tool' && (blocks[i] as { status?: string }).status === 'running') {
              targetIndex = i
              break
            }
          }
        }
        if (targetIndex === -1) return msg
        const target = blocks[targetIndex] as Extract<MessageBlock, { type: 'tool' }>
        blocks[targetIndex] = { ...target, args: (target.args || '') + delta }
        return { ...msg, blocks }
      }
      case 'tool_result': {
        const blocks = matchToolResult(msg.blocks ?? [], parsed)
        return { ...msg, blocks }
      }
      case 'question': {
        const blocks = [...closeOpenThinking(msg.blocks)]
        const id = String(parsed.id ?? '')
        if (parsed.status === 'answered') {
          const idx = blocks.findIndex(b => b.type === 'question' && b.id === id)
          if (idx >= 0) {
            blocks[idx] = {
              ...(blocks[idx] as Extract<MessageBlock, { type: 'question' }>),
              status: 'answered',
              answers: (parsed.answers ?? []) as QuestionAnswer[]
            }
          }
          return { ...msg, blocks }
        }
        blocks.push({
          type: 'question',
          id,
          status: 'pending',
          questions: (parsed.questions ?? []) as QuestionItem[]
        })
        return { ...msg, blocks }
      }
      case 'plan':
        return { ...msg, plan: (parsed.steps ?? []) as PlanStep[] }
      case 'attachments': {
        const blocks = [...(msg.blocks ?? [])]
        blocks.push({
          type: 'attachments',
          side: parsed.side === 'input' ? 'input' : 'output',
          items: (parsed.items ?? []) as AttachmentItem[]
        })
        return { ...msg, blocks }
      }
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
      case 'retry': {
        const attempt = Number(parsed.attempt) || 1
        const maxAttempts = Number(parsed.maxAttempts) || attempt
        // 同一次重连过程原地更新计数（zcode 式单行 n/m）；内容恢复时整行才归档为静态
        return { ...msg, retryNotice: `重新连接中... ${attempt}/${maxAttempts}` }
      }
      case 'notice':
        // 瞬态系统提示（如 MCP 工具本轮装配失败）：不落历史，刷新后不保留
        return { ...msg, mcpNotice: String(parsed.content ?? '') }
      case 'done':
        markFinished()
        return flushRetry({ ...msg, blocks: closeOpenThinking(msg.blocks) })
      case 'stopped':
        // 用户主动中断：部分过程已由后端落库，状态行显示"已停止"（无折叠入口）
        markFinished()
        return flushRetry({ ...msg, blocks: closeOpenThinking(msg.blocks), stopped: true })
      case 'error':
        markFinished()
        return flushRetry({ ...msg, blocks: closeOpenThinking(msg.blocks), interrupted: true })
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

  /**
   * 用户主动中断当前会话轮次：请求后端停止引擎。
   * 界面收尾由 SSE stopped 事件（连接活着）或恢复轮询取回落库行（连接已断）驱动，
   * 这里只负责调用与失败提示，不乐观翻转状态。
   */
  async function stopTurn(chatId: string): Promise<void> {
    if (!messagesApi.chatLoadingMap.value.get(chatId)) {
      return
    }
    try {
      await stopChat(chatId)
    } catch (e) {
      console.error('中断失败:', e)
      message.error('中断失败，请重试')
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
   * 恢复轮询取回的落库行 → 消息补丁（两处恢复路径共用）。
   * 行尾的 stopped 标记块转为消息停止态（状态行"已停止"，无折叠入口）并不参与渲染；
   * 等待中的问答块门闸已随连接丢失，锁定为未作答。
   */
  function recoveredRowPatch(row: BotMessageRow, msg: ChatMessage): ChatMessage {
    const rawBlocks = (row.blocks ?? []) as MessageBlock[]
    const stoppedTurn = rawBlocks.some(b => b.type === 'stopped')
    return {
      ...msg,
      content: textOfBlocks(rawBlocks),
      blocks: rawBlocks
        .filter(b => b.type !== 'stopped')
        .map(block =>
          block.type === 'question' && block.status === 'pending'
            ? { ...block, status: 'answered' as const, answers: [] }
            : block
        ),
      plan: (row.plan ?? undefined) as ChatMessage['plan'],
      durationMs: row.durationMs ?? undefined,
      history: historyFromVersions(row.versions) ?? msg.history,
      interrupted: false,
      stopped: stoppedTurn || undefined
    }
  }

  /**
   * 断线自愈：连接被掐断后后端仍在执行，结束时把本轮完整工作过程
   * （内容块/清单/耗时）一次性写入 agent_message——轮询该表直到本轮
   * assistant 行出现即取整行还原；超时放弃才提示中断。
   */
  function startRecoveryPolling(
    chatId: string,
    aiMessageIndex: number,
    onGiveUp: () => void,
    onRecovered: (row: BotMessageRow) => void,
    onStopped?: () => void
  ): void {
    const startTime = Date.now()
    let lastActiveProbe = 0

    const timer = setInterval(async () => {
      // 加载状态被其他路径复位（如用户切走/删除）则停止
      if (!messagesApi.chatLoadingMap.value.get(chatId)) {
        clearInterval(timer)
        onStopped?.()
        return
      }
      const elapsed = Date.now() - startTime
      try {
        const rows = await getBotMessages(chatId, { skipErrorMessage: true })
        // 本轮 = 最后一个 user 行之后的首个 assistant 行（assistant 行只在运行结束时落库）
        let lastUserSeq = -1
        for (const row of rows ?? []) {
          if (row.role === 'user' && (row.seq ?? 0) > lastUserSeq) lastUserSeq = row.seq ?? 0
        }
        const row = [...(rows ?? [])]
          .reverse()
          .find(r => r.role === 'assistant' && (r.seq ?? 0) > lastUserSeq)

        if (row) {
          clearInterval(timer)
          onRecovered(row)
          return
        }
        // 后端已不在执行该会话（回合随重启/异常消亡）：立即放弃，不空等 10 分钟锁死编辑入口
        if (elapsed > 20000 && Date.now() - lastActiveProbe > 20000) {
          lastActiveProbe = Date.now()
          try {
            const active = await isChatActive(chatId)
            if (!active) {
              clearInterval(timer)
              onGiveUp()
              return
            }
          } catch {
            // 活跃探测失败（后端暂不可达）：交由既有超时兜底
          }
        }
        if (elapsed > RECOVERY_MAX_WAIT_MS) {
          clearInterval(timer)
          onGiveUp()
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

  /**
   * 孤儿回合接管：历史加载发现运行中的回合（末行 user 行，或仍在执行的 WIP
   * assistant 行被 loadMessages 剥离替换）——置为加载中并重连回放续播；
   * 重连不可用（回合已结束/网络不通）再回退到恢复轮询，落库后自动补全完整回复。
   */
  function recoverPendingTurn(chatId: string): void {
    const msgs = messagesApi.getChatMessages(chatId)
    const pendingIndex = msgs.length - 1
    if (pendingIndex < 0 || msgs[pendingIndex].role !== 'assistant' || msgs[pendingIndex].content) {
      return
    }
    // 防重入：loadMessages 会被并发调用两次（selectChat 手动 + 路由 watch），
    // 两次都通过上面的占位 guard 就会开两条回放流同时 patch 同一消息——内容整份翻倍
    if (recoveringChatIds.has(chatId)) {
      return
    }
    recoveringChatIds.add(chatId)
    const releaseRecovery = (): void => {
      recoveringChatIds.delete(chatId)
      recoveryCleanups.delete(cleanupRecovery)
    }
    messagesApi.setLoading(chatId, true)

    const token: string = localStorage.getItem('token') || ''
    const onRecoveredRow = (row: BotMessageRow): void => {
      releaseRecovery()
      patchMessage(chatId, pendingIndex, (msg) => recoveredRowPatch(row, msg))
      messagesApi.setLoading(chatId, false)
    }
    const fallbackToPolling = (): void => {
      // 防重入位继续持有到轮询结束：轮询期间的重复接管同样会写出重复内容
      startRecoveryPolling(chatId, pendingIndex, () => {
        // 始终无结果：标记中断（刷新仍可从历史恢复）
        releaseRecovery()
        patchMessage(chatId, pendingIndex, (msg) => ({ ...msg, interrupted: true }))
        messagesApi.setLoading(chatId, false)
      }, onRecoveredRow, releaseRecovery)
    }

    // 重连回放续播（服务端缓冲了本轮事件）：优先于轮询——能看到实时过程而非干等结果
    let lastSeq = 0
    let attempts = MAX_RECONNECT_ATTEMPTS
    let replayEs: EventSource | null = null
    let stallTimer: ReturnType<typeof setTimeout> | null = null

    const clearStallTimer = (): void => {
      if (stallTimer) {
        clearTimeout(stallTimer)
        stallTimer = null
      }
    }
    const closeReplayEs = (): void => {
      if (replayEs) {
        replayEs.close()
        replayEs = null
      }
    }
    // 卸载/清理：关流停表并释放防重入位（组件重建后允许重新接管）
    const cleanupRecovery = (): void => {
      clearStallTimer()
      closeReplayEs()
      recoveringChatIds.delete(chatId)
    }
    recoveryCleanups.add(cleanupRecovery)
    // 回放流也有 15s 心跳喂狗；静默超时视为断流，走重连/回退
    const rearmStallTimer = (): void => {
      clearStallTimer()
      stallTimer = setTimeout(() => {
        if (!messagesApi.chatLoadingMap.value.get(chatId)) {
          releaseRecovery()
          return
        }
        closeReplayEs()
        if (attempts > 0) {
          attempts--
          connect()
        } else {
          releaseRecovery()
          fallbackToPolling()
        }
      }, STREAM_WATCHDOG_TIMEOUT_MS)
    }

    const finishReplay = (): void => {
      cleanupRecovery()
      releaseRecovery()
      messagesApi.setLoading(chatId, false)
    }

    const connect = (): void => {
      replayEs = reconnectChatStream(chatId, lastSeq, token)
      rearmStallTimer()
      replayEs.onmessage = (event: MessageEvent) => {
        let parsed: Record<string, any> | null = null
        try {
          const obj = JSON.parse(event.data)
          if (obj && typeof obj === 'object') parsed = obj
        } catch {
          parsed = null
        }
        if (!parsed) return
        if (parsed.type === 'replay_unavailable' || parsed.type === 'superseded') {
          // 回合已结束/缓冲停用/连接被更新的接管顶掉：服务端随即关流，
          // onerror 里回退恢复轮询（被顶掉时轮询由持有直播的那个端负责收尾）
          attempts = 0
          return
        }
        rearmStallTimer()
        const seqNum = Number(parsed.seq)
        if (Number.isFinite(seqNum) && seqNum > 0) lastSeq = seqNum
        // 回放/直播期间落库行出现（WIP 步增量刷新）也不切换到轮询：直播更实时，
        // done/stopped 事件收尾即拿到完整内容
        let finished = false
        patchMessage(chatId, pendingIndex, (msg) => applyEnvelope(msg, parsed!, () => {
          finished = true
        }))
        followIfCurrent(chatId)
        if (finished) {
          finishReplay()
        }
      }
      replayEs.onerror = () => {
        closeReplayEs()
        clearStallTimer()
        // 已被其他路径收尾（如停止后 done 已应用）则不再接续
        if (!messagesApi.chatLoadingMap.value.get(chatId)) {
          releaseRecovery()
          return
        }
        if (attempts > 0) {
          attempts--
          connect()
        } else {
          releaseRecovery()
          fallbackToPolling()
        }
      }
    }
    connect()
  }

  /** 组件卸载前关闭所有流式连接 */
  function cleanup(): void {
    disarmStreamWatchdog()
    for (const fn of recoveryCleanups) {
      try {
        fn()
      } catch (ignored) {
        // 清理回调失败不影响其余清理
      }
    }
    recoveryCleanups.clear()
    recoveringChatIds.clear()
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

  return { sendMessage, prepareChatId, cleanup, recoverPendingTurn, stopTurn }
}
