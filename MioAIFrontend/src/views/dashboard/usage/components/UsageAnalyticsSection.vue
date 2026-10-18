<template>
  <div class="analytics-section">
    <div class="analytics-grid">
      <a-card title="使用去向" class="chart-card">
        <template #extra>
          <span class="metric-tag" v-if="analytics">按{{ analytics.valueLabel }}</span>
        </template>
        <a-spin :spinning="loading">
          <UsageFlowSankey
            :flow="analytics?.flow ?? null"
            :value-label="analytics?.valueLabel ?? ''"
            :value-unit="analytics?.valueUnit ?? ''"
          />
        </a-spin>
      </a-card>

      <a-card title="使用构成" class="chart-card">
        <template #extra>
          <span class="metric-tag" v-if="analytics">{{ analytics.valueLabel }}</span>
        </template>
        <a-spin :spinning="loading">
          <UsageDrillTreemap
            :nodes="analytics?.drill ?? []"
            :value-label="analytics?.valueLabel ?? ''"
            :value-unit="analytics?.valueUnit ?? ''"
          />
        </a-spin>
      </a-card>
    </div>

    <a-card title="使用明细" class="chart-card">
      <template #extra>
        <span class="metric-tag" v-if="analytics">
          {{ analytics.totalEvents.toLocaleString() }} 条 · {{ analytics.measureLabel }}
        </span>
      </template>
      <UsageEventScatter
        :events="analytics?.events ?? []"
        :measure-label="analytics?.measureLabel ?? ''"
        :measure-unit="analytics?.measureUnit ?? ''"
        :value-label="analytics?.valueLabel ?? ''"
        :value-unit="analytics?.valueUnit ?? ''"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import type { UsageAnalytics } from '@/types'
import UsageFlowSankey from './UsageFlowSankey.vue'
import UsageDrillTreemap from './UsageDrillTreemap.vue'
import UsageEventScatter from './UsageEventScatter.vue'

defineProps<{
  analytics: UsageAnalytics | null
  loading: boolean
}>()
</script>

<style lang="scss" scoped>
.analytics-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analytics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 16px;
  align-items: start;

  > :deep(.ant-card) {
    min-width: 0;
  }
}

.chart-card {
  border-radius: 12px;

  :deep(.ant-card-head-title) {
    font-weight: 600;
  }
}

.metric-tag {
  font-size: 12px;
  color: $text-muted;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
