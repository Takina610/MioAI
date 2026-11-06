<template>
  <div
    class="chat-layout"
    :class="{
      'sidebar-collapsed': sidebarCollapsed,
      'rail-open': railOpen,
      'rail-peek': railPeeking,
      'rail-wide': (railOpen || railPeeking) && agentRailExpanded,
      'is-mobile-shell': isMobile,
      // 任一移动端覆盖层（会话抽屉/智能体抽屉）展开即视为 drawer-open：
      // 头部开合按钮隐藏（抽屉各有自带关闭钮），避免悬浮按钮压在抽屉头部上
      'mobile-drawer-open': mobileDrawerOpen || mobileAgentsVisible
    }"
  >
    <AgentRail
      :agents="railSoloAgents"
      :groups="railGroupAgents"
      :current-agent-id="agentId"
      :visible="railOpen || railPeeking"
      :expanded="agentRailExpanded"
      :peek="railPeeking && sidebarPeeking"
      :overlay="railPeeking && !sidebarPeeking"
      @mouseenter="panelEnter"
      @mouseleave="panelLeave"
      @select="handleRailSelect"
      @create-group="groupModalVisible = true"
      @toggle-expanded="agentRailExpanded = !agentRailExpanded"
      @toggle-pin="handleRailTogglePin"
      @reorder="handleRailReorder"
    />
    <ChatSidebar
      ref="sidebarRef"
      :can-edit-agent="userStore.isLoggedIn && agentInfo != null && agentInfo.type !== 0"
      @edit-agent="handleEditCurrentAgent"
      @pin="handleSessionPin"
      :agent-info="agentInfo"
      :can-personalize="userStore.isLoggedIn && agentInfo?.type === 0"
      :chat-list="chatList"
      :current-chat-id="currentChatId"
      :chat-list-loading="chatListLoading"
      :streaming-chat-ids="streamingChatIds"
      :collapsed="sidebarCollapsed"
      :peeking="sidebarPeeking"
      :enter-panel="panelEnter"
      :leave-panel="panelLeave"
      @select="selectChat"
      @new-chat="createNewChat"
      @search="searchModalVisible = true"
      @share="shareChat"
      @delete="requestDelete"
      @load-more="loadSessions(true)"
      @login="authModalVisible = true"
      @personalize="personalizeVisible = true"
      @agents="mobileAgentsVisible = true"
      @close="mobileDrawerVisible = false"
    />

    <div class="main-container">
      <div class="chat-header">
        <button
          class="sidebar-toggle"
          aria-label="切换侧边栏"
          @click="onToggleClick"
          @mouseenter="onToggleHover"
          @mouseleave="onToggleLeave"
        >
          <!-- 侧边栏开关（面板左分栏图标） -->
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <rect x="3.2" y="4.8" width="17.6" height="14.4" rx="3.2" />
            <line x1="9.2" y1="4.8" x2="9.2" y2="19.2" />
          </svg>
        </button>
        <div class="header-title-wrap">
          <h3 class="chat-title">{{ currentChatTitle }}</h3>
          <span v-if="!currentChatId" class="chat-subtitle">内容由AI动态生成</span>
        </div>
        <div class="header-spacer"></div>
      </div>

      <div class="chat-container">
        <ChatMessageList
          ref="messageListRef"
          :messages="messages"
          :is-loading="isLoading"
          :can-modify="userStore.isLoggedIn"
          :chat-id="currentChatId ?? undefined"
          :group-mode="agentInfo?.type === 2"
          :group-members="agentInfo?.members"
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
          @send-prompt="sendFromUi"
          @navigate="navigateFromUi"
        />
        <!-- 左下角常驻机器人：眼睛跟随指针，情绪随会话执行情况实时切换。
             群聊不显示（成员/群主头像在消息流内动态呈现） -->
        <BotPet
          v-if="agentInfo?.type !== 2"
          class="chat-pet"
          :config="petIconConfig"
          :mood="petMood"
          :fx-signal="petFxSignal"
          eye-color="#faf9f5"
          :label="agentInfo?.name || 'MioBot'"
        />
        <ChatInput
          v-model="inputMessage"
          v-model:effort="reasoningEffort"
          :agent-name="agentInfo?.name"
          :agent-avatar="agentInfo?.avatar"
          :agent-icon="agentInfo?.icon"
          :has-messages="messages.length > 0"
          :loading="isLoading"
          :supported-efforts="supportedEfforts"
          :render-cards="renderCards"
          :leaving-keys="leavingKeys"
          :ref-setter="setCardRef"
          :context-usage="contextUsage"
          :knowledge-bases="agentInfo?.knowledgeBases"
          :skills="agentInfo?.skills"
          :mcp-tools="agentInfo?.mcpTools"
          :current-chat-id="currentChatId ?? ''"
          :free-models="freeModels"
          :providers="providers"
          :current-model="currentModel"
          @select-model="handleSelectModel"
          @manage-models="manageModelsVisible = true"
          @add-files="handleAddFiles"
          @remove-pending="handleRemovePending"
          @send="handleSend"
          @stop="handleStop"
          @input-focus="inputListening = true"
          @input-blur="inputListening = false"
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

    <!-- 管理模型：内嵌个人中心的模型设置（免费档目录展示 + 供应商增删改/测试/启停） -->
    <a-modal
      v-model:open="manageModelsVisible"
      title="管理模型"
      :footer="null"
      :width="720"
      wrap-class-name="manage-models-modal"
    >
      <ModelSettings />
    </a-modal>

    <!-- MioBot 个性化设置（内置智能体按用户独立配置）：保存后重拉详情让开场白/图标即时生效 -->
    <AgentPersonalizeModal
      v-model:visible="personalizeVisible"
      :agent="agentInfo"
      @saved="loadAgentInfo"
    />

    <!-- 会话搜索命令面板（Ctrl+K）：会话/智能体检索 + 新建会话等快捷操作 -->
    <ChatSearchModal
      v-model:visible="searchModalVisible"
      :agent-id="agentId"
      :current-chat-id="currentChatId ?? ''"
      :recent="chatList"
      @select-session="selectChat"
      @select-agent="handleSearchSelectAgent"
      @new-chat="createNewChat"
      @open-dashboard="router.push('/dashboard')"
    />

    <!-- 创建群聊：从我的自定义智能体中选成员（≥2 个）+ 起名，成功后进入群聊对话 -->
    <GroupCreateModal
      v-model:visible="groupModalVisible"
      :agents="groupCandidates"
      @success="handleGroupCreated"
    />

    <!-- 编辑当前智能体（会话胶囊右上角设置按钮）：复用智能体编辑弹窗 -->
    <AgentCreateModal
      v-model:visible="agentEditVisible"
      mode="edit"
      :agent-id="agentId"
      @success="onCurrentAgentEdited"
    />

    <!-- 编辑当前群聊：改名 + 移除/添加成员 -->
    <EditGroupModal
      v-model:visible="groupEditVisible"
      :group="agentInfo?.type === 2 ? agentInfo : null"
      :candidates="groupCandidates"
      @saved="onCurrentAgentEdited"
    />

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

    <!-- 移动端：侧栏抽屉遮罩（点击关闭）+ 智能体切换抽屉（补位桌面左轨） -->
    <MobileScrim v-if="mobileDrawerOpen || mobileAgentsVisible" @close="closeMobileOverlays" />
    <MobileAgentDrawer
      :visible="mobileAgentsVisible"
      :agents="railSoloAgents"
      :groups="railGroupAgents"
      :current-agent-id="agentId"
      @close="mobileAgentsVisible = false"
      @select="handleMobileAgentSelect"
      @create-group="mobileAgentsVisible = false; groupModalVisible = true"
      @toggle-pin="handleRailTogglePin"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { getAgentDetail, getMyMioBotAgentId } from '@/api/agent'
