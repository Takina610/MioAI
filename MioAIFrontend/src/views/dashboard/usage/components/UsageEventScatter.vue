<template>
  <div class="scatter-block">
    <div class="scatter-toolbar" v-if="selection">
      <span class="toolbar-label">框选结果</span>
      <a-button size="small" type="link" @click="clearBrush">清除框选</a-button>
    </div>

    <div v-if="!hasData" class="chart-empty">范围内没有记录</div>
    <div v-show="hasData" ref="chartRef" class="chart-canvas"></div>

    <div v-if="selection" class="selection-strip">
      <div class="selection-item">
        <span class="item-label">记录</span>
        <span class="item-value">{{ selection.count.toLocaleString() }}</span>
        <span class="item-unit">/ {{ events.length.toLocaleString() }}</span>
      </div>
      <div class="selection-item">
        <span class="item-label">{{ measureLabel }}</span>
        <span class="item-value">{{ formatCompact(selection.total) }}</span>
        <span class="item-unit">{{ measureUnit }}</span>
      </div>
      <div class="selection-item">
        <span class="item-label">成功</span>
        <span class="item-value">{{ selection.successRate.toFixed(1) }}%</span>
      </div>
      <div class="selection-item">
        <span class="item-label">平均</span>
        <span class="item-value">{{ formatCompact(selection.avg) }}</span>
        <span class="item-unit">{{ measureUnit }}</span>
      </div>
      <div class="selection-item">
        <span class="item-label">峰值</span>
        <span class="item-value">{{ formatCompact(selection.max) }}</span>
        <span class="item-unit">{{ measureUnit }}</span>
      </div>
      <div class="selection-item range">
        <span class="item-label">时段</span>
        <span class="item-value">{{ selection.rangeText }}</span>
      </div>
      <div class="selection-agents" v-if="selection.agents.length > 0">
        <span class="agent-chip" v-for="agent in selection.agents" :key="agent.name">
          {{ agent.name }}<b>{{ agent.count }}</b>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import type { EChartsOption } from 'echarts'
import type { UsageEventPoint } from '@/types'
import { useEChart } from '@/composables/charts/useEChart'
import { formatCompact } from '../composables/format'
import {
  ECHARTS_FONT_FAMILY,
  axisStyle,
  chartTooltip,
  compactAxis,
  escapeHtml
} from '../composables/echartsTheme'

interface SelectionSummary {
  count: number
  total: number
  avg: number
  max: number
  successRate: number
  rangeText: string
  agents: Array<{ name: string; count: number }>
}

interface BrushParams {
  areas?: Array<{ coordRange?: number[][] }>
}

const props = defineProps<{
  events: UsageEventPoint[]
  measureLabel: string
  measureUnit: string
  valueLabel: string
  valueUnit: string
}>()

const chartRef = ref<HTMLElement | null>(null)
const { render, on, instance } = useEChart(chartRef)
const selection = ref<SelectionSummary | null>(null)

const hasData = computed(() => props.events.length > 0)

const maxMeasure = computed(() => Math.max(...props.events.map(event => event.value), 1))

function symbolSize(value: number): number {
  const ratio = Math.sqrt(Math.max(0, value) / maxMeasure.value)
  return 5 + ratio * 9
}

