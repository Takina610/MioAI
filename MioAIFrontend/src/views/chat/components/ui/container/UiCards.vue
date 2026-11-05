<template>
  <div class="ui-cards">
    <div v-for="(card, i) in cards" :key="i" class="ui-card">
      <div v-if="card.value" class="ui-card-value">
        {{ card.value }}
        <span v-if="card.trend === 'up'" class="ui-trend up">↑</span>
        <span v-else-if="card.trend === 'down'" class="ui-trend down">↓</span>
      </div>
      <div class="ui-card-title" :class="{ lead: !card.value }">{{ card.title }}</div>
      <div v-if="card.desc" class="ui-card-desc">{{ card.desc }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MessageBlock, UiCardItem } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const cards = computed<UiCardItem[]>(() =>
  (props.block.cards ?? []).filter(c => c && c.title)
)
</script>

<style scoped lang="scss">
.ui-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 10px;
}

.ui-card {
  padding: 12px 14px;
  background: #fff;
  border: 1px solid $border-warm-subtle;
  border-radius: 8px;
}

/* 有 value 时 value 是大字头行、title 是标签；纯标题卡（如阶段卡）title 升为头行 */
.ui-card-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 20px;
  font-weight: 600;
  color: $primary-color;
  line-height: 1.3;
}

.ui-trend {
  font-size: 14px;

  &.up { color: $primary-color; }
  &.down { color: $text-muted; }
}

.ui-card-title {
  margin-top: 4px;
  font-size: 13px;
  color: $text-secondary;

  &.lead {
    font-size: 15px;
    font-weight: 600;
    color: $text-secondary;
  }
}

.ui-card-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: $text-muted;
}
</style>
