<template>
  <div class="chat-layout">
    <ChatSidebar
      ref="sidebarRef"
      :agent-info="agentInfo"
      :chat-list="chatList"
      :current-chat-id="currentChatId"
      :chat-list-loading="chatListLoading"
      :streaming-chat-ids="streamingChatIds"
      @select="selectChat"
      @new-chat="createNewChat"
      @share="shareChat"
      @delete="requestDelete"
      @load-more="loadSessions(true)"
      @login="authModalVisible = true"
    />

    <div class="main-container">
      <div class="chat-header" v-if="currentChatId">
        <h3 class="chat-title">{{ currentChatTitle }}</h3>
      </div>
      <div class="chat-header" v-else>
        <h3 class="chat-title">新对话</h3>
        <span class="chat-subtitle">内容由AI动态生成</span>
      </div>

      <div class="chat-container">
        <ChatMessageList
          ref="messageListRef"
          :messages="messages"
          :is-loading="isLoading"
          :can-modify="userStore.isLoggedIn"
          :chat-id="currentChatId ?? undefined"
          @edit="handleEditMessage"
          @regenerate="handleRegenerate"
          @switch-version="handleSwitchVersion"
        />
        <ChatInput
          v-model="inputMessage"
          v-model:effort="reasoningEffort"
          :agent-name="agentInfo?.name"
          :agent-avatar="agentInfo?.avatar"
          :has-messages="messages.length > 0"
          :loading="isLoading"
          :supported-efforts="supportedEfforts"
          :pending="pendingAttachments"
          @add-files="handleAddFiles"
          @remove-pending="handleRemovePending"
          @send="handleSend"
        >
          <template #above-input>
            <ChatPlanPanel :plan="activePlan" :streaming="isLoading" />
          </template>
        </ChatInput>
      </div>
    </div>

    <AuthModal v-model:visible="authModalVisible" @success="handleAuthSuccess" />

    <a-modal
      v-model:open="deleteModalVisible"
      title="⚠️确定删除对话？"
      ok-text="删除"
      cancel-text="取消"
      ok-type="danger"
      :confirm-loading="deleteLoading"
      centered
      :z-index="2000"
      @ok="handleDeleteConfirm"
      @cancel="deleteModalVisible = false"
    >
      <p style="color: #666;">确定要删除该对话吗？删除后将无法恢复。</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { getAgentById } from '@/api/agent'
import { useUserStore } from '@/store/user'
import type { Agent, AttachmentItem, ChatMessage, MessageBlock, PendingAttachment } from '@/types'
import { isImageName } from './attachmentUtils'
import AuthModal from '@/components/AuthModal.vue'
import ChatSidebar from './components/ChatSidebar.vue'
import ChatMessageList from './components/ChatMessageList.vue'
import ChatInput from './components/ChatInput.vue'
import ChatPlanPanel from './components/ChatPlanPanel.vue'
import { useChatSessions } from './composables/useChatSessions'
import { useChatMessages } from './composables/useChatMessages'
import { useChatStream } from './composables/useChatStream'
import { truncateConversation, getReasoningEfforts, uploadAttachment, deleteAttachment } from '@/api/chat'
import { getBotMessages } from '@/api/botMessages'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const agentId = ref<number>(0)
const agentInfo = ref<Agent | null>(null)
const authModalVisible = ref<boolean>(false)
const inputMessage = ref<string>('')

// 思考等级：本地记忆，随每次发送透传给模型；档位列表按当前模型能力动态拉取
const reasoningEffort = ref<string>(localStorage.getItem('reasoning-effort') || 'high')
watch(reasoningEffort, v => localStorage.setItem('reasoning-effort', v))
const supportedEfforts = ref<string[]>([])

onMounted(async () => {
  try {
    const res = await getReasoningEfforts()
    supportedEfforts.value = res.efforts ?? []
    // 本地记忆的档位该模型不支持（如 medium/none）→ 回退到支持列表里最接近"高"的档
    if (supportedEfforts.value.length && !supportedEfforts.value.includes(reasoningEffort.value)) {
      reasoningEffort.value = supportedEfforts.value.includes('high')
        ? 'high'
        : supportedEfforts.value[supportedEfforts.value.length - 1]
    }
  } catch (e) {
    console.error('拉取思考档位失败，使用兜底列表', e)
  }
})

const sidebarRef = ref<InstanceType<typeof ChatSidebar> | null>(null)
const messageListRef = ref<InstanceType<typeof ChatMessageList> | null>(null)

function scrollToBottom(): void {
  messageListRef.value?.scrollToBottom()
}

/** 流式期间的贴底跟随：用户滚上去阅读时不拉动滚动条 */
function followStream(): void {
  if (messageListRef.value?.isNearBottom()) {
    messageListRef.value?.scrollToBottom()
  }
}

function scrollToChatListTop(): void {
  sidebarRef.value?.scrollToTop()
}

// 同一会话的缺失提示只弹一次（进入页面时 loadMessages 会被调两次）
let lastMissingHint = { conversationId: '', at: 0 }

