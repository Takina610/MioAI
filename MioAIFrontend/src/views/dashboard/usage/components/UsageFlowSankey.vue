<template>
  <div class="analytics-chart">
    <div v-if="!hasData" class="chart-empty">范围内没有记录</div>
    <div v-show="hasData" ref="chartRef" class="chart-canvas" :style="{ height: `${height}px` }"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import type { UsageFlow, UsageFlowNode } from '@/types'
import { useEChart } from '@/composables/charts/useEChart'
import {
  ECHARTS_FONT_FAMILY,
  ECHARTS_PALETTE,
  ECHARTS_TEXT_COLOR,
  chartTooltip,
  escapeHtml
} from '../composables/echartsTheme'

const props = defineProps<{
  flow: UsageFlow | null
  valueLabel: string
  valueUnit: string
}>()

const chartRef = ref<HTMLElement | null>(null)
const { render } = useEChart(chartRef)

const hasData = computed(() => (props.flow?.links.length ?? 0) > 0)

/** 节点高度随节点数增长：节点多时不拥挤，节点少时不留大片空白 */
const height = computed(() => {
  const count = props.flow?.nodes.length ?? 0
  return Math.min(620, Math.max(320, 160 + count * 26))
})

const totalValue = computed(() =>
  (props.flow?.nodes ?? []).filter(node => node.layer === 0).reduce((sum, node) => sum + node.value, 0)
)

/** 层级配色：同层按节点轮换完整调色板，层级之间用透明度区分流向先后 */
function hexToRgba(hex: string, alpha: number): string {
  const value = hex.replace('#', '')
  const r = parseInt(value.slice(0, 2), 16)
  const g = parseInt(value.slice(2, 4), 16)
  const b = parseInt(value.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function buildNodeColors(nodes: UsageFlowNode[]): Map<string, string> {
  const colors = new Map<string, string>()
  const byLayer = new Map<number, UsageFlowNode[]>()
  for (const node of nodes) {
    const list = byLayer.get(node.layer) ?? []
    list.push(node)
    byLayer.set(node.layer, list)
  }
  const hasUserLayer = nodes.some(node => node.name.startsWith('u:'))
  const layerAlpha = hasUserLayer ? [0.9, 0.95, 0.72] : [0.94, 0.74]

  for (const [layer, list] of byLayer) {
    const alpha = layerAlpha[layer] ?? 0.8
    // 同层按用量降序取色：大流量节点优先拿主色，颜色分布更稳定
    const sorted = [...list].sort((a, b) => b.value - a.value)
    sorted.forEach((node, index) => {
      // "其他"是聚合项不是真实实体：用暖灰，避免彩色被误读为独立分类
      if (node.displayName.startsWith('其他')) {
        colors.set(node.name, 'rgba(140, 138, 130, 0.55)')
        return
      }
      const cycle = Math.floor(index / ECHARTS_PALETTE.length)
      colors.set(
        node.name,
        hexToRgba(ECHARTS_PALETTE[index % ECHARTS_PALETTE.length], Math.max(0.5, alpha - cycle * 0.14))
      )
    })
  }
  return colors
}

function buildOption(): EChartsOption {
  const flow = props.flow
  if (!flow) return {}
  const display = new Map(flow.nodes.map(node => [node.name, node.displayName]))
  const nodeColors = buildNodeColors(flow.nodes)
  const layers = [...new Set(flow.nodes.map(node => node.layer))].sort((a, b) => a - b)

  return {
    backgroundColor: 'transparent',
    tooltip: {
      ...chartTooltip(),
      trigger: 'item',
      formatter: (params: unknown) => {
        const data = (params as { data?: { name?: string; value?: number } }).data
        if (!data?.name) return ''
        const name = escapeHtml(display.get(data.name) ?? data.name)
        const value = Number(data.value ?? 0)
        const percent = totalValue.value > 0 ? ((value / totalValue.value) * 100).toFixed(1) : '0'
        return `<div style="font-weight:500;margin-bottom:2px">${name}</div>`
          + `<div style="color:#6e6b62">${props.valueLabel} <b style="color:#141413">${value.toLocaleString()}</b> ${props.valueUnit}</div>`
          + `<div style="color:#8c8a82">占总量 ${percent}%</div>`
      }
    },
    series: [
      {
        type: 'sankey',
        left: 8,
        right: 158,
        top: 12,
        bottom: 12,
        nodeWidth: 12,
        nodeGap: 18,
        nodeAlign: 'justify',
        draggable: false,
        emphasis: { focus: 'adjacency' },
        data: flow.nodes.map(node => ({
          name: node.name,
          depth: node.layer,
          displayName: node.displayName,
          itemStyle: { color: nodeColors.get(node.name), borderRadius: 2 }
        })),
        links: flow.links.map(link => ({
          source: link.source,
          target: link.target,
          value: link.value
        })),
        label: {
          color: ECHARTS_TEXT_COLOR,
          fontFamily: ECHARTS_FONT_FAMILY,
          fontSize: 12,
          width: 128,
          overflow: 'truncate',
          formatter: (params: unknown) => {
            const data = (params as { data?: { name?: string } }).data
            return display.get(data?.name ?? '') ?? data?.name ?? ''
          }
        },
        lineStyle: { color: 'gradient', opacity: 0.38, curveness: 0.5 },
        levels: layers.map(layer => ({ depth: layer }))
      } as never
    ]
  }
}

onMounted(() => {
  if (hasData.value) render(buildOption())
})

watch(
  () => [props.flow, props.valueLabel],
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
}

.chart-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  color: $text-muted;
}
</style>
