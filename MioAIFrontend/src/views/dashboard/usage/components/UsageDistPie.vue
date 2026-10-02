<template>
  <div ref="chartRef" class="chart-container"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import type { NameValue } from '@/types'
import { useEChart } from '../composables/useEChart'

const props = withDefaults(defineProps<{
  data: NameValue[]
}>(), {})

const chartRef = ref<HTMLDivElement | null>(null)
const { render } = useEChart(chartRef)

function renderChart(): void {
  if (props.data.length === 0) {
    render({
      title: {
        text: '暂无数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#999', fontSize: 16, fontWeight: 'normal' }
      },
      series: []
    })
    return
  }

  render({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '0%', left: 'center', icon: 'circle' },
    series: [{
      name: '调用次数',
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 6,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: { show: false, position: 'center' },
      emphasis: {
        label: {
          show: true,
          fontSize: 16,
          fontWeight: 'bold',
          formatter: '{b}\n{c}'
        }
      },
      labelLine: { show: false },
      data: props.data
    }]
  })
}

watch(() => props.data, () => {
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
