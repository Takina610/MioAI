<template>
  <div class="ui-actions">
    <button
      v-for="(action, i) in actions"
      :key="i"
      class="ui-action-btn"
      :class="action.style === 'primary' ? 'primary' : 'default'"
      @click="emit('send', action.prompt)"
    >{{ action.label }}</button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MessageBlock, UiActionItem } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()
const emit = defineEmits<{ (e: 'send', text: string): void }>()

/** prompt 型操作按钮（后端 op 白名单外的业务按钮在源头上已被丢弃，这里只会收到聊天类按钮） */
const actions = computed<UiActionItem[]>(() =>
  (props.block.actions ?? []).filter(a => a && a.label && a.prompt)
)
</script>

<style scoped lang="scss">
.ui-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ui-action-btn {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;

  &.primary {
    background: $primary-color;
    border: 1px solid $primary-color;
    color: #fff;

    &:hover { opacity: 0.88; }
  }

  &.default {
    background: #fff;
    border: 1px solid $border-warm;
    color: $text-secondary;

    &:hover {
      border-color: $primary-color;
      color: $primary-color;
    }
  }
}
</style>
