import { nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { chatWithMioBot, chatWithStream } from '@/api/chat'
import type { ChatMessage, PlanStep, ToolEvent } from '@/types'
import {
  generateConversationId,
  generateMessageId,
  type ChatMessagesApi
} from './useChatMessages'

// 流式看门狗：连接挂死（收不到任何事件）时强制异常收尾
const STREAM_WATCHDOG_TIMEOUT_MS = 90000

/**
 * 消息发送与流式接收：MioBot SSE（信封协议）与本地 Ollama 直连两种通道。
 * <p>信封处理：answer/thinking 增量累积，tool_call/tool_result 按 id 配对成
 * 工具卡片，plan 覆盖为最新任务清单快照；多会话切换时按 chatId 过滤过期回调。
 */
export function useChatStream(options: {
  messagesApi: ChatMessagesApi
  ensureSession: (chatId: string) => void
  updateTitle: (userContent: string, aiContent: string, chatId: string) => void
  scrollToBottom: () => void
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
      router.push(`/chat/${chatId}`)
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
      thinking: '',
      tools: [],
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
        token,
        history,
        (rawData) => {
          if (currentStreamChatId !== chatId) return
          if (rawData && rawData !== '[DONE]') {
            patchMessage(chatId, aiMessageIndex, (msg) => ({
              ...msg,
              content: msg.content + rawData
            }))
            scrollIfCurrent(chatId)
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

    eventSource = chatWithMioBot(content, chatId, token)
    const es = eventSource

    // 流式异常收尾：移除空的AI消息、尝试生成标题、复位加载状态
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
      handleEmptyOrPartial(chatId, aiMessageIndex)
      finishStream()
    }

    // 连接被异常中断且收不到任何事件时，看门狗强制走异常收尾
    disarmStreamWatchdog()
    streamWatchdogTimer = setTimeout(() => {
      streamWatchdogTimer = null
      if (messagesApi.chatLoadingMap.value.get(chatId)) {
        handleStreamError(new Event('stream-watchdog'))
      }
    }, STREAM_WATCHDOG_TIMEOUT_MS)

    es.onmessage = (event: MessageEvent) => {
      const rawData = event.data

      // 不是当前会话的消息则忽略
      if (currentEventSourceChatId !== chatId) {
        return
      }

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

      scrollIfCurrent(chatId)
      if (finished) {
        finishStream()
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

  /** 把一条信封事件应用到 AI 消息上，返回新消息对象 */
  function applyEnvelope(
    msg: ChatMessage,
    parsed: Record<string, any>,
    markFinished: () => void
  ): ChatMessage {
    switch (String(parsed.type ?? '')) {
      case 'answer':
        return { ...msg, content: msg.content + String(parsed.delta ?? parsed.content ?? '') }
      case 'thinking':
        return { ...msg, thinking: (msg.thinking || '') + String(parsed.delta ?? parsed.content ?? '') }
      case 'tool_call': {
        const toolEvent: ToolEvent = {
          id: parsed.id ? String(parsed.id) : undefined,
          tool: String(parsed.tool ?? ''),
          args: parsed.args !== undefined ? String(parsed.args) : undefined,
          status: 'running'
        }
        return { ...msg, tools: [...(msg.tools ?? []), toolEvent] }
      }
      case 'tool_result': {
        const tools = matchToolResult(msg.tools ?? [], parsed)
        return { ...msg, tools }
      }
      case 'plan':
        return { ...msg, plan: (parsed.steps ?? []) as PlanStep[] }
      case 'usage':
        return {
          ...msg,
          usage: {
            inputTokens: Number(parsed.inputTokens) || undefined,
            outputTokens: Number(parsed.outputTokens) || undefined,
            durationMs: Number(parsed.durationMs) || undefined
          }
        }
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
  function matchToolResult(tools: ToolEvent[], parsed: Record<string, any>): ToolEvent[] {
    const id = parsed.id ? String(parsed.id) : ''
    const tool = String(parsed.tool ?? '')
    let targetIndex = -1
    if (id) {
      targetIndex = tools.findIndex(t => t.id === id)
    }
    if (targetIndex === -1) {
      const running = tools.map((t, i) => ({ t, i })).filter(x => x.t.status === 'running')
      const byName = running.filter(x => x.t.tool === tool)
      const picked = byName.length > 0 ? byName[byName.length - 1] : running[running.length - 1]
      targetIndex = picked ? picked.i : -1
    }
    if (targetIndex === -1) {
      // 没有配对的调用：补一张已完成卡片，保证结果不丢
      return [...tools, {
        tool,
        result: parsed.content !== undefined ? String(parsed.content) : undefined,
        status: 'done'
      }]
    }
    return tools.map((t, i) => i === targetIndex
      ? { ...t, status: 'done', result: parsed.content !== undefined ? String(parsed.content) : t.result }
      : t)
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

  function scrollIfCurrent(chatId: string): void {
    if (messagesApi.currentChatId.value === chatId) {
      nextTick(() => options.scrollToBottom())
    }
  }

  /** 异常收尾：空 AI 消息移除；已有内容则保留并标记中断 */
  function handleEmptyOrPartial(chatId: string, aiMessageIndex: number): void {
    const msgs = messagesApi.getChatMessages(chatId)
    const last = msgs[aiMessageIndex]
    if (!last) return
    const empty = !last.content && !(last.tools && last.tools.length) && !last.thinking
    if (empty) {
      messagesApi.setChatMessages(chatId, msgs.filter((_, i) => i !== aiMessageIndex))
      message.error('连接中断，请重试')
    } else {
      patchMessage(chatId, aiMessageIndex, (msg) => ({ ...msg, interrupted: true }))
    }
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
