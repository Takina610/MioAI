<template>
  <div class="chart-wrapper">
    <div v-if="!hasData" class="chart-empty">暂无数据</div>
    <div v-show="hasData" ref="chartRef" class="chart-canvas" :style="{ height: `${height}px` }"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import type { TimePoint } from '@/types'
import { useEChart } from '@/composables/useEChart'
import {
  ECHARTS_FONT_FAMILY,
  axisStyle,
  chartTooltip,
  compactAxis,
  escapeHtml
} from '../composables/echartsTheme'

const props = withDefaults(defineProps<{
  /** 本周期趋势数据 */
  current: TimePoint[]
  /** 上周期趋势数据（虚线对比） */
  prev?: TimePoint[]
  color?: string
  height?: number
}>(), {
  prev: () => [],
  color: '#2aa1a9',
  height: 300
})

const chartRef = ref<HTMLElement | null>(null)
const { render } = useEChart(chartRef)

const hasData = computed(() => props.current.length > 0)

function buildOption(): EChartsOption {
  const current = props.current.map(point => Number(point.value))
  const prev = props.current.map((_, index) => {
    const point = props.prev[index]
    return point ? Number(point.value) : 0
  })
  const showZoom = props.current.length > 12

  return {
    backgroundColor: 'transparent',
    color: [props.color, '#8c8a82'],
    tooltip: {
      ...chartTooltip(),
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: '#d2cfc5', type: 'dashed' } },
      formatter: (params: unknown) => {
        const list = params as Array<{ dataIndex: number }>
        const index = list[0]?.dataIndex ?? 0
        const point = props.current[index]
        if (!point) return ''
        const prevValue = props.prev[index] ? Number(props.prev[index].value) : 0
        return `<div style="font-weight:500;margin-bottom:2px">${escapeHtml(point.fullTime || point.time)}</div>`
          + `<div style="color:#6e6b62"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${props.color};margin-right:6px"></span>本周期 <b style="color:#141413">${Number(point.value).toLocaleString()}</b></div>`
          + `<div style="color:#6e6b62"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#8c8a82;margin-right:6px"></span>上周期 <b style="color:#141413">${prevValue.toLocaleString()}</b></div>`
      }
    },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 9,
      itemHeight: 9,
      textStyle: { color: '#6e6b62', fontSize: 11, fontFamily: ECHARTS_FONT_FAMILY },
      data: ['本周期', '上周期']
    },
    grid: { left: 8, right: 16, top: 32, bottom: showZoom ? 54 : 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: props.current.map(point => point.time),
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
    dataZoom: showZoom
      ? [
          { type: 'inside', xAxisIndex: 0, filterMode: 'filter' },
          {
            type: 'slider',
            xAxisIndex: 0,
            height: 18,
            bottom: 4,
            borderColor: '#e8e6dc',
            backgroundColor: '#f5f3ec',
            fillerColor: 'rgba(42, 161, 169, 0.12)',
            handleStyle: { color: '#2aa1a9', borderColor: '#2aa1a9' },
            moveHandleStyle: { color: '#2aa1a9' },
            dataBackground: {
              lineStyle: { color: '#d2cfc5' },
              areaStyle: { color: 'rgba(140, 138, 130, 0.15)' }
            },
            selectedDataBackground: {
              lineStyle: { color: '#2aa1a9' },
              areaStyle: { color: 'rgba(42, 161, 169, 0.18)' }
            },
            textStyle: { color: '#8c8a82', fontSize: 10, fontFamily: ECHARTS_FONT_FAMILY }
          }
        ]
      : [],
    series: [
      {
        name: '本周期',
        type: 'line',
        data: current,
        smooth: true,
        showSymbol: false,
        symbolSize: 5,
        lineStyle: { width: 2.5, color: props.color },
        itemStyle: { color: props.color },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: areaFade(0.22) },
              { offset: 1, color: areaFade(0.02) }
            ]
          }
        },
        emphasis: { focus: 'series' }
      },
      {
        name: '上周期',
        type: 'line',
        data: prev,
        smooth: true,
        showSymbol: false,
        symbolSize: 5,
        lineStyle: { width: 1.8, color: '#8c8a82', type: 'dashed' },
        itemStyle: { color: '#8c8a82' },
        emphasis: { focus: 'series' }
      }
    ]
  }
}

/** 面积渐变的底色跟随主色 */
function areaFade(alpha: number): string {
  const hex = props.color.replace('#', '')
  if (hex.length === 6) {
    const r = parseInt(hex.slice(0, 2), 16)
    const g = parseInt(hex.slice(2, 4), 16)
    const b = parseInt(hex.slice(4, 6), 16)
    return `rgba(${r}, ${g}, ${b}, ${alpha})`
  }
  return `rgba(140, 138, 130, ${alpha})`
}

onMounted(() => {
  if (hasData.value) render(buildOption())
})

watch(
  () => [props.current, props.prev, props.color],
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
