<template>
  <div class="contribution">
    <div class="calendar">
      <div class="grid" :style="{ gridTemplateColumns: `repeat(${weekCount}, 1fr)` }">
        <div
          v-for="(cell, index) in cells"
          :key="index"
          class="cell"
          :class="cellClass(cell)"
          @mouseenter="showTip($event, cell, index)"
          @mouseleave="hideTip"
        ></div>
      </div>
      <div class="month-labels" :style="{ gridTemplateColumns: `repeat(${weekCount}, 1fr)` }">
        <span
          v-for="tick in monthTicks"
          :key="tick.col"
          class="month-label"
          :style="{ gridColumnStart: tick.col }"
        >{{ tick.label }}</span>
      </div>
    </div>

    <div class="cal-tooltip" v-if="tip.visible" :style="{ left: tip.x + 'px', top: tip.y + 'px' }">
      <div class="tip-title">{{ tip.title }}</div>
      <div class="tip-row" v-for="line in tip.lines" :key="line">{{ line }}</div>
    </div>

    <div class="cal-legend">
      <span class="legend-text">少</span>
      <span class="cell lv-0"></span>
      <span class="cell lv-1"></span>
      <span class="cell lv-2"></span>
      <span class="cell lv-3"></span>
      <span class="cell lv-4"></span>
      <span class="legend-text">多</span>
      <span class="legend-mode" v-if="mode === 'weekly'">按周合计着色</span>
      <span class="legend-mode" v-else-if="mode === 'cumulative'">按累计值着色</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import type { ContributionDay } from '@/types'
import type { ContributionMode } from '../composables/useTokenInsights'
import { formatCompact } from '../composables/format'

interface Cell {
  date: string
  /** 当日 Token */
  value: number
  calls: number
  /** 展示值：每日=当日 / 每周=周合计 / 累计=窗口内累计 */
  display: number
  future: boolean
  /** 每周视图的周区间标题 */
  range?: string
}

const props = defineProps<{
  days: ContributionDay[]
  mode: ContributionMode
}>()

const tip = ref({ visible: false, x: 0, y: 0, title: '', lines: [] as string[] })

const dayMap = computed(() => new Map(props.days.map(day => [day.date, day])))

/**
 * 滚动 12 个月窗口（对齐周，从 11 个月前的月初所在周到当前周）：
 * 末列就是当前月，右侧不留未来空白
 */
const windowStart = computed(() =>
  dayjs().subtract(11, 'month').startOf('month').startOf('week')
)

/** 周列：windowStart 起每周一列，直至当前周 */
const weeks = computed<string[][]>(() => {
  const columns: string[][] = []
  let cursor = windowStart.value
  const endWeekStart = dayjs().startOf('week')
  while (!cursor.isAfter(endWeekStart)) {
    const week: string[] = []
    for (let i = 0; i < 7; i++) {
      week.push(cursor.add(i, 'day').format('YYYY-MM-DD'))
    }
    columns.push(week)
    cursor = cursor.add(1, 'week')
  }
  return columns
})

const weekCount = computed(() => weeks.value.length)

/** 每周合计（weekly 模式着色/提示用） */
const weekTotals = computed(() => {
  const map = dayMap.value
  const today = dayjs().format('YYYY-MM-DD')
  return weeks.value.map(week => {
    let value = 0
    let calls = 0
    let lastActive = ''
    for (const key of week) {
      if (key > today) {
        continue
      }
      const hit = map.get(key)
      if (hit) {
        value += Number(hit.tokens)
        calls += Number(hit.calls)
      }
      lastActive = key
    }
    const first = week[0]
    const range = `${first.slice(5).replace('-', '/')} ~ ${lastActive ? lastActive.slice(5).replace('-', '/') : first.slice(5).replace('-', '/')}`
    return { value, calls, range }
  })
})

/** 日历格：列=周，行=星期；display 按视图取值 */
const cells = computed<Cell[]>(() => {
  const map = dayMap.value
  const today = dayjs().format('YYYY-MM-DD')
  const totals = weekTotals.value
  const result: Cell[] = []
  let running = 0
  weeks.value.forEach((week, weekIndex) => {
    for (const key of week) {
      const hit = map.get(key)
      const value = hit ? Number(hit.tokens) : 0
      const calls = hit ? Number(hit.calls) : 0
      const future = key > today
      if (!future) {
        running += value
      }
      const display =
        props.mode === 'weekly' ? (future ? 0 : totals[weekIndex].value)
        : props.mode === 'cumulative' ? running
        : value
      result.push({
        date: key,
        value,
        calls,
        display,
        future,
        range: totals[weekIndex].range
      })
    }
  })
  return result
})