import { pinChat } from '@/api/chat/memory'
import { useUserStore } from '@/store/user'
import type { AgentDetail, ChatMessage, ChatRefItem, MessageBlock, ModelSelection, SubagentPanelTarget } from '@/types'
import { isImageName, sandboxFileUrl } from './lib/attachmentUtils'
import { peekChatHandoff, takeChatHandoff } from '@/composables/chat/chatHandoff'
import AuthModal from '@/components/auth/AuthModal.vue'
import BotPet from '@/components/agent/bot-icon/BotPet.vue'
import MobileScrim from '@/mobile/MobileScrim.vue'
import MobileAgentDrawer from '@/mobile/components/MobileAgentDrawer.vue'
import { useIsMobile } from '@/mobile/useIsMobile'
import { DEFAULT_BOT_ICON, parseBotIcon } from '@/components/agent/bot-icon/types'
import type { BotIconConfig } from '@/components/agent/bot-icon/types'
import ChatSidebar from './components/layout/ChatSidebar.vue'
import AgentRail from './components/layout/AgentRail.vue'
import GroupCreateModal from './components/dialogs/GroupCreateModal.vue'
import ChatMessageList from './components/messages/ChatMessageList.vue'
import ChatInput from './components/input/ChatInput.vue'
import ChatPlanPanel from './components/messages/ChatPlanPanel.vue'
import ChatSearchModal from './components/dialogs/ChatSearchModal.vue'
import SubagentPanel from './components/messages/SubagentPanel.vue'
import AgentPersonalizeModal from './components/dialogs/AgentPersonalizeModal.vue'
import AgentCreateModal from '@/components/agent/AgentCreateModal.vue'
import EditGroupModal from './components/dialogs/EditGroupModal.vue'
import { useChatSessions } from './composables/session/useChatSessions'
import { useChatMessages, generateMessageId } from './composables/message/useChatMessages'
import { useChatStream } from './composables/stream/useChatStream'
import { useBotMood } from './composables/message/useBotMood'
import { endTurn, syncBackendTurns } from './composables/stream/useAgentActivity'
import { buildRefMarkerPrefix } from './lib/refMarkers'
import { buildSubagentTranscript } from './lib/subagentTranscript'
import { getActiveTurns, recordModelSwitch } from '@/api/chat'
import ModelSettings from '@/views/dashboard/profile/components/ModelSettings.vue'
import { loadModelCatalog, useModelCatalog, writeLocalSelection, readSessionModel, writeSessionModel, modelDisplayName } from '@/composables/model/useModelCatalog'
import { useReasoningEfforts } from '@/composables/model/useReasoningEfforts'
import { useChatShell } from './composables/session/useChatShell'
import { useAgentRail } from './composables/session/useAgentRail'
import { useContextUsage } from './composables/editor/useContextUsage'
import { useChatAttachments } from './composables/attachment/useChatAttachments'
import { useMessageActions } from './composables/message/useMessageActions'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const agentId = ref<number>(0)
const agentInfo = ref<AgentDetail | null>(null)
const authModalVisible = ref<boolean>(false)
const personalizeVisible = ref<boolean>(false)
const inputMessage = ref<string>('')