const messagesApi = useChatMessages({
  scrollToBottom,
  // URL 携带的会话已不存在（被删除或脏链接）：提示一次并回到新对话
  onConversationMissing: (conversationId) => {
    const now = Date.now()
    if (lastMissingHint.conversationId !== conversationId || now - lastMissingHint.at > 2000) {
      message.warning('会话不存在或已删除')
      lastMissingHint = { conversationId, at: now }
    }
    if (currentChatId.value === conversationId) {
      switchChat('')
      router.replace(`/chat/${agentId.value}`)
    }
  },
  // 历史末尾是 user 行：回合仍在后端执行，交给流式模块恢复轮询接管
  onPendingTurn: (chatId) => {
    streamApi.recoverPendingTurn(chatId)
  }
})

const {
  chatList,
  chatListLoading,
  deleteModalVisible,
  deleteLoading,
  loadChatHistory,
  ensureSession,
  updateChatTitleWithTypewriter,
  shareChat,
  requestDelete,
  confirmDelete
} = useChatSessions({
  agentId,
  onCurrentChatDeleted: createNewChat
})

const streamApi = useChatStream({
  agentId,
  messagesApi,
  ensureSession,
  updateTitle: updateChatTitleWithTypewriter,
  scrollToBottom,
  followStream,
  scrollToChatListTop
})
const { sendMessage, prepareChatId, cleanup: cleanupStream } = streamApi

/** 正在流式执行中的会话（侧边栏显示加载动画，切走也能看出进度在跑） */
const streamingChatIds = computed(() => [...messagesApi.chatLoadingMap.value.keys()])

const { currentChatId, messages, isLoading, switchChat, loadMessages } = messagesApi

const currentChatTitle = computed(() => {
  const chat = chatList.value.find(c => c.id === currentChatId.value)
  return chat?.title || '新对话'
})

/** 当前会话最近一次任务清单（取最后一条携带 plan 的消息，随流式实时更新） */
const activePlan = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const plan = messages.value[i].plan
    if (plan && plan.length) {
      return plan
    }
  }
  return null
})

watch(
  () => route.params.agentId,
  (id) => {
    if (id) {
      agentId.value = Number(id)
      loadAgentInfo()
    }
  },
  { immediate: true }
)

watch(
  () => route.params.conversationId,
  (conversationId) => {
    if (conversationId && typeof conversationId === 'string') {
      switchChat(conversationId)
      loadMessages(conversationId)
    } else {
      switchChat('')
    }
  },
  { immediate: true }
)

// 刷新时路由 watch（immediate）可能早于启动登录校验完成，loadMessages 因未登录提前返回——
// 登录态就绪后补拉一次当前会话
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn && currentChatId.value && !messages.value.length) {
      loadMessages(currentChatId.value)
    }
  }
)

async function loadAgentInfo(): Promise<void> {
  try {
    agentInfo.value = await getAgentById(agentId.value)
    if (userStore.isLoggedIn) {
      loadSessions()
    }
  } catch (e) {
    console.error(e)
    router.push('/404')
  }
}

/** 加载会话列表；仅在真实拉取过后检查列表是否撑满一屏，避免游客/无更多页时空转 */
async function loadSessions(isLoadMore: boolean = false): Promise<void> {
  const loaded = await loadChatHistory(isLoadMore)
  if (!loaded) return
  nextTick(() => {
    sidebarRef.value?.checkListFilled()
  })
}

function createNewChat(): void {
  switchChat('')
  inputMessage.value = ''
  router.push(`/chat/${agentId.value}`)
}

function selectChat(conversationId: string): void {
  switchChat(conversationId)
  router.push(`/chat/${agentId.value}/${conversationId}`)
  loadMessages(conversationId)
}

function handleSend(): void {
  const content = inputMessage.value.trim()
  if (!content || isLoading.value) return

  // 游客仅 MioBot 可直接对话，自定义智能体引导登录
  if (agentId.value !== 1 && !userStore.isLoggedIn) {
    authModalVisible.value = true
    return
  }

  // 附件在选中时已上传；仍在途的门控等待，失败的单卡跳过并提示
  if (pendingAttachments.value.some(p => p.status === 'uploading')) {
    message.warning('附件正在上传，请稍候')
    return
  }
  if (pendingAttachments.value.some(p => p.status === 'error')) {
    message.warning('部分附件上传失败，已自动跳过')
  }
  const attachments = pendingAttachments.value
    .filter(p => p.status === 'done' && p.item)
    .map(p => p.item as AttachmentItem)

  sendMessage(content, { reasoningEffort: reasoningEffort.value, attachments })
  cleanupPendingAttachments()
  inputMessage.value = ''
}

/** 待上传附件：选中文件即开始上传到沙箱（卡片带实时进度环） */
const pendingAttachments = ref<PendingAttachment[]>([])