/** 着色基准：每日/每周取窗口内最大值，累计取期末累计值 */
const maxValue = computed(() => {
  const active = cells.value.filter(cell => !cell.future)
  if (props.mode === 'cumulative') {
    return active.length > 0 ? active[active.length - 1].display : 0
  }
  return Math.max(...active.map(cell => cell.display), 0)
})

const levelOf = (value: number): number => {
  if (value <= 0 || maxValue.value <= 0) {
    return 0
  }
  return Math.min(4, Math.floor((value / maxValue.value) * 4) + 1)
}

const cellClass = (cell: Cell): string[] => {
  if (cell.future) {
    return ['lv-empty']
  }
  return [`lv-${levelOf(cell.display)}`]
}

/** 月份标签：跨月的第一列标注新月份；相邻标签至少隔 3 列（首列常是跨月残周，防挤压） */
const monthTicks = computed(() => {
  const ticks: { col: number; label: string }[] = []
  let prevMonth = ''
  weeks.value.forEach((week, index) => {
    const month = week[0].slice(5, 7)
    if (month !== prevMonth) {
      ticks.push({ col: index + 1, label: `${Number(month)}月` })
      prevMonth = month
    }
  })
  return ticks.filter((tick, index) =>
    index === 0 ? ticks.length < 2 || ticks[1].col - tick.col >= 3 : tick.col - ticks[index - 1].col >= 3
  )
})

function showTip(event: MouseEvent, cell: Cell, index: number): void {
  if (cell.future) {
    return
  }
  const weekIndex = Math.floor(index / 7)
  let title = dayjs(cell.date).format('M月D日')
  let lines: string[]
  if (props.mode === 'weekly') {
    title = `本周 ${weekTotals.value[weekIndex].range}`
    lines = [`Token：${formatCompact(cell.display)}`, `轮次：${weekTotals.value[weekIndex].calls}`]
  } else if (props.mode === 'cumulative') {
    lines = [`窗口累计 Token：${formatCompact(cell.display)}`, `当日：${formatCompact(cell.value)}`]
  } else {
    lines = [`Token：${formatCompact(cell.display)}`, `轮次：${cell.calls}`]
  }
  // fixed 定位相对视口：夹在视口内避免贴边截断
  const x = Math.min(Math.max(event.clientX, 90), window.innerWidth - 90)
  const y = Math.max(event.clientY, 70)
  tip.value = { visible: true, x, y, title, lines }
}

function hideTip(): void {
  tip.value.visible = false
}
</script>

<style lang="scss" scoped>
.contribution {
  position: relative;
}

.calendar {
  .grid {
    display: grid;
    grid-auto-flow: column;
    grid-template-rows: repeat(7, 14px);
    gap: 3px;
  }

  .month-labels {
    display: grid;
    margin-top: 6px;

    .month-label {
      font-size: 11px;
      color: #8c8a82;
      white-space: nowrap;
    }
  }
}

.cell {
  width: 100%;
  height: 14px;
  border-radius: 3px;
  background: #ece9de;

  &.lv-1 { background: rgba(42, 161, 169, 0.25); }
  &.lv-2 { background: rgba(42, 161, 169, 0.5); }
  &.lv-3 { background: rgba(42, 161, 169, 0.75); }
  &.lv-4 { background: #2aa1a9; }
  &.lv-empty { background: transparent; }

  &:not(.lv-empty):hover {
    outline: 1.5px solid #263749;
    outline-offset: -1px;
  }
}

.cal-legend {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;

  .legend-text {
    font-size: 11px;
    color: #8c8a82;
    margin: 0 2px;
  }

  .cell {
    width: 11px;
    height: 11px;
  }

  .legend-mode {
    font-size: 11px;
    color: #8c8a82;
    margin-left: 10px;
  }
}

/* tooltip 用 fixed 定位：不受卡片 overflow 裁剪 */
.cal-tooltip {
  position: fixed;
  transform: translate(-50%, calc(-100% - 10px));
  background: #141413;
  color: #faf9f5;
  border-radius: 8px;
  padding: 7px 11px;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
  z-index: 20;

  .tip-title {
    font-weight: 500;
    margin-bottom: 2px;
  }

  .tip-row {
    opacity: 0.85;
    line-height: 1.6;
  }
}
</style>
