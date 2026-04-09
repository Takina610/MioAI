<template>
  <div class="chat-layout">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-top">
        <div class="logo-section" v-show="!isCollapsed">
          <img :src="agentInfo?.avatar || '/favicon.ico'" alt="Avatar" class="agent-avatar" />
          <span class="agent-name">{{ agentInfo?.name || '智能体' }}</span>
        </div>
        <a-button
          type="text"
          class="collapse-btn"
          @click="toggleCollapse"
        >
          <MenuFoldOutlined v-if="!isCollapsed" />
          <MenuUnfoldOutlined v-else />
        </a-button>
      </div>

      <div class="sidebar-content">
        <div class="sidebar-actions">
          <div class="new-chat-btn-wrapper" v-show="!isCollapsed">
            <a-button class="new-chat-btn" @click="createNewChat">
              <FormOutlined />
              <span class="btn-text">新对话</span>
              <div class="shortcut-hint">
                <span class="key-box">Ctrl</span>
                <span class="key-box">K</span>
              </div>
            </a-button>
          </div>
          <a-tooltip placement="right" v-if="isCollapsed">
            <template #title>新对话</template>
            <div class="new-chat-btn-collapsed" @click="createNewChat">
              <FormOutlined />
            </div>
          </a-tooltip>

          <div class="app-square-btn-wrapper" v-show="!isCollapsed">
            <a-button class="app-square-btn" @click="goDashboard">
              <RobotOutlined />
              <span class="btn-text">智能体广场</span>
            </a-button>
          </div>
          <a-tooltip placement="right" v-if="isCollapsed">
            <template #title>智能体广场</template>
            <div class="app-square-btn-collapsed" @click="goDashboard">
              <RobotOutlined />
            </div>
          </a-tooltip>
        </div>

        <div class="chat-list-section">
          <div class="section-title" v-show="!isCollapsed">历史会话</div>
          <div class="chat-list" @scroll="handleChatListScroll" ref="chatListRef">
            <template v-if="chatListLoading && chatList.length === 0">
              <div v-for="i in 3" :key="'skeleton-' + i" class="chat-item-skeleton">
                <a-skeleton :paragraph="{ rows: 1 }" :title="false" active />
              </div>
            </template>
            <a-tooltip placement="right" v-if="isCollapsed" v-for="chat in chatList" :key="chat.id">
              <template #title>{{ chat.title }}</template>
              <div
                class="chat-item chat-item-collapsed"
                :class="{ active: currentChatId === chat.id }"
                @click="selectChat(chat.id)"
              >
                <MessageOutlined />
              </div>
            </a-tooltip>
            <template v-if="!isCollapsed">
              <div
                v-for="chat in chatList"
                :key="chat.id"
                class="chat-item"
                :class="{ active: currentChatId === chat.id }"
                @click="selectChat(chat.id)"
              >
                <div class="chat-item-content">
                  <div class="chat-item-title">{{ chat.title }}</div>
                  <div class="chat-item-time">{{ formatTime(chat.updateTime) }}</div>
                </div>
                <a-dropdown :trigger="['click']">
                  <a-button type="text" size="small" class="chat-item-more" @click.stop>
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item key="share" @click="shareChat(chat.id)">
                        <ShareAltOutlined /> 分享对话
                      </a-menu-item>
                      <a-menu-item key="delete" class="delete-menu-item" @click="confirmDeleteChat(chat.id)">
                        <DeleteOutlined /> 删除对话
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </div>
              <div v-if="chatListLoading && chatList.length > 0" class="chat-list-loading">
                <a-spin size="small" />
              </div>
            </template>
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <template v-if="userStore.isLoggedIn">
          <a-dropdown :trigger="['click']" placement="topLeft">
            <div class="user-info" :class="{ collapsed: isCollapsed }">
              <a-avatar :size="isCollapsed ? 36 : 40" :src="userStore.userAvatar">
                {{ userStore.userName?.charAt(0)?.toUpperCase() }}
              </a-avatar>
              <div class="user-detail" v-show="!isCollapsed">
                <span class="user-name">{{ userStore.userName }}</span>
                <span class="user-role">{{ userStore.userInfo?.userProfile }}</span>
              </div>
            </div>
            <template #overlay>
              <a-menu>
                <a-menu-item key="home" @click="goHome">
                  <HomeOutlined /> 返回首页
                </a-menu-item>
                <a-menu-item key="profile" @click="goProfile">
                  <UserOutlined /> 个人中心
                </a-menu-item>
                <a-menu-item key="dashboard" @click="goDashboard">
                  <SettingOutlined /> 控制台
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout">
                  <LogoutOutlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <template v-else>
          <a-button type="primary" block @click="showAuthModal" v-show="!isCollapsed">
            登录
          </a-button>
          <a-button type="primary" @click="showAuthModal" v-show="isCollapsed">
            <UserOutlined />
          </a-button>
        </template>
      </div>
    </aside>

    <div class="main-container">
      <div class="chat-header" v-if="currentChatId">
        <h3 class="chat-title">{{ currentChatTitle }}</h3>
      </div>
      <div class="chat-header" v-else>
        <h3 class="chat-title">新对话</h3>
        <span class="chat-subtitle">内容由AI动态生成</span>
      </div>

      <div class="chat-container">
        <div class="chat-messages" ref="messagesRef" v-if="messages.length > 0">
          <div class="messages-wrapper">
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="message"
              :class="msg.role"
              @mouseenter="handleMouseEnter(msg.id)"
              @mouseleave="hoverMessageId = ''"
            >
              <div class="message-content">
                <div class="message-text" v-html="formatMessage(msg.content)"></div>
                <div class="message-actions">
                  <div class="copy-area" v-show="hoverMessageId === msg.id && msg.content">
                    <a-tooltip :title="copiedMessageId === msg.id ? '已复制' : '复制'">
                      <a-button type="text" size="small" class="copy-btn" :class="{ 'copied': copiedMessageId === msg.id }" @click="copyMessage(msg.content, msg.id)">
                        <CheckOutlined v-if="copiedMessageId === msg.id" />
                        <CopyOutlined v-else />
                      </a-button>
                    </a-tooltip>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="isLoading" class="message assistant">
              <div class="message-content">
                <div class="message-loading">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-center-area" :class="{ 'has-messages': messages.length > 0 }">
          <div class="welcome-section" v-if="messages.length === 0 && !isLoading">
            <img :src="agentInfo?.avatar || '/favicon.ico'" alt="Agent" class="welcome-avatar" />
            <h2 class="welcome-title">我能帮什么忙吗，{{ userStore.userName }}？</h2>
          </div>
          <div class="chat-input-wrapper">
            <div class="chat-input-container">
              <div class="input-box">
                <a-textarea
                  v-model:value="inputMessage"
                  :placeholder="`给 ${agentInfo?.name || '智能体'} 发送消息`"
                  :auto-size="{ minRows: 1, maxRows: 6 }"
                  @pressEnter="handleEnter"
                  class="chat-textarea"
                />
                <a-button
                  type="primary"
                  shape="circle"
                  class="send-btn"
                  :disabled="!inputMessage.trim() || isLoading"
                  :loading="isLoading"
                  @click="sendMessage"
                >
                  <ArrowUpOutlined v-if="!isLoading" />
                </a-button>
              </div>
            </div>
          </div>
        </div>
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
import { ref, computed, onMounted, nextTick, watch, onUnmounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { marked } from 'marked'
import { useUserStore } from '@/store/user'
import { getAgentById } from '@/api/agent'
import {
  chatWithCSApp,
  chatWithDefaultAgent,
  chatWithMioManus,
  chatWithCustomAgent,
  generateTitle
} from '@/api/chat'

import { getChatIdsPage, getChatHistory, deleteChat as deleteChatApi } from '@/api/chatMemory'
import type { Agent } from '@/types'
import AuthModal from '@/components/AuthModal.vue'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
  MoreOutlined,
  DeleteOutlined,
  HomeOutlined,
  RobotOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
  ArrowUpOutlined,
  FormOutlined,
  CopyOutlined,
  ShareAltOutlined,
  CheckOutlined
} from '@ant-design/icons-vue'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createTime: Date
}

