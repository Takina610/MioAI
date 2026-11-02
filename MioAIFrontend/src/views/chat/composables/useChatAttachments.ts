import { computed, nextTick, ref, watch } from 'vue'
import { uploadAttachment, deleteAttachment } from '@/api/chat'
import { usePendingCardAnimations } from './usePendingCardAnimations'
import { isImageName, MAX_PENDING_ATTACHMENTS, sandboxFileUrl } from '../attachmentUtils'
import { generateConversationId } from './useChatMessages'
import { useUserStore } from '@/store/user'
import type { AttachmentItem, ChatMessage, MessageBlock, PendingAttachment } from '@/types'

/**
 * 聊天页待传附件：选中即上传（进度环）、发送前门控、按会话隔离草稿、
 * 编辑消息时的附件清单。从 index.vue 抽出。
 */

/** 该消息随发的附件（attachments 输入块）：编辑/重生成重发时沿用原路径 */
export function attachmentsOf(msg: ChatMessage): AttachmentItem[] {
  return (msg.blocks ?? [])
    .filter((b): b is Extract<MessageBlock, { type: 'attachments' }> => b.type === 'attachments')
    .filter(b => b.side === 'input')
    .flatMap(b => b.items)
}

export interface UseChatAttachmentsOptions {
  /** 当前会话 id（可能为空串 = 未落会话的新会话） */
  currentChatId: () => string
  /** 路由上的会话 id（整页刷新后内存态尚未恢复时的兜底） */
  routeConversationId: () => string | undefined
  /** 当前输入文本（草稿按会话隔离） */
  inputMessage: () => string
  /** 恢复某会话草稿文本 */
  setInputMessage: (text: string) => void
  /** 设置当前会话 id（转正时写入） */
  setCurrentChatId: (id: string) => void
  /** 取某会话消息列表（编辑时定位原附件） */
  getMessages: (chatId: string) => ChatMessage[]
}