// 思考等级：本地记忆随每次发送透传给模型，档位按当前模型能力解析（与首页输入卡同一实现）
// ---------- 对话模型：目录（免费档+自定义供应商）与当前选择（个人中心/聊天页共享一份内存态） ----------
const { freeModels, providers, selection: currentModel, loaded: catalogLoaded } = useModelCatalog()
const {
  effort: reasoningEffort,
  supported: supportedEfforts,
  resolve: refreshSupportedEfforts
} = useReasoningEfforts(currentModel, { freeModels, providers })

/** 自定义供应商端点配置（仅存浏览器本地；随聊天请求瞬态经过后端，不落库） */
const currentProvider = computed(() => {
  const cur = currentModel.value
  if (cur.source !== 'custom' || cur.providerId == null) return null
  const p = providers.value.find(x => x.id === cur.providerId)
  return p ? { baseUrl: p.baseUrl, apiKey: p.apiKey, apiFormat: p.apiFormat } : null
})

/** 当前模型的元数据快照：后端按此路由/解析上下文窗口（自定义声明 > 免费目录 > 配置默认） */
const currentModelContext = computed(() => {
  const cur = currentModel.value
  if (!cur.model) return null
  let contextWindow: number | undefined
  if (cur.source === 'custom' && cur.providerId != null) {
    contextWindow = providers.value.find(p => p.id === cur.providerId)
      ?.models.find(m => m.id === cur.model)?.contextWindow
  } else {
    contextWindow = freeModels.value.find(f => f.id === cur.model)?.contextWindow
  }
  return { source: cur.source, model: cur.model, contextWindow }
})

onMounted(async () => {
  await loadModelCatalog()
  if (currentModel.value.model) {
    refreshSupportedEfforts(currentModel.value)
  }
})

/** 聊天页切换模型：更新共享选择（写本地镜像），思考档位随之按新模型重新解析。
 *  会话内切换：记住该会话的模型；已开始的对话插入居中切换提示（登录后落库，刷新保留） */
async function handleSelectModel(next: ModelSelection): Promise<void> {
  const prev = currentModel.value
  if (next.source === prev.source
    && next.providerId === prev.providerId
    && next.model === prev.model) {
    return
  }
  currentModel.value = next
  // 选择仅存浏览器本地
  writeLocalSelection(next)
  const chatId = currentChatId.value
  if (chatId) {
    writeSessionModel(chatId, next)
    // 对话已开始（有消息）才提示切换：新会话静默换模型
    if (messagesApi.getChatMessages(chatId).length > 0 && next.model) {
      appendModelSwitchNotice(chatId, modelDisplayName(prev), modelDisplayName(next))
    }
  }
  await refreshSupportedEfforts(next)
}

/** 会话内切换模型的居中提示行（独立于用户/智能体消息）：本地先上屏，登录态落库保刷新 */
function appendModelSwitchNotice(chatId: string, from: string, to: string): void {
  const notice: ChatMessage = {
    id: generateMessageId(),
    role: 'system',
    content: `已从 ${from} 切换至 ${to}`,
    createTime: new Date()
  }
  messagesApi.setChatMessages(chatId, [...messagesApi.getChatMessages(chatId), notice])
  if (userStore.isLoggedIn) {
    recordModelSwitch(chatId, from, to).catch(() => {
      // 落库失败（会话执行中/网络异常）：提示仅本次可见
    })
  }
}