interface ChatSession {
  id: string
  title: string
  updateTime: Date
  hasMessage: boolean
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref<boolean>(false)
const authModalVisible = ref<boolean>(false)
const agentInfo = ref<Agent | null>(null)
const currentChatId = ref<string>('')
const chatList = ref<ChatSession[]>([])
const chatListLoading = ref<boolean>(false)
const chatListCurrent = ref<number>(1)
const chatListPageSize = ref<number>(10)
const chatListTotal = ref<number>(0)
const chatListHasMore = ref<boolean>(true)
const messages = ref<ChatMessage[]>([])
const inputMessage = ref<string>('')
const isLoading = ref<boolean>(false)
const messagesRef = ref<HTMLElement | null>(null)
const chatListRef = ref<HTMLElement | null>(null)
const hoverMessageId = ref<string>('')
const copiedMessageId = ref<string>('')
const deleteModalVisible = ref<boolean>(false)
const deleteLoading = ref<boolean>(false)
const pendingDeleteChatId = ref<string>('')

marked.setOptions({
  gfm: true,
  breaks: true
})

let eventSource: EventSource | null = null

const agentId = ref<number>(0)

const currentChatTitle = computed(() => {
  const chat = chatList.value.find(c => c.id === currentChatId.value)
  return chat?.title || '新对话'
})

watch(
  () => route.params.id,
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
      currentChatId.value = conversationId
      loadMessages(conversationId)
    } else {
      currentChatId.value = ''
      messages.value = []
    }
  },
  { immediate: true }
)

