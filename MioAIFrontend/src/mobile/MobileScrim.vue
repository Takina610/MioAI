<template>
  <!-- 移动端抽屉遮罩：抽屉展开时铺满视口，点击关闭。
       仅在移动形态下由外层条件渲染，桌面形态完全不产生节点，
       因此对既有"常驻侧栏 + 悬浮 peek"交互零影响。 -->
  <div class="mobile-scrim" @click="emit('close')"></div>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  (e: 'close'): void
}>()
</script>

<style lang="scss" scoped>
.mobile-scrim {
  position: fixed;
  inset: 0;
  // 高于内容区、低于侧栏抽屉（66）与头部按钮
  z-index: 65;
  background: rgba(20, 20, 19, 0.32);
  animation: scrim-in 0.2s ease;
}

@keyframes scrim-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}
</style>
