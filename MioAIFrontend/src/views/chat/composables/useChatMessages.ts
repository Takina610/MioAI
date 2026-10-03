import { ref, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { getChatHistory } from '@/api/chatMemory'
import { useUserStore } from '@/store/user'
import type { ChatMessage } from '@/types'

export function generateConversationId(): string {
  const timestamp = Date.now().toString()
  const random = Math.floor(Math.random() * 10000).toString().padStart(4, '0')
  return timestamp + random
}

export function generateMessageId(): string {
  return Math.floor(Math.random() * 100000000).toString()
}

/** 合并连续的 assistant 消息（MioBot 多步回复的历史加载形态：中间步骤文本与最终回答连排） */
export function mergeConsecutiveAssistantMessages(msgs: ChatMessage[]): ChatMessage[] {
  if (msgs.length === 0) return []
  const result: ChatMessage[] = []
  for (const msg of msgs) {
    const last = result[result.length - 1]
    if (msg.role === 'assistant' && last && last.role === 'assistant') {
      last.content += msg.content
    } else {
      result.push({ ...msg })
    }
  }
  return result
}

/**
 * 多会话消息管理：每个会话的消息与加载状态各自维护在 Map 中，
 * 仅当会话为当前会话时同步到展示用的 messages/isLoading。
 */
export function useChatMessages(options: {
  scrollToBottom: () => void
  /** URL 携带的会话在服务端不存在（已删除或脏链接）时回调，由页面负责回到新对话 */
  onConversationMissing?: (conversationId: string) => void
}) {
  const userStore = useUserStore()

  const currentChatId = ref<string>('')
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref<boolean>(false)
  const chatMessagesMap = ref<Map<string, ChatMessage[]>>(new Map())
  const chatLoadingMap = ref<Map<string, boolean>>(new Map())

  function syncDisplay(chatId: string): void {
    if (currentChatId.value !== chatId) return
    messages.value = chatMessagesMap.value.get(chatId) || []
    isLoading.value = chatLoadingMap.value.get(chatId) || false
  }

  /** 切换当前会话；传空串则回到无会话状态 */
  function switchChat(chatId: string): void {
    currentChatId.value = chatId
    syncDisplay(chatId)
  }

  function getChatMessages(chatId: string): ChatMessage[] {
    return chatMessagesMap.value.get(chatId) || []
  }

  function setChatMessages(chatId: string, msgs: ChatMessage[]): void {
    chatMessagesMap.value.set(chatId, msgs)
    syncDisplay(chatId)
  }

  function setLoading(chatId: string, loading: boolean): void {
    if (loading) {
      chatLoadingMap.value.set(chatId, true)
    } else {
      chatLoadingMap.value.delete(chatId)
    }
    syncDisplay(chatId)
  }

  async function loadMessages(conversationId: string): Promise<void> {
    if (!userStore.isLoggedIn) return

    try {
      // 新会话刚发送首条消息时会与本请求竞态（后端会话记录尚未落库），此时静默失败并保留本地消息
      const res = await getChatHistory(conversationId, { skipErrorMessage: true })
      if (!res) {
        return
      }
      let loadedMessages = res.map((item: any, index: number) => ({
        id: `${conversationId}_${index}`,
        role: item.role,
        content: item.content,
        createTime: new Date()
      }))

      loadedMessages = mergeConsecutiveAssistantMessages(loadedMessages)

      // 该会话正在流式接收中时不覆盖 Map，仅刷新展示
      const isLoadingThisChat = chatLoadingMap.value.get(conversationId)
      if (!isLoadingThisChat) {
        chatMessagesMap.value.set(conversationId, loadedMessages)
      }

      if (currentChatId.value === conversationId) {
        messages.value = isLoadingThisChat
          ? (chatMessagesMap.value.get(conversationId) || loadedMessages)
          : loadedMessages
      }
      nextTick(() => {
        options.scrollToBottom()
      })
    } catch (e) {
      console.error(e)
      // 会话打不开（不存在或非本人）：回到新对话并提示一次，避免弹误导性的“加载消息失败”
      const bizCode = (e as Error & { code?: number }).code
      if (bizCode === 40400 || bizCode === 40101) {
        // 本地已有该会话消息说明是新会话首条消息的落库竞态，静默保留本地消息
        if (!chatMessagesMap.value.get(conversationId)?.length) {
          options.onConversationMissing?.(conversationId)
        }
        return
      }
      // 本地已有该会话的消息（正在接收中或刚创建），不提示错误
      if (!chatMessagesMap.value.get(conversationId)?.length) {
        message.error('加载消息失败')
      }
    }
  }

  return {
    currentChatId,
    messages,
    isLoading,
    chatMessagesMap,
    chatLoadingMap,
    switchChat,
    getChatMessages,
    setChatMessages,
    setLoading,
    loadMessages
  }
}

export type ChatMessagesApi = ReturnType<typeof useChatMessages>
