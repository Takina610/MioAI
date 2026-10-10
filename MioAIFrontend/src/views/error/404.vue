<template>
  <div class="not-found">
    <div class="content">
      <img src="/logo.png" alt="logo" class="logo" />
      <h1>未找到页面</h1>
      <p>页面不存在，请确认网址是否正确。</p>
      <div class="actions">
        <a-button type="primary" @click="refreshPage">
          刷新页面
        </a-button>
        <a-button @click="goChat">
          继续聊天
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goChat(): void {
  router.push('/chat')
}

function refreshPage(): void {
  window.location.reload()
}
</script>

<style lang="scss" scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-cream;

  .content {
    text-align: center;

    .logo {
      width: 80px;
      height: 80px;
      margin-bottom: 24px;
    }

    h1 {
      font-size: 18px;
      font-weight: 500;
      color: #141413;
      margin-bottom: 12px;
    }

    p {
      font-size: 14px;
      color: #8c8a82;
      margin-bottom: 32px;
    }

    .actions {
      display: flex;
      gap: 16px;
      justify-content: center;

      :deep(.ant-btn-primary) {
        background: $primary-color;
        border-color: $primary-color;

        &:hover {
          background: darken($primary-color, 10%);
          border-color: darken($primary-color, 10%);
        }
      }

      :deep(.ant-btn-default) {
        border-color: #d8d5cc;
        color: #6e6b62;

        &:hover {
          color: $primary-color;
          border-color: $primary-color;
        }
      }
    }
  }
}
</style>
