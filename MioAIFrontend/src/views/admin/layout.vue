<template>
  <div class="admin-layout">
    <header class="admin-header">
      <div class="header-brand">
        <img src="/logo.png" alt="Logo" class="logo-img" />
        <span class="logo-text">MioAI</span>
        <span class="header-divider">|</span>
        <span class="page-title">{{ pageTitle }}</span>
      </div>
    </header>
    <main class="admin-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { message } from 'ant-design-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 权限守卫：非管理员跳转 403
if (userStore.userInfo?.userRole !== 'admin') {
  message.error('无权访问管理后台')
  router.replace('/403')
}

const pageTitleMap: Record<string, string> = {
  '/admin/user': '用户管理',
  '/admin/agent': '智能体管理',
  '/admin/mcp': 'MCP 管理',
  '/admin/knowledge': '知识库管理',
  '/admin/usage': '使用记录管理'
}

const pageTitle = computed(() => pageTitleMap[route.path] || '管理后台')
</script>

<style lang="scss" scoped>
.admin-layout {
  min-height: 100vh;
  background: #f0eee6;

  .admin-header {
    height: 56px;
    background: #f5f3ec;
    border-bottom: 1px solid #e8e6dc;
    display: flex;
    align-items: center;
    padding: 0 24px;

    .header-brand {
      display: flex;
      align-items: center;
      gap: 10px;

      .logo-img {
        width: 28px;
        height: 28px;
      }

      .logo-text {
        font-size: 16px;
        font-weight: 600;
        color: #141413;
      }

      .header-divider {
        color: #b0aea5;
        font-weight: 300;
      }

      .page-title {
        font-size: 15px;
        font-weight: 500;
        color: #5f5d55;
      }
    }
  }

  .admin-content {
    padding: 24px;
    max-width: 1200px;
    margin: 0 auto;
  }
}
</style>