function buildOption(): EChartsOption {
  const success = props.events.filter(event => event.status === 1)
  const failure = props.events.filter(event => event.status !== 1)

  const toData = (events: UsageEventPoint[]) => events.map(event => ({
    value: [event.time, event.value],
    raw: event
  }))

  return {
    backgroundColor: 'transparent',
    animation: false,
    tooltip: {
      ...chartTooltip(),
      trigger: 'item',
      formatter: (params: unknown) => {
        const raw = (params as { data?: { raw?: UsageEventPoint } }).data?.raw
        if (!raw) return ''
        const time = dayjs(raw.time).format('MM-DD HH:mm:ss')
        const status = raw.status === 1 ? '成功' : '失败'
        return `<div style="font-weight:500;margin-bottom:2px">${time}</div>`
          + `<div style="color:#6e6b62">${props.measureLabel} <b style="color:#141413">${raw.value.toLocaleString()}</b> ${props.measureUnit}</div>`
          + `<div style="color:#6e6b62">智能体 ${escapeHtml(raw.agentName)}${raw.model ? ` · ${escapeHtml(raw.model)}` : ''}</div>`
          + `<div style="color:#6e6b62">目标 ${escapeHtml(raw.targetName)}</div>`
          + `<div style="color:${raw.status === 1 ? '#4f9e63' : '#c0453a'}">${status}</div>`
      }
    },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 9,
      itemHeight: 9,
      textStyle: { color: '#6e6b62', fontSize: 11, fontFamily: ECHARTS_FONT_FAMILY },
      data: ['成功', '失败']
    },
    toolbox: {
      right: 0,
      top: 24,
      itemSize: 13,
      feature: {
        brush: {
          type: ['rect', 'clear'],
          title: { rect: '框选', clear: '清除框选' }
        },
        dataZoom: { title: { zoom: '缩放', back: '还原缩放' } }
      }
    },
    grid: { left: 8, right: 16, top: 56, bottom: 62, containLabel: true },
    xAxis: {
      type: 'time',
      ...axisStyle(),
      splitLine: { show: false },
      axisLabel: {
        color: '#8c8a82',
        fontSize: 11,
        fontFamily: ECHARTS_FONT_FAMILY,
        hideOverlap: true,
        formatter: (value: number) => dayjs(value).format('MM-DD HH:mm')
      }
    },
    yAxis: {
      type: 'value',
      name: props.measureLabel,
      nameTextStyle: { color: '#8c8a82', fontSize: 11, align: 'left' },
      ...axisStyle(),
      // 顶部留 15% 余量：最高点不贴边，也不压到右上角工具箱
      max: (value: { max: number }) => Math.max(1, Math.ceil(value.max * 1.15)),
      axisLabel: {
        color: '#8c8a82',
        fontSize: 11,
        fontFamily: ECHARTS_FONT_FAMILY,
        formatter: (value: number) => compactAxis(value)
      }
    },
    dataZoom: [
      {
        type: 'inside',
        xAxisIndex: 0,
        filterMode: 'filter',
        // 拖动留给框选：inside 默认把拖动当平移，会和 brush 抢手势
        moveOnMouseMove: false,
        moveOnMouseWheel: false,
        zoomOnMouseWheel: true
      },
      {
        type: 'slider',
        xAxisIndex: 0,
        height: 20,
        bottom: 6,
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
    ],
    brush: {
      xAxisIndex: 0,
      brushType: 'rect',
      brushMode: 'single',
      transformable: false,
      throttleType: 'debounce',
      throttleDelay: 260,
      brushStyle: {
        borderWidth: 1,
        color: 'rgba(42, 161, 169, 0.08)',
        borderColor: '#2aa1a9'
      }
    },
    series: [
      {
        name: '成功',
        type: 'scatter',
        data: toData(success),
        symbolSize: (value: number[]) => symbolSize(value[1]),
        itemStyle: { color: 'rgba(42, 161, 169, 0.7)' },
        emphasis: { itemStyle: { color: '#2aa1a9' } }
      },
      {
        name: '失败',
        type: 'scatter',
        data: toData(failure),
        symbolSize: (value: number[]) => symbolSize(value[1]),
        itemStyle: { color: 'rgba(192, 69, 58, 0.7)' },
        emphasis: { itemStyle: { color: '#c0453a' } }
      }
    ] as never
  }
}

function draw(): void {
  render(buildOption())
  // 默认进入矩形框选：进页面即可直接拖动选区，无需先点工具箱。
  // 需等 setOption 渲染完成后再切光标，否则动作会被随后的首帧覆盖
  requestAnimationFrame(() => {
    instance()?.dispatchAction({
      type: 'takeGlobalCursor',
      key: 'brush',
      brushOption: { brushType: 'rect', brushMode: 'single' }
    })
  })
}

