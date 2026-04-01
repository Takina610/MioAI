<template>
  <div class="dashboard-layout">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-header">
        <a class="logo-link" @click="refreshPage">
          <img src="/favicon.svg" alt="Logo" class="logo-img" />
          <span class="logo-text" v-show="!isCollapsed">MioAI</span>
        </a>
      </div>
      
      <div class="sidebar-menu">
        <div class="menu-group">
          <div class="menu-group-title" v-show="!isCollapsed">应用</div>
          <div class="menu-items">
            <a-tooltip :title="isCollapsed ? '应用广场' : ''" placement="right">
              <div 
                class="menu-item" 
                :class="{ active: isActive('agent-market') }"
                @click="navigateTo('agent-market')"
              >
                <RobotOutlined class="menu-icon" />
                <span class="menu-text" v-show="!isCollapsed">应用广场</span>
              </div>
            </a-tooltip>
            <a-tooltip :title="isCollapsed ? '应用管理' : ''" placement="right">
              <div 
                class="menu-item" 
                :class="{ active: isActive('agents') }"
                @click="navigateTo('agents')"
              >
                <AppstoreOutlined class="menu-icon" />
                <span class="menu-text" v-show="!isCollapsed">应用管理</span>
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
        <a-button
          type="text"
          class="collapse-btn"
          @click="toggleCollapse"
        >
          <MenuFoldOutlined v-if="!isCollapsed" />
          <MenuUnfoldOutlined v-else />
        </a-button>
      </div>
    </aside>

    <div class="main-container">
      <header class="header">
        <div class="header-left">
          <h1 class="page-title">{{ pageTitle }}</h1>
        </div>
        <div class="header-right">
          <a-dropdown :trigger="['hover']">
            <div class="user-dropdown">
              <a-avatar :size="36" :src="userStore.userAvatar">
                {{ userStore.userName?.charAt(0)?.toUpperCase() }}
              </a-avatar>
              <span class="user-name">{{ userStore.userName }}</span>
              <DownOutlined />
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
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import {
  RobotOutlined,
  AppstoreOutlined,
  ShopOutlined,
  ToolOutlined,
  GlobalOutlined,
  DatabaseOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DownOutlined,
  HomeOutlined,
  UserOutlined,
  LogoutOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isCollapsed = ref(false)
const currentPath = ref('agent-market')

const pageTitle = computed(() => {
  const titles = {
    'agent-market': '应用广场',
    'agents': '应用管理',
    'mcp-market': 'MCP广场',
    'mcp': 'MCP管理',
    'public-knowledge': '公共知识库',
    'knowledge': '知识库管理',
    'profile': '个人中心'
  }
  return titles[currentPath.value] || '应用广场'
})

function isActive(key) {
  return currentPath.value === key
}

function refreshPage() {
  window.location.reload()
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

function navigateTo(key) {
  currentPath.value = key
  const routes = {
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

function goHome() {
  router.push('/')
}

async function handleLogout() {
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

  .sidebar-header {
    height: 64px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-bottom: 1px solid #e8eaed;

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

    .collapse-btn {
      width: 100%;
      color: #5f6368;
      display: flex;
      align-items: center;
      justify-content: center;

      &:hover {
        color: #2aa1a9;
        background: rgba(42, 161, 169, 0.08);
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
      .collapse-btn {
        padding: 0;
      }
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

  .header-left {
    .page-title {
      font-size: 20px;
      font-weight: 600;
      color: #202124;
    }
  }

  .header-right {
    .user-dropdown {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      padding: 6px 12px;
      border-radius: 8px;
      transition: background 0.3s;

      &:hover {
        background: rgba(42, 161, 169, 0.08);
      }

      .user-name {
        font-size: 14px;
        color: #202124;
      }
    }
  }
}

.content {
  flex: 1;
  padding: 24px;
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