// 管理模型弹窗：内嵌个人中心的模型设置（免费档目录展示 + 供应商增删改/测试/启停）
const manageModelsVisible = ref(false)

const sidebarRef = ref<InstanceType<typeof ChatSidebar> | null>(null)
const messageListRef = ref<InstanceType<typeof ChatMessageList> | null>(null)

// ---------- 侧栏相位 + 移动端抽屉（见 useChatShell） ----------
const { isMobile } = useIsMobile()

const {
  sidebarPhase,
  sidebarPeeking,
  railPeeking,
  mobileDrawerVisible,
  mobileAgentsVisible,
  mobileDrawerOpen,
  sidebarCollapsed,
  onToggleClick,
  onToggleHover,
  onToggleLeave,
  panelEnter,
  panelLeave,
  sidebarClosePeek,
  closeMobileOverlays,
  onMobileKeydown
} = useChatShell({
  isMobile,
  routeFullPath: () => route.fullPath
})

// ---------- 智能体左轨（见 useAgentRail） ----------
const {
  agentRailExpanded,
  myAgents,
  groupModalVisible,
  railSoloAgents,
  railGroupAgents,
  groupCandidates,
  loadMyAgents,
  handleRailTogglePin,
  handleRailReorder
} = useAgentRail()

/** 左轨常驻展示：登录且相位 0（都展开）。移动端左轨整体隐藏，恒不展示 */
const railOpen = computed(() => !isMobile.value && userStore.isLoggedIn && sidebarPhase.value === 0)

// ---------- 会话搜索命令面板 ----------
const searchModalVisible = ref(false)

function handleRailSelect(targetAgentId: number): void {
  if (targetAgentId === agentId.value) {
    createNewChat()
  } else {
    router.push(`/chat/${targetAgentId}`)
  }
}

/** 移动端智能体抽屉点选：先收抽屉（覆盖层不跟随路由过渡），再走同一套导航 */
function handleMobileAgentSelect(targetAgentId: number): void {
  mobileAgentsVisible.value = false
  handleRailSelect(targetAgentId)
}

function handleGroupCreated(groupAgentId: number): void {
  loadMyAgents()
  router.push(`/chat/${groupAgentId}`)
}

// ---------- 当前智能体/群聊的编辑入口（会话胶囊右上角设置按钮） ----------
const agentEditVisible = ref(false)
const groupEditVisible = ref(false)

/** 智能体→复用编辑弹窗；群聊→编辑群聊（改名+移除成员） */
function handleEditCurrentAgent(): void {
  if (agentInfo.value?.type === 2) {
    groupEditVisible.value = true
  } else {
    agentEditVisible.value = true
  }
}

function onCurrentAgentEdited(): void {
  loadAgentInfo()
  loadMyAgents()
}

/** 会话置顶：本地乐观移动（置顶到最前 / 取消回到普通组顶部），落库失败回读 */
function handleSessionPin(conversationId: string, pinned: boolean): void {
  const list = [...chatList.value]
  const idx = list.findIndex(c => c.id === conversationId)
  if (idx < 0) return
  const [item] = list.splice(idx, 1)
  const updated = { ...item, pinned: pinned ? 1 : 0 }
  if (pinned) {
    list.unshift(updated)
  } else {
    const pinnedCount = list.filter(c => c.pinned === 1).length
    list.splice(pinnedCount, 0, updated)
  }
  chatList.value = list
  pinChat(conversationId, pinned).catch(() => loadSessions())
}

function handleSearchSelectAgent(targetAgentId: number): void {
  router.push(`/chat/${targetAgentId}`)
}

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
    streamApi.recoverPendingTurn(chatId, agentId.value)
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
  scrollToChatListTop,
  // 后端游客配额用尽（auth_required 事件）：撤掉占位回复并弹登录框
  onAuthRequired: () => {
    authModalVisible.value = true
  }
})
const { sendMessage, prepareChatId, cleanup: cleanupStream, stopTurn } = streamApi

