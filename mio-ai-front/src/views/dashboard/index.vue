<template>
  <div class="dashboard-layout">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-top">
        <div class="logo-section" v-show="!isCollapsed">
          <a class="logo-link" @click="refreshPage">
            <img src="/favicon.svg" alt="Logo" class="logo-img" />
            <span class="logo-text">MioAI</span>
          </a>
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
      
      <div class="sidebar-menu">
        <div class="menu-group">
          <div class="menu-group-title" v-show="!isCollapsed">智能体</div>
          <div class="menu-items">
            <a-tooltip :title="isCollapsed ? '智能体广场' : ''" placement="right">
              <div 
                class="menu-item" 
                :class="{ active: isActive('agent-market') }"
                @click="navigateTo('agent-market')"
              >
                <RobotOutlined class="menu-icon" />
                <span class="menu-text" v-show="!isCollapsed">智能体广场</span>
              </div>
            </a-tooltip>
            <a-tooltip :title="isCollapsed ? '智能体管理' : ''" placement="right">
              <div 
                class="menu-item" 
                :class="{ active: isActive('agents') }"
                @click="navigateTo('agents')"
              >
                <AppstoreOutlined class="menu-icon" />
                <span class="menu-text" v-show="!isCollapsed">智能体管理</span>
              </div>
            </a-tooltip>
          </div>
        </div>

        <div class="menu-group">
          <div class="menu-group-title" v-show="!isCollapsed">MCP</div>
          <div class="menu-items">
            <a-tooltip :title="isCollapsed ? 'MCP广场' : ''" placement="right">
              <div 
                class="menu-item" 
                :class="{ active: isActive('mcp-market') }"
                @click="navigateTo('mcp-market')"
              >
                <ShopOutlined class="menu-icon" />
                <span class="menu-text" v-show="!isCollapsed">MCP广场</span>
              </div>
            </a-tooltip>
            <a-tooltip :title="isCollapsed ? 'MCP管理' : ''" placement="right">
              <div 
                class="menu-item" 
                :class="{ active: isActive('mcp') }"
                @click="navigateTo('mcp')"
              >
                <ToolOutlined class="menu-icon" />
                <span class="menu-text" v-show="!isCollapsed">MCP管理</span>
              </div>
            </a-tooltip>
          </div>
        </div>

        <div class="menu-group">
          <div class="menu-group-title" v-show="!isCollapsed">知识库</div>
          <div class="menu-items">
            <a-tooltip :title="isCollapsed ? '公共知识库' : ''" placement="right">
              <div 
                class="menu-item" 
                :class="{ active: isActive('public-knowledge') }"
                @click="navigateTo('public-knowledge')"
              >
                <GlobalOutlined class="menu-icon" />
                <span class="menu-text" v-show="!isCollapsed">公共知识库</span>
              </div>
            </a-tooltip>
            <a-tooltip :title="isCollapsed ? '知识库管理' : ''" placement="right">
              <div 
                class="menu-item" 
                :class="{ active: isActive('knowledge') }"
                @click="navigateTo('knowledge')"
              >
                <DatabaseOutlined class="menu-icon" />
                <span class="menu-text" v-show="!isCollapsed">知识库管理</span>
              </div>
            </a-tooltip>
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
                <a-menu-item key="profile" @click="navigateTo('profile')">
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
      <header class="header">
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" @login-required="showAuthModal" />
          </transition>
        </router-view>
      </main>
    </div>

    <AuthModal v-model:visible="authModalVisible" @success="handleAuthSuccess" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import AuthModal from '@/components/AuthModal.vue'
import {
  RobotOutlined,
  AppstoreOutlined,
  ShopOutlined,
  ToolOutlined,
  GlobalOutlined,
  DatabaseOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  HomeOutlined,
  UserOutlined,
  LogoutOutlined
} from '@ant-design/icons-vue'

type MenuKey = 'agent-market' | 'agents' | 'mcp-market' | 'mcp' | 'public-knowledge' | 'knowledge' | 'profile'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isCollapsed = ref<boolean>(false)
const currentPath = ref<MenuKey>('agent-market')
const authModalVisible = ref<boolean>(false)

const pageTitle = computed<string>(() => {
  const titles: Record<MenuKey, string> = {
    'agent-market': '智能体广场',
    'agents': '智能体管理',
    'mcp-market': 'MCP广场',
    'mcp': 'MCP管理',
    'public-knowledge': '公共知识库',
    'knowledge': '知识库管理',
    'profile': '个人中心'
  }
  return titles[currentPath.value] || '智能体广场'
})

watch(
  () => route.path,
  (path) => {
    const pathMap: Record<string, MenuKey> = {
      '/dashboard/agent-market': 'agent-market',
      '/dashboard/agents': 'agents',
      '/dashboard/mcp-market': 'mcp-market',
      '/dashboard/mcp': 'mcp',
      '/dashboard/public-knowledge': 'public-knowledge',
      '/dashboard/knowledge': 'knowledge',
      '/dashboard/profile': 'profile'
    }
    if (pathMap[path]) {
      currentPath.value = pathMap[path]
    }
  },
  { immediate: true }
)

function isActive(key: MenuKey): boolean {
  return currentPath.value === key
}

function refreshPage(): void {
  window.location.reload()
}

function toggleCollapse(): void {
  isCollapsed.value = !isCollapsed.value
}

function navigateTo(key: MenuKey): void {
  currentPath.value = key
  const routes: Record<MenuKey, string> = {
    'agent-market': '/dashboard/agent-market',
    'agents': '/dashboard/agents',
    'mcp-market': '/dashboard/mcp-market',
    'mcp': '/dashboard/mcp',
    'public-knowledge': '/dashboard/public-knowledge',
    'knowledge': '/dashboard/knowledge',
    'profile': '/dashboard/profile'
  }
  router.push(routes[key] || '/dashboard/agent-market')
}

function goHome(): void {
  router.push('/')
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
</script>

<style lang="scss" scoped>
.dashboard-layout {
  display: flex;
  min-height: 100vh;
  background: #f5f7fa;
}

.sidebar {
  width: 220px;
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
          color: $primary-color;
          white-space: nowrap;
        }
      }
    }

    .collapse-btn {
      color: #5f6368;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 4px;

      &:hover {
        color: #2aa1a9;
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
        color: #909399;
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
          color: #5f6368;
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
    .sidebar-menu {
      .menu-group {
        .menu-items {
          .menu-item {
            margin: 4px 8px;
            padding: 10px;
            justify-content: center;
          }
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
}

.header {
  height: 64px;
  background: #f9fafd;
  border-bottom: 1px solid #e8eaed;
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content {
  flex: 1;
  // padding: 24px;
  overflow-y: auto;
  background: #ffffff;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
