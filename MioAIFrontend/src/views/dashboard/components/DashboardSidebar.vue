<template>
  <!-- 悬浮胶囊侧边栏：收起时整体滑出屏幕，Header 上的收缩按钮悬浮唤出（peek） -->
  <aside
    class="sidebar"
    :class="{ collapsed: collapsed, peek: peeking }"
    @mouseenter="enterPanel"
    @mouseleave="leavePanel"
  >
    <div class="sidebar-capsule">
      <div class="sidebar-top">
        <a class="logo-link" @click="refreshPage">
          <img src="/logo.png" alt="Logo" class="logo-img" />
          <span class="logo-text">MioAI</span>
        </a>
      </div>

      <div class="sidebar-menu">
        <div class="menu-group" v-for="group in menuGroups" :key="group.title">
          <div class="menu-group-title">{{ group.title }}</div>
          <div class="menu-items">
            <div
              v-for="item in group.items"
              :key="item.key"
              class="menu-item"
              :class="{ active: currentKey === item.key }"
              @click="navigateTo(item)"
            >
              <component :is="item.icon" class="menu-icon" />
              <span class="menu-text">{{ item.label }}</span>
            </div>
          </div>
        </div>
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
                <a-menu-item key="home" @click="router.push('/')">
                  <HomeOutlined /> 返回首页
                </a-menu-item>
                <a-menu-item key="profile" @click="router.push('/dashboard/profile')">
                  <UserOutlined /> 个人中心
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
          <a-button type="primary" block @click="emit('login-required')">
            登录
          </a-button>
        </template>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import { useDashboardMenu } from '../composables/useDashboardMenu'
import {
  HomeOutlined,
  UserOutlined,
  LogoutOutlined
} from '@ant-design/icons-vue'

const props = defineProps<{
  /** 收起态：整体滑出屏幕，Header 收缩按钮悬浮时临时滑出（peek） */
  collapsed: boolean
  peeking: boolean
  enterPanel: () => void
  leavePanel: () => void
}>()

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { menuGroups, currentKey, navigateTo } = useDashboardMenu()

// 面板收起（含 peek 收回）时关闭挂在 body 上的用户菜单浮层
const userMenuOpen = ref(false)
watch(() => props.collapsed && !props.peeking, hidden => {
  if (hidden) {
    userMenuOpen.value = false
  }
})

/** 退出登录后，受保护页面需要回退到对应的公开列表页 */
const logoutRouteMap: Record<string, string> = {
  KnowledgeDetail: '/dashboard/knowledge',
  AgentEdit: '/dashboard/agent',
  KnowledgeMarketDetail: '/dashboard/knowledge/market',
  McpDetail: '/dashboard/mcp',
  McpMarketDetail: '/dashboard/mcp/market',
  Usage: '/dashboard/usage',
  Profile: '/dashboard'
}

function refreshPage(): void {
  window.location.reload()
}

async function handleLogout(): Promise<void> {
  await userStore.logout()
  const targetRoute = logoutRouteMap[route.name as string]
  if (targetRoute) {
    router.push(targetRoute)
  }
  message.success('已退出登录')
}
</script>

<style lang="scss" scoped>
.sidebar {
  @include floating-sidebar-shell(244px);

  // peek 悬浮唤出：面板滑出过程中顶行内容保持隐藏，面板就位后再在按钮右侧淡入，
  // 避免 logo 与 Header 收缩按钮在滑动中交叠产生割裂感
  &.peek .sidebar-top {
    padding-left: 72px;

    .logo-link {
      animation: peek-logo-in 0.2s ease 0.26s backwards;
    }
  }

  .sidebar-top {
    height: 64px;
    min-height: 64px;
    display: flex;
    align-items: center;
    padding: 0 16px;
    border-bottom: 1px solid #e8e6dc;

    .logo-link {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      text-decoration: none;

      .logo-img {
        width: 28px;
        height: 28px;
      }

      .logo-text {
        font-size: 20px;
        font-weight: 700;
        color: $text-dark;
        white-space: nowrap;
      }
    }
  }

  .sidebar-menu {
    flex: 1;
    overflow-y: auto;
    padding: 16px 0;

    .menu-group {
      margin-bottom: 8px;

      .menu-group-title {
        padding: 8px 20px;
        font-size: 12px;
        font-weight: 500;
        color: #8c8a82;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }

      .menu-items {
        .menu-item {
          display: flex;
          align-items: center;
          padding: 10px 16px;
          margin: 4px 12px;
          cursor: pointer;
          color: #5f5d55;
          transition: all 0.2s;
          border-radius: 8px;

          &:hover {
            color: #2aa1a9;
            background: rgba(42, 161, 169, 0.08);
          }

          &.active {
            color: #fff;
            background: $primary-color;
            border-radius: 10px;
          }

          .menu-icon {
            font-size: 18px;
            min-width: 18px;
          }

          .menu-text {
            margin-left: 12px;
            font-size: 14px;
            white-space: nowrap;
          }
        }
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
  }
}

@keyframes peek-logo-in {
  from {
    opacity: 0;
    transform: translateX(-6px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}
</style>
