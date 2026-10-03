import { ref, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import { getChatIdsPage, deleteChat as deleteChatApi } from '@/api/chatMemory'
import { generateTitle } from '@/api/chat'
import { useUserStore } from '@/store/user'
import type { ChatSession } from '@/types'

/**
 * 侧边栏会话列表：分页加载、置顶/新建占位、删除确认、分享链接、标题打字机。
 */
export function useChatSessions(options: {
  agentId: Ref<number>
  /** 删除的是当前会话时，由页面负责回到新对话状态 */
  onCurrentChatDeleted: () => void
}) {
  const userStore = useUserStore()

  const chatList = ref<ChatSession[]>([])
  const chatListLoading = ref<boolean>(false)
  const chatListHasMore = ref<boolean>(true)
  const chatListCurrent = ref<number>(1)
  const chatListTotal = ref<number>(0)
  const chatListPageSize = ref<number>(10)

  const deleteModalVisible = ref<boolean>(false)
  const deleteLoading = ref<boolean>(false)
  const pendingDeleteChatId = ref<string>('')

  /** 实际执行了拉取返回 true；未登录/加载中/无更多页时跳过并返回 false */
  async function loadChatHistory(isLoadMore: boolean = false): Promise<boolean> {
    if (!userStore.isLoggedIn) return false
    if (chatListLoading.value) return false
    if (isLoadMore && !chatListHasMore.value) return false

    chatListLoading.value = true

    try {
      const current = isLoadMore ? chatListCurrent.value + 1 : 1
      const res = await getChatIdsPage(options.agentId.value, current, chatListPageSize.value)

      const newChats = res.records.map((item: any) => ({
        id: item.conversationId,
        title: item.title || '新对话',
        updateTime: item.updateTime ? new Date(item.updateTime) : new Date(),
        hasMessage: true
      }))

      chatList.value = isLoadMore ? [...chatList.value, ...newChats] : newChats

      chatListCurrent.value = res.current
      chatListTotal.value = res.total
      chatListHasMore.value = res.current < res.pages
    } catch (e) {
      console.error(e)
    } finally {
      chatListLoading.value = false
    }
    return true
  }

  /** 发送消息后将会话置顶；不存在（新会话）则插入占位项 */
  function ensureSession(chatId: string): void {
    const index = chatList.value.findIndex(c => c.id === chatId)
    if (index === -1) {
      chatList.value.unshift({
        id: chatId,
        title: '新对话',
        updateTime: new Date(),
        hasMessage: true
      })
    } else {
      const existingChat = chatList.value[index]
      existingChat.updateTime = new Date()
      chatList.value.splice(index, 1)
      chatList.value.unshift(existingChat)
    }
  }

  /** 新会话首轮对话完成后生成标题，并逐字打出 */
  async function updateChatTitleWithTypewriter(
    userContent: string,
    aiContent: string,
    chatId: string
  ): Promise<void> {
    const title = await generateTitle(options.agentId.value, chatId, userContent + '\n' + aiContent)

    const chatItem = chatList.value.find(c => c.id === chatId)
    if (!chatItem) return

    chatItem.title = ''

    for (let i = 0; i < title.length; i++) {
      chatItem.title += title[i]
      await new Promise(resolve => setTimeout(resolve, 50))
    }
  }

  function shareChat(conversationId: string): void {
    const shareUrl = `${window.location.origin}/share/${options.agentId.value}/${conversationId}`
    navigator.clipboard.writeText(shareUrl).then(() => {
      message.success('分享链接已复制到剪贴板')
    }).catch(() => {
      message.error('复制失败')
    })
  }

  function requestDelete(conversationId: string): void {
    pendingDeleteChatId.value = conversationId
    deleteModalVisible.value = true
  }

  async function confirmDelete(
    isCurrentChat: (chatId: string) => boolean
  ): Promise<void> {
    if (!pendingDeleteChatId.value) return

    deleteLoading.value = true
    try {
      await deleteChatApi(pendingDeleteChatId.value)
      chatList.value = chatList.value.filter(c => c.id !== pendingDeleteChatId.value)
      if (isCurrentChat(pendingDeleteChatId.value)) {
        options.onCurrentChatDeleted()
      }
      message.success('删除成功')
      deleteModalVisible.value = false
    } catch (e) {
      console.error(e)
      message.error('删除失败')
    } finally {
      deleteLoading.value = false
      pendingDeleteChatId.value = ''
    }
  }

  return {
    chatList,
    chatListLoading,
    chatListHasMore,
    deleteModalVisible,
    deleteLoading,
    loadChatHistory,
    ensureSession,
    updateChatTitleWithTypewriter,
    shareChat,
    requestDelete,
    confirmDelete
  }
}