export function useChatAttachments(options: UseChatAttachmentsOptions) {
  const userStore = useUserStore()

  const pendingAttachments = ref<PendingAttachment[]>([])

  /** 编辑会话消息时的附件（原附件 + 新追加），发送时按最终清单生效 */
  const editAttachments = ref<PendingAttachment[]>([])

  // 待上传卡片动画（GSAP）：实例挂在本页——会话切换的 watcher 里能先 arm 静默再换表，
  // 保证切换/新建时卡片直接落位（子组件内实例化时 watcher 顺序颠倒，静默标志永远晚到）
  const {
    renderCards,
    leavingKeys,
    setCardRef,
    beginSilentSwap: beginPendingSilentSwap
  } = usePendingCardAnimations(computed(() => pendingAttachments.value))

  // 编辑列表与输入框同一套 GSAP 进出场动画（独立实例，互不干扰）
  const {
    renderCards: renderEditCards,
    leavingKeys: leavingEditKeys,
    setCardRef: setEditCardRef
  } = usePendingCardAnimations(computed(() => editAttachments.value))

  // 草稿与待传附件按会话隔离：切换时各自保存/恢复。'' 代表未落会话的新会话状态——
  // 纯文字、纯文件、图文混合都要记录。切换/新建由 ChatInput 侧 arm 静默（整表直接落位不播动画）；
  // ''→真实 id 的"转正"迁移在 prepareChat 里做（见下），此处只做纯存/取
  const draftByChat = new Map<string, string>()
  const pendingByChat = new Map<string, PendingAttachment[]>()

  watch(options.currentChatId, (newId, oldId) => {
    const from = oldId ?? ''
    const to = newId ?? ''
    if (from === to) return
    // 会话切换/新建=附件整表替换：先 arm 静默（本 watcher 内同步生效），卡片直接落位不播动画
    beginPendingSilentSwap()
    draftByChat.set(from, options.inputMessage())
    pendingByChat.set(from, pendingAttachments.value)
    options.setInputMessage(draftByChat.get(to) ?? '')
    pendingAttachments.value = pendingByChat.get(to) ?? []
  })

  /**
   * 未落会话转正：'' 新会话产生首个附件时生成正式会话 id，
   * 并把草稿/待传附件迁移过去（隔离 watch 随后加载的就是迁移后的状态，不会清空）
   */
  function prepareChat(): string {
    // 整页刷新后 currentChatId 可能尚未恢复，路由上的会话 id 优先
    const existing = options.currentChatId() || options.routeConversationId() || ''
    if (existing) {
      options.setCurrentChatId(existing)
      return existing
    }
    const id = userStore.isLoggedIn ? generateConversationId() : 'temp_' + Date.now()
    draftByChat.set(id, options.inputMessage())
    pendingByChat.set(id, pendingAttachments.value)
    options.setCurrentChatId(id)
    return id
  }

  async function handleAddFiles(files: File[]): Promise<void> {
    const chatId = prepareChat()
    // 转正触发的会话隔离 watch 在 nextTick 内完成加载，先等它再 push
    await nextTick()
    const slots = MAX_PENDING_ATTACHMENTS - pendingAttachments.value.length
    for (const file of files.slice(0, Math.max(0, slots))) {
      const record: PendingAttachment = {
        key: `pending_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
        name: file.name,
        size: file.size,
        status: 'uploading',
        progress: 0,
        previewSrc: isImageName(file.name) ? URL.createObjectURL(file) : undefined
      }
      record.localPreviewUrl = record.previewSrc
      // 经响应式代理写入：闭包里必须持有代理引用，直接改原始对象不会触发渲染
      pendingAttachments.value.push(record)
      const live = pendingAttachments.value[pendingAttachments.value.length - 1]
      uploadAttachment(file, chatId, percent => {
        live.progress = percent
      })
        .then(item => {
          live.status = 'done'
          live.progress = 1
          live.item = item
          if (live.localPreviewUrl) {
            // 上传完成：改用沙箱接口出图，释放本地预览
            URL.revokeObjectURL(live.localPreviewUrl)
            live.localPreviewUrl = undefined
            live.previewSrc = sandboxFileUrl(item.path)
          }
        })
        .catch(error => {
          console.error('附件上传失败:', error)
          live.status = 'error'
        })
    }
  }

  function handleRemovePending(key: string): void {
    const record = pendingAttachments.value.find(p => p.key === key)
    pendingAttachments.value = pendingAttachments.value.filter(p => p.key !== key)
    // 沙箱文件删除与本地预览释放延迟到离场动画后（缩略图淡出期间保持可显，避免破图闪现）
    window.setTimeout(() => {
      if (record?.localPreviewUrl) {
        URL.revokeObjectURL(record.localPreviewUrl)
      }
      // 已传到沙箱的文件同步删除（失败忽略：每日清理任务 7 天兜底）
      if (record?.item?.path) {
        deleteAttachment(record.item.path).catch(() => {})
      }
    }, 320)
  }

  function cleanupPendingAttachments(): void {
    const records = pendingAttachments.value
    pendingAttachments.value = []
    // 本地预览释放延迟到离场动画后（发送时全部卡片向中间缩小淡出，期间缩略图保持可显）
    window.setTimeout(() => {
      for (const record of records) {
        if (record.localPreviewUrl) {
          URL.revokeObjectURL(record.localPreviewUrl)
        }
      }
    }, 320)
  }

  /** 即将发送的附件（done 且有 item 的） */
  function readyAttachments(): AttachmentItem[] {
    return pendingAttachments.value
      .filter(p => p.status === 'done' && p.item)
      .map(p => p.item as AttachmentItem)
  }

  function hasUploading(): boolean {
    return pendingAttachments.value.some(p => p.status === 'uploading')
  }

  function hasUploadError(): boolean {
    return pendingAttachments.value.some(p => p.status === 'error')
  }

  function handleEditStart(index: number): void {
    const msg = options.getMessages(options.currentChatId())[index]
    if (!msg) return
    editAttachments.value = attachmentsOf(msg).map(a => ({
      key: a.path,
      name: a.name,
      size: a.size,
      status: 'done' as const,
      progress: 1,
      item: { ...a },
      previewSrc: isImageName(a.name) ? sandboxFileUrl(a.path) : undefined
    }))
  }

  function handleEditCancel(): void {
    for (const record of editAttachments.value) {
      if (record.localPreviewUrl) {
        URL.revokeObjectURL(record.localPreviewUrl)
      }
    }
    editAttachments.value = []
  }

  /** 编辑中新追加文件：走与输入框相同的上传流程 */
  async function handleAddEditFiles(files: File[]): Promise<void> {
    const chatId = options.currentChatId()
    if (!chatId) return
    for (const file of files.slice(0, Math.max(0, MAX_PENDING_ATTACHMENTS - editAttachments.value.length))) {
      const record: PendingAttachment = {
        key: `edit_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
        name: file.name,
        size: file.size,
        status: 'uploading',
        progress: 0,
        previewSrc: isImageName(file.name) ? URL.createObjectURL(file) : undefined,
        addedDuringEdit: true
      }
      record.localPreviewUrl = record.previewSrc
      editAttachments.value.push(record)
      const live = editAttachments.value[editAttachments.value.length - 1]
      uploadAttachment(file, chatId, percent => {
        live.progress = percent
      })
        .then(item => {
          live.status = 'done'
          live.progress = 1
          live.item = item
          if (live.localPreviewUrl) {
            URL.revokeObjectURL(live.localPreviewUrl)
            live.localPreviewUrl = undefined
            live.previewSrc = sandboxFileUrl(item.path)
          }
        })
        .catch(error => {
          console.error('附件上传失败:', error)
          live.status = 'error'
        })
    }
  }

  /** 编辑中移除附件：本次编辑新追加且已上传的立即从沙箱删除；原附件在发送时按 diff 删除 */
  function handleRemoveEditAtt(key: string): void {
    const record = editAttachments.value.find(p => p.key === key)
    if (record?.localPreviewUrl) {
      URL.revokeObjectURL(record.localPreviewUrl)
    }
    if (record?.addedDuringEdit && record.item?.path) {
      deleteAttachment(record.item.path).catch(() => {})
    }
    editAttachments.value = editAttachments.value.filter(p => p.key !== key)
  }

  return {
    pendingAttachments,
    editAttachments,
    renderCards,
    leavingKeys,
    setCardRef,
    renderEditCards,
    leavingEditKeys,
    setEditCardRef,
    handleAddFiles,
    handleRemovePending,
    cleanupPendingAttachments,
    readyAttachments,
    hasUploading,
    hasUploadError,
    handleEditStart,
    handleEditCancel,
    handleAddEditFiles,
    handleRemoveEditAtt
  }
}