// 正在执行中的会话（侧边栏执行动画）：执行状态在后端，轮询补齐——
// 刷新/切页后前端内存态（chatLoadingMap）清空，动画不再依赖"点开会话触发接管"才出现
const backendStreamingIds = ref<Set<string>>(new Set())
async function refreshBackendStreaming(): Promise<void> {
  if (!userStore.isLoggedIn) return
  try {
    const turns = await getActiveTurns()
    backendStreamingIds.value = new Set(turns.map(t => t.chatId))
    // 左轨活动绑定同步：补挂本页无流连接的执行中会话（按 agentId 归属）；
    // 后端 active 列表是执行与否的权威——已结束的轮次在此兜底清除（正常收尾由 done 事件先行）
    syncBackendTurns(turns)
  } catch {
    // 后端暂不可达：保留现有状态，下个周期再试
  }
}
const streamingChatIds = computed(() => {
  const ids = new Set(backendStreamingIds.value)
  for (const chatId of messagesApi.chatLoadingMap.value.keys()) {
    ids.add(chatId)
  }
  return [...ids]
})
let streamingPollTimer: number | undefined
onMounted(() => {
  refreshBackendStreaming()
  streamingPollTimer = window.setInterval(refreshBackendStreaming, 10_000)
})
watch(() => userStore.isLoggedIn, (loggedIn) => {
  if (loggedIn) refreshBackendStreaming()
})

const { currentChatId, messages, isLoading, switchChat, loadMessages } = messagesApi

// ---------- 会话级模型记忆：进入会话恢复该会话用的模型（无记录沿用当前选择） ----------
/** 恢复会话记住的模型；与当前选择一致时不做动作（档位/容量指示不空转） */
function restoreSessionModel(chatId: string): void {
  if (!chatId) return
  const remembered = readSessionModel(chatId)
  if (remembered
    && (remembered.source !== currentModel.value.source
      || remembered.providerId !== currentModel.value.providerId
      || remembered.model !== currentModel.value.model)) {
    currentModel.value = remembered
    writeLocalSelection(remembered)
    refreshSupportedEfforts(remembered)
  }
}

// 目录未就绪时供应商条目不可见，恢复会误走探测——目录就绪后才解析（切换会话/首载各触发一次）
watch(
  [() => messagesApi.currentChatId.value, catalogLoaded],
  ([chatId, ready]) => {
    if (ready) restoreSessionModel(chatId)
  }
)

// 轮次结束即时同步：done/stopped/error 时后端已释放占用，乐观移除并立即刷新，
// 不等下个 10s 轮询——侧边栏执行动画与会话状态同步停止
watch(isLoading, (loading, was) => {
  if (!was || loading) return
  const id = currentChatId.value
  if (id) endTurn(id)
  if (id && backendStreamingIds.value.has(id)) {
    const next = new Set(backendStreamingIds.value)
    next.delete(id)
    backendStreamingIds.value = next
  }
  refreshBackendStreaming()
})

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

// ---------- 上下文容量指示器（见 useContextUsage） ----------
const { contextUsage } = useContextUsage({
  currentChatId: () => currentChatId.value,
  agentId: () => agentId.value,
  modelContext: () => currentModelContext.value,
  isLoading: () => isLoading.value
})

// ---------- 左下角常驻机器人（情绪随会话执行情况实时切换） ----------
const inputListening = ref(false)

// ---------- 待传附件 / 草稿隔离 / 编辑附件（见 useChatAttachments） ----------
const {
  pendingAttachments,
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
} = useChatAttachments({
  currentChatId: () => currentChatId.value,
  routeConversationId: () => route.params.conversationId as string | undefined,
  inputMessage: () => inputMessage.value,
  setInputMessage: v => { inputMessage.value = v },
  setCurrentChatId: id => { messagesApi.currentChatId.value = id },
  getMessages: chatId => messagesApi.getChatMessages(chatId)
})

const uploadingCount = computed(() => pendingAttachments.value.filter(p => p.status === 'uploading').length)
// 必须在 useBotMood 之前声明：uploading 信号在 watch 注册期就会被求值
const { mood: petMood, fxSignal: petFxSignal } = useBotMood(
  messages, isLoading, inputListening, inputMessage, uploadingCount, activePlan, currentChatId
)
const petIconConfig = computed<BotIconConfig>(
  () => parseBotIcon(agentInfo.value?.icon) ?? DEFAULT_BOT_ICON
)

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

