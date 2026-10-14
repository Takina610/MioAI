<template>
  <div class="chart-wrapper">
    <VueApexCharts
      v-if="hasData"
      type="area"
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
import type { TimePoint } from '@/types'
import { baseLineChartColors } from '../composables/chartTheme'

const props = withDefaults(defineProps<{
  /** 本周期趋势数据 */
  current: TimePoint[]
  /** 上周期趋势数据（虚线对比） */
  prev?: TimePoint[]
  color?: string
}>(), {
  prev: () => [],
  color: '#2aa1a9'
})

const hasData = computed(() => props.current.length > 0)

const chartSeries = computed(() => [
  {
    name: '本周期',
    data: props.current.map(point => Number(point.value))
  },
  {
    name: '上周期',
    data: props.current.map((_, index) => {
      const prevPoint = props.prev[index]
      return prevPoint ? Number(prevPoint.value) : 0
    })
  }
])

const chartOptions = computed<ApexOptions>(() => ({
  ...(baseLineChartColors() as ApexOptions),
  colors: [props.color, '#8c8a82'],
  chart: {
    ...(baseLineChartColors().chart ?? {}),
    type: 'area',
    height: 300
  },
  stroke: {
    curve: 'smooth',
    width: [2.5, 1.8],
    dashArray: [0, 5],
    lineCap: 'round'
  },
  fill: {
    type: 'gradient',
    gradient: {
      shadeIntensity: 1,
      opacityFrom: 0.22,
      opacityTo: 0.02,
      stops: [0, 100]
    }
  },
  markers: { size: 0, strokeWidth: 0, hover: { size: 5 } },
  xaxis: {
    ...(baseLineChartColors().xaxis ?? {}),
    categories: props.current.map(point => point.time),
    tickAmount: 6,
    labels: {
      rotate: 0,
      hideOverlappingLabels: true,
      style: { colors: '#8c8a82', fontSize: '11px' }
    }
  },
  tooltip: {
    ...(baseLineChartColors().tooltip ?? {}),
    custom: ({ dataPointIndex }: { dataPointIndex: number }) => {
      const point = props.current[dataPointIndex]
      const currentValue = point ? Number(point.value) : 0
      const prevPoint = props.prev[dataPointIndex]
      const prevValue = prevPoint ? Number(prevPoint.value) : 0
      return `<div class="apex-custom-tooltip">`
        + `<div class="tooltip-title">${point?.time ?? '-'}</div>`
        + `<div class="tooltip-row"><span class="dot" style="background:${props.color}"></span>本周期 <b>${currentValue.toLocaleString()}</b></div>`
        + `<div class="tooltip-row"><span class="dot" style="background:#8c8a82"></span>上周期 <b>${prevValue.toLocaleString()}</b></div>`
        + `</div>`
    }
  }
}))
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
