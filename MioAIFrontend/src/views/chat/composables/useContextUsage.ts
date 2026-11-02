import { onUnmounted, ref, watch } from 'vue'
import { getContextUsage } from '@/api/chat'
import type { ContextUsageData } from '@/types'

/**
 * 上下文容量指示器：会话/智能体/模型切换、轮次收尾刷新；轮次进行中每 5s 跟随步进。
 * 从 index.vue 抽出——轮次内 agent_message 的 WIP 行随每个模型步增量落库，
 * 干跑估算随之增长，轮询即可近似实时。窗口大小按当前模型解析。
 */
export function useContextUsage(options: {
  currentChatId: () => string | undefined
  agentId: () => number
  /** 当前模型的元数据快照（窗口大小来源），变化即触发刷新 */
  modelContext: () => unknown
  /** 当前是否在流式生成中 */
  isLoading: () => boolean
}) {
  const contextUsage = ref<ContextUsageData | null>(null)
  let usageReqSeq = 0
  let usagePollTimer: number | undefined

  async function refreshContextUsage(): Promise<void> {
    const chatId = options.currentChatId()
    if (!chatId) {
      contextUsage.value = null
      return
    }
    const seq = ++usageReqSeq
    try {
      const data = await getContextUsage(chatId, options.agentId(), options.modelContext() as never)
      // 只应用最新一次请求：轮询重叠/切换会话时丢弃过期响应
      if (seq === usageReqSeq && options.currentChatId() === chatId) {
        contextUsage.value = data
      }
    } catch {
      // 非关键指标：失败静默，指示器维持现状
    }
  }

  function stopUsagePolling(): void {
    if (usagePollTimer) {
      window.clearInterval(usagePollTimer)
      usagePollTimer = undefined
    }
  }

  function startUsagePolling(): void {
    stopUsagePolling()
    usagePollTimer = window.setInterval(refreshContextUsage, 5000)
  }

  watch(
    [() => options.currentChatId(), () => options.agentId(), () => options.modelContext()],
    () => refreshContextUsage()
  )

  watch(
    () => options.isLoading(),
    (now, was) => {
      if (now) {
        startUsagePolling()
      } else {
        stopUsagePolling()
        if (was) refreshContextUsage()
      }
    }
  )

  onUnmounted(stopUsagePolling)

  return { contextUsage, refreshContextUsage, stopUsagePolling }
}
