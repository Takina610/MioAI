<template>
  <header class="header" :class="{ 'is-floating': floating }">
    <div class="header-inner">
      <router-link class="logo" to="/">
        <img src="/logo.png" alt="MioAI" class="logo-img" />
        <span class="logo-text">MioAI</span>
      </router-link>

      <div class="header-right">
        <template v-if="userStore.isLoggedIn">
          <a-dropdown :trigger="['hover']" overlay-class-name="hm-user-menu">
            <div class="user-info">
              <a-avatar :size="30" :src="userStore.userAvatar">
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
        <button v-else type="button" class="hm-btn" @click="router.push('/chat')">快速开始</button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { SettingOutlined, UserOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/store/user'
import { message } from 'ant-design-vue'

/** 滑过首屏后顶栏收成居中胶囊；用滚动量而非位置触发，回顶时无缝还原 */
const SCROLL_TRIGGER = 60

const router = useRouter()
const userStore = useUserStore()

const floating = ref(false)

function onScroll(): void {
  floating.value = window.scrollY > SCROLL_TRIGGER
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})

async function handleLogout(): Promise<void> {
  await userStore.logout()
  message.success('已退出登录')
}
</script>

<style lang="scss" scoped>
/* 未滚动：与页面同底、无边框，整体融合在背景里 */
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: transparent;
  border-bottom: 1px solid transparent;
  transition: background 0.3s var(--hm-ease), border-color 0.3s var(--hm-ease),
    padding 0.3s var(--hm-ease);
}

.header-inner {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  // 与首屏内容同宽，收起时胶囊与输入卡左边缘对齐
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding-inline: clamp(20px, 4vw, 40px);
  transition: max-width 0.36s var(--hm-ease), padding 0.36s var(--hm-ease);
}

/* 滚动后：整条顶栏收成居中的悬浮胶囊 */
.header.is-floating {
  background: transparent;
  pointer-events: none;

  .header-inner {
    pointer-events: auto;
    // 内容宽度收缩到视觉居中：胶囊只包住 logo + 右侧动作
    max-width: min(560px, calc(100% - 32px));
    height: 56px;
    padding-inline: 18px;
    border-radius: 999px;
    background: rgba(250, 249, 245, 0.82);
    backdrop-filter: blur(14px) saturate(1.4);
    -webkit-backdrop-filter: blur(14px) saturate(1.4);
    border: 1px solid var(--hm-border-subtle);
    box-shadow: 0 8px 28px -10px rgba(20, 20, 19, 0.18), 0 1px 2px rgba(20, 20, 19, 0.04);
  }

  .logo .logo-text {
    font-size: 18px;
  }

  .logo .logo-img {
    width: 24px;
    height: 24px;
  }
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;

  .logo-img {
    width: 30px;
    height: 30px;
    transition: width 0.3s var(--hm-ease), height 0.3s var(--hm-ease);
  }

  .logo-text {
    font-family: var(--hm-font-display);
    font-size: 22px;
    font-weight: 700;
    color: var(--hm-text);
    letter-spacing: -0.01em;
    transition: font-size 0.3s var(--hm-ease);
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 10px 4px 4px;
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
  }
}

.header-right .hm-btn {
  padding: 9px 20px;
  font-size: 14px;
}
</style>
