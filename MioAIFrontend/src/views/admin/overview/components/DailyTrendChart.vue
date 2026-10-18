<template>
  <div class="chart-wrapper">
    <div v-if="!hasData" class="chart-empty">暂无数据</div>
    <div v-show="hasData" ref="chartRef" class="chart-canvas"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import type { OverviewDayTrend, TrendMetric } from '@/api/adminOverview'
import { useEChart } from '@/composables/charts/useEChart'
import {
  ECHARTS_FONT_FAMILY,
  axisStyle,
  chartTooltip,
  compactAxis,
  escapeHtml
} from '@/views/dashboard/usage/composables/echartsTheme'
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

const chartRef = ref<HTMLElement | null>(null)
const { render } = useEChart(chartRef)

const hasData = computed(() => props.points.some(point => Number(point[props.metric]) > 0))

function tooltipRow(color: string, label: string, value: string): string {
  return `<div style="display:flex;align-items:center;gap:6px;line-height:1.7">`
    + `<span style="width:8px;height:8px;border-radius:50%;background:${color};flex-shrink:0"></span>`
    + `<span style="color:#6e6b62">${label}</span>`
    + `<b style="margin-left:auto;color:#141413;font-variant-numeric:tabular-nums">${value}</b>`
    + `</div>`
}

function buildOption(): EChartsOption {
  const meta = METRIC_META[props.metric]
  const color = meta.color
  return {
    backgroundColor: 'transparent',
    tooltip: {
      ...chartTooltip(),
      trigger: 'axis',
      confine: true,
      axisPointer: { type: 'line', lineStyle: { color: '#d2cfc5', type: 'dashed' } },
      formatter: (params: unknown) => {
        const list = params as Array<{ dataIndex: number }>
        const index = list[0]?.dataIndex ?? 0
        const point = props.points[index]
        if (!point) return ''
        return `<div style="font-weight:500;margin-bottom:4px">${escapeHtml(point.date)}</div>`
          + tooltipRow('#2aa1a9', '对话轮次', String(point.turns))
          + tooltipRow('#d98f45', 'Token', formatCompact(point.tokens))
          + tooltipRow('#c0453a', '失败', String(point.failedTurns))
      }
    },
    grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: props.points.map(point => point.date.slice(5).replace('-', '/')),
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
    series: [
      {
        name: meta.label,
        type: 'line',
        data: props.points.map(point => Number(point[props.metric])),
        smooth: true,
        showSymbol: false,
        symbolSize: 5,
        lineStyle: { width: 2.5, color },
        itemStyle: { color },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: hexToRgba(color, 0.26) },
              { offset: 1, color: hexToRgba(color, 0.02) }
            ]
          }
        },
        emphasis: { focus: 'series' }
      }
    ]
  }
}

function hexToRgba(hex: string, alpha: number): string {
  const value = hex.replace('#', '')
  const r = parseInt(value.slice(0, 2), 16)
  const g = parseInt(value.slice(2, 4), 16)
  const b = parseInt(value.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

onMounted(() => {
  if (hasData.value) render(buildOption())
})

watch(
  () => [props.points, props.metric],
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
  height: 280px;
}

.chart-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-muted;
  font-size: 14px;
}
</style>
