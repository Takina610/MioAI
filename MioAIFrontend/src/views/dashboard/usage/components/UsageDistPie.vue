<template>
  <div class="dist-wrapper">
    <template v-if="data.length > 0">
      <div class="donut-box">
        <BaseDonutChart
          :items="donutItems"
          :colors="palette"
          center-label="总计"
          :format-value="formatNumber"
          :unit="unit ?? '次'"
          :height="280"
        />
      </div>
      <div class="dist-legend">
        <div class="legend-item" v-for="(item, index) in data" :key="item.id">
          <span class="dot" :style="{ background: palette[index % palette.length] }"></span>
          <span class="legend-avatar" v-if="item.avatar">
            <a-avatar :src="item.avatar" :size="18" />
          </span>
          <span class="legend-name" :title="item.name">{{ item.name }}</span>
          <span class="legend-value">{{ formatNumber(item.value) }}</span>
          <span class="legend-percent">{{ percentOf(item.value) }}%</span>
        </div>
      </div>
    </template>
    <div v-else class="chart-empty">暂无数据</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { NameValue } from '@/types'
import BaseDonutChart from '@/components/charts/BaseDonutChart.vue'
import { ECHARTS_PALETTE } from '../composables/echartsTheme'
import { formatNumber } from '../composables/format'

const props = defineProps<{
  data: NameValue[]
  /** 数值含义（"次"/"chunks"），用于 tooltip */
  unit?: string
}>()

const palette = ECHARTS_PALETTE

const donutItems = computed(() =>
  props.data.map(item => ({ name: item.name, value: Number(item.value) }))
)

function percentOf(value: number): string {
  const total = props.data.reduce((sum, item) => sum + Number(item.value), 0)
  if (total === 0) {
    return '0'
  }
  return ((value / total) * 100).toFixed(1).replace(/\.0$/, '')
}
</script>

<style lang="scss" scoped>
.dist-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;

  .donut-box {
    flex: 0 0 46%;
    min-width: 0;
  }

  .dist-legend {
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

      .legend-value {
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
}

.chart-empty {
  height: 280px;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8c8a82;
  font-size: 14px;
}
</style>