// 搜索面板"个性化设置"条目跳转携带：进入 MioBot 页（详情加载完成）即打开个性化弹窗
watch(
  [() => route.query.personalize, agentInfo],
  ([p, info]) => {
    if (p === '1' && userStore.isLoggedIn && info?.type === 0) {
      personalizeVisible.value = true
      router.replace({ query: {} })
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

// 首页输入卡交接：目标智能体的详情就绪后取走（会话 id 与附件在首页已备好），
// 灌进本页状态后直接走 handleSend——用户在首页发出即落地成对话页的首条消息
watch(
  [agentId, agentInfo],
  async ([id, info]) => {
    const handoff = peekChatHandoff()
    if (!handoff || !info || id !== handoff.agentId) return
    takeChatHandoff()
    switchChat(handoff.chatId)
    // 会话切换触发的草稿/待传附件恢复在 watcher 内落位，等它完成再写入交接的附件
    await nextTick()
    pendingAttachments.value = handoff.attachments.map(item => ({
      key: `pending_${item.path}`,
      name: item.name,
      size: item.size,
      status: 'done' as const,
      progress: 1 as number,
      item,
      previewSrc: isImageName(item.name) ? sandboxFileUrl(item.path) : undefined
    }))
    inputMessage.value = handoff.content
    await nextTick()
    handleSend()
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
    // detail 接口（不缓存）对内置智能体按当前用户合并个性化覆盖（开场白/图标/温度）
    agentInfo.value = await getAgentDetail(agentId.value)
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
  // 收起态下从 peek 面板选中会话：先收回面板再展示对话
  sidebarClosePeek()
  switchChat(conversationId)
  router.push(`/chat/${agentId.value}/${conversationId}`)
  loadMessages(conversationId)
}

function handleSend(refs: ChatRefItem[] = []): void {
  let content = inputMessage.value.trim()
  const hasCompact = refs.some(r => r.type === 'compact')
  // Compact token 优先编排为压缩指令；随附文字作为压缩后的追问一并携带
  if (hasCompact) {
    content = content ? `/compact ${content}` : '/compact'
  }
  if ((!content && !refs.length) || isLoading.value) return

  // 游客仅 MioBot 可直接对话（自定义智能体引导登录），且累计只能发送两条，之后强制登录
  if (!guestCanSend()) return

  // 附件在选中时已上传；仍在途的门控等待，失败的单卡跳过并提示
  if (hasUploading()) {
    message.warning('附件正在上传，请稍候')
    return
  }
  if (hasUploadError()) {
    message.warning('部分附件上传失败，已自动跳过')
  }
  const attachments = readyAttachments()

  // 引用编排为标记前缀（zcode 同构）：消息文本本身携带 [#名](#sess_id) 等标记，
  // 前端渲染成彩色 token，复制/编辑/持久化的都是标记文本
  const markerPrefix = buildRefMarkerPrefix(refs)
  if (markerPrefix) {
    content = content ? `${markerPrefix} ${content}` : markerPrefix
  }
  // 仅引用无文本：给模型一句基于引用材料继续的明确指令
  if (!content) {
    content = '请基于我引用的上下文继续。'
  }
  // 会话从首条消息起记住自己的模型（再次进入恢复，不再全局统一）
  writeSessionModel(streamApi.prepareChatId(), currentModel.value)
  sendMessage(content, {
    reasoningEffort: reasoningEffort.value,
    modelSelection: currentModel.value,
    provider: currentProvider.value,
    contextWindow: currentModelContext.value?.contextWindow,
    attachments,
    refs
  })
  noteGuestTurn()
  cleanupPendingAttachments()
  inputMessage.value = ''
}

/** 动态 UI 站内跳转（agent_card 的按钮）：路径由后端生成，这里只做白名单校验后导航 */
function navigateFromUi(path: string): void {
  if (!path.startsWith('/')) return
  // 对话里刚建的智能体/群聊，左轨尚未重新拉取（loadMyAgents 只在 onMounted 跑）——
  // 先刷新再跳，否则用户回到对话页看不到新建项，会以为没创建成功
  loadMyAgents()
  router.push(path)
}

/** 动态 UI 交互（actions 按钮/表单提交）：编排好的文本直接作为用户消息发送（不带附件） */
function sendFromUi(text: string): void {
  const content = text.trim()
  if (!content || isLoading.value) return
  if (!guestCanSend()) return
  writeSessionModel(streamApi.prepareChatId(), currentModel.value)
  sendMessage(content, {
    reasoningEffort: reasoningEffort.value,
    modelSelection: currentModel.value,
    provider: currentProvider.value,
    contextWindow: currentModelContext.value?.contextWindow
  })
  noteGuestTurn()
}

// ---------- 游客对话限制：仅 MioBot（内置 id=1）可聊，累计两条后强制登录 ----------
const MIOBOT_AGENT_ID = 1
const GUEST_TURN_LIMIT = 2
const GUEST_TURN_KEY = 'guest-turn-count'
const guestTurnCount = ref(Number(localStorage.getItem(GUEST_TURN_KEY) || '0'))

/** 游客发送门禁：额度用尽弹登录框；登录用户/自定义智能体沿用原登录引导 */
function guestCanSend(): boolean {
  if (userStore.isLoggedIn) return true
  if (agentId.value !== MIOBOT_AGENT_ID) {
    authModalVisible.value = true
    return false
  }
  if (guestTurnCount.value >= GUEST_TURN_LIMIT) {
    message.warning(`游客最多可发送 ${GUEST_TURN_LIMIT} 条消息，登录后继续对话`)
    authModalVisible.value = true
    return false
  }
  return true
}

function noteGuestTurn(): void {
  if (userStore.isLoggedIn) return
  guestTurnCount.value += 1
  localStorage.setItem(GUEST_TURN_KEY, String(guestTurnCount.value))
}

/** 停止按钮：中断当前会话正在执行的轮次（后端停止引擎，界面由 stopped 事件/恢复轮询收尾） */
function handleStop(): void {
  if (currentChatId.value) {
    stopTurn(currentChatId.value)
  }
}

// ---------- 消息级操作（见 useMessageActions） ----------
const { handleRegenerate, handleEditMessage, handleSwitchVersion } = useMessageActions({
  messagesApi,
  currentChatId: () => currentChatId.value,
  sendMessage,
  reasoningEffort: () => reasoningEffort.value,
  currentModel: () => currentModel.value,
  currentProvider: () => currentProvider.value,
  currentModelContext: () => currentModelContext.value
})

function handleDeleteConfirm(): void {
  confirmDelete((chatId) => chatId === currentChatId.value)
}

/** 全局快捷键：Ctrl/Cmd+K 新对话（保持原快捷键不变）；Ctrl/Cmd+P 打开会话搜索（与侧边栏搜索入口提示一致） */
function handleKeyboardShortcut(e: KeyboardEvent): void {
  if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    createNewChat()
  } else if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key.toLowerCase() === 'p') {
    e.preventDefault()
    searchModalVisible.value = true
  }
}

function handleAuthSuccess(): void {
  // 在内置 MioBot 页登录：直接转到该用户的专属 MioBot（补建失败再原地刷新）
  if (agentId.value === MIOBOT_AGENT_ID && tryRedirectToMyMioBot()) return
  window.location.reload()
}

// ---------- 登录用户访问内置 MioBot 页（/chat/1）→ 跳转自己的专属 MioBot ----------
let mioBotRedirecting = false

/** 发起跳转；返回 true 表示跳转已在途或已开始（调用方不应再做其他收尾） */
function tryRedirectToMyMioBot(): boolean {
  if (mioBotRedirecting) return true
  if (!userStore.isLoggedIn || agentId.value !== MIOBOT_AGENT_ID) return false
  mioBotRedirecting = true
  getMyMioBotAgentId()
    .then(ownId => {
      if (ownId && ownId !== MIOBOT_AGENT_ID) {
        window.location.href = `/chat/${ownId}`
        return
      }
      // 补建异常/无个人 MioBot：复位标志，交给调用方兜底
      mioBotRedirecting = false
      if (agentId.value === MIOBOT_AGENT_ID) window.location.reload()
    })
    .catch(() => {
      // 补建失败（网络/后端异常）：复位标志并原地刷新回到确定状态
      mioBotRedirecting = false
      if (agentId.value === MIOBOT_AGENT_ID) window.location.reload()
    })
  return true
}

// 登录态就绪（含刷新后恢复登录）且停留在 /chat/1 时补跳；与 handleAuthSuccess 由标志去重
watch(
  () => [userStore.isLoggedIn, agentId.value],
  ([loggedIn, id]) => {
    if (loggedIn && id === MIOBOT_AGENT_ID) tryRedirectToMyMioBot()
  },
  { immediate: true }
)

onMounted(() => {
  window.addEventListener('keydown', handleKeyboardShortcut)
  window.addEventListener('keydown', onMobileKeydown)
  loadMyAgents()
})

watch(() => userStore.isLoggedIn, () => loadMyAgents())

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyboardShortcut)
  window.removeEventListener('keydown', onMobileKeydown)
  if (streamingPollTimer !== undefined) {
    window.clearInterval(streamingPollTimer)
    streamingPollTimer = undefined
  }
})