async function loadAgentInfo(): Promise<void> {
  try {
    const res = await getAgentById(agentId.value)
    agentInfo.value = res
    await loadChatHistory()
    
    if (route.params.conversationId && typeof route.params.conversationId === 'string') {
      currentChatId.value = route.params.conversationId
      loadMessages(route.params.conversationId)
    }
  } catch (e) {
    console.error(e)
    router.push('/404')
  }
}

async function loadChatHistory(isLoadMore: boolean = false): Promise<void> {
  if (!userStore.isLoggedIn) return
  
  if (chatListLoading.value) return
  if (isLoadMore && !chatListHasMore.value) return
  
  chatListLoading.value = true
  
  try {
    const current = isLoadMore ? chatListCurrent.value + 1 : 1
    const res = await getChatIdsPage(String(agentId.value), current, chatListPageSize.value)
    
    const newChats = res.records.map((item: any) => ({
      id: item.conversationId,
      title: item.title || '新对话',
      updateTime: item.updateTime ? new Date(item.updateTime) : new Date(),
      hasMessage: true
    }))
    
    if (isLoadMore) {
      chatList.value = [...chatList.value, ...newChats]
    } else {
      chatList.value = newChats
    }
    
    chatListCurrent.value = res.current
    chatListTotal.value = res.total
    chatListHasMore.value = res.current < res.pages
  } catch (e) {
    console.error(e)
  } finally {
    chatListLoading.value = false
    nextTick(() => {
      checkAndLoadMore()
    })
  }
}

function checkAndLoadMore(): void {
  if (!chatListRef.value || !chatListHasMore.value || chatListLoading.value) return
  
  const { scrollHeight, clientHeight } = chatListRef.value
  
  if (scrollHeight <= clientHeight) {
    loadChatHistory(true)
  }
}

function handleChatListScroll(event: Event): void {
  const target = event.target as HTMLElement
  const scrollBottom = target.scrollHeight - target.scrollTop - target.clientHeight
  
  if (scrollBottom < 50 && chatListHasMore.value && !chatListLoading.value) {
    loadChatHistory(true)
  }
}

function toggleCollapse(): void {
  isCollapsed.value = !isCollapsed.value
}

function createNewChat(): void {
  currentChatId.value = ''
  messages.value = []
  inputMessage.value = ''
  router.push(`/chat/${agentId.value}`)
}

function selectChat(conversationId: string): void {
  currentChatId.value = conversationId
  router.push(`/chat/${agentId.value}/${conversationId}`)
  loadMessages(conversationId)
}

async function loadMessages(conversationId: string): Promise<void> {
  if (!userStore.isLoggedIn) return
  
  try {
    const res = await getChatHistory(conversationId)
    if (!res) {
      return
    }
    messages.value = res.map((item: any, index: number) => ({
      id: `${conversationId}_${index}`,
      role: item.role,
      content: item.content,
      createTime: new Date()
    }))
    nextTick(() => {
      scrollToBottom()
    })
  } catch (e) {
    console.error(e)
    message.error('加载消息失败')
  }
}

