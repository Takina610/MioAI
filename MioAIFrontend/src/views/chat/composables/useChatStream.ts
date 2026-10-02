import { nextTick, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import {
  chatWithCSApp,
  chatWithDefaultAgent,
  chatWithMioManus,
  chatWithCustomAgent,
  chatWithStream
} from '@/api/chat'
import type { ChatMessage, MessageSegment } from '@/types'
import {
  generateConversationId,
  generateMessageId,
  type ChatMessagesApi
} from './useChatMessages'

// 后端 SseEmitter 45s 超时，看门狗阈值需大于它，只在连接"挂死"时触发
const STREAM_WATCHDOG_TIMEOUT_MS = 60000

/**
 * 消息发送与流式接收：SSE（服务端各智能体接口）与本地 Ollama 直连两种通道，
 * 含流式看门狗（连接挂死时强制异常收尾）与多会话切换时的过期回调过滤。
 */
export function useChatStream(options: {
  agentId: Ref<number>
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
  let currentEventSourceChatId = '' // 当前 EventSource 对应的会话ID
  let activeStream: { close: () => void } | null = null
  let currentStreamChatId = '' // 当前 Ollama 流式请求对应的会话ID
  let streamWatchdogTimer: ReturnType<typeof setTimeout> | null = null // 流式看门狗：连接被异常中断且收不到任何事件时强制收尾

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

    const isMioManus = options.agentId.value === 3
    const aiMessage: ChatMessage = {
      id: generateMessageId(),
      role: 'assistant',
      content: '',
      createTime: new Date(),
      segments: isMioManus ? [] : undefined
    }
    messagesApi.setChatMessages(chatId, [...chatMessages, aiMessage])

    const token: string = localStorage.getItem('token') || ''
    const userId = userStore.userInfo?.id || null

    // 本地大模型：Ollama 直连
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
        userId,
        history,
        (rawData) => {
          if (currentStreamChatId !== chatId) return
          if (rawData && rawData !== '[DONE]') {
            const msgs = messagesApi.getChatMessages(chatId)
            if (aiMessageIndex < msgs.length) {
              const updated = [...msgs]
              updated[aiMessageIndex] = {
                ...updated[aiMessageIndex],
                content: updated[aiMessageIndex].content + rawData
              }
              messagesApi.setChatMessages(chatId, updated)
              if (messagesApi.currentChatId.value === chatId) {
                nextTick(() => options.scrollToBottom())
              }
            }
          }
          if (rawData === '[DONE]') {
            messagesApi.setLoading(chatId, false)
            activeStream = null
            currentStreamChatId = ''
          }
        },
        (error) => {
          console.error('Ollama 错误:', error)
          // 移除本轮发送的空 AI 消息；已有部分内容则保留并标记中断，与 SSE 异常收尾行为一致
          const ollamaMessages = messagesApi.getChatMessages(chatId)
          const ollamaLast = ollamaMessages[ollamaMessages.length - 1]
          if (ollamaLast && ollamaLast.role === 'assistant' && !ollamaLast.content) {
            messagesApi.setChatMessages(chatId, ollamaMessages.slice(0, -1))
          } else if (ollamaLast && ollamaLast.role === 'assistant') {
            const updatedMessages = [...ollamaMessages]
            updatedMessages[updatedMessages.length - 1] = { ...ollamaLast, interrupted: true }
            messagesApi.setChatMessages(chatId, updatedMessages)
          }
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

    const agentId = options.agentId.value
    if (agentId === 1) {
      eventSource = chatWithDefaultAgent(content, chatId, agentId, userId)
    } else if (agentId === 2) {
      eventSource = chatWithCSApp(content, chatId, agentId, token)
    } else if (agentId === 3) {
      eventSource = chatWithMioManus(content, chatId, agentId, token)
    } else {
      eventSource = chatWithCustomAgent(content, chatId, agentId, token)
    }
    const es = eventSource

    // 流式异常收尾：移除空的AI消息、尝试生成标题、复位加载状态
    const handleStreamError = (error: Event) => {
      console.error('SSE连接错误:', error)

      // 不是当前会话的消息则忽略
      if (currentEventSourceChatId !== chatId) {
        return
      }
      // 该会话已不在加载中，说明消息已完成接收（[DONE]已处理），忽略此错误
      if (!messagesApi.chatLoadingMap.value.get(chatId)) {
        return
      }

      disarmStreamWatchdog()

      // 移除空的AI消息；已有部分内容则保留并标记中断
      const errorMessages = messagesApi.getChatMessages(chatId)
      const lastMsg = errorMessages[errorMessages.length - 1]
      if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
        messagesApi.setChatMessages(chatId, errorMessages.slice(0, -1))
        message.error('连接中断，请重试')
      } else if (lastMsg && lastMsg.role === 'assistant') {
        const updatedMessages = [...errorMessages]
        updatedMessages[updatedMessages.length - 1] = { ...lastMsg, interrupted: true }
        messagesApi.setChatMessages(chatId, updatedMessages)
      }

      if (isNewChat && userStore.isLoggedIn) {
        const finalContent = messagesApi.getChatMessages(chatId)[aiMessageIndex]?.content || ''
        options.updateTitle(content, finalContent, chatId)
      }

      messagesApi.setLoading(chatId, false)
      es.close()
      eventSource = null
      currentEventSourceChatId = ''
    }

    // 连接被异常中断且收不到任何事件时（如代理未转发断连），看门狗强制走异常收尾
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

      // 旧协议结束标记
      if (rawData === '[DONE]') {
        finishStream()
        return
      }

      const chatMsgs = messagesApi.getChatMessages(chatId)
      if (aiMessageIndex >= chatMsgs.length) {
        return
      }

      let parsed: Record<string, any> | null = null
      try {
        const obj = JSON.parse(rawData)
        if (obj && typeof obj === 'object') parsed = obj
      } catch {
        // 旧协议：裸文本 chunk，按正文增量处理
      }

      const updated = [...chatMsgs]
      const target = updated[aiMessageIndex]
      // 无分段事件时保持 undefined：空数组也是 truthy，会导致消息在
      // MarkdownView 与 MioManusMessage 两个分支间切换、组件补丁崩溃
      let segments = target.segments
      let content = target.content
      let usage = target.usage
      let finished = false

      if (!parsed) {
        content += rawData
      } else {
        const type = String(parsed.type ?? '')
        switch (type) {
          case 'answer':
            // 正文增量
            content += String(parsed.delta ?? parsed.content ?? '')
            break
          case 'usage':
            usage = {
              inputTokens: Number(parsed.inputTokens) || undefined,
              outputTokens: Number(parsed.outputTokens) || undefined,
              durationMs: Number(parsed.durationMs) || undefined
            }
            break
          case 'done':
            finished = true
            break
          default: {
            // 其余类型归入分段：delta 语义合并到同类末段，content 语义为整段新增
            const next = [...(segments ?? [])]
            const isDelta = parsed.delta !== undefined
            const text = String(parsed.delta ?? parsed.content ?? '')
            const last = next[next.length - 1]
            if (isDelta && last && last.type === type) {
              next[next.length - 1] = { ...last, content: last.content + text }
            } else {
              next.push({
                type: (type || undefined) as MessageSegment['type'],
                content: text,
                tool: parsed.tool,
                args: parsed.args
              })
            }
            segments = next
            // 思考/动作整段与最终回复计入 content（标题生成、复制仍可用）
            if (!isDelta && (type === 'thinking' || type === 'action' || type === 'final')) {
              content += text
            }
            break
          }
        }
      }

      updated[aiMessageIndex] = { ...target, content, segments, usage }
      messagesApi.setChatMessages(chatId, updated)

      if (messagesApi.currentChatId.value === chatId) {
        nextTick(() => options.scrollToBottom())
      }
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
