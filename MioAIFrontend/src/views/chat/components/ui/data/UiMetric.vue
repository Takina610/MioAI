<template>
  <div class="ui-metric">
    <div v-for="(item, i) in items" :key="i" class="ui-metric-item">
      <div class="ui-metric-value">
        {{ item.value }}<span v-if="item.unit" class="ui-metric-unit">{{ item.unit }}</span>
        <span v-if="item.trend === 'up'" class="ui-metric-trend up">↑</span>
        <span v-else-if="item.trend === 'down'" class="ui-metric-trend down">↓</span>
      </div>
      <div class="ui-metric-label">{{ item.label }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MessageBlock, UiMetricItem } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const items = computed<UiMetricItem[]>(() =>
  ((props.block.items ?? []) as UiMetricItem[]).filter(m => m && m.label && m.value != null)
)
</script>

<style scoped lang="scss">
/* KPI 数字条：大数字+标签+趋势，紧凑单行（cards 的紧凑变体） */
.ui-metric {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
}

.ui-metric-value {
  display: flex;
  align-items: baseline;
  gap: 3px;
  font-size: 22px;
  font-weight: 600;
  color: $primary-color;
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
}

.ui-metric-unit {
  font-size: 13px;
  font-weight: 400;
}

.ui-metric-trend {
  font-size: 14px;

  &.up { color: $primary-color; }
  &.down { color: $text-muted; }
}

.ui-metric-label {
  margin-top: 2px;
  font-size: 12px;
  color: $text-muted;
}
</style>
