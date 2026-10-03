<template>
  <aside class="sidebar" :class="{ collapsed: isCollapsed }">
    <div class="sidebar-top">
      <div class="logo-section" v-show="!isCollapsed">
        <a class="logo-link" @click="refreshPage">
          <img src="/favicon.ico" alt="Logo" class="logo-img" />
          <span class="logo-text">MioAI</span>
        </a>
      </div>
      <a-button type="text" class="collapse-btn" @click="isCollapsed = !isCollapsed">
        <MenuFoldOutlined v-if="!isCollapsed" />
        <MenuUnfoldOutlined v-else />
      </a-button>
    </div>

    <div class="sidebar-menu">
      <div class="menu-group" v-for="group in menuGroups" :key="group.title">
        <div class="menu-group-title" v-show="!isCollapsed">{{ group.title }}</div>
        <div class="menu-items">
          <a-tooltip v-for="item in group.items" :key="item.key" :title="isCollapsed ? item.label : ''" placement="right">
            <div
              class="menu-item"
              :class="{ active: currentKey === item.key }"
              @click="navigateTo(item)"
            >
              <component :is="item.icon" class="menu-icon" />
              <span class="menu-text" v-show="!isCollapsed">{{ item.label }}</span>
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
        <a-button type="primary" block @click="emit('login-required')" v-show="!isCollapsed">
          登录
        </a-button>
        <a-button type="primary" @click="emit('login-required')" v-show="isCollapsed">
          <UserOutlined />
        </a-button>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import { useDashboardMenu } from '../composables/useDashboardMenu'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  HomeOutlined,
  UserOutlined,
  LogoutOutlined
} from '@ant-design/icons-vue'

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { menuGroups, currentKey, navigateTo } = useDashboardMenu()

const isCollapsed = ref(false)

/** 退出登录后，受保护页面需要回退到对应的公开列表页 */
const logoutRouteMap: Record<string, string> = {
  KnowledgeDetail: '/dashboard/knowledge',
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
  width: 220px;
  background: #f9fafd;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  border-right: 1px solid #e8eaed;

  &.collapsed {
    width: 64px;

    .sidebar-top {
      justify-content: center;
      padding: 0;
    }

    .sidebar-menu .menu-group .menu-items .menu-item {
      margin: 4px 8px;
      padding: 10px;
      justify-content: center;
    }

    .sidebar-footer {
      padding: 8px;
      display: flex;
      justify-content: center;
    }
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
  }
}
</style>
