<template>
  <div class="forbidden">
    <div class="content">
      <img src="/favicon.ico" alt="logo" class="logo" />
      <h1>无权访问</h1>
      <p>您没有权限访问此页面。</p>
      <div class="actions">
        <a-button type="primary" @click="goKnowledge">
          返回控制台
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goKnowledge(): void {
  router.push('/dashboard')
}
</script>

<style lang="scss" scoped>
.forbidden {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;

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
      color: #333;
      margin-bottom: 12px;
    }

    p {
      font-size: 14px;
      color: #999;
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
    }
  }
}
</style>