// 组件销毁前关闭流式连接
onBeforeUnmount(() => {
  cleanupStream()
})
</script>

<style lang="scss" scoped>
.chat-layout {
  position: relative;
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
  // 侧边栏展开时给悬浮胶囊让位（300 = 12 出血 + 280 胶囊 + 8 间隙），收起时收回出血位
  margin: 12px 12px 12px 300px;
  border: 1px solid $border-warm-subtle;
  border-radius: 18px;
  transition: margin-left 0.28s cubic-bezier(0.32, 0.72, 0.35, 1);
}

.sidebar-collapsed .main-container {
  margin-left: 12px;
}

// 胶囊圆角/左边线宽度做成可过渡属性：收轨时融合侧平滑还原为独立胶囊
:deep(.sidebar .sidebar-capsule) {
  transition: box-shadow 0.28s, border-radius 0.28s cubic-bezier(0.32, 0.72, 0.35, 1),
    border-left-width 0.28s cubic-bezier(0.32, 0.72, 0.35, 1);
}

// 相位0 常驻融合：轨道占位 64px（信息面板 240px），会话胶囊右移拼接、拆除左边线，
// 两栏之间由轨道右缘的 1px 分隔线承担（无阴影）；main-container 同步让位
.rail-open {
  :deep(.sidebar) {
    transform: translateX(64px);
  }

  &.rail-wide :deep(.sidebar) {
    transform: translateX(240px);
  }

  :deep(.sidebar .sidebar-capsule) {
    border-radius: 0 18px 18px 0;
    border-left-width: 0;
  }

  .main-container {
    margin-left: 364px;
  }

  &.rail-wide .main-container {
    margin-left: 540px;
  }
}

