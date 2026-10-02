import { ref, computed, watch, onMounted, nextTick } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { getUsageStats } from '@/api/usageStats'
import type { UsageStats, MetricValue, TimePoint, NameValue } from '@/types'

export type TabKey = 'agent' | 'knowledge' | 'mcp'
export type LineMetric = 'count' | 'agentCount' | 'tokens' | 'avgTokens'

export interface MetricCardItem {
  key: string
  label: string
  displayValue: string | number
  unit: string
  trend: number
}

/** 各 Tab 的指标文案 */
export const metricLabelMap: Record<TabKey, Record<LineMetric, string>> = {
  agent: {
    count: '使用成功次数',
    agentCount: '使用智能体数',
    tokens: 'Token 总量',
    avgTokens: '平均单次请求 Token 量'
  },
  knowledge: {
    count: '检索次数',
    agentCount: '使用知识库数',
    tokens: 'Chunk 总量',
    avgTokens: '平均每次检索 Chunk 量'
  },
  mcp: {
    count: '调用次数',
    agentCount: '使用 MCP 工具数',
    tokens: '成功调用次数',
    avgTokens: '平均执行耗时'
  }
}

const metricCardLabelMap: Record<TabKey, Record<string, string>> = {
  agent: {
    agentCount: '使用智能体数',
    successCount: '使用成功次数',
    tokenCount: 'Token 总量',
    avgTokens: '平均单次请求 Token 量'
  },
  knowledge: {
    agentCount: '使用知识库数',
    successCount: '检索次数',
    tokenCount: 'Chunk 总量',
    avgTokens: '平均每次检索 Chunk 量'
  },
  mcp: {
    agentCount: '使用 MCP 工具数',
    successCount: '调用次数',
    tokenCount: '成功调用次数',
    avgTokens: '平均执行耗时'
  }
}

const metricCardUnitMap: Record<TabKey, Record<string, string>> = {
  agent: { agentCount: '个', successCount: '次', tokenCount: 'tokens', avgTokens: 'tokens' },
  knowledge: { agentCount: '个', successCount: '次', tokenCount: 'chunks', avgTokens: 'chunks' },
  mcp: { agentCount: '个', successCount: '次', tokenCount: '次', avgTokens: 'ms' }
}

/** 折线图各指标配色 */
export const lineMetricColorMap: Record<LineMetric, string> = {
  count: '#2aa1a9',
  agentCount: '#5470c6',
  tokens: '#91cc75',
  avgTokens: '#fac858'
}

/** 时间范围快捷选项 */
export const datePresets = [
  { label: '最近3小时', value: [dayjs().subtract(3, 'hour'), dayjs()] },
  { label: '最近6小时', value: [dayjs().subtract(6, 'hour'), dayjs()] },
  { label: '最近12小时', value: [dayjs().subtract(12, 'hour'), dayjs()] },
  { label: '最近24小时', value: [dayjs().subtract(24, 'hour'), dayjs()] },
  { label: '今天', value: [dayjs().startOf('day'), dayjs()] },
  { label: '昨天', value: [dayjs().subtract(1, 'day').startOf('day'), dayjs().subtract(1, 'day').endOf('day')] },
  { label: '最近7天', value: [dayjs().subtract(7, 'day'), dayjs()] },
  { label: '最近30天', value: [dayjs().subtract(30, 'day'), dayjs()] }
]

function formatNumberWithUnit(value: number): { value: string; unit: string } {
  if (value >= 1_000_000_000) {
    return { value: String(Math.round(value / 1_000_000_000)), unit: 'B' }
  }
  if (value >= 1_000_000) {
    return { value: String(Math.round(value / 1_000_000)), unit: 'M' }
  }
  if (value >= 1_000) {
    return { value: String(Math.round(value / 1_000)), unit: 'K' }
  }
  return { value: String(value), unit: '' }
}

/** 使用记录页：统计数据加载与指标卡片组装 */
export function useUsageStats() {
  const activeTab = ref<TabKey>('agent')
  const dateRange = ref<[Dayjs, Dayjs]>([dayjs().subtract(7, 'day'), dayjs()])
  const lineMetric = ref<LineMetric>('agentCount')
  const loading = ref(false)
  const metricCards = ref<MetricCardItem[]>([])
  const trendMap = ref<Record<string, TimePoint[]>>({})
  const prevTrendMap = ref<Record<string, TimePoint[]>>({})
  const pieData = ref<NameValue[]>([])

  const chartTitle = computed(() => `${metricLabelMap[activeTab.value][lineMetric.value]}分布`)

  const pieChartTitle = computed(() => {
    const map: Record<TabKey, string> = {
      agent: '智能体调用次数分布',
      knowledge: '知识库检索次数分布',
      mcp: 'MCP 调用次数分布'
    }
    return map[activeTab.value]
  })

  const rankTitle = computed(() => {
    const map: Record<TabKey, string> = {
      agent: '智能体调用排行榜',
      knowledge: '知识库检索排行榜',
      mcp: 'MCP 调用排行榜'
    }
    return map[activeTab.value]
  })

  const rankList = computed(() => {
    const max = Math.max(...pieData.value.map(d => d.value), 1)
    return pieData.value.map(item => ({
      id: item.id,
      name: item.name,
      avatar: item.avatar,
      value: item.value,
      percent: Math.round((item.value / max) * 100)
    }))
  })

  function buildMetricCards(metrics: Record<string, MetricValue>): void {
    const labels = metricCardLabelMap[activeTab.value]
    const units = metricCardUnitMap[activeTab.value]
    metricCards.value = ['agentCount', 'successCount', 'tokenCount', 'avgTokens'].map(key => {
      const rawValue = metrics[key]?.value ?? 0
      const isTokenLike = key === 'tokenCount' || key === 'avgTokens'
      const formatted = isTokenLike ? formatNumberWithUnit(rawValue) : null
      return {
        key,
        label: labels[key],
        displayValue: formatted ? `${formatted.value}${formatted.unit}` : rawValue,
        unit: units[key],
        trend: metrics[key]?.trend ?? 0
      }
    })
  }

  async function fetchStats(): Promise<void> {
    if (!dateRange.value || dateRange.value.length !== 2) return

    loading.value = true
    try {
      const [start, end] = dateRange.value
      const data = await getUsageStats(activeTab.value, {
        startTime: start.format('YYYY-MM-DD HH:mm:ss'),
        endTime: end.format('YYYY-MM-DD HH:mm:ss')
      })

      buildMetricCards(data.metrics)
      trendMap.value = data.trendMap || {}
      prevTrendMap.value = data.prevTrendMap || {}
      // 兼容旧后端：trendMap 缺失时用 trend 填充 count 指标
      if (Object.keys(trendMap.value).length === 0 && data.trend && data.trend.length > 0) {
        trendMap.value = { count: data.trend }
      }
      pieData.value = data.distribution || []
    } catch (e) {
      console.error('获取使用记录统计失败', e)
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    nextTick(fetchStats)
  })

  watch(activeTab, () => {
    lineMetric.value = 'agentCount'
    fetchStats()
  })

  return {
    activeTab,
    dateRange,
    loading,
    metricCards,
    trendMap,
    prevTrendMap,
    pieData,
    chartTitle,
    pieChartTitle,
    rankTitle,
    rankList,
    fetchStats,
    lineMetric
  }
}
