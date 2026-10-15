import { ref, watch, onMounted } from 'vue'
import {
  getContribution,
  getTokenTrend,
  getUsageProfile
} from '@/api/usageStats'
import type {
  ContributionData,
  TokenTrendData,
  UsageProfile
} from '@/types'

export interface TokenInsightsApis {
  contribution: (year?: number) => Promise<ContributionData>
  tokenTrend: (days: TokenRange, dimension: TokenDimension) => Promise<TokenTrendData>
  withProfile?: boolean
}

export type ContributionMode = 'daily' | 'weekly' | 'cumulative'
export type TokenDimension = 'model' | 'agent'
export type TokenRange = 7 | 30

/** Token 概览区：贡献图（三视图）+ Token 趋势/模型用量（维度与区间切换）+ 数据画像 */
export function useTokenInsights(apis?: TokenInsightsApis) {
  const api = apis ?? { contribution: getContribution, tokenTrend: getTokenTrend, withProfile: true }
  const profile = ref<UsageProfile | null>(null)
  const contribution = ref<ContributionData | null>(null)
  const contributionMode = ref<ContributionMode>('daily')
  const trend = ref<TokenTrendData | null>(null)
  const trendLoading = ref(false)
  const tokenRange = ref<TokenRange>(7)
  const tokenDimension = ref<TokenDimension>('model')

  async function fetchProfile(): Promise<void> {
    if (!api.withProfile) return
    try {
      profile.value = await getUsageProfile()
    } catch (e) {
      console.error('获取数据画像失败', e)
    }
  }

  async function fetchContribution(): Promise<void> {
    try {
      const year = new Date().getFullYear()
      // 热力图是滚动 12 个月窗口：合并当前年与上一年的逐日数据
      const [current, previous] = await Promise.all([api.contribution(year), api.contribution(year - 1)])
      const days = [...previous.days, ...current.days].sort((a, b) => a.date.localeCompare(b.date))
      contribution.value = { days, summary: current.summary }
    } catch (e) {
      console.error('获取贡献图失败', e)
    }
  }

  async function fetchTrend(): Promise<void> {
    trendLoading.value = true
    try {
      trend.value = await api.tokenTrend(tokenRange.value, tokenDimension.value)
    } catch (e) {
      console.error('获取 Token 趋势失败', e)
    } finally {
      trendLoading.value = false
    }
  }

  watch([tokenRange, tokenDimension], fetchTrend)

  onMounted(() => {
    fetchProfile()
    fetchContribution()
    fetchTrend()
  })

  return {
    profile,
    contribution,
    contributionMode,
    trend,
    trendLoading,
    tokenRange,
    tokenDimension,
    fetchTrend
  }
}
