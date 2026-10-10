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
          :edit-render-cards="renderEditCards"
          :edit-leaving-keys="leavingEditKeys"
          :edit-ref-setter="setEditCardRef"
          @edit="handleEditMessage"
          @edit-start="handleEditStart"
          @edit-cancel="handleEditCancel"
          @add-edit-files="handleAddEditFiles"
          @remove-edit-att="handleRemoveEditAtt"
          @regenerate="handleRegenerate"
          @switch-version="handleSwitchVersion"
          @open-subagent="subagentPanelTarget = $event"
        />
        <ChatInput
          v-model="inputMessage"
          v-model:effort="reasoningEffort"
          :agent-name="agentInfo?.name"
          :agent-avatar="agentInfo?.avatar"
          :has-messages="messages.length > 0"
          :loading="isLoading"
          :supported-efforts="supportedEfforts"
          :render-cards="renderCards"
          :leaving-keys="leavingKeys"
          :ref-setter="setCardRef"
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

    <!-- 子智能体只读对话面板（点击主消息流的子智能体行打开，仅浏览不可交互）。
         外壳承载宽度动画（0↔最终宽）：聊天区随抽屉展开/收缩平滑推移，面板本体定宽不回流 -->
    <Transition name="subagent-drawer">
      <div v-if="subagentTranscript" class="subagent-drawer">
        <SubagentPanel
          :transcript="subagentTranscript"
          @close="subagentPanelTarget = null"
        />
      </div>
    </Transition>

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
      <p style="color: #6e6b62;">确定要删除该对话吗？删除后将无法恢复。</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { getAgentById } from '@/api/agent'
import { useUserStore } from '@/store/user'
import type { Agent, AttachmentItem, ChatMessage, MessageBlock, PendingAttachment, SubagentPanelTarget } from '@/types'
import { isImageName } from './attachmentUtils'
import AuthModal from '@/components/AuthModal.vue'
import ChatSidebar from './components/ChatSidebar.vue'
import ChatMessageList from './components/ChatMessageList.vue'
import ChatInput from './components/ChatInput.vue'
import ChatPlanPanel from './components/ChatPlanPanel.vue'
import SubagentPanel from './components/SubagentPanel.vue'
import { useChatSessions } from './composables/useChatSessions'
import { useChatMessages, generateConversationId } from './composables/useChatMessages'
import { useChatStream } from './composables/useChatStream'
import { usePendingCardAnimations } from './composables/usePendingCardAnimations'
import { buildSubagentTranscript } from './subagentTranscript'
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

// ---------- 子智能体只读面板 ----------
// 打开键持久指向某条消息的某个 Agent 块；内容随消息块流实时重建（流式期间面板同步更新）
const subagentPanelTarget = ref<SubagentPanelTarget | null>(null)

/** 按版本号取该消息显示的内容块（与消息列表的版本切换语义一致） */
function blocksAtVersion(msg: ChatMessage, version: number): MessageBlock[] | undefined {
  const total = (msg.history?.length ?? 0) + 1
  if (version >= total || !msg.history?.length) return msg.blocks
  return msg.history[version - 1]?.blocks
}

const subagentTranscript = computed(() => {
  const target = subagentPanelTarget.value
  if (!target) return null
  const msg = messages.value.find(m => m.id === target.messageId)
  const blocks = msg ? blocksAtVersion(msg, target.version) : undefined
  if (!blocks?.length) return null
  const idx = target.blockKey.startsWith('id:')
    ? blocks.findIndex(b => b.type === 'tool' && b.id === target.blockKey.slice(3))
    : Number(target.blockKey.slice(4))
  if (!Number.isInteger(idx) || idx < 0 || idx >= blocks.length) return null
  return buildSubagentTranscript(blocks, idx)
})

// 会话切换即收起面板（面板内容属于当前会话的消息块）
watch(
  () => messagesApi.currentChatId.value,
  () => {
    subagentPanelTarget.value = null
  }
)

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
  // 草稿/待传附件的清空与恢复由会话隔离 watch 接管（'' 键即新会话状态）
  switchChat('')
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

/**
 * 未落会话转正：'' 新会话产生首个附件时生成正式会话 id，
 * 并把草稿/待传附件迁移过去（隔离 watch 随后加载的就是迁移后的状态，不会清空）
 */
