<template>
  <div class="chat-layout">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-top">
        <div class="logo-section" v-show="!isCollapsed">
          <img :src="agentInfo?.avatar || '/favicon.svg'" alt="Avatar" class="agent-avatar" />
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
        <div class="new-chat-btn-wrapper" v-show="!isCollapsed">
          <a-button class="new-chat-btn" @click="createNewChat">
            <PlusSquareOutlined />
            <span class="btn-text">新对话</span>
            <div class="shortcut-hint">
              <span class="key-box">Ctrl</span>
              <span class="key-box">K</span>
            </div>
          </a-button>
        </div>
        <div class="new-chat-btn-collapsed" v-show="isCollapsed" @click="createNewChat">
          <PlusSquareOutlined />
        </div>
        <div class="chat-list">
          <div
            v-for="chat in chatList"
            :key="chat.id"
            class="chat-item"
            :class="{ active: currentChatId === chat.id }"
            @click="selectChat(chat.id)"
          >
            <MessageOutlined v-if="isCollapsed" />
            <template v-else>
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
                    <a-menu-item key="delete" @click="deleteChat(chat.id)">
                      <DeleteOutlined /> 删除对话
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
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
                <span class="user-role">{{ userStore.userInfo?.userRole === 'admin' ? '管理员' : '普通用户' }}</span>
              </div>
            </div>
            <template #overlay>
              <a-menu>
                <a-menu-item key="home" @click="goHome">
                  <HomeOutlined /> 返回首页
                </a-menu-item>
                <a-menu-item key="dashboard" @click="goDashboard">
                  <AppstoreOutlined /> 控制台
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
            >
              <div class="message-avatar">
                <a-avatar v-if="msg.role === 'user'" :src="userStore.userAvatar">
                  {{ userStore.userName?.charAt(0)?.toUpperCase() }}
                </a-avatar>
                <a-avatar v-else :src="agentInfo?.avatar" class="agent-avatar-msg">
                  {{ agentInfo?.name?.charAt(0) }}
                </a-avatar>
              </div>
              <div class="message-content">
                <div class="message-text" v-html="formatMessage(msg.content)"></div>
              </div>
            </div>
            <div v-if="isLoading" class="message assistant">
              <div class="message-avatar">
                <a-avatar :src="agentInfo?.avatar" class="agent-avatar-msg">
                  {{ agentInfo?.name?.charAt(0) }}
                </a-avatar>
              </div>
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
            <img :src="agentInfo?.avatar || '/favicon.svg'" alt="Agent" class="welcome-avatar" />
            <h2 class="welcome-title">今天有什么可以帮到你？</h2>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import { getAgentById } from '@/api/agent'
import type { Agent } from '@/types'
import AuthModal from '@/components/AuthModal.vue'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PlusSquareOutlined,
  MessageOutlined,
  MoreOutlined,
  DeleteOutlined,
  HomeOutlined,
  AppstoreOutlined,
  LogoutOutlined,
  UserOutlined,
  ArrowUpOutlined
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
const messages = ref<ChatMessage[]>([])
const inputMessage = ref<string>('')
const isLoading = ref<boolean>(false)
const messagesRef = ref<HTMLElement | null>(null)

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

async function loadAgentInfo(): Promise<void> {
  try {
    const res = await getAgentById(agentId.value)
    agentInfo.value = res
    loadChatHistory()
  } catch (e) {
    console.error(e)
    message.error('加载智能体信息失败')
  }
}

function loadChatHistory(): void {
  const stored = localStorage.getItem(`chat_history_${agentId.value}`)
  if (stored) {
    const allChats: ChatSession[] = JSON.parse(stored)
    chatList.value = allChats.filter(chat => chat.hasMessage)
    if (chatList.value.length > 0) {
      selectChat(chatList.value[0].id)
    }
  }
}

function saveChatHistory(): void {
  const stored = localStorage.getItem(`chat_history_${agentId.value}`)
  let allChats: ChatSession[] = []
  if (stored) {
    allChats = JSON.parse(stored)
  }
  
  const existingIds = new Set(allChats.map(c => c.id))
  for (const chat of chatList.value) {
    if (!existingIds.has(chat.id)) {
      allChats.push(chat)
    } else {
      const index = allChats.findIndex(c => c.id === chat.id)
      if (index !== -1) {
        allChats[index] = chat
      }
    }
  }
  
  localStorage.setItem(`chat_history_${agentId.value}`, JSON.stringify(allChats))
}

function toggleCollapse(): void {
  isCollapsed.value = !isCollapsed.value
}

function createNewChat(): void {
  currentChatId.value = ''
  messages.value = []
  inputMessage.value = ''
}

function selectChat(chatId: string): void {
  currentChatId.value = chatId
  loadMessages(chatId)
}

