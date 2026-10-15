<template>
  <div class="chart-wrapper">
    <VueApexCharts
      v-if="hasData"
      type="area"
      width="100%"
      height="280"
      :options="chartOptions"
      :series="chartSeries"
    />
    <div v-else class="chart-empty">暂无数据</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VueApexCharts from 'vue3-apexcharts'
import type { ApexOptions } from 'apexcharts'
import type { OverviewDayTrend, TrendMetric } from '@/api/adminOverview'
import { baseLineChartColors } from '@/views/dashboard/usage/composables/chartTheme'
import { formatCompact } from '@/views/dashboard/usage/composables/format'

const props = defineProps<{
  points: OverviewDayTrend[]
  metric: TrendMetric
}>()

const METRIC_META: Record<TrendMetric, { label: string; color: string }> = {
  turns: { label: '对话轮次', color: '#2aa1a9' },
  tokens: { label: 'Token 总量', color: '#d98f45' },
  failedTurns: { label: '失败轮次', color: '#c0453a' }
}

const hasData = computed(() => props.points.some(point => Number(point[props.metric]) > 0))

const chartSeries = computed(() => [
  {
    name: METRIC_META[props.metric].label,
    data: props.points.map(point => Number(point[props.metric]))
  }
])

const chartOptions = computed<ApexOptions>(() => {
  const base = baseLineChartColors()
  return {
    ...(base as ApexOptions),
    colors: [METRIC_META[props.metric].color],
    chart: {
      ...(base.chart ?? {}),
      type: 'area',
      height: 280
    },
    stroke: { curve: 'smooth', width: 2.5, lineCap: 'round' },
    fill: {
      type: 'gradient',
      gradient: { shadeIntensity: 0.4, opacityFrom: 0.28, opacityTo: 0.02, stops: [0, 90, 100] }
    },
    markers: { size: 0, strokeWidth: 0, hover: { size: 5 } },
    xaxis: {
      ...(base.xaxis ?? {}),
      categories: props.points.map(point => point.date.slice(5).replace('-', '/')),
      tickAmount: props.points.length > 10 ? 10 : props.points.length,
      labels: { rotate: 0, hideOverlappingLabels: true, style: { colors: '#8c8a82', fontSize: '11px' } }
    },
    tooltip: {
      ...(base.tooltip ?? {}),
      custom: ({ dataPointIndex }: { dataPointIndex: number }) => {
        const point = props.points[dataPointIndex]
        if (!point) return ''
        return `<div class="apex-custom-tooltip"><div class="tooltip-title">${point.date}</div>
          <div class="tooltip-row"><span class="dot" style="background:#2aa1a9"></span>对话轮次 <b>${point.turns}</b></div>
          <div class="tooltip-row"><span class="dot" style="background:#d98f45"></span>Token <b>${formatCompact(point.tokens)}</b></div>
          <div class="tooltip-row"><span class="dot" style="background:#c0453a"></span>失败 <b>${point.failedTurns}</b></div>
        </div>`
      }
    },
    legend: { show: false }
  }
})
</script>

<style lang="scss" scoped>
.chart-wrapper {
  width: 100%;
}

.chart-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-muted;
  font-size: 14px;
}

:global(.apex-custom-tooltip) {
  padding: 8px 12px;
  background: $bg-ivory;
  border: 1px solid $border-warm-subtle;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(20, 20, 19, 0.08);

  .tooltip-title {
    font-weight: 500;
    color: $text-dark;
    margin-bottom: 4px;
  }

  .tooltip-row {
    display: flex;
    align-items: center;
    gap: 6px;
    color: $text-secondary;
    font-size: 12px;
    line-height: 1.7;

    b {
      color: $text-dark;
    }

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      flex-shrink: 0;
    }
  }
}
</style>
