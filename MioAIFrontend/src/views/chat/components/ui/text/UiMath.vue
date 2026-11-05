<template>
  <div class="ui-math" v-html="html"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import type { MessageBlock } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

/**
 * KaTeX 渲染（throwOnError:false：坏公式原样显示不炸块）。
 * katex 默认 trust:false——\href/\includegraphics 等危险指令不执行，v-html 安全。
 */
const html = computed(() => {
  try {
    return katex.renderToString(props.block.tex ?? '', {
      throwOnError: false,
      displayMode: props.block.display !== false
    })
  } catch {
    return props.block.tex ?? ''
  }
})
</script>

<style scoped lang="scss">
.ui-math {
  padding: 4px 0;
  overflow-x: auto;
  font-size: 15px;
  color: $text-secondary;

  /* KaTeX 默认配色与暖色主题调和（错误红保留以提示坏公式） */
  :deep(.katex) {
    font-size: 1.06em;
  }
}
</style>
