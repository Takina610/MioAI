import { message } from 'ant-design-vue'
import { truncateConversation, deleteAttachment } from '@/api/chat'
import { getBotMessages } from '@/api/chat/messages'
import { useUserStore } from '@/store/user'
import { parseRefMarkers } from '../../lib/refMarkers'
import { attachmentsOf } from '../attachment/useChatAttachments'
import type { ChatMessagesApi } from './useChatMessages'
import type { AttachmentItem, ChatMessage, ModelSelection } from '@/types'

/** 发送能力（由 useChatStream 提供） */
type SendMessageFn = (
  content: string,
  options?: {
    skipUserMessage?: boolean
    skipUserPersist?: boolean
    reasoningEffort?: string
    modelSelection?: ModelSelection | null
    provider?: { baseUrl: string; apiKey: string; apiFormat: string } | null
    contextWindow?: number
    attachments?: import('@/types').AttachmentItem[]
    refs?: import('@/types').ChatRefItem[]
    history?: ChatMessage[]
    groupSeq?: number
  }
) => void

/**
 * 消息级操作：重新生成 / 编辑用户消息 / 切换回复版本 + 前置校验。
 * 从 index.vue 抽出（截断以服务端 seq 精确定位，本地列表不维护 seq）。
 */

export interface UseMessageActionsOptions {
  messagesApi: ChatMessagesApi
  currentChatId: () => string | undefined
  /** 发送消息（复用流式模块能力） */
  sendMessage: SendMessageFn
  reasoningEffort: () => string | undefined
  currentModel: () => ModelSelection
  currentProvider: () => { baseUrl: string; apiKey: string; apiFormat: string } | null
  currentModelContext: () => { contextWindow?: number } | null
}

/** 取服务端消息行（截断以 seq 精确定位，本地列表不维护 seq） */
async function fetchRows(chatId: string): Promise<Array<{ role: string; seq: number; groupSeq?: number | null }>> {
  const rows = await getBotMessages(chatId, { skipErrorMessage: true })
  return (rows ?? []).map(r => ({ role: r.role, seq: r.seq ?? 0, groupSeq: r.groupSeq ?? null }))
}

/** 旧回复快照存入版本历史（供 <n/n> 切换）；空回复不留版本 */
export function snapshotHistory(oldReply?: ChatMessage, priorHistory?: ChatMessage[]): ChatMessage[] | undefined {
  const history = [...(priorHistory ?? [])]
  if (oldReply && (oldReply.content || oldReply.blocks?.length)) {
    history.push({ ...oldReply, history: undefined, activeVersion: undefined })
  }
  return history.length ? history : undefined
}

