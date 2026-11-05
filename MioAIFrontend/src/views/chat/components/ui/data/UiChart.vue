<template>
  <div v-if="hasData" ref="chartRef" class="ui-chart"></div>
  <div v-else class="ui-chart-empty">暂无数据</div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import { useEChart } from '@/composables/charts/useEChart'
import type { MessageBlock } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const chartRef = ref<HTMLElement | null>(null)
const { render } = useEChart(chartRef)

const series = computed(() => (props.block.series ?? []).filter(s => s && Array.isArray(s.data)))
const categories = computed(() => props.block.categories ?? [])
const hasData = computed(() => series.value.some(s => s.data.length > 0))

/* 主色透明度梯度区分多系列（不引入新色相），暖灰兜底第四系 */
const PALETTE = [
  'rgba(42, 161, 169, 0.95)',
  'rgba(42, 161, 169, 0.55)',
  'rgba(42, 161, 169, 0.3)',
  'rgba(140, 138, 130, 0.55)'
]
const AXIS_COLOR = '#8c8a82'
const SPLIT_LINE = '#e8e6dc'

function buildOption(): EChartsOption {
  const raw = props.block.chartType
  const type = raw === 'pie' ? 'pie' : raw === 'scatter' ? 'scatter' : raw === 'line' ? 'line' : 'bar'
  const cats = categories.value

  if (type === 'pie') {
    const first = series.value[0]
    return {
      color: PALETTE,
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: AXIS_COLOR, fontSize: 12 } },
      series: [
        {
          type: 'pie',
          radius: ['38%', '68%'],
          center: ['50%', '46%'],
          data: first.data.map((value, i) => ({ name: cats[i] || `#${i + 1}`, value })),
          label: { color: AXIS_COLOR, fontSize: 12 }
        }
      ]
    }
  }

  return {
    color: PALETTE,
    tooltip: { trigger: 'axis' },
    legend: series.value.length > 1
      ? { bottom: 0, textStyle: { color: AXIS_COLOR, fontSize: 12 } }
      : undefined,
    grid: { left: 8, right: 12, top: 12, bottom: series.value.length > 1 ? 32 : 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: cats,
      axisLine: { lineStyle: { color: SPLIT_LINE } },
      axisTick: { show: false },
      axisLabel: { color: AXIS_COLOR, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: SPLIT_LINE } },
      axisLabel: { color: AXIS_COLOR, fontSize: 11 }
    },
    series: series.value.map(s => ({
      name: s.name,
      type,
      data: s.data,
      smooth: type === 'line',
      // 散点=类别轴上的点图（分布/离群观察），柱图限宽
      ...(type === 'scatter' ? { symbolSize: 10 } : { barMaxWidth: 32 })
    }))
  }
}

onMounted(() => {
  if (hasData.value) render(buildOption())
})

watch(
  () => props.block,
  () => {
    if (hasData.value) render(buildOption())
  },
  { deep: true }
)
</script>

<style scoped lang="scss">
.ui-chart {
  width: 100%;
  height: 240px;
}

.ui-chart-empty {
  padding: 20px 0;
  font-size: 13px;
  color: $text-muted;
  text-align: center;
}
</style>
