import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { deleteAttachment, uploadAttachment } from '@/api/chat'
import { getMyMioBotAgentId } from '@/api/agent'
import { useUserStore } from '@/store/user'
import { setChatHandoff } from '@/composables/chat/chatHandoff'
import { generateConversationId } from '@/views/chat/composables/useChatMessages'
import { usePendingCardAnimations } from '@/views/chat/composables/usePendingCardAnimations'
import { MAX_PENDING_ATTACHMENTS, isImageName, sandboxFileUrl } from '@/views/chat/attachmentUtils'
import type { AttachmentItem, PendingAttachment } from '@/types'

/** 游客直连内置 MioBot；登录用户的专属 MioBot 由后端按用户解析 */
const MIOBOT_AGENT_ID = 1

/**
 * 首页首屏输入卡：复用对话页的 ChatInput，但发送不在这里落地——
 * 会话状态与流式接收都在对话页，所以这里只把输入内容与附件备好，
 * 交接给对话页后由它走自己那条发送链路。
 */
export function useHomeChat() {
  const router = useRouter()
  const userStore = useUserStore()

  const input = ref('')
  const pendingAttachments = ref<PendingAttachment[]>([])
  /** 跳转准备中：防止连点重复交接 */
  const sending = ref(false)
  /** 附件先于会话上传：首个文件落盘即定下会话 id，跳转后首条消息沿用同一个 */
  const chatId = ref('')

  const { renderCards, leavingKeys, setCardRef } = usePendingCardAnimations(
    computed(() => pendingAttachments.value)
  )
  const uploadingCount = computed(
    () => pendingAttachments.value.filter(p => p.status === 'uploading').length
  )

  function ensureChatId(): string {
    if (!chatId.value) chatId.value = generateConversationId()
    return chatId.value
  }

  async function addFiles(files: File[]): Promise<void> {
    if (!files.length) return
    const id = ensureChatId()
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
      uploadAttachment(file, id, (percent) => {
        live.progress = percent
      })
        .then((item) => {
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
        .catch((error) => {
          console.error('附件上传失败:', error)
          live.status = 'error'
        })
    }
  }

  function removePending(key: string): void {
    const record = pendingAttachments.value.find(p => p.key === key)
    pendingAttachments.value = pendingAttachments.value.filter(p => p.key !== key)
    // 沙箱文件删除与本地预览释放延迟到离场动画后（缩略图淡出期间保持可显，避免破图闪现）
    window.setTimeout(() => {
      if (record?.localPreviewUrl) URL.revokeObjectURL(record.localPreviewUrl)
      // 已传到沙箱的文件同步删除（失败忽略：每日清理任务 7 天兜底）
      if (record?.item?.path) deleteAttachment(record.item.path).catch(() => {})
    }, 320)
  }

  /** 交接给对话页并跳转：游客走内置 MioBot，登录用户走自己的专属 MioBot */
  async function send(): Promise<void> {
    const content = input.value.trim()
    if (!content || sending.value) return
    if (uploadingCount.value > 0) {
      message.warning('附件正在上传，请稍候')
      return
    }
    sending.value = true
    try {
      if (pendingAttachments.value.some(p => p.status === 'error')) {
        message.warning('部分附件上传失败，已自动跳过')
      }
      let agentId = MIOBOT_AGENT_ID
      if (userStore.isLoggedIn) {
        // 专属 MioBot 不存在时后端会补建。解析失败就留在首页让用户重试：
        // 退回 /chat/1 会被对话页整页重定向到专属 MioBot，刷新后交接就丢了
        // （失败提示由请求层给出，这里只保证不把异常抛到点击处理器外）
        try {
          agentId = await getMyMioBotAgentId()
        } catch {
          return
        }
      }
      setChatHandoff({
        agentId,
        chatId: ensureChatId(),
        content,
        attachments: pendingAttachments.value
          .filter(p => p.status === 'done' && p.item)
          .map(p => p.item as AttachmentItem)
      })
      await router.push(`/chat/${agentId}`)
    } finally {
      sending.value = false
    }
  }

  return {
    input,
    sending,
    renderCards,
    leavingKeys,
    setCardRef,
    addFiles,
    removePending,
    send
  }
}
