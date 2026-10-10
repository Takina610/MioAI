<template>
  <aside class="sidebar" :class="{ collapsed: isCollapsed }">
    <div class="sidebar-top">
      <div class="logo-section" v-show="!isCollapsed">
        <img :src="agentInfo?.avatar || '/logo.png'" alt="Avatar" class="agent-avatar" />
        <span class="agent-name">{{ agentInfo?.name || 'MioBot' }}</span>
      </div>
      <a-button
        type="text"
        class="collapse-btn"
        @click="isCollapsed = !isCollapsed"
      >
        <MenuFoldOutlined v-if="!isCollapsed" />
        <MenuUnfoldOutlined v-else />
      </a-button>
    </div>

    <div class="sidebar-content">
      <div class="sidebar-actions">
        <div class="new-chat-btn-wrapper" v-show="!isCollapsed">
          <a-button class="new-chat-btn" @click="emit('new-chat')">
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
          <div class="new-chat-btn-collapsed" @click="emit('new-chat')">
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

      <ChatSessionList
        ref="sessionListRef"
        :chat-list="chatList"
        :current-chat-id="currentChatId"
        :chat-list-loading="chatListLoading"
        :is-collapsed="isCollapsed"
        :streaming-chat-ids="streamingChatIds"
        @select="emit('select', $event)"
        @share="emit('share', $event)"
        @delete="emit('delete', $event)"
        @load-more="emit('load-more')"
      />
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
        <a-button type="primary" block @click="emit('login')" v-show="!isCollapsed">
          登录
        </a-button>
        <a-button type="primary" @click="emit('login')" v-show="isCollapsed">
          <UserOutlined />
        </a-button>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import type { Agent, ChatSession } from '@/types'
import ChatSessionList from './ChatSessionList.vue'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  HomeOutlined,
  RobotOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
  FormOutlined
} from '@ant-design/icons-vue'

defineProps<{
  agentInfo: Agent | null
  chatList: ChatSession[]
  currentChatId: string
  chatListLoading: boolean
  /** 正在流式执行中的会话 ID（列表项显示加载动画） */
  streamingChatIds?: string[]
}>()

const emit = defineEmits<{
  (e: 'select', conversationId: string): void
  (e: 'new-chat'): void
  (e: 'share', conversationId: string): void
  (e: 'delete', conversationId: string): void
  (e: 'load-more'): void
  (e: 'login'): void
}>()

const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref<boolean>(false)
const sessionListRef = ref<InstanceType<typeof ChatSessionList> | null>(null)

function checkListFilled(): void {
  sessionListRef.value?.checkListFilled()
}

function scrollToTop(): void {
  sessionListRef.value?.scrollToTop()
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

async function handleLogout(): Promise<void> {
  await userStore.logout()
  message.success('已退出登录')
  window.location.reload()
}

defineExpose({ checkListFilled, scrollToTop })
</script>

<style lang="scss" scoped>
.sidebar {
  width: 280px;
  background: #f5f3ec;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  border-right: 1px solid #e8e6dc;

  &.collapsed {
    width: 64px;
  }

  .sidebar-top {
    height: 64px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 16px;
    border-bottom: 1px solid #e8e6dc;

    .logo-section {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 12px;

      .agent-avatar {
        width: 32px;
        height: 32px;
        border-radius: 25%;
        object-fit: cover;
      }

      .agent-name {
        font-size: 16px;
        font-weight: 600;
        color: #141413;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }

    .collapse-btn {
      color: #5f5d55;
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

    .sidebar-content {
      padding: 8px;
    }

    .sidebar-footer {
      padding: 8px;
      display: flex;
      justify-content: center;
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
      border: 1px solid #e8e6dc;
      border-radius: 8px;
      color: #5f5d55;
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
      border: 1px solid #e8e6dc;
      border-radius: 8px;
      cursor: pointer;

      &:hover {
        background: rgba($primary-color, 0.08);
        border-color: $primary-color;
        color: $primary-color;
      }
    }
  }

  .sidebar-footer {
    padding: 16px;
    border-top: 1px solid #e8e6dc;

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
          color: #141413;
        }

        .user-role {
          margin-top: 4px;
          font-size: 12px;
          color: #8c8a82;
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
}

@media (max-width: 768px) {
  .sidebar {
    width: 240px;

    &.collapsed {
      width: 64px;
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
}
</style>