function handleAddFiles(files: File[]): void {
  const chatId = prepareChatId()
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
          live.previewSrc = `${import.meta.env.VITE_API_BASE_URL || ''}/bot/attachment/download?path=${encodeURIComponent(item.path)}`
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
  if (record?.localPreviewUrl) {
    URL.revokeObjectURL(record.localPreviewUrl)
  }
  // 已传到沙箱的文件同步删除（失败忽略：每日清理任务 7 天兜底）
  if (record?.item?.path) {
    deleteAttachment(record.item.path).catch(() => {})
  }
  pendingAttachments.value = pendingAttachments.value.filter(p => p.key !== key)
}

function cleanupPendingAttachments(): void {
  for (const record of pendingAttachments.value) {
    if (record.localPreviewUrl) {
      URL.revokeObjectURL(record.localPreviewUrl)
    }
  }
  pendingAttachments.value = []
}

const MAX_PENDING_ATTACHMENTS = 5

function handleDeleteConfirm(): void {
  confirmDelete((chatId) => chatId === currentChatId.value)
}

/** 消息操作的前置校验：登录 + 会话已建立 + 当前无流式任务 */
function canModifyMessages(): boolean {
  if (!userStore.isLoggedIn) return false
  if (!currentChatId.value) return false
  // 按当前会话判断执行状态：其他会话的执行/恢复轮询不应拦截本会话的编辑
  if (messagesApi.chatLoadingMap.value.get(currentChatId.value)) {
    message.warning('当前会话有任务正在执行，请等完成后再操作')
    return false
  }
  return true
}

/** 取服务端消息行（截断以 seq 精确定位，本地列表不维护 seq） */
async function fetchRows(chatId: string): Promise<Array<{ role: string; seq: number; groupSeq?: number | null }>> {
  const rows = await getBotMessages(chatId, { skipErrorMessage: true })
  return (rows ?? []).map(r => ({ role: r.role, seq: r.seq ?? 0, groupSeq: r.groupSeq ?? null }))
}

/** 旧回复快照存入版本历史（供 <n/n> 切换）；空回复不留版本 */
function snapshotHistory(oldReply?: ChatMessage, priorHistory?: ChatMessage[]): ChatMessage[] | undefined {
  const history = [...(priorHistory ?? [])]
  if (oldReply && (oldReply.content || oldReply.blocks?.length)) {
    history.push({ ...oldReply, history: undefined, activeVersion: undefined })
  }
  return history.length ? history : undefined
}

/** 重新生成：截断到最后一条用户消息（本地+服务端），原文重发且不重复落库；旧回复存为版本 */
async function handleRegenerate(): Promise<void> {
  if (!canModifyMessages()) return
  const chatId = currentChatId.value
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
  sendMessage(content, { skipUserMessage: true, skipUserPersist: true, reasoningEffort: reasoningEffort.value, history, attachments: attachmentsOf(msgs[lastUserIndex]) })
}

/** 编辑用户消息：截断该消息及其后历史（本地+服务端），以新内容重新发送；旧回复存为版本 */
async function handleEditMessage(index: number, newContent: string): Promise<void> {
  if (!canModifyMessages()) return
  const chatId = currentChatId.value
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
  sendMessage(newContent, { reasoningEffort: reasoningEffort.value, history, groupSeq: groupKey, attachments: attachmentsOf(msgs[index]) })
}

/** 该消息随发的附件（attachments 输入块）：编辑/重生成重发时沿用原路径 */
function attachmentsOf(msg: ChatMessage): AttachmentItem[] {
  return (msg.blocks ?? [])
    .filter((b): b is Extract<MessageBlock, { type: 'attachments' }> => b.type === 'attachments')
    .filter(b => b.side === 'input')
    .flatMap(b => b.items)
}

/** 切换回复版本：只改前端显示，不动服务端历史 */
function handleSwitchVersion(messageId: string, version: number): void {
  const chatId = currentChatId.value
  if (!chatId) return
  const msgs = messagesApi.getChatMessages(chatId)
  const idx = msgs.findIndex(m => m.id === messageId)
  if (idx < 0) return
  const updated = [...msgs]
  updated[idx] = { ...updated[idx], activeVersion: version }
  messagesApi.setChatMessages(chatId, updated)
}

function handleKeyboardShortcut(e: KeyboardEvent): void {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    createNewChat()
  }
}

function handleAuthSuccess(): void {
  window.location.reload()
}

onMounted(() => {
  window.addEventListener('keydown', handleKeyboardShortcut)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyboardShortcut)
})

// 组件销毁前关闭流式连接
onBeforeUnmount(() => {
  cleanupStream()
})
</script>

<style lang="scss" scoped>
.chat-layout {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.chat-header {
  height: 64px;
  min-height: 64px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 24px;
  border-bottom: 1px solid #e8eaed;

  .chat-title {
    font-size: 16px;
    font-weight: 600;
    color: #1d2129;
    margin: 0;
  }

  .chat-subtitle {
    font-size: 12px;
    color: #86909c;
  }
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}
</style>
