<template>
  <!-- 悬浮胶囊侧边栏：收起时整体滑出屏幕（无图标栏），peek/展开时以圆角面板浮现 -->
  <aside
    class="sidebar"
    :class="{ collapsed: collapsed, peek: peeking }"
    @mouseenter="enterPanel"
    @mouseleave="leavePanel"
  >
    <div class="sidebar-capsule">
      <div class="sidebar-top">
        <img :src="agentInfo?.avatar || '/logo.png'" alt="Avatar" class="agent-avatar" />
        <span class="agent-name">{{ agentInfo?.name || 'MioBot' }}</span>
        <button
          v-if="canPersonalize"
          class="personalize-btn"
          title="个性化设置"
          @click="emit('personalize')"
        >
          <SettingOutlined />
        </button>
      </div>

      <div class="sidebar-content">
        <div class="sidebar-actions">
          <!-- 搜索入口：胶囊输入框样式（图2），点击打开命令面板（Ctrl+P） -->
          <button class="search-entry" @click="emit('search')">
            <SearchOutlined class="btn-icon" />
            <span class="btn-text">搜索</span>
            <span class="shortcut-hint">
              <span class="key-box">Ctrl</span>
              <span class="key-box">P</span>
            </span>
          </button>

          <a-button class="new-chat-btn" @click="emit('new-chat')">
            <FormOutlined />
            <span class="btn-text">新对话</span>
            <span class="shortcut-hint">
              <span class="key-box">Ctrl</span>
              <span class="key-box">K</span>
            </span>
          </a-button>

          <a-button class="app-square-btn" @click="goDashboard">
            <RobotOutlined />
            <span class="btn-text">智能体广场</span>
          </a-button>
        </div>

        <ChatSessionList
          ref="sessionListRef"
          :chat-list="chatList"
          :current-chat-id="currentChatId"
          :chat-list-loading="chatListLoading"
          :streaming-chat-ids="streamingChatIds"
          @select="emit('select', $event)"
          @share="emit('share', $event)"
          @delete="emit('delete', $event)"
          @load-more="emit('load-more')"
        />
      </div>

      <div class="sidebar-footer">
        <template v-if="userStore.isLoggedIn">
          <a-dropdown :trigger="['click']" placement="topLeft" :open="userMenuOpen" @open-change="(v: boolean) => (userMenuOpen = v)">
            <div class="user-info">
              <a-avatar :size="40" :src="userStore.userAvatar">
                {{ userStore.userName?.charAt(0)?.toUpperCase() }}
              </a-avatar>
              <div class="user-detail">
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
          <a-button type="primary" block @click="emit('login')">
            登录
          </a-button>
        </template>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import type { Agent, ChatSession } from '@/types'
import ChatSessionList from './ChatSessionList.vue'
import {
  SearchOutlined,
  HomeOutlined,
  RobotOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
  FormOutlined
} from '@ant-design/icons-vue'

const props = defineProps<{
  agentInfo: Agent | null
  chatList: ChatSession[]
  currentChatId: string
  chatListLoading: boolean
  /** 正在流式执行中的会话 ID（列表项显示加载动画） */
  streamingChatIds?: string[]
  /** 内置智能体且已登录：显示个性化设置入口 */
  canPersonalize?: boolean
  /** 收起态：整体滑出屏幕，悬浮收缩按钮时临时滑出（peek） */
  collapsed: boolean
  peeking: boolean
  enterPanel: () => void
  leavePanel: () => void
}>()

const emit = defineEmits<{
  (e: 'select', conversationId: string): void
  (e: 'new-chat'): void
  (e: 'search'): void
  (e: 'share', conversationId: string): void
  (e: 'delete', conversationId: string): void
  (e: 'load-more'): void
  (e: 'login'): void
  (e: 'personalize'): void
}>()

const router = useRouter()
const userStore = useUserStore()

const sessionListRef = ref<InstanceType<typeof ChatSessionList> | null>(null)

// 面板收起（含 peek 收回）时同步关闭内部浮层：下拉框挂在 body 上，不会随面板 transform 消失
const userMenuOpen = ref(false)
watch(() => props.collapsed && !props.peeking, hidden => {
  if (hidden) {
    userMenuOpen.value = false
    sessionListRef.value?.closeMenus()
  }
})

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
  @include floating-sidebar-shell(304px);

  // peek 悬浮唤出：logo 从中心向四周放大出现（延迟期由 backwards 保持隐藏）；
  // 缩进量与收缩按钮错开（按钮 28~64px），互不压叠
  &.peek .sidebar-top {
    padding-left: 64px;

    .agent-avatar,
    .agent-name {
      animation: peek-logo-in 0.22s ease 0.24s backwards;
    }
  }

  // 收起且未 peek（面板收回中）：logo 原地缩小消失的确定性动画（both 保持末态），
  // 不依赖 transition——面板同时左移会掩盖过渡的感知
  &.collapsed:not(.peek) .sidebar-top {
    .agent-avatar,
    .agent-name {
      animation: peek-logo-out 0.2s ease both;
    }
  }

  .sidebar-top {
    height: 64px;
    min-height: 64px;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 16px;
    border-bottom: 1px solid #e8e6dc;
    transition: padding-left 0.28s cubic-bezier(0.32, 0.72, 0.35, 1);

    .agent-avatar,
    .agent-name {
      transform-origin: center;
      // 覆盖 peek↔展开切换（无动画参与的方向）：0→1 平滑浮现
      transition: opacity 0.22s ease, transform 0.22s ease;
    }

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

    .personalize-btn {
      margin-left: auto;
      flex-shrink: 0;
      width: 28px;
      height: 28px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: none;
      border-radius: 8px;
      background: transparent;
      color: #8c8a82;
      cursor: pointer;
      font-size: 14px;
      transition: color 0.2s, background 0.2s;

      &:hover {
        color: $primary-color;
        background: rgba(42, 161, 169, 0.08);
      }
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

      // 快捷键提示盒：搜索入口与新对话按钮共用
      .shortcut-hint {
        display: flex;
        gap: 4px;

        .key-box {
          padding: 1px 6px;
          background: #f0eee6;
          border: 1px solid #e0ddd2;
          border-radius: 4px;
          font-size: 11px;
          color: #8c8a82;
          font-weight: 500;
        }
      }

      .new-chat-btn .shortcut-hint .key-box {
        background: rgba($primary-color, 0.15);
        border-color: rgba($primary-color, 0.3);
        color: $primary-color;
      }
    }

    // 搜索入口：胶囊输入框样式（图2），点击打开命令面板
    .search-entry {
      width: 100%;
      height: 40px;
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 0 12px;
      background: #fff;
      border: 1px solid #e8e6dc;
      border-radius: 20px;
      color: #8c8a82;
      font-size: 14px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        border-color: $primary-color;
        color: $primary-color;
        background: rgba($primary-color, 0.06);
      }

      .btn-icon {
        font-size: 15px;
      }

      .btn-text {
        flex: 1;
        text-align: left;
      }
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

@keyframes peek-logo-in {
  from {
    opacity: 0;
    transform: scale(0.6);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes peek-logo-out {
  from {
    opacity: 1;
    transform: none;
  }

  to {
    opacity: 0;
    transform: scale(0.5);
  }
}
</style>
