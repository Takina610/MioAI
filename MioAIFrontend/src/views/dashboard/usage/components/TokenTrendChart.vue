<template>
  <div class="chart-wrapper">
    <VueApexCharts
      v-if="hasData"
      type="line"
      width="100%"
      height="300"
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
import type { TokenTrendSeries } from '@/types'
import { baseLineChartColors, CHART_FONT_FAMILY } from '../composables/chartTheme'
import { formatCompact } from '../composables/format'

const props = defineProps<{
  series: TokenTrendSeries[]
}>()

const hasData = computed(() => props.series.length > 0)

const chartSeries = computed(() =>
  props.series.map(entry => ({
    name: entry.name,
    data: entry.points.map(point => Number(point.tokens))
  }))
)

const chartOptions = computed<ApexOptions>(() => ({
  ...(baseLineChartColors() as ApexOptions),
  colors: ['#2aa1a9', '#d98f45', '#8b6fd6', '#4f9e63', '#263749', '#c0453a'],
  chart: {
    ...(baseLineChartColors().chart ?? {}),
    type: 'line',
    height: 300
  },
  markers: { size: 0, strokeWidth: 0, hover: { size: 5 } },
  xaxis: {
    ...(baseLineChartColors().xaxis ?? {}),
    categories: props.series[0]?.points.map(point => point.date.slice(5).replace('-', '/')) ?? [],
    tickAmount: props.series[0]?.points.length && props.series[0].points.length > 10 ? 10 : props.series[0]?.points.length,
    labels: {
      rotate: 0,
      hideOverlappingLabels: true,
      style: { colors: '#8c8a82', fontSize: '11px' }
    }
  },
  tooltip: {
    ...(baseLineChartColors().tooltip ?? {}),
    custom: ({ dataPointIndex }: { dataPointIndex: number }) => {
      const date = props.series[0]?.points[dataPointIndex]?.date ?? ''
      const rows = props.series
        .map((entry, index) => {
          const value = Number(entry.points[dataPointIndex]?.tokens ?? 0)
          const color = ['#2aa1a9', '#d98f45', '#8b6fd6', '#4f9e63', '#263749', '#c0453a'][index % 6]
          return `<div class="tooltip-row"><span class="dot" style="background:${color}"></span>${escapeHtml(entry.name)} <b>${formatCompact(value)}</b></div>`
        })
        .join('')
      return `<div class="apex-custom-tooltip"><div class="tooltip-title">${date}</div>${rows}</div>`
    }
  },
  legend: {
    show: true,
    position: 'top',
    horizontalAlign: 'left',
    fontFamily: CHART_FONT_FAMILY,
    fontSize: '12px',
    markers: { size: 6, shape: 'circle' },
    itemMargin: { horizontal: 8 }
  }
}))

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, ch => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  })[ch] ?? ch)
}
</script>

<style lang="scss" scoped>
.chart-wrapper {
  width: 100%;
}

.chart-empty {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8c8a82;
  font-size: 14px;
}

:global(.apex-custom-tooltip) {
  padding: 8px 12px;
  background: #faf9f5;
  border: 1px solid #e8e6dc;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(20, 20, 19, 0.08);

  .tooltip-title {
    font-weight: 500;
    color: #141413;
    margin-bottom: 4px;
  }

  .tooltip-row {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #6e6b62;
    font-size: 12px;
    line-height: 1.7;

    b {
      color: #141413;
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
