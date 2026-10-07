<template>
  <header class="header">
    <div class="home-container header-inner">
      <a class="logo" @click="refreshPage">
        <img src="/logo.png" alt="MioAI" class="logo-img" />
        <span class="logo-text">MioAI</span>
      </a>

      <nav class="nav">
        <router-link class="nav-link" to="/chat">MioBot</router-link>
        <router-link class="nav-link" to="/dashboard/agent-market">智能体广场</router-link>
        <router-link class="nav-link" to="/dashboard/mcp-market">MCP 广场</router-link>
        <router-link class="nav-link" to="/dashboard/public-knowledge">知识库</router-link>
      </nav>

      <div class="header-right">
        <template v-if="userStore.isLoggedIn">
          <a-dropdown :trigger="['hover']" overlay-class-name="hm-user-menu">
            <div class="user-info">
              <a-avatar :size="34" :src="userStore.userAvatar">
                {{ userStore.userName?.charAt(0)?.toUpperCase() }}
              </a-avatar>
              <span class="user-name">{{ userStore.userName }}</span>
            </div>
            <template #overlay>
              <a-menu>
                <a-menu-item key="dashboard" @click="router.push('/dashboard')">
                  <SettingOutlined /> 控制台
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
          <button type="button" class="hm-btn" @click="router.push('/chat')">快速开始</button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { SettingOutlined, UserOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/store/user'
import { message } from 'ant-design-vue'

const router = useRouter()
const userStore = useUserStore()

function refreshPage(): void {
  window.location.reload()
}

async function handleLogout(): Promise<void> {
  await userStore.logout()
  message.success('已退出登录')
}
</script>

<style lang="scss" scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(240, 238, 230, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--hm-border-subtle);
}

.header-inner {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 40px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  flex-shrink: 0;

  .logo-img {
    width: 30px;
    height: 30px;
  }

  .logo-text {
    font-family: var(--hm-font-display);
    font-size: 22px;
    font-weight: 700;
    color: var(--hm-text);
    letter-spacing: -0.01em;
  }
}

.nav {
  display: flex;
  gap: 32px;
  margin-right: auto;

  .nav-link {
    font-size: 15px;
    font-weight: 500;
    color: var(--hm-text);
    opacity: 0.82;
    transition: opacity 0.2s var(--hm-ease);

    &:hover {
      opacity: 1;
    }
  }

  @media (max-width: 900px) {
    display: none;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 5px 10px 5px 5px;
  border-radius: 999px;
  border: 1px solid transparent;
  transition: border-color 0.2s var(--hm-ease), background 0.2s var(--hm-ease);

  &:hover {
    border-color: var(--hm-border);
    background: var(--hm-bg-raised);
  }

  .user-name {
    font-size: 14px;
    font-weight: 500;
    color: var(--hm-text);
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    @media (max-width: 480px) {
      display: none;
    }
  }
}

.header-right .hm-btn {
  padding: 10px 22px;
  font-size: 14px;
}
</style>
