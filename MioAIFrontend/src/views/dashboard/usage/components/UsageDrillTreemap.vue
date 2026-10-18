<template>
  <div class="analytics-chart">
    <div v-if="!hasData" class="chart-empty">范围内没有记录</div>
    <div v-show="hasData" ref="chartRef" class="chart-canvas"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import type { UsageDrillNode } from '@/types'
import { useEChart } from '@/composables/charts/useEChart'
import {
  ECHARTS_PALETTE,
  ECHARTS_FONT_FAMILY,
  chartTooltip,
  escapeHtml
} from '../composables/echartsTheme'

const props = defineProps<{
  nodes: UsageDrillNode[]
  valueLabel: string
  valueUnit: string
}>()

const chartRef = ref<HTMLElement | null>(null)
const { render } = useEChart(chartRef)

const hasData = computed(() => props.nodes.length > 0)
const totalValue = computed(() => props.nodes.reduce((sum, node) => sum + node.value, 0))

/** 一级节点透明度渐变：同类下钻保留同一色相，仅靠深浅区分 */
function categoryColor(index: number): string {
  const base = ECHARTS_PALETTE[index % ECHARTS_PALETTE.length]
  const alpha = index < ECHARTS_PALETTE.length ? 0.92 : 0.55
  return hexToRgba(base, alpha)
}

function hexToRgba(hex: string, alpha: number): string {
  const value = hex.replace('#', '')
  const r = parseInt(value.slice(0, 2), 16)
  const g = parseInt(value.slice(2, 4), 16)
  const b = parseInt(value.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

interface DrillData {
  name: string
  value: number
  children?: DrillData[]
  itemStyle?: { color: string; borderColor: string; borderWidth: number }
}

function buildTree(): DrillData[] {
  return props.nodes.map((node, index) => {
    const color = categoryColor(index)
    return {
      name: node.name,
      value: node.value,
      itemStyle: { color, borderColor: '#faf9f5', borderWidth: 2 },
      children: (node.children ?? []).map(child => ({
        name: child.name,
        value: child.value,
        itemStyle: {
          color: hexToRgba(ECHARTS_PALETTE[index % ECHARTS_PALETTE.length], 0.45),
          borderColor: '#faf9f5',
          borderWidth: 2
        }
      }))
    }
  })
}

function buildOption(): EChartsOption {
  return {
    backgroundColor: 'transparent',
    tooltip: {
      ...chartTooltip(),
      trigger: 'item',
      formatter: (params: unknown) => {
        const data = (params as { data?: DrillData }).data
        if (!data) return ''
        const percent = totalValue.value > 0 ? ((data.value / totalValue.value) * 100).toFixed(1) : '0'
        return `<div style="font-weight:500;margin-bottom:2px">${escapeHtml(data.name)}</div>`
          + `<div style="color:#6e6b62">${props.valueLabel} <b style="color:#141413">${data.value.toLocaleString()}</b> ${props.valueUnit}</div>`
          + `<div style="color:#8c8a82">占总量 ${percent}%</div>`
      }
    },
    series: [
      {
        name: '全部分类',
        type: 'treemap',
        left: 0,
        right: 0,
        top: 0,
        bottom: 26,
        roam: false,
        nodeClick: 'zoomToNode',
        leafDepth: 1,
        drillDownIcon: '',
        breadcrumb: {
          show: true,
          bottom: 0,
          height: 20,
          emptyItemWidth: 60,
          itemStyle: { color: '#f5f3ec', borderColor: '#e8e6dc', borderWidth: 1 },
          textStyle: { color: '#6e6b62', fontSize: 11, fontFamily: ECHARTS_FONT_FAMILY },
          emphasis: { itemStyle: { color: '#e8e6dc' } }
        },
        label: {
          show: true,
          color: '#ffffff',
          fontSize: 12,
          fontFamily: ECHARTS_FONT_FAMILY,
          formatter: (params: unknown) => {
            const data = (params as { data?: DrillData }).data
            return data ? `${data.name}` : ''
          }
        },
        upperLabel: {
          show: true,
          height: 22,
          color: '#141413',
          fontSize: 12,
          fontWeight: 500,
          fontFamily: ECHARTS_FONT_FAMILY
        },
        itemStyle: { borderColor: '#faf9f5', borderWidth: 2, gapWidth: 2, borderRadius: 4 },
        levels: [
          {
            label: { show: false },
            upperLabel: { show: false },
            itemStyle: { borderWidth: 0, gapWidth: 3 }
          },
          { colorSaturation: [0.35, 0.6], itemStyle: { gapWidth: 2, borderWidth: 2 } },
          { colorSaturation: [0.3, 0.5], itemStyle: { gapWidth: 1, borderWidth: 1 } }
        ],
        data: buildTree()
      } as never
    ]
  }
}

onMounted(() => {
  if (hasData.value) render(buildOption())
})

watch(
  () => [props.nodes, props.valueLabel],
  () => {
    if (hasData.value) render(buildOption())
  },
  { deep: true, flush: 'post' }
)
</script>

<style lang="scss" scoped>
.analytics-chart {
  width: 100%;
}

.chart-canvas {
  width: 100%;
  height: 380px;
}

.chart-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  color: $text-muted;
}
</style>
