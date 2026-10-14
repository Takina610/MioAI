import request from '@/utils/request'
import type { UsageStats, ContributionData, TokenTrendData, UsageProfile } from '@/types'

export interface UsageStatsQueryParams {
  startTime: string
  endTime: string
}

export function getUsageStats(
  type: 'agent' | 'knowledge' | 'mcp' | 'skill',
  params: UsageStatsQueryParams
): Promise<UsageStats> {
  return request({
    url: `/usage-stats/${type}`,
    method: 'get',
    params: params as unknown as Record<string, unknown>
  })
}

export function getAdminUsageStats(
  type: 'agent' | 'knowledge' | 'mcp' | 'skill',
  params: UsageStatsQueryParams
): Promise<UsageStats> {
  return request({
    url: `/admin/usage-stats/${type}`,
    method: 'get',
    params: params as unknown as Record<string, unknown>
  })
}

export function getUsageProfile(): Promise<UsageProfile> {
  return request({
    url: '/usage-stats/profile',
    method: 'get'
  })
}

export function getContribution(year?: number): Promise<ContributionData> {
  return request({
    url: '/usage-stats/contribution',
    method: 'get',
    params: year ? { year } : {}
  })
}

export function getTokenTrend(
  days: 7 | 30,
  dimension: 'model' | 'agent'
): Promise<TokenTrendData> {
  return request({
    url: '/usage-stats/token-trend',
    method: 'get',
    params: { days, dimension }
  })
}
