<template>
  <div class="ui-choice">
    <div v-if="block.title" class="ui-choice-title">{{ block.title }}</div>
    <div class="ui-choice-options">
      <button
        v-for="(option, i) in options"
        :key="i"
        class="ui-choice-btn"
        @click="emit('send', option.prompt || option.label)"
      >{{ option.label }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MessageBlock, UiChoiceOption } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()
const emit = defineEmits<{ (e: 'send', text: string): void }>()

/** 单选胶囊：点击即把 prompt（缺省 label）作为用户消息发送（与 actions 同一回传链） */
const options = computed<UiChoiceOption[]>(() =>
  (props.block.options ?? []).filter(o => o && o.label)
)
</script>

<style scoped lang="scss">
.ui-choice-title {
  margin-bottom: 8px;
  font-size: 13px;
  color: $text-secondary;
}

.ui-choice-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ui-choice-btn {
  padding: 6px 14px;
  background: #fff;
  border: 1px solid $primary-color;
  border-radius: 999px;
  font-size: 13px;
  color: $primary-color;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: $primary-color;
    color: #fff;
  }
}
</style>
