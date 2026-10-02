<template>
  <div ref="chartRef" class="chart-container"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import type { TimePoint } from '@/types'
import { useEChart } from '../composables/useEChart'

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

const chartRef = ref<HTMLDivElement | null>(null)
const { render } = useEChart(chartRef)

const EMPTY_OPTION: EChartsOption = {
  title: {
    text: '暂无数据',
    left: 'center',
    top: 'center',
    textStyle: { color: '#999', fontSize: 16, fontWeight: 'normal' }
  },
  xAxis: { type: 'category' as const, data: [] },
  yAxis: { type: 'value' as const },
  series: []
}

function renderChart(): void {
  if (props.current.length === 0) {
    render(EMPTY_OPTION)
    return
  }

  const times = props.current.map(p => p.time)
  const currentValues = props.current.map(p => Number(p.value))
  const prevValues = times.map((_, index) => {
    const prevPoint = props.prev[index]
    return prevPoint ? Number(prevPoint.value) : 0
  })

  render({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e8eaed',
      textStyle: { color: '#333' },
      formatter: (params: any) => {
        const index = params[0].dataIndex
        const time = props.current[index]?.time ?? '-'
        let html = `<div style="margin-bottom:4px;font-weight:500">${time}</div>`
        params.forEach((p: any) => {
          html += `<div style="margin:2px 0">
            <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};margin-right:6px"></span>
            <span><strong>${p.value}</strong></span>
          </div>`
        })
        return html
      }
    },
    legend: { top: 0, right: 0, data: ['本周期', '上周期'] },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times,
      axisLine: { lineStyle: { color: '#e8eaed' } },
      axisLabel: { color: '#666' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f0f0' } },
      axisLabel: { color: '#666' }
    },
    series: [
      {
        name: '本周期',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        showSymbol: false,
        lineStyle: { width: 3, color: props.color },
        itemStyle: { color: props.color },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: `${props.color}40` },
            { offset: 1, color: `${props.color}05` }
          ])
        },
        data: currentValues
      },
      {
        name: '上周期',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: false,
        lineStyle: { width: 2, color: '#999', type: 'dashed' },
        itemStyle: { color: '#999' },
        data: prevValues
      }
    ]
  })
}

watch(() => [props.current, props.prev, props.color], () => {
  nextTick(renderChart)
}, { deep: true })

onMounted(() => {
  nextTick(renderChart)
})
</script>

<style lang="scss" scoped>
.chart-container {
  width: 100%;
  height: 320px;
}
</style>
