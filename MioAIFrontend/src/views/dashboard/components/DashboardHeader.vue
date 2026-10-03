<template>
  <header class="header">
    <div class="header-left">
      <div v-if="isAdmin" class="admin-btns">
        <a
          v-for="entry in adminEntries"
          :key="entry.path"
          :href="`${baseUrl}${entry.path}`"
          target="_blank"
          rel="noopener noreferrer"
          class="admin-btn"
        >
          <span class="btn-text">{{ entry.label }}</span>
          <ExportOutlined class="btn-icon" />
        </a>
      </div>
    </div>
    <div class="header-right"></div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ExportOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const baseUrl = window.location.origin

const isAdmin = computed(() => userStore.userInfo?.userRole === 'admin')

/** 管理后台跳转入口 */
const adminEntries = [
  { path: '/admin/user', label: '用户管理' },
  { path: '/admin/mcp', label: 'MCP 管理' },
  { path: '/admin/knowledge', label: '知识库管理' },
  { path: '/admin/usage', label: '记录管理' }
]
</script>

<style lang="scss" scoped>
.header {
  height: 64px;
  background: #f9fafd;
  border-bottom: 1px solid #e8eaed;
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .header-left {
    flex: 1;
  }

  .header-right {
    display: flex;
    align-items: center;
  }

  .admin-btns {
    display: flex;
    align-items: center;
    gap: 12px;

    .admin-btn {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 18px;
      background: linear-gradient(180deg, #ffffff 0%, #f5f7fa 100%);
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04), inset 0 1px 0 #ffffff;
      color: #4b5563;
      font-size: 14px;
      font-weight: 500;
      text-decoration: none;
      transition: all 0.2s ease;

      &:hover {
        background: linear-gradient(180deg, #f0fdfd 0%, #e6f7f7 100%);
        border-color: #2aa1a9;
        color: #2aa1a9;
        box-shadow: 0 4px 10px rgba(42, 161, 169, 0.12), 0 1px 3px rgba(0, 0, 0, 0.06), inset 0 1px 0 #ffffff;
      }

      &:active {
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06), inset 0 1px 2px rgba(0, 0, 0, 0.04);
      }

      .btn-text {
        display: flex;
        align-items: center;
        gap: 6px;
      }

      .btn-icon {
        font-size: 14px;
        opacity: 0.7;
      }
    }
  }
}
</style>
