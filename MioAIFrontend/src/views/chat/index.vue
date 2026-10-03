<template>
  <div class="chat-layout">
    <ChatSidebar
      ref="sidebarRef"
      :agent-info="agentInfo"
      :chat-list="chatList"
      :current-chat-id="currentChatId"
      :chat-list-loading="chatListLoading"
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
        <ChatMessageList ref="messageListRef" :messages="messages" :is-loading="isLoading" />
        <ChatInput
          v-model="inputMessage"
          :agent-name="agentInfo?.name"
          :agent-avatar="agentInfo?.avatar"
          :has-messages="messages.length > 0"
          :loading="isLoading"
          @send="handleSend"
        >
          <template #above-input>
            <ChatPlanPanel :plan="activePlan" />
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
import type { Agent } from '@/types'
import AuthModal from '@/components/AuthModal.vue'
import ChatSidebar from './components/ChatSidebar.vue'
import ChatMessageList from './components/ChatMessageList.vue'
import ChatInput from './components/ChatInput.vue'
import ChatPlanPanel from './components/ChatPlanPanel.vue'
import { useChatSessions } from './composables/useChatSessions'
import { useChatMessages } from './composables/useChatMessages'
import { useChatStream } from './composables/useChatStream'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const agentId = ref<number>(0)
const agentInfo = ref<Agent | null>(null)
const authModalVisible = ref<boolean>(false)
const inputMessage = ref<string>('')

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

const { sendMessage, cleanup: cleanupStream } = useChatStream({
  agentId,
  messagesApi,
  ensureSession,
  updateTitle: updateChatTitleWithTypewriter,
  scrollToBottom,
  followStream,
  scrollToChatListTop
})

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

  sendMessage(content)
  inputMessage.value = ''
}

function handleDeleteConfirm(): void {
  confirmDelete((chatId) => chatId === currentChatId.value)
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
