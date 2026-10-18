<template>
  <div class="base-donut">
    <div v-if="!hasData" class="donut-empty">暂无数据</div>
    <div v-show="hasData" ref="chartRef" class="donut-canvas" :style="{ height: `${height}px` }"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import { useEChart } from '@/composables/charts/useEChart'
import {
  ECHARTS_FONT_FAMILY,
  ECHARTS_PALETTE,
  chartTooltip,
  escapeHtml
} from '@/views/dashboard/usage/composables/echartsTheme'

export interface DonutItem {
  name: string
  value: number
}

const props = withDefaults(defineProps<{
  items: DonutItem[]
  colors?: string[]
  /** 圆心下方说明（如 Token 总量） */
  centerLabel?: string
  height?: number
  /** 数值展示（圆心合计与 tooltip 共用） */
  formatValue?: (value: number) => string
  /** tooltip 数值单位 */
  unit?: string
}>(), {
  colors: () => [...ECHARTS_PALETTE],
  centerLabel: '',
  height: 280,
  formatValue: (value: number) => value.toLocaleString('zh-CN'),
  unit: ''
})

const chartRef = ref<HTMLElement | null>(null)
const { render } = useEChart(chartRef)

const hasData = computed(() => props.items.length > 0)

const total = computed(() => props.items.reduce((sum, item) => sum + Number(item.value), 0))

const colors = computed(() =>
  props.items.map((_, index) => props.colors[index % props.colors.length])
)

function percentOf(value: number): string {
  if (total.value === 0) return '0'
  return ((value / total.value) * 100).toFixed(1).replace(/\.0$/, '')
}

function buildOption(): EChartsOption {
  return {
    backgroundColor: 'transparent',
    color: colors.value,
    title: {
      text: props.formatValue(total.value),
      subtext: props.centerLabel,
      left: 'center',
      top: '41%',
      itemGap: 2,
      textStyle: {
        color: '#141413',
        fontSize: 22,
        fontWeight: 600,
        fontFamily: ECHARTS_FONT_FAMILY
      },
      subtextStyle: {
        color: '#8c8a82',
        fontSize: 12,
        fontFamily: ECHARTS_FONT_FAMILY
      }
    },
    tooltip: {
      ...chartTooltip(),
      trigger: 'item',
      formatter: (params: unknown) => {
        const entry = params as { name?: string; value?: number; percent?: number }
        const value = Number(entry.value ?? 0)
        return `<div style="font-weight:500;margin-bottom:2px">${escapeHtml(entry.name ?? '')}</div>`
          + `<div style="color:#6e6b62">${props.formatValue(value)}${props.unit ? ` ${escapeHtml(props.unit)}` : ''}</div>`
          + `<div style="color:#8c8a82">占 ${entry.percent ?? percentOf(value)}%</div>`
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['58%', '80%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderColor: '#faf9f5', borderWidth: 2 },
        emphasis: { scale: true, scaleSize: 4 },
        data: props.items.map(item => ({ name: item.name, value: Number(item.value) }))
      }
    ]
  }
}

onMounted(() => {
  if (hasData.value) render(buildOption())
})

watch(
  () => [props.items, props.colors, props.centerLabel],
  () => {
    if (hasData.value) render(buildOption())
  },
  { deep: true, flush: 'post' }
)
</script>

<style lang="scss" scoped>
.base-donut {
  width: 100%;
}

.donut-canvas {
  width: 100%;
}

.donut-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-muted;
  font-size: 14px;
}
</style>
