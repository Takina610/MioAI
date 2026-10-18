import { onMounted, onUnmounted, type Ref } from 'vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import type { EChartsOption } from 'echarts'

/** echarts 实例生命周期管理：初始化、渲染、事件、自适应、销毁 */
export function useEChart(containerRef: Ref<HTMLElement | null>) {
  let chart: ECharts | null = null
  const pending: Array<{ event: string; handler: (params: never) => void }> = []

  function ensure(): ECharts | null {
    if (!chart && containerRef.value) {
      chart = echarts.init(containerRef.value)
      for (const entry of pending) {
        chart.on(entry.event, entry.handler as never)
      }
    }
    return chart
  }

  function render(option: EChartsOption): void {
    const instance = ensure()
    if (!instance) return
    instance.clear()
    instance.setOption(option)
    instance.resize()
    // 卡片/网格布局在同一帧内还会变化（如 v-show 刚切为可见），下一帧再量一次
    requestAnimationFrame(() => instance.resize())
  }

  /** 绑定图表事件（首次 render 前调用会缓存，初始化后统一挂载） */
  function on(event: string, handler: (params: never) => void): void {
    const instance = ensure()
    if (instance) {
      instance.on(event, handler as never)
    } else {
      pending.push({ event, handler })
    }
  }

  function resize(): void {
    chart?.resize()
  }

  /** 直接访问实例（交互类操作如清除框选需要走 ECharts action） */
  function instance(): ECharts | null {
    return chart
  }

  let observer: ResizeObserver | null = null

  onMounted(() => {
    window.addEventListener('resize', resize)
    if (containerRef.value) {
      observer = new ResizeObserver(() => chart?.resize())
      observer.observe(containerRef.value)
    }
  })

  onUnmounted(() => {
    window.removeEventListener('resize', resize)
    observer?.disconnect()
    observer = null
    chart?.dispose()
    chart = null
  })

  return { render, on, resize, instance }
}