function loadMessages(chatId: string): void {
  const stored = localStorage.getItem(`messages_${agentId.value}_${chatId}`)
  if (stored) {
    messages.value = JSON.parse(stored)
  } else {
    messages.value = []
  }
  nextTick(() => {
    scrollToBottom()
  })
}

function saveMessages(): void {
  localStorage.setItem(
    `messages_${agentId.value}_${currentChatId.value}`,
    JSON.stringify(messages.value)
  )
}

function deleteChat(chatId: string): void {
  chatList.value = chatList.value.filter((c) => c.id !== chatId)
  saveChatHistory()
  localStorage.removeItem(`messages_${agentId.value}_${chatId}`)
  if (currentChatId.value === chatId) {
    if (chatList.value.length > 0) {
      selectChat(chatList.value[0].id)
    } else {
      currentChatId.value = ''
      messages.value = []
    }
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

async function sendMessage(): Promise<void> {
  const content = inputMessage.value.trim()
  if (!content || isLoading.value) return

  if (!currentChatId.value) {
    currentChatId.value = Date.now().toString()
  }

  const userMessage: ChatMessage = {
    id: Date.now().toString(),
    role: 'user',
    content,
    createTime: new Date()
  }
  messages.value.push(userMessage)
  inputMessage.value = ''

  const existingChat = chatList.value.find(c => c.id === currentChatId.value)
  if (!existingChat) {
    const newChat: ChatSession = {
      id: currentChatId.value,
      title: content.slice(0, 20) + (content.length > 20 ? '...' : ''),
      updateTime: new Date(),
      hasMessage: true
    }
    chatList.value.unshift(newChat)
  } else {
    existingChat.updateTime = new Date()
  }
  
  saveChatHistory()
  saveMessages()
  nextTick(() => {
    scrollToBottom()
  })

  isLoading.value = true

  try {
    const response = await mockAIResponse(content)
    const assistantMessage: ChatMessage = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: response,
      createTime: new Date()
    }
    messages.value.push(assistantMessage)
    saveMessages()
    nextTick(() => {
      scrollToBottom()
    })
  } catch (e) {
    console.error(e)
    message.error('发送消息失败')
  } finally {
    isLoading.value = false
  }
}

async function mockAIResponse(content: string): Promise<string> {
  await new Promise((resolve) => setTimeout(resolve, 1000))
  return `我是${agentInfo.value?.name || '智能体'}，收到您的消息："${content}"。\n\n这是一个模拟的回复，实际功能需要连接后端AI服务。`
}

function scrollToBottom(): void {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function formatMessage(content: string): string {
  return content.replace(/\n/g, '<br>')
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

function showAuthModal(): void {
  authModalVisible.value = true
}

function handleAuthSuccess(): void {
  window.location.reload()
}

async function handleLogout(): Promise<void> {
  await userStore.logout()
  message.success('已退出登录')
  router.push('/')
}

onMounted(() => {
  if (!userStore.isLoggedIn) {
    authModalVisible.value = true
  }
  window.addEventListener('keydown', handleKeyboardShortcut)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyboardShortcut)
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

    .new-chat-btn-wrapper {
      margin-bottom: 12px;
    }

    .new-chat-btn {
      width: 100%;
      height: 44px;
      display: flex;
      align-items: center;
      justify-content: flex-start;
      gap: 10px;
      padding: 0 16px;
      background: #fff;
      border: 1px solid #e5e6eb;
      border-radius: 8px;
      color: #1d2129;
      font-size: 14px;
      font-weight: 500;
      transition: all 0.2s;

      &:hover {
        background: rgba($primary-color, 0.08);
        border-color: $primary-color;
        color: $primary-color;
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
          background: #f2f3f5;
          border: 1px solid #e5e6eb;
          border-radius: 4px;
          font-size: 11px;
          color: #86909c;
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
      margin: 0 auto 12px;
      background: #fff;
      border: 1px solid #e5e6eb;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        background: rgba($primary-color, 0.08);
        border-color: $primary-color;
        color: $primary-color;
      }
    }

    .chat-list {
      flex: 1;
      overflow-y: auto;

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

          .message-text {
            max-width: 70%;
            padding: 12px 16px;
            border-radius: 12px;
            font-size: 14px;
            line-height: 1.6;
            word-break: break-word;
          }

          .message-loading {
            display: flex;
            gap: 4px;
            padding: 12px 16px;
            background: #f5f5f5;
            border-radius: 12px;

            span {
              width: 8px;
              height: 8px;
              background: #909399;
              border-radius: 50%;
              animation: bounce 1.4s infinite ease-in-out;

              &:nth-child(1) {
                animation-delay: -0.32s;
              }
              &:nth-child(2) {
                animation-delay: -0.16s;
              }
            }
          }
        }

        &.user .message-text {
          background: $primary-color;
          color: #fff;
        }

        &.assistant .message-text {
          background: #f5f5f5;
          color: #202124;
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

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}
</style>
