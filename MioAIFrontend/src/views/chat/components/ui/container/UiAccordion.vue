<template>
  <div class="ui-accordion">
    <div v-for="(section, i) in sections" :key="i" class="acc-item" :class="{ open: openIndex === i }">
      <button class="acc-head" @click="openIndex = openIndex === i ? -1 : i">
        <CaretRightOutlined class="acc-caret" />
        <span>{{ section.title }}</span>
      </button>
      <div v-show="openIndex === i" class="acc-body">{{ section.text }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CaretRightOutlined } from '@ant-design/icons-vue'
import type { MessageBlock, UiAccordionSection } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const sections = computed<UiAccordionSection[]>(() =>
  (props.block.sections ?? []).filter(s => s && s.title && s.text)
)

/* 默认首节展开：全收起等于空态 */
const openIndex = ref(0)
</script>

<style scoped lang="scss">
.ui-accordion {
  display: flex;
  flex-direction: column;
  border: 1px solid $border-warm-subtle;
  border-radius: 8px;
  overflow: hidden;
}

.acc-item {
  & + & {
    border-top: 1px solid $border-warm-subtle;
  }
}

.acc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  background: transparent;
  border: none;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  color: $text-secondary;
  cursor: pointer;

  &:hover {
    color: $primary-color;
  }
}

.acc-caret {
  font-size: 11px;
  color: $text-muted;
  transition: transform 0.18s ease;
}

.acc-item.open .acc-caret {
  transform: rotate(90deg);
  color: $primary-color;
}

.acc-body {
  padding: 0 12px 12px 31px;
  font-size: 13px;
  line-height: 1.7;
  color: $text-muted;
  white-space: pre-wrap;
}
</style>
