import { onMounted, onUnmounted, type Ref } from 'vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import type { EChartsOption } from 'echarts'

/** echarts 实例生命周期管理：初始化、渲染、自适应、销毁 */
export function useEChart(containerRef: Ref<HTMLElement | null>) {
  let chart: ECharts | null = null

  function render(option: EChartsOption): void {
    if (!chart && containerRef.value) {
      chart = echarts.init(containerRef.value)
    }
    if (!chart) return
    chart.clear()
    chart.resize()
    chart.setOption(option)
  }

  function resize(): void {
    chart?.resize()
  }

  onMounted(() => {
    window.addEventListener('resize', resize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', resize)
    chart?.dispose()
    chart = null
  })

  return { render }
}
