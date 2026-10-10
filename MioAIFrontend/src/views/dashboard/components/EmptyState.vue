<template>
  <div class="empty-state">
    <img :src="image" alt="empty" class="empty-image" />
    <p class="empty-desc">{{ description }}</p>
    <p v-if="hint" class="empty-hint">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  /** 空状态插图 */
  image: string
  /** 主文案 */
  description: string
  /** 引导文案 */
  hint?: string
}>(), {
  hint: ''
})
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 300px);
  text-align: center;

  .empty-image {
    width: 400px;
    height: auto;
    object-fit: contain;
    margin-bottom: -80px;
    position: relative;
    z-index: 1;
  }

  .empty-desc {
    font-size: 16px;
    color: #6e6b62;
    margin-bottom: 8px;
    position: relative;
    z-index: 1;
  }

  .empty-hint {
    font-size: 14px;
    color: #8c8a82;
    position: relative;
    z-index: 1;
  }
}
</style>