export function useMessageActions(options: UseMessageActionsOptions) {
  const userStore = useUserStore()
  const { messagesApi } = options

  /** 消息操作的前置校验：登录 + 会话已建立 + 当前无流式任务 */
  function canModifyMessages(): boolean {
    if (!userStore.isLoggedIn) return false
    if (!options.currentChatId()) return false
    // 按当前会话判断执行状态：其他会话的执行/恢复轮询不应拦截本会话的编辑
    if (messagesApi.chatLoadingMap.value.get(options.currentChatId()!)) {
      message.warning('当前会话有任务正在执行，请等完成后再操作')
      return false
    }
    return true
  }

  /** 重新生成：截断到最后一条用户消息（本地+服务端），原文重发且不重复落库；旧回复存为版本 */
  async function handleRegenerate(): Promise<void> {
    if (!canModifyMessages()) return
    const chatId = options.currentChatId()!
    const msgs = messagesApi.getChatMessages(chatId)
    let lastUserIndex = -1
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === 'user') { lastUserIndex = i; break }
    }
    if (lastUserIndex < 0) return
    const content = msgs[lastUserIndex].content
    const oldReply = msgs[msgs.length - 1]
    const history = snapshotHistory(
      oldReply.role === 'assistant' ? oldReply : undefined,
      oldReply.role === 'assistant' ? oldReply.history : undefined
    )
    try {
      const rows = await fetchRows(chatId)
      const lastUserRow = [...rows].reverse().find(r => r.role === 'user')
      if (!lastUserRow) {
        message.error('找不到原始提问')
        return
      }
      const groupKey = lastUserRow.groupSeq ?? lastUserRow.seq
      await truncateConversation(chatId, lastUserRow.seq, groupKey)
    } catch (e) {
      console.error(e)
      message.error('操作失败，请重试')
      return
    }

    messagesApi.setChatMessages(chatId, msgs.slice(0, lastUserIndex + 1))
    options.sendMessage(content, {
      skipUserMessage: true,
      skipUserPersist: true,
      reasoningEffort: options.reasoningEffort(),
      modelSelection: options.currentModel(),
      provider: options.currentProvider(),
      contextWindow: options.currentModelContext()?.contextWindow,
      history,
      attachments: attachmentsOf(msgs[lastUserIndex])
    })
  }

  /** 编辑用户消息：截断该消息及其后历史（本地+服务端），以新内容重新发送；旧回复存为版本 */
  async function handleEditMessage(index: number, newContent: string, editAttachments?: AttachmentItem[]): Promise<void> {
    if (!canModifyMessages()) return
    const chatId = options.currentChatId()!
    const msgs = messagesApi.getChatMessages(chatId)
    if (!msgs[index] || msgs[index].role !== 'user') {
      message.error('编辑目标已变化，请重试')
      return
    }
    const oldReply = msgs.slice(index + 1).find(m => m.role === 'assistant')
    const history = snapshotHistory(oldReply, oldReply?.history)
    let groupKey: number | undefined

    try {
      const rows = await fetchRows(chatId)
      // 本地该消息是第 n 条 user → 服务端第 n 个 user 行；保留其前一行的 seq
      const userOrdinal = msgs.slice(0, index + 1).filter(m => m.role === 'user').length
      const targetRow = rows.filter(r => r.role === 'user')[userOrdinal - 1]
      if (!targetRow) {
        message.error('找不到原始提问')
        return
      }
      const prevRow = [...rows].reverse().find(r => r.seq < targetRow.seq)
      groupKey = targetRow.groupSeq ?? targetRow.seq
      await truncateConversation(chatId, prevRow ? prevRow.seq : 0, groupKey)
    } catch (e) {
      console.error(e)
      message.error('操作失败，请重试')
      return
    }

    messagesApi.setChatMessages(chatId, msgs.slice(0, index))

    // 编辑后的附件清单（确认时同步捕获）：保留的沿用原路径；被移除的原附件从沙箱删除（消息已截断，成为孤儿）
    const finalAttachments = editAttachments ?? []
    const finalPaths = new Set(finalAttachments.map(a => a.path))
    for (const a of attachmentsOf(msgs[index])) {
      if (!finalPaths.has(a.path)) {
        deleteAttachment(a.path).catch(() => {})
      }
    }
    // 编辑重发保留引用：从标记文本还原引用清单，服务端据此重新注入上下文
    const editRefs = parseRefMarkers(newContent)
    options.sendMessage(newContent, {
      reasoningEffort: options.reasoningEffort(),
      modelSelection: options.currentModel(),
      provider: options.currentProvider(),
      contextWindow: options.currentModelContext()?.contextWindow,
      history,
      groupSeq: groupKey,
      attachments: finalAttachments,
      refs: editRefs.length ? editRefs : undefined
    })
  }

  /** 切换回复版本：只改前端显示，不动服务端历史 */
  function handleSwitchVersion(messageId: string, version: number): void {
    const chatId = options.currentChatId()
    if (!chatId) return
    const msgs = messagesApi.getChatMessages(chatId)
    const idx = msgs.findIndex(m => m.id === messageId)
    if (idx < 0) return
    const updated = [...msgs]
    updated[idx] = { ...updated[idx], activeVersion: version }
    messagesApi.setChatMessages(chatId, updated)
  }

  return { canModifyMessages, handleRegenerate, handleEditMessage, handleSwitchVersion }
}
