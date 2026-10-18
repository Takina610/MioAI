<template>
  <div class="chart-wrapper">
    <div v-if="!hasData" class="chart-empty">暂无数据</div>
    <div v-show="hasData" ref="chartRef" class="chart-canvas" :style="{ height: `${height}px` }"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import type { TokenTrendSeries } from '@/types'
import { useEChart } from '@/composables/useEChart'
import {
  ECHARTS_PALETTE,
  ECHARTS_FONT_FAMILY,
  axisStyle,
  chartTooltip,
  compactAxis,
  escapeHtml
} from '../composables/echartsTheme'
import { formatCompact } from '../composables/format'

const props = withDefaults(defineProps<{
  series: TokenTrendSeries[]
  height?: number
}>(), {
  height: 300
})

const chartRef = ref<HTMLElement | null>(null)
const { render } = useEChart(chartRef)

const hasData = computed(() => props.series.length > 0)

function colorOf(index: number): string {
  return ECHARTS_PALETTE[index % ECHARTS_PALETTE.length]
}

/** 只列当日有量的系列并按数值降序：0 值不占 tooltip 行 */
function tooltipRows(dataIndex: number): string {
  const rows = props.series
    .map((entry, index) => ({
      name: entry.name,
      value: Number(entry.points[dataIndex]?.tokens ?? 0),
      color: colorOf(index)
    }))
    .filter(row => row.value > 0)
    .sort((a, b) => b.value - a.value)

  if (rows.length === 0) {
    return `<div style="color:#8c8a82">当日无 Token 消耗</div>`
  }
  return rows
    .map(row =>
      `<div style="display:flex;align-items:center;gap:6px;line-height:1.7">`
      + `<span style="width:8px;height:8px;border-radius:50%;background:${row.color};flex-shrink:0"></span>`
      + `<span style="max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#6e6b62">${escapeHtml(row.name)}</span>`
      + `<b style="margin-left:auto;color:#141413;font-variant-numeric:tabular-nums">${formatCompact(row.value)}</b>`
      + `</div>`
    )
    .join('')
}

function buildOption(): EChartsOption {
  const dates = props.series[0]?.points.map(point => point.date.slice(5).replace('-', '/')) ?? []
  return {
    backgroundColor: 'transparent',
    color: ECHARTS_PALETTE,
    tooltip: {
      ...chartTooltip(),
      trigger: 'axis',
      confine: true,
      axisPointer: { type: 'line', lineStyle: { color: '#d2cfc5', type: 'dashed' } },
      extraCssText: 'box-shadow: 0 2px 8px rgba(20,20,19,0.08); border-radius: 8px; max-width: 280px;',
      formatter: (params: unknown) => {
        const list = params as Array<{ dataIndex: number }>
        const dataIndex = list[0]?.dataIndex ?? 0
        const date = props.series[0]?.points[dataIndex]?.date ?? ''
        return `<div style="font-weight:500;margin-bottom:4px">${escapeHtml(date)}</div>${tooltipRows(dataIndex)}`
      }
    },
    legend: {
      top: 0,
      left: 0,
      type: 'scroll',
      itemWidth: 10,
      itemHeight: 8,
      itemGap: 10,
      pageIconSize: 8,
      pageTextStyle: { color: '#8c8a82', fontSize: 10 },
      textStyle: { color: '#6e6b62', fontSize: 11, fontFamily: ECHARTS_FONT_FAMILY },
      data: props.series.map(entry => entry.name)
    },
    grid: { left: 8, right: 16, top: 42, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      ...axisStyle(),
      splitLine: { show: false },
      axisLabel: {
        color: '#8c8a82',
        fontSize: 11,
        fontFamily: ECHARTS_FONT_FAMILY,
        hideOverlap: true
      }
    },
    yAxis: {
      type: 'value',
      ...axisStyle(),
      axisLabel: {
        color: '#8c8a82',
        fontSize: 11,
        fontFamily: ECHARTS_FONT_FAMILY,
        formatter: (value: number) => compactAxis(value)
      }
    },
    series: props.series.map((entry, index) => ({
      name: entry.name,
      type: 'line' as const,
      data: entry.points.map(point => Number(point.tokens)),
      smooth: true,
      showSymbol: false,
      symbolSize: 5,
      lineStyle: { width: 2.2, color: colorOf(index) },
      itemStyle: { color: colorOf(index) },
      emphasis: { focus: 'series' as const }
    }))
  }
}

onMounted(() => {
  if (hasData.value) render(buildOption())
})

watch(
  () => props.series,
  () => {
    if (hasData.value) render(buildOption())
  },
  { deep: true, flush: 'post' }
)
</script>

<style lang="scss" scoped>
.chart-wrapper {
  width: 100%;
}

.chart-canvas {
  width: 100%;
}

.chart-empty {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-muted;
  font-size: 14px;
}
</style>
