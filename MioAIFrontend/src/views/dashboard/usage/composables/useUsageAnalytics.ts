import { onMounted, ref, watch, type Ref } from 'vue'
import type { Dayjs } from 'dayjs'
import type { UsageAnalytics, UsageDayDetail } from '@/types'
import type { TabKey } from './useUsageStats'

export interface UsageAnalyticsQuery {
  startTime: string
  endTime: string
  userId?: number
  agentId?: number
}

export interface UsageAnalyticsApis {
  analytics: (type: TabKey, params: UsageAnalyticsQuery) => Promise<UsageAnalytics>
  dayDetail: (date: string, type: TabKey, userId?: number) => Promise<UsageDayDetail>
}

export interface UsageAnalyticsFilters {
  userId?: number
  agentId?: number
}

const DATETIME = 'YYYY-MM-DD HH:mm:ss'

/**
 * 使用记录深度分析：使用去向 / 使用构成 / 使用明细 + 日历单日下钻。
 * 与指标区共享同一 time range 与 tab；管理端通过 filters 追加用户/智能体过滤。
 */
export function useUsageAnalytics(
  apis: UsageAnalyticsApis,
  ctx: {
    type: Ref<TabKey>
    range: Ref<[Dayjs, Dayjs]>
    filters?: () => UsageAnalyticsFilters
  }
) {
  const analytics = ref<UsageAnalytics | null>(null)
  const loading = ref(false)
  const selectedDate = ref<string | null>(null)
  const dayDetail = ref<UsageDayDetail | null>(null)
  const dayLoading = ref(false)

  function queryParams(): UsageAnalyticsQuery {
    const [start, end] = ctx.range.value
    return {
      startTime: start.format(DATETIME),
      endTime: end.format(DATETIME),
      ...ctx.filters?.()
    }
  }

  async function fetchAnalytics(): Promise<void> {
    loading.value = true
    try {
      analytics.value = await apis.analytics(ctx.type.value, queryParams())
    } catch (e) {
      console.error('获取使用分析失败', e)
    } finally {
      loading.value = false
    }
  }

  async function fetchDay(date: string): Promise<void> {
    dayLoading.value = true
    try {
      dayDetail.value = await apis.dayDetail(date, ctx.type.value, ctx.filters?.().userId)
    } catch (e) {
      console.error('获取单日明细失败', e)
      dayDetail.value = null
    } finally {
      dayLoading.value = false
    }
  }

  /** 日历点击：选中日期并加载当日明细 */
  function selectDate(date: string): void {
    if (selectedDate.value === date) {
      clearDate()
      return
    }
    selectedDate.value = date
    void fetchDay(date)
  }

  function clearDate(): void {
    selectedDate.value = null
    dayDetail.value = null
  }

  /** 手动刷新（管理端改筛选后调用；单日明细一并刷新） */
  function refresh(): void {
    void fetchAnalytics()
    if (selectedDate.value) {
      void fetchDay(selectedDate.value)
    }
  }

  watch(ctx.type, () => {
    void fetchAnalytics()
    if (selectedDate.value) {
      void fetchDay(selectedDate.value)
    }
  })

  watch(ctx.range, () => void fetchAnalytics(), { deep: true })

  onMounted(() => void fetchAnalytics())

  return {
    analytics,
    loading,
    selectedDate,
    dayDetail,
    dayLoading,
    fetchAnalytics,
    selectDate,
    clearDate,
    refresh
  }
}