function summarize(events: UsageEventPoint[], rangeText: string): SelectionSummary {
  const count = events.length
  const total = events.reduce((sum, event) => sum + event.value, 0)
  const successCount = events.filter(event => event.status === 1).length
  const agentCounts = new Map<string, number>()
  for (const event of events) {
    agentCounts.set(event.agentName, (agentCounts.get(event.agentName) ?? 0) + 1)
  }
  const agents = [...agentCounts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3)
    .map(([name, agentCalls]) => ({ name, count: agentCalls }))
  return {
    count,
    total,
    avg: count === 0 ? 0 : total / count,
    max: Math.max(...events.map(event => event.value), 0),
    successRate: count === 0 ? 0 : (successCount / count) * 100,
    rangeText,
    agents
  }
}

function handleBrush(params: BrushParams): void {
  const coordRange = params.areas?.[0]?.coordRange
  if (!coordRange || coordRange.length < 2) {
    selection.value = null
    return
  }
  const [xRange, yRange] = coordRange
  const xMin = Math.min(Number(xRange[0]), Number(xRange[1]))
  const xMax = Math.max(Number(xRange[0]), Number(xRange[1]))
  const yMin = Math.min(Number(yRange[0]), Number(yRange[1]))
  const yMax = Math.max(Number(yRange[0]), Number(yRange[1]))
  const hit = props.events.filter(event =>
    event.time >= xMin && event.time <= xMax && event.value >= yMin && event.value <= yMax
  )
  if (hit.length === 0) {
    selection.value = null
    return
  }
  const start = dayjs(Math.max(xMin, Math.min(...hit.map(event => event.time))))
  const end = dayjs(Math.min(xMax, Math.max(...hit.map(event => event.time))))
  const rangeText = start.isSame(end, 'day')
    ? `${start.format('MM-DD HH:mm')}–${end.format('HH:mm')}`
    : `${start.format('MM-DD HH:mm')}–${end.format('MM-DD HH:mm')}`
  selection.value = summarize(hit, rangeText)
}

function clearBrush(): void {
  selection.value = null
  // 只清框选，保留时间滑块的缩放状态
  instance()?.dispatchAction({ type: 'brush', areas: [] })
}

onMounted(() => {
  if (hasData.value) draw()
  on('brushEnd', ((params: BrushParams) => handleBrush(params)) as never)
})

watch(
  () => props.events,
  () => {
    selection.value = null
    if (hasData.value) draw()
  },
  { deep: true, flush: 'post' }
)
</script>

<style lang="scss" scoped>
.scatter-block {
  width: 100%;
}

.scatter-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 24px;
  margin-bottom: 4px;

  .toolbar-label {
    font-size: 12px;
    color: #141413;
    font-weight: 500;
  }
}

.chart-canvas {
  width: 100%;
  height: 360px;
}

.chart-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  color: $text-muted;
}

.selection-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 18px;
  padding: 10px 14px;
  background: #f5f3ec;
  border-radius: 10px;
  margin-top: 4px;

  .selection-item {
    display: flex;
    align-items: baseline;
    gap: 5px;

    .item-label {
      font-size: 12px;
      color: #6e6b62;
    }

    .item-value {
      font-size: 14px;
      font-weight: 600;
      color: #141413;
      font-variant-numeric: tabular-nums;
    }

    .item-unit {
      font-size: 11px;
      color: #8c8a82;
    }

    &.range .item-value {
      font-weight: 500;
      font-size: 13px;
    }
  }

  .selection-agents {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-left: auto;

    .agent-chip {
      font-size: 11px;
      color: #6e6b62;
      background: #faf9f5;
      border: 1px solid #e8e6dc;
      border-radius: 999px;
      padding: 2px 8px;

      b {
        color: #141413;
        margin-left: 4px;
      }
    }
  }
}
</style>