function handleEnter(e: KeyboardEvent): void {
  if (!e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function handleKeyboardShortcut(e: KeyboardEvent): void {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    createNewChat()
  }
}

function sendMessage(): void {
  const content = inputMessage.value.trim()
  if (!content || isLoading.value) return

  const isDefaultAgent = agentId.value === 1

  if (!isDefaultAgent && !userStore.isLoggedIn) {
    authModalVisible.value = true
    return
  }

  const isNewChat = !currentChatId.value
  if (isNewChat && userStore.isLoggedIn) {
    currentChatId.value = generateConversationId()
  }
  
  if (isNewChat && !userStore.isLoggedIn) {
    currentChatId.value = 'temp_' + Date.now()
  }

  const userMessageIndex = messages.value.length
  messages.value.push({
    id: generateUUID(),
    role: 'user',
    content,
    createTime: new Date()
  })

  inputMessage.value = ''

  if (userStore.isLoggedIn) {
    const existingChatIndex = chatList.value.findIndex(c => c.id === currentChatId.value)
    if (existingChatIndex === -1) {
      chatList.value.unshift({
        id: currentChatId.value,
        title: '新对话',
        updateTime: new Date(),
        hasMessage: true
      })
    } else {
      const existingChat = chatList.value[existingChatIndex]
      existingChat.updateTime = new Date()
      chatList.value.splice(existingChatIndex, 1)
      chatList.value.unshift(existingChat)
    }
  }

  if (isNewChat && userStore.isLoggedIn) {
    router.push(`/chat/${agentId.value}/${currentChatId.value}`)
  }

  nextTick(() => {
    scrollToBottom()
    scrollToChatListTop()
  })

  isLoading.value = true

  const aiMessageIndex = messages.value.length
  messages.value.push({
    id: generateUUID(),
    role: 'assistant',
    content: '',
    createTime: new Date()
  })

  const token: string = localStorage.getItem('token') || ''
  const userId = userStore.userInfo?.id || null

  if (eventSource) {
    eventSource.close()
  }

  if (isDefaultAgent) {
    eventSource = chatWithDefaultAgent(content, currentChatId.value, agentId.value, userId)
  } else if (agentId.value === 2) {
    eventSource = chatWithCSApp(content, currentChatId.value, agentId.value, token)
  } else if (agentId.value === 3) {
    eventSource = chatWithMioManus(content, currentChatId.value, agentId.value, token)
  } else {
    eventSource = chatWithCustomAgent(content, currentChatId.value, agentId.value, token)
  }

  eventSource.onmessage = (event) => {
    const data = event.data
    if (data && data !== '[DONE]') {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
      }
    }
    
    if (data === '[DONE]') {
      if (isNewChat && userStore.isLoggedIn) {
        updateChatTitleWithTypewriter(content, messages.value[aiMessageIndex].content, userMessageIndex)
      }
      isLoading.value = false
      if (eventSource) {
        eventSource.close()
      }
    }
  }
  
  eventSource.onerror = () => {
    if (isNewChat && userStore.isLoggedIn) {
      updateChatTitleWithTypewriter(content, messages.value[aiMessageIndex].content, userMessageIndex)
    }
    isLoading.value = false
    if (eventSource) {
      eventSource.close()
    }
  }
}

async function updateChatTitleWithTypewriter(userContent: string, aiContent: string, chatIndex: number): Promise<void> {
  const title = await generateTitle(agentId.value, currentChatId.value, userContent + '\n' + aiContent)
  
  const chatItem = chatList.value.find(c => c.id === currentChatId.value)
  if (!chatItem) return
  
  chatItem.title = ''
  
  for (let i = 0; i < title.length; i++) {
    chatItem.title += title[i]
    await new Promise(resolve => setTimeout(resolve, 50))
  }
}


function generateConversationId(): string {
  const timestamp = Date.now().toString()
  const random = Math.floor(Math.random() * 10000).toString().padStart(4, '0')
  return timestamp + random
}

function generateUUID(): string {
  return Math.floor(Math.random() * 100000000).toString()
}

