import request from '@/utils/request'
import type {
  UsageStats,
  ContributionData,
  TokenTrendData,
  UsageProfile,
  UsageAnalytics,
  UsageDayDetail
} from '@/types'

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
  params: UsageStatsQueryParams & { userId?: number; agentId?: number }
): Promise<UsageStats> {
  return request({
    url: `/admin/usage-stats/${type}`,
    method: 'get',
    params: params as unknown as Record<string, unknown>
  })
}

/** 全用户日历热力图（管理端） */
export function getAdminContribution(year?: number): Promise<ContributionData> {
  return request({
    url: '/admin/usage-stats/contribution',
    method: 'get',
    params: year ? { year } : undefined
  })
}

/** 全用户 Token 趋势：模型/智能体维度（管理端） */
export function getAdminTokenTrend(days: 7 | 30, dimension: 'model' | 'agent'): Promise<TokenTrendData> {
  return request({
    url: '/admin/usage-stats/token-trend',
    method: 'get',
    params: { days, dimension }
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

/** 使用去向 / 使用构成 / 使用明细（当前用户） */
export function getUsageAnalytics(
  type: string,
  params: UsageStatsQueryParams
): Promise<UsageAnalytics> {
  return request({
    url: `/usage-stats/analytics/${type}`,
    method: 'get',
    params: params as unknown as Record<string, unknown>
  })
}

/** 使用去向 / 使用构成 / 使用明细（管理端，全用户） */
export function getAdminUsageAnalytics(
  type: string,
  params: UsageStatsQueryParams & { userId?: number; agentId?: number }
): Promise<UsageAnalytics> {
  return request({
    url: `/admin/usage-stats/analytics/${type}`,
    method: 'get',
    params: params as unknown as Record<string, unknown>
  })
}

/** 日历点击某天的明细（当前用户） */
export function getUsageDayDetail(date: string, type: string): Promise<UsageDayDetail> {
  return request({
    url: '/usage-stats/day-detail',
    method: 'get',
    params: { date, type }
  })
}

/** 日历点击某天的明细（管理端，可按用户过滤） */
export function getAdminUsageDayDetail(
  date: string,
  type: string,
  userId?: number
): Promise<UsageDayDetail> {
  return request({
    url: '/admin/usage-stats/day-detail',
    method: 'get',
    params: { date, type, userId }
  })
}
