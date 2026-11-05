<template>
  <blockquote class="ui-quote">
    <p class="ui-quote-text">{{ block.text }}</p>
    <footer v-if="block.author || block.source" class="ui-quote-src">
      <span v-if="block.author">{{ block.author }}</span>
      <span v-if="block.author && block.source">·</span>
      <span v-if="block.source">{{ block.source }}</span>
    </footer>
  </blockquote>
</template>

<script setup lang="ts">
import type { MessageBlock } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

defineProps<{ block: UiBlock; chatId?: string }>()
</script>

<style scoped lang="scss">
.ui-quote {
  margin: 0;
  padding: 4px 0 4px 14px;
  border-left: 3px solid rgba(42, 161, 169, 0.45);
}

.ui-quote-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: $text-secondary;
  white-space: pre-wrap;
}

.ui-quote-src {
  margin-top: 6px;
  font-size: 12px;
  color: $text-muted;
}
</style>
