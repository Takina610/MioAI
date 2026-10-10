<template>
  <div class="login-prompt">
    <p class="prompt-title">登录以使用</p>
    <p class="prompt-desc">您当前处于未登录状态，登录后可使用完整服务</p>
    <a-button type="primary" @click="emit('login')">
      登录
    </a-button>
  </div>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  (e: 'login'): void
}>()
</script>

<style lang="scss" scoped>
.login-prompt {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 300px);
  text-align: center;

  .prompt-title {
    font-size: 20px;
    font-weight: 600;
    color: #141413;
    margin-bottom: 12px;
  }

  .prompt-desc {
    font-size: 14px;
    color: #5f5d55;
    margin-bottom: 24px;
  }
}
</style>
