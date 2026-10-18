<template>
  <div class="contribution">
    <div ref="chartRef" class="calendar-canvas"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import type { EChartsOption } from 'echarts'
import type { ContributionDay } from '@/types'
import type { ContributionMode } from '../composables/useTokenInsights'
import { useEChart } from '@/composables/useEChart'
import { formatCompact } from '../composables/format'
import {
  ECHARTS_FONT_FAMILY,
  chartTooltip,
  escapeHtml
} from '../composables/echartsTheme'

interface CalendarCell {
  date: string
  value: number
  calls: number
  display: number
  weekCalls: number
  weekRange: string
}

const props = defineProps<{
  days: ContributionDay[]
  mode: ContributionMode
  selectedDate?: string | null
}>()

const emit = defineEmits<{
  (e: 'select-date', date: string): void
}>()

const chartRef = ref<HTMLElement | null>(null)
const { render, on } = useEChart(chartRef)

const dayMap = computed(() => new Map(props.days.map(day => [day.date, day])))
const today = dayjs().format('YYYY-MM-DD')

/** 滚动 12 个月窗口（对齐周，从 11 个月前的月初所在周开始） */
const windowStart = computed(() => dayjs().subtract(11, 'month').startOf('month').startOf('week'))

/** 窗口内逐日单元格：display 按视图取值（每日/每周合计/累计） */
const cells = computed<CalendarCell[]>(() => {
  const start = windowStart.value
  const result: CalendarCell[] = []
  let running = 0
  const weekAcc = new Map<number, { value: number; calls: number; first: string; last: string }>()

  for (let cursor = start; !cursor.isAfter(dayjs(), 'day'); cursor = cursor.add(1, 'day')) {
    const key = cursor.format('YYYY-MM-DD')
    const hit = dayMap.value.get(key)
    const value = hit ? Number(hit.tokens) : 0
    const calls = hit ? Number(hit.calls) : 0
    running += value
    const weekIndex = Math.floor(cursor.diff(start, 'day') / 7)
    const acc = weekAcc.get(weekIndex) ?? { value: 0, calls: 0, first: key, last: key }
    acc.value += value
    acc.calls += calls
    acc.last = key
    weekAcc.set(weekIndex, acc)
    result.push({ date: key, value, calls, display: value, weekCalls: 0, weekRange: '' })
  }

  result.forEach((cell, index) => {
    const weekIndex = Math.floor(dayjs(cell.date).diff(start, 'day') / 7)
    const acc = weekAcc.get(weekIndex)
    cell.weekCalls = acc?.calls ?? 0
    cell.weekRange = acc ? `${acc.first.slice(5)} ~ ${acc.last.slice(5)}` : ''
    if (props.mode === 'weekly') {
      cell.display = acc?.value ?? 0
    } else if (props.mode === 'cumulative') {
      cell.display = result.slice(0, index + 1).reduce((sum, item) => sum + item.value, 0)
    }
  })
  return result
})

/** 着色基准：每日/每周取最大值，累计取期末累计值 */
const maxValue = computed(() => {
  if (props.mode === 'cumulative') {
    return cells.value.length > 0 ? cells.value[cells.value.length - 1].display : 0
  }
  return Math.max(...cells.value.map(cell => cell.display), 0)
})

function tooltipHtml(cell: CalendarCell): string {
  const title = dayjs(cell.date).format('M月D日')
  if (props.mode === 'weekly') {
    return `<div style="font-weight:500;margin-bottom:2px">${title}</div>`
      + `<div style="color:#6e6b62">本周 Token <b style="color:#141413">${formatCompact(cell.display)}</b></div>`
      + `<div style="color:#6e6b62">当日 <b style="color:#141413">${formatCompact(cell.value)}</b></div>`
      + `<div style="color:#8c8a82">周区间 ${escapeHtml(cell.weekRange)} · 轮次 ${cell.weekCalls}</div>`
  }
  if (props.mode === 'cumulative') {
    return `<div style="font-weight:500;margin-bottom:2px">${title}</div>`
      + `<div style="color:#6e6b62">窗口累计 <b style="color:#141413">${formatCompact(cell.display)}</b></div>`
      + `<div style="color:#6e6b62">当日 <b style="color:#141413">${formatCompact(cell.value)}</b></div>`
  }
  return `<div style="font-weight:500;margin-bottom:2px">${title}</div>`
    + `<div style="color:#6e6b62">Token <b style="color:#141413">${formatCompact(cell.value)}</b></div>`
    + `<div style="color:#6e6b62">轮次 <b style="color:#141413">${cell.calls}</b></div>`
}

function buildOption(): EChartsOption {
  const selected = props.selectedDate
  const seriesData = cells.value.map(cell => [cell.date, cell.display])

  return {
    backgroundColor: 'transparent',
    tooltip: {
      ...chartTooltip(),
      formatter: (params: unknown) => {
        const date = (params as { value?: [string, number] }).value?.[0]
        const cell = cells.value.find(item => item.date === date)
        return cell ? tooltipHtml(cell) : ''
      }
    },
    visualMap: {
      type: 'continuous',
      show: false,
      min: 0,
      max: Math.max(maxValue.value, 1),
      seriesIndex: 0,
      inRange: {
        color: ['#ece9de', 'rgba(42, 161, 169, 0.28)', 'rgba(42, 161, 169, 0.52)', 'rgba(42, 161, 169, 0.76)', '#2aa1a9']
      }
    },
    calendar: {
      top: 30,
      left: 30,
      right: 12,
      range: [windowStart.value.format('YYYY-MM-DD'), today],
      cellSize: ['auto', 14],
      orient: 'horizontal',
      splitLine: { show: false },
      itemStyle: { color: 'transparent', borderWidth: 2, borderColor: '#faf9f5' },
      dayLabel: {
        firstDay: 1,
        nameMap: 'ZH',
        color: '#8c8a82',
        fontSize: 10,
        fontFamily: ECHARTS_FONT_FAMILY
      },
      monthLabel: {
        nameMap: 'ZH',
        color: '#6e6b62',
        fontSize: 11,
        margin: 8,
        fontFamily: ECHARTS_FONT_FAMILY
      },
      yearLabel: {
        show: true,
        color: '#8c8a82',
        fontSize: 11,
        margin: 30,
        fontFamily: ECHARTS_FONT_FAMILY
      }
    },
    series: [
      {
        type: 'heatmap',
        coordinateSystem: 'calendar',
        data: seriesData,
        itemStyle: { borderColor: '#faf9f5', borderWidth: 2, borderRadius: 3 },
        emphasis: { itemStyle: { borderColor: '#263749', borderWidth: 1.5 } }
      },
      ...(selected
        ? [{
            type: 'heatmap',
            coordinateSystem: 'calendar',
            silent: true,
            z: 4,
            data: [[selected, 0]],
            itemStyle: {
              color: 'transparent',
              borderColor: '#263749',
              borderWidth: 1.5,
              borderRadius: 3
            }
          }]
        : [])
    ] as never
  }
}

onMounted(() => {
  render(buildOption())
  on('click', ((params: { componentType?: string; value?: [string, number] }) => {
    if (params.componentType !== 'series') return
    const date = params.value?.[0]
    if (typeof date === 'string' && date <= today) {
      emit('select-date', date)
    }
  }) as never)
})

watch(
  () => [props.days, props.mode, props.selectedDate],
  () => render(buildOption()),
  { deep: true }
)
</script>

<style lang="scss" scoped>
.contribution {
  position: relative;
  width: 100%;
}

.calendar-canvas {
  width: 100%;
  height: 156px;
}
</style>
