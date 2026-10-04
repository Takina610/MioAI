import { ref, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { getBotMessages } from '@/api/botMessages'
import { useUserStore } from '@/store/user'
import type { ChatMessage, MessageBlock } from '@/types'

export function generateConversationId(): string {
  const timestamp = Date.now().toString()
  const random = Math.floor(Math.random() * 10000).toString().padStart(4, '0')
  return timestamp + random
}

export function generateMessageId(): string {
  return Math.floor(Math.random() * 100000000).toString()
}

/** 内容块的文本拼接（复制/标题生成/分享页用的 content） */
export function textOfBlocks(blocks: MessageBlock[]): string {
  return blocks
    .filter(b => b.type === 'text')
    .map(b => b.text)
    .join('')
}

/**
 * 多会话消息管理：每个会话的消息与加载状态各自维护在 Map 中，
 * 仅当会话为当前会话时同步到展示用的 messages/isLoading。
 */
export function useChatMessages(options: {
  scrollToBottom: () => void
  /** URL 携带的会话在服务端不存在（已删除或脏链接）时回调，由页面负责回到新对话 */
  onConversationMissing?: (conversationId: string) => void
  /** 加载发现末尾是 user 行（上轮回合仍在后端执行/曾被中断）：由页面接管为恢复轮询 */
  onPendingTurn?: (chatId: string) => void
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
      // agent_message 完整持久化：还原工作过程（内容块/任务清单/耗时）。
      // 新会话刚发送首条消息时会与本请求竞态（会话记录尚未落库，返回 40400）——
      // 稍候重试一次，仍不存在才按"会话缺失"处理。
      let loadedMessages: ChatMessage[] | null = null
      let rows = null as Awaited<ReturnType<typeof getBotMessages>> | null
      for (let attempt = 0; attempt < 2; attempt++) {
        try {
          rows = await getBotMessages(conversationId, { skipErrorMessage: true })
          break
        } catch (e) {
          const code = (e as Error & { code?: number }).code
          if (code !== 40400 || attempt > 0) throw e
          await new Promise(resolve => setTimeout(resolve, 1500))
        }
      }
      if (rows && rows.length > 0) {
        loadedMessages = rows.map((row, index) => {
          const blocks = (row.blocks ?? undefined) as MessageBlock[] | undefined
          return {
            id: `${conversationId}_${row.seq ?? index}`,
            role: row.role === 'user' ? 'user' : 'assistant',
            content: textOfBlocks(blocks ?? []),
            createTime: row.createTime ? new Date(row.createTime) : new Date(),
            blocks,
            plan: (row.plan ?? undefined) as ChatMessage['plan'],
            durationMs: row.durationMs ?? undefined
          }
        })
      } else {
        return
      }

      // 该会话正在流式接收中时不覆盖 Map，仅刷新展示
      const isLoadingThisChat = chatLoadingMap.value.get(conversationId)
      if (!isLoadingThisChat) {
        chatMessagesMap.value.set(conversationId, loadedMessages)
      }

      // 末尾是 user 行：上一轮的回复仍在后端执行（页面曾被关闭/丢弃/中断）——
      // 补一条进行中的空消息并交给恢复轮询接管，后端落库后自动补全
      if (
        loadedMessages.length > 0 &&
        loadedMessages[loadedMessages.length - 1].role === 'user' &&
        !isLoadingThisChat
      ) {
        const withPending: ChatMessage[] = [...loadedMessages, {
          id: `pending_${conversationId}`,
          role: 'assistant',
          content: '',
          blocks: [],
          createTime: new Date()
        }]
        chatMessagesMap.value.set(conversationId, withPending)
        options.onPendingTurn?.(conversationId)
      }

      if (currentChatId.value === conversationId) {
        messages.value = chatMessagesMap.value.get(conversationId) || loadedMessages
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