// 双栏同时 peek（都收起态）：会话胶囊 peek 右移让位与轨道拼接。
// 仅左轨 peek（会话栏常驻）不位移会话胶囊——轨道以更高 z 覆盖其左缘（带投影），
// 会话胶囊与 header 开关按钮位置不动，杜绝任何重叠冲突
.rail-peek {
  :deep(.sidebar.collapsed.peek) {
    transform: translateX(64px);
  }

  &.rail-wide :deep(.sidebar.collapsed.peek) {
    transform: translateX(240px);
  }

  :deep(.sidebar.collapsed.peek .sidebar-capsule) {
    border-radius: 0 18px 18px 0;
    border-left-width: 0;
  }
}

// 移动端（满宽内容区、侧栏抽屉化、左轨隐藏、子智能体面板全屏）见 mobile/styles/shell.scss
// 与 mobile/styles/chat.scss（按 html.is-mobile 标记生效，断点定义在 mobile/breakpoints.ts）

.chat-header {
  height: 64px;
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid #e8e6dc;

  .sidebar-toggle {
    // 高于侧边栏胶囊（z-60）：peek 滑出时按钮保持可点，避免面板盖住按钮后被困在收起态
    position: relative;
    z-index: 70;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    flex-shrink: 0;
    border: none;
    border-radius: 8px;
    background: transparent;
    color: #5f5d55;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      color: $primary-color;
      background: rgba(42, 161, 169, 0.08);
    }
  }

  .header-title-wrap {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;

    .chat-title {
      font-size: 16px;
      font-weight: 600;
      color: #141413;
      margin: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 100%;
    }

    .chat-subtitle {
      font-size: 12px;
      color: #8c8a82;
    }
  }

  // 与左侧按钮等宽的占位，保证标题绝对居中
  .header-spacer {
    width: 36px;
    flex-shrink: 0;
  }
}

// 移动端抽屉展开时隐藏头部开合按钮：抽屉自带关闭钮（同智能体抽屉交互），
// 悬浮按钮压在抽屉头部的叠层观感不再出现。
// is-mobile-shell / mobile-drawer-open 都在 .chat-layout 根节点上，须写顶层（嵌套会反转后代方向）
.chat-layout.is-mobile-shell.mobile-drawer-open .chat-header .sidebar-toggle {
  opacity: 0;
  pointer-events: none;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

// 左下角常驻机器人：避开输入框与消息区（窄屏与移动端隐藏，见下方注释与 mobile 层）
.chat-pet {
  position: absolute;
  left: 16px;
  bottom: 20px;
  z-index: 5;
}

// 窄屏与移动端隐藏桌面宠物，见 mobile/styles/chat.scss（按 html.is-tablet 标记生效）

// 子智能体面板外壳：宽度驱动的抽屉（0↔最终宽）。聊天区随宽度变化逐帧推移，
// 面板本体保持定宽由外壳裁切，内容不回流；无 transform，入场/退场结束时聊天区
// 已处于最终位置，不会出现动画结束后突然回跳。
// 卡片化布局：与主容器同出血（上下 12px），右缘圆角对齐
.subagent-drawer {
  flex-shrink: 0;
  width: clamp(360px, 36vw, 560px);
  height: calc(100vh - 24px);
  margin: 12px 12px 12px 0;
  overflow: hidden;
  background: $bg-ivory;
  border: 1px solid $border-warm-subtle;
  border-radius: 0 18px 18px 0;
  box-shadow: -8px 0 24px rgba(20, 20, 19, 0.04);
}

.subagent-drawer-enter-active,
.subagent-drawer-leave-active {
  transition: width 0.26s cubic-bezier(0.32, 0.72, 0.35, 1), border-left-width 0.26s, margin-left 0.26s;
  will-change: width;
}

// enter-from 必须是 width:0（起始态）；leave-from 不能设宽度（须等于当前 560，
// 否则离场首帧吸附到 0、过渡变成 0→0 退化为瞬隐），离场只由 leave-to 收到 0
.subagent-drawer-enter-from {
  width: 0;
  border-left-width: 0;
  margin-left: 0;
}

.subagent-drawer-leave-to {
  width: 0;
  border-left-width: 0;
  margin-left: 0;
}
</style>
