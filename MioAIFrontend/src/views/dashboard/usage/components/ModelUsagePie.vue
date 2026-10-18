<template>
  <div class="usage-pie" v-if="series.length > 0">
    <div class="donut-box">
      <BaseDonutChart
        :items="donutItems"
        :colors="palette"
        center-label="Token 总量"
        :format-value="formatCompact"
        unit="tokens"
        :height="280"
      />
    </div>
    <div class="pie-legend">
      <div class="legend-item" v-for="(entry, index) in series" :key="entry.id">
        <span class="legend-avatar" v-if="entry.avatar">
          <a-avatar :src="entry.avatar" :size="18" />
        </span>
        <span class="dot" v-else :style="{ background: legendColor(index) }"></span>
        <span class="legend-name" :title="entry.name">{{ entry.name }}</span>
        <span class="legend-token">{{ formatCompact(entry.totalTokens) }}</span>
        <span class="legend-percent">{{ percentOf(index) }}%</span>
      </div>
    </div>
  </div>
  <div v-else class="chart-empty">暂无数据</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TokenTrendSeries } from '@/types'
import BaseDonutChart from '@/components/charts/BaseDonutChart.vue'
import { ECHARTS_PALETTE } from '../composables/echartsTheme'
import { formatCompact } from '../composables/format'

const props = defineProps<{
  series: TokenTrendSeries[]
}>()

const palette = ECHARTS_PALETTE

const donutItems = computed(() =>
  props.series.map(entry => ({ name: entry.name, value: Number(entry.totalTokens) }))
)

const totalTokens = computed(() =>
  props.series.reduce((sum, entry) => sum + Number(entry.totalTokens), 0)
)

function legendColor(index: number): string {
  return palette[index % palette.length]
}

function percentOf(index: number): string {
  if (totalTokens.value === 0) {
    return '0'
  }
  return ((Number(props.series[index].totalTokens) / totalTokens.value) * 100)
    .toFixed(1)
    .replace(/\.0$/, '')
}
</script>

<style lang="scss" scoped>
.usage-pie {
  display: flex;
  align-items: center;
  gap: 12px;
}

.donut-box {
  flex: 0 0 46%;
  min-width: 0;
}

.pie-legend {
  flex: 1;
  min-width: 0;
  max-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding-right: 4px;

  .legend-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 8px;
    border-radius: 8px;

    &:hover {
      background: #f5f3ec;
    }

    .dot {
      width: 9px;
      height: 9px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .legend-avatar {
      flex-shrink: 0;
      display: flex;
    }

    .legend-name {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 13px;
      color: #141413;
    }

    .legend-token {
      font-size: 13px;
      color: #6e6b62;
      font-variant-numeric: tabular-nums;
    }

    .legend-percent {
      font-size: 12px;
      color: #8c8a82;
      width: 44px;
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
  }
}

.chart-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8c8a82;
  font-size: 14px;
  width: 100%;
}
</style>