function prepareChat(): string {
  // 整页刷新后 currentChatId 可能尚未恢复，路由上的会话 id 优先
  const existing = messagesApi.currentChatId.value || (route.params.conversationId as string) || ''
  if (existing) {
    messagesApi.currentChatId.value = existing
    return existing
  }
  const id = userStore.isLoggedIn ? generateConversationId() : 'temp_' + Date.now()
  draftByChat.set(id, inputMessage.value)
  pendingByChat.set(id, pendingAttachments.value)
  messagesApi.currentChatId.value = id
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

const MAX_PENDING_ATTACHMENTS = 50

/** 编辑会话消息时的附件（原附件 + 新追加），发送时按最终清单生效 */
const editAttachments = ref<PendingAttachment[]>([])

// 编辑列表与输入框同一套 GSAP 进出场动画（独立实例，互不干扰）
const {
  renderCards: renderEditCards,
  leavingKeys: leavingEditKeys,
  setCardRef: setEditCardRef,
} = usePendingCardAnimations(computed(() => editAttachments.value))

function handleEditStart(index: number): void {
  const msg = messagesApi.getChatMessages(currentChatId.value)[index]
  if (!msg) return
  editAttachments.value = attachmentsOf(msg).map(a => ({
    key: a.path,
    name: a.name,
    size: a.size,
    status: 'done' as const,
    progress: 1,
    item: { ...a },
    previewSrc: isImageName(a.name)
      ? `${import.meta.env.VITE_API_BASE_URL || ''}/bot/attachment/download?path=${encodeURIComponent(a.path)}`
      : undefined
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
  const chatId = currentChatId.value
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
          live.previewSrc = `${import.meta.env.VITE_API_BASE_URL || ''}/bot/attachment/download?path=${encodeURIComponent(item.path)}`
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

// 待上传卡片动画（GSAP）：实例挂在本页——会话切换的 watcher 里能先 arm 静默再换表，
// 保证切换/新建时卡片直接落位（子组件内实例化时 watcher 顺序颠倒，静默标志永远晚到）
const {
  renderCards,
  leavingKeys,
  setCardRef,
  beginSilentSwap: beginPendingSilentSwap,
} = usePendingCardAnimations(computed(() => pendingAttachments.value))

// 草稿与待传附件按会话隔离：切换时各自保存/恢复。'' 代表未落会话的新会话状态——
// 纯文字、纯文件、图文混合都要记录。切换/新建由 ChatInput 侧 arm 静默（整表直接落位不播动画）；
// ''→真实 id 的"转正"迁移在 prepareChat 里做（见下），此处只做纯存/取
const draftByChat = new Map<string, string>()
const pendingByChat = new Map<string, PendingAttachment[]>()
watch(
  () => messagesApi.currentChatId.value,
  (newId, oldId) => {
    const from = oldId ?? ''
    const to = newId ?? ''
    if (from === to) return
    // 会话切换/新建=附件整表替换：先 arm 静默（本 watcher 内同步生效），卡片直接落位不播动画
    beginPendingSilentSwap()
    draftByChat.set(from, inputMessage.value)
    pendingByChat.set(from, pendingAttachments.value)
    inputMessage.value = draftByChat.get(to) ?? ''
    pendingAttachments.value = pendingByChat.get(to) ?? []
  },
)

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
async function handleEditMessage(index: number, newContent: string, editAttachments?: AttachmentItem[]): Promise<void> {
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

  // 编辑后的附件清单（确认时同步捕获）：保留的沿用原路径；被移除的原附件从沙箱删除（消息已截断，成为孤儿）
  const finalAttachments = editAttachments ?? []
  const finalPaths = new Set(finalAttachments.map(a => a.path))
  for (const a of attachmentsOf(msgs[index])) {
    if (!finalPaths.has(a.path)) {
      deleteAttachment(a.path).catch(() => {})
    }
  }
  sendMessage(newContent, { reasoningEffort: reasoningEffort.value, history, groupSeq: groupKey, attachments: finalAttachments })
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
  background: #f0eee6;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: $bg-ivory;
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
  border-bottom: 1px solid #e8e6dc;

  .chat-title {
    font-size: 16px;
    font-weight: 600;
    color: #141413;
    margin: 0;
  }

  .chat-subtitle {
    font-size: 12px;
    color: #8c8a82;
  }
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

// 子智能体面板外壳：宽度驱动的抽屉（0↔最终宽）。聊天区随宽度变化逐帧推移，
// 面板本体保持定宽由外壳裁切，内容不回流；无 transform，入场/退场结束时聊天区
// 已处于最终位置，不会出现动画结束后突然回跳
.subagent-drawer {
  flex-shrink: 0;
  width: clamp(360px, 36vw, 560px);
  height: 100vh;
  overflow: hidden;
  background: $bg-ivory;
  border-left: 1px solid $border-warm-subtle;
  box-shadow: -8px 0 24px rgba(20, 20, 19, 0.04);
}

.subagent-drawer-enter-active,
.subagent-drawer-leave-active {
  transition: width 0.26s cubic-bezier(0.32, 0.72, 0.35, 1), border-left-width 0.26s;
  will-change: width;
}

// enter-from 必须是 width:0（起始态）；leave-from 不能设宽度（须等于当前 560，
// 否则离场首帧吸附到 0、过渡变成 0→0 退化为瞬隐），离场只由 leave-to 收到 0
.subagent-drawer-enter-from {
  width: 0;
  border-left-width: 0;
}

.subagent-drawer-leave-to {
  width: 0;
  border-left-width: 0;
}
</style>