function scrollToBottom(): void {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function scrollToChatListTop(): void {
  if (chatListRef.value) {
    chatListRef.value.scrollTop = 0
  }
}

function handleMouseEnter(msgId: string): void {
  hoverMessageId.value = msgId
  if (copiedMessageId.value && copiedMessageId.value !== msgId) {
    copiedMessageId.value = ''
  }
}

async function copyMessage(content: string, messageId: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(content)
    copiedMessageId.value = messageId
  } catch (e) {
    console.error(e)
    message.error('复制失败')
  }
}

function shareChat(conversationId: string): void {
  const shareUrl = `${window.location.origin}/share/${agentId.value}/${conversationId}`
  navigator.clipboard.writeText(shareUrl).then(() => {
    message.success('分享链接已复制到剪贴板')
  }).catch(() => {
    message.error('复制失败')
  })
}

function confirmDeleteChat(conversationId: string): void {
  pendingDeleteChatId.value = conversationId
  deleteModalVisible.value = true
}

async function handleDeleteConfirm(): Promise<void> {
  if (!pendingDeleteChatId.value) return
  
  deleteLoading.value = true
  try {
    await deleteChatApi(pendingDeleteChatId.value)
    chatList.value = chatList.value.filter(c => c.id !== pendingDeleteChatId.value)
    if (currentChatId.value === pendingDeleteChatId.value) {
      createNewChat()
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

function formatMessage(content: string): string {
  if (!content) return ''
  try {
    marked.setOptions({
      breaks: true,
      gfm: true
    })
    return marked.parse(content) as string
  } catch (e) {
    console.error('Markdown parse error:', e)
    return content.replace(/\n/g, '<br>')
  }
}

function formatTime(date: Date | string): string {
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return d.toLocaleDateString()
}

function goHome(): void {
  router.push('/')
}

function goDashboard(): void {
  router.push('/dashboard')
}

function goProfile(): void {
  router.push('/dashboard/profile')
}

function showAuthModal(): void {
  authModalVisible.value = true
}

function handleAuthSuccess(): void {
  window.location.reload()
}

async function handleLogout(): Promise<void> {
  await userStore.logout()
  message.success('已退出登录')
}

onMounted(() => {
  window.addEventListener('keydown', handleKeyboardShortcut)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyboardShortcut)
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style lang="scss" scoped>
.chat-layout {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}

.sidebar {
  width: 280px;
  background: #f9fafd;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  border-right: 1px solid #e8eaed;

  &.collapsed {
    width: 64px;
  }

  .sidebar-top {
    height: 64px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 16px;
    border-bottom: 1px solid #e8eaed;

    .logo-section {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 12px;

      .agent-avatar {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        object-fit: cover;
      }

      .agent-name {
        font-size: 16px;
        font-weight: 600;
        color: #202124;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }

    .collapse-btn {
      color: #5f6368;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 4px;

      &:hover {
        color: $primary-color;
        background: rgba(42, 161, 169, 0.08);
      }
    }
  }

  &.collapsed {
    .sidebar-top {
      justify-content: center;
      padding: 0;
    }
  }

  .sidebar-content {
    flex: 1;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    padding: 12px;

    .sidebar-actions {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-bottom: 16px;
    }

    .new-chat-btn {
      width: 100%;
      height: 44px;
      display: flex;
      align-items: center;
      justify-content: flex-start;
      padding: 0 16px;
      background: linear-gradient(135deg, rgba($primary-color, 0.1) 0%, rgba($primary-color, 0.05) 100%);
      border: 1px solid $primary-color;
      border-radius: 10px;
      color: $primary-color;
      font-size: 15px;
      font-weight: 600;

      &:hover {
        background: linear-gradient(135deg, rgba($primary-color, 0.18) 0%, rgba($primary-color, 0.1) 100%);
        border-color: darken($primary-color, 5%);
        color: darken($primary-color, 5%);
      }

      .btn-text {
        flex: 1;
        text-align: left;
      }

      .shortcut-hint {
        display: flex;
        gap: 4px;

        .key-box {
          padding: 2px 6px;
          background: rgba($primary-color, 0.15);
          border: 1px solid rgba($primary-color, 0.3);
          border-radius: 4px;
          font-size: 11px;
          color: $primary-color;
          font-weight: 500;
        }
      }
    }

    .new-chat-btn-collapsed {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      margin: 0 auto;
      background: linear-gradient(135deg, rgba($primary-color, 0.1) 0%, rgba($primary-color, 0.05) 100%);
      border: 1px solid $primary-color;
      border-radius: 10px;
      cursor: pointer;
      color: $primary-color;

      &:hover {
        background: linear-gradient(135deg, rgba($primary-color, 0.18) 0%, rgba($primary-color, 0.1) 100%);
        border-color: darken($primary-color, 5%);
        color: darken($primary-color, 5%);
      }
    }

    .app-square-btn {
      width: 100%;
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: flex-start;
      padding: 0 16px;
      background: #fff;
      border: 1px solid #e5e6eb;
      border-radius: 8px;
      color: #5f6368;
      font-size: 15px;
      font-weight: 500;

      &:hover {
        background: rgba($primary-color, 0.08);
        border-color: $primary-color;
        color: $primary-color;
      }

      .btn-text {
        flex: 1;
        text-align: left;
      }
    }

    .app-square-btn-collapsed {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      margin: 0 auto;
      background: #fff;
      border: 1px solid #e5e6eb;
      border-radius: 8px;
      cursor: pointer;

      &:hover {
        background: rgba($primary-color, 0.08);
        border-color: $primary-color;
        color: $primary-color;
      }
    }

    .chat-list-section {
      flex: 1;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      border-top: 1px solid #e8eaed;
      padding-top: 12px;

      .section-title {
        font-size: 12px;
        color: #86909c;
        padding: 0 4px;
        margin-bottom: 8px;
        font-weight: 500;
      }
    }

    .chat-list {
      flex: 1;
      overflow-y: auto;
      overflow-x: hidden;
      scrollbar-width: thin;
      scrollbar-color: transparent transparent;

      &:hover {
        scrollbar-color: rgba(0, 0, 0, 0.2) transparent;
      }

      &::-webkit-scrollbar {
        width: 6px;
        height: 6px;
      }

      &::-webkit-scrollbar-button {
        display: none;
      }

      &::-webkit-scrollbar-track {
        background: transparent;
      }

      &::-webkit-scrollbar-thumb {
        background: transparent;
        border-radius: 3px;
        transition: background 0.3s;
      }

      &:hover::-webkit-scrollbar-thumb {
        background: rgba(0, 0, 0, 0.15);
      }

      &:hover::-webkit-scrollbar-thumb:hover {
        background: rgba(0, 0, 0, 0.25);
      }

      .chat-item {
        display: flex;
        align-items: center;
        padding: 10px 12px;
        margin: 4px 0;
        cursor: pointer;
        color: #5f6368;
        border-radius: 8px;
        transition: all 0.2s;

        &:hover {
          background: rgba(42, 161, 169, 0.08);
        }

        &.active {
          background: rgba(42, 161, 169, 0.12);
          color: $primary-color;
        }

        &.chat-item-collapsed {
          justify-content: center;
          width: 40px;
          height: 40px;
          margin: 4px auto;
          padding: 0;
        }

        .chat-item-content {
          flex: 1;
          min-width: 0;

          .chat-item-title {
            font-size: 14px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }

          .chat-item-time {
            font-size: 12px;
            color: #909399;
            margin-top: 2px;
          }
        }

        .chat-item-more {
          opacity: 0;
          transition: opacity 0.2s;
        }

        &:hover .chat-item-more {
          opacity: 1;
        }
      }

      .chat-item-skeleton {
        padding: 10px 12px;
        margin: 4px 0;
      }

      .chat-list-loading {
        display: flex;
        justify-content: center;
        padding: 12px 0;
      }
    }
  }

  .sidebar-footer {
    padding: 16px;
    border-top: 1px solid #e8eaed;

    .user-info {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px;
      cursor: pointer;
      border-radius: 8px;
      transition: background 0.3s;

      &:hover {
        background: rgba(42, 161, 169, 0.08);
      }

      .user-detail {
        display: flex;
        flex-direction: column;

        .user-name {
          font-size: 14px;
          font-weight: 500;
          color: #202124;
        }

        .user-role {
          margin-top: 4px;
          font-size: 12px;
          color: #909399;
        }
      }

      &.collapsed {
        justify-content: center;
        padding: 8px;
      }
    }

    :deep(.ant-btn-primary) {
      background: $primary-color;
      border-color: $primary-color;

      &:hover {
        background: darken($primary-color, 10%);
        border-color: darken($primary-color, 10%);
      }
    }
  }

  &.collapsed {
    .sidebar-content {
      padding: 8px;

      .chat-list {
        .chat-item {
          justify-content: center;
          padding: 10px;
        }
      }
    }

    .sidebar-footer {
      padding: 8px;
      display: flex;
      justify-content: center;
    }
  }
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

  .chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    padding-bottom: 120px;

    .messages-wrapper {
      max-width: 800px;
      margin: 0 auto;

      .message {
        display: flex;
        gap: 16px;
        margin-bottom: 24px;

        &.user {
          flex-direction: row-reverse;

          .message-content {
            align-items: flex-end;
          }
        }

        .message-avatar {
          flex-shrink: 0;

          .agent-avatar-msg {
            background: $primary-color;
          }
        }

        .message-content {
          flex: 1;
          display: flex;
          flex-direction: column;
          position: relative;

          .message-text {
            max-width: 70%;
            padding: 12px 16px;
            border-radius: 12px;
            font-size: 14px;
            line-height: 1.6;
            word-break: break-word;

            :deep(pre) {
              background: #f6f8fa;
              border-radius: 6px;
              padding: 12px 16px;
              overflow-x: auto;
              margin: 8px 0;

              code {
                font-family: 'Consolas', 'Monaco', monospace;
                font-size: 13px;
              }
            }

            :deep(code) {
              background: #f6f8fa;
              padding: 2px 6px;
              border-radius: 4px;
              font-family: 'Consolas', 'Monaco', monospace;
              font-size: 13px;
            }

            :deep(p) {
              margin: 0 0 8px 0;

              &:last-child {
                margin-bottom: 0;
              }
            }

            :deep(ul), :deep(ol) {
              margin: 8px 0;
              padding-left: 20px;
            }

            :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
              margin: 12px 0 8px 0;
              font-weight: 600;
            }

            :deep(a) {
              color: $primary-color;
              text-decoration: none;

              &:hover {
                text-decoration: underline;
              }
            }

            :deep(blockquote) {
              border-left: 4px solid $primary-color;
              padding-left: 12px;
              margin: 8px 0;
              color: #666;
            }
          }

          .message-loading {
            display: flex;
            gap: 6px;
            padding: 8px 0;

            span {
              width: 8px;
              height: 8px;
              background: $primary-color;
              border-radius: 50%;
              animation: loading-bounce 1.4s infinite ease-in-out both;

              &:nth-child(1) {
                animation-delay: -0.32s;
              }
              &:nth-child(2) {
                animation-delay: -0.16s;
              }
              &:nth-child(3) {
                animation-delay: 0s;
              }
            }
          }

          .message-actions {
            min-height: 35px;
            margin-top: 8px;
            display: flex;
            gap: 8px;
            align-items: center;

            .copy-btn {
              color: #86909c;
              padding: 4px 8px;
              height: auto;
              font-size: 14px;
              transition: all 0.2s;

              &:hover {
                color: $primary-color;
                background: rgba($primary-color, 0.08);
              }

              &.copied {
                color: #52c41a;
              }
            }
          }
        }

        &.user .message-text {
          background: $primary-color;
          color: #fff;

          :deep(code) {
            background: rgba(255, 255, 255, 0.2);
          }

          :deep(pre) {
            background: rgba(255, 255, 255, 0.1);
          }
        }

        &.assistant .message-text {
          background: transparent;
          color: #202124;
          max-width: 100%;
          padding: 0;
        }
      }
    }
  }

  .chat-center-area {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    flex: 1;
    padding: 24px;
    width: 100%;
    box-sizing: border-box;

    &.has-messages {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      flex: none;
      justify-content: flex-end;
      padding: 16px 24px 24px;
      background: linear-gradient(to top, #fff 80%, transparent);

      .welcome-section {
        display: none;
      }
    }

    .welcome-section {
      display: flex;
      flex-direction: row;
      align-items: center;
      gap: 16px;
      margin-bottom: 32px;

      .welcome-avatar {
        width: 48px;
        height: 48px;
        border-radius: 50%;
        object-fit: cover;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
        flex-shrink: 0;
      }

      .welcome-title {
        font-size: 24px;
        font-weight: 600;
        color: #1d2129;
        margin: 0;
        white-space: nowrap;
      }
    }

    .chat-input-wrapper {
      width: 100%;
      max-width: 800px;

      .chat-input-container {
        width: 100%;

        .input-box {
          display: flex;
          align-items: center;
          gap: 12px;
          background: #fff;
          border: 1px solid #e5e6eb;
          border-radius: 24px;
          padding: 14px 18px;
          box-shadow: 0 4px 12px rgba(242, 243, 245, 1);
          transition: all 0.2s;

          &:focus-within {
            border-color: $primary-color;
            box-shadow: 0 4px 16px rgba(42, 161, 169, 0.2);
          }

          .chat-textarea {
            flex: 1;
            border: none;
            background: transparent;
            resize: none;
            font-size: 16px;
            line-height: 1.5;
            color: #1d2129;

            &:focus {
              outline: none;
              box-shadow: none;
            }

            &::placeholder {
              color: #86909c;
            }

            :deep(.ant-input) {
              border: none;
              background: transparent;
              padding: 0;

              &:focus {
                outline: none;
                box-shadow: none;
              }
            }
          }

          .send-btn {
            width: 40px;
            height: 40px;
            min-width: 40px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: $primary-color;
            border-color: $primary-color;
            flex-shrink: 0;

            &:hover:not(:disabled) {
              background: darken($primary-color, 10%);
              border-color: darken($primary-color, 10%);
            }

            &:disabled {
              background: #c9cdd4;
              border-color: #c9cdd4;
            }

            :deep(.anticon) {
              font-size: 18px;
            }
          }
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .sidebar {
    width: 240px;

    &.collapsed {
      width: 64px;
    }
  }

  .chat-container {
    .chat-center-area {
      padding: 16px;

      .welcome-section {
        gap: 12px;

        .welcome-avatar {
          width: 40px;
          height: 40px;
        }

        .welcome-title {
          font-size: 20px;
        }
      }

      .chat-input-wrapper {
        .chat-input-container {
          .input-box {
            padding: 12px 14px;
            border-radius: 20px;

            .chat-textarea {
              font-size: 14px;
            }

            .send-btn {
              width: 36px;
              height: 36px;
              min-width: 36px;

              :deep(.anticon) {
                font-size: 16px;
              }
            }
          }
        }
      }
    }
  }
}

@media (max-width: 480px) {
  .sidebar {
    position: absolute;
    z-index: 100;
    height: 100%;

    &.collapsed {
      width: 0;
      overflow: hidden;
    }
  }

  .chat-container {
    .chat-center-area {
      .welcome-section {
        .welcome-title {
          font-size: 20px;
        }
      }
    }
  }
}

@keyframes loading-bounce {
  0%, 80%, 100% {
    transform: scale(0);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

:deep(ul), :deep(ol) {
  list-style: decimal;
}

:deep(table) {
  border: 1px solid #ccc;
}
:deep(table) td,
:deep(table) th {
  border-bottom: 1px solid #ccc;
  border-right: 1px solid #ccc;
  padding: 5px 10px;
}
:deep(table) th {
  // border-bottom: 2px solid #ccc;
  text-align: center;
  background: #dee8ee;
}
:deep(table) th:last-child {
  border-right: none;
}
:deep(table) td:last-child {
  border-right: none;
}

:deep(table) tr:last-child td {
  border-bottom: none;
}
:deep(table) tr:nth-child(even) {
  background: #eff3f5;
}
/* blockquote 样式 */
:deep(blockquote) {
  display: block;
  border-left: 8px solid #d0e5f2;
  padding: 5px 10px;
  margin: 10px 0;
  line-height: 1.4;
  font-size: 100%;
  background-color: #f1f1f1;
}

.delete-menu-item {
  color: #ff4d4f !important;

  &:hover {
    background-color: #fff1f0 !important;
    color: #ff4d4f !important;
  }

  :deep(.ant-dropdown-menu-item-icon) {
    color: #ff4d4f !important;
  }
}

:deep(.delete-menu-item) {
  color: #ff4d4f !important;

  .ant-dropdown-menu-item-icon {
    color: #ff4d4f !important;
  }

  &:hover {
    background-color: #fff1f0 !important;
    color: #ff4d4f !important;
  }
}
</style>
