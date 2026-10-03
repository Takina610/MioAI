<template>
  <span class="zcode-spinner" :style="{ fontSize: `${size}px` }">{{ frame }}</span>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

withDefaults(defineProps<{
  /** 字号（px），同时也是渲染宽度 */
  size?: number
}>(), { size: 16 })

// ZCode 终端同款 braille 旋转帧
const FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏']
const FRAME_INTERVAL_MS = 90

const frame = ref(FRAMES[0])
let timer: number | undefined

onMounted(() => {
  let i = 0
  timer = window.setInterval(() => {
    i = (i + 1) % FRAMES.length
    frame.value = FRAMES[i]
  }, FRAME_INTERVAL_MS)
})

onUnmounted(() => {
  if (timer !== undefined) {
    clearInterval(timer)
  }
})
</script>

<style lang="scss" scoped>
.zcode-spinner {
  font-family: 'Cascadia Mono', 'Consolas', 'Menlo', monospace;
  color: $primary-color;
  display: inline-block;
  width: 1em;
  text-align: center;
  line-height: 1;
  vertical-align: middle;
  user-select: none;
}
</style>
