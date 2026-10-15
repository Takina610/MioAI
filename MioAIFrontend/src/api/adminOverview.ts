import request from '@/utils/request'
import type { NameValue, TokenTrendSeries } from '@/types'

export interface OverviewStats {
  users: number
  agents: number
  knowledgeBases: number
  mcpTools: number
  skills: number
  conversations: number
  messages: number
  chatFiles: number
  todayTurns: number
  todayInputTokens: number
  todayOutputTokens: number
  todayFailedTurns: number
  runningTasks: number
}

export function getOverviewStats(): Promise<OverviewStats> {
  return request({
    url: '/admin/overview/stats',
    method: 'get'
  })
}

export interface OverviewDayTrend {
  date: string
  turns: number
  tokens: number
  failedTurns: number
}

export interface OverviewCharts {
  days: number
  trend: OverviewDayTrend[]
  modelTrend: TokenTrendSeries[]
  agentDistribution: NameValue[]
  userRank: NameValue[]
}

export type TrendMetric = 'turns' | 'tokens' | 'failedTurns'

export function getOverviewCharts(days: number): Promise<OverviewCharts> {
  return request({
    url: '/admin/overview/charts',
    method: 'get',
    params: { days }
  })
}
