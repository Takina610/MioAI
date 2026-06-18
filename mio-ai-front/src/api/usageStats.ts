import request from '@/utils/request'
import type { UsageStats } from '@/types'

export interface UsageStatsQueryParams {
  startTime: string
  endTime: string
}

export function getUsageStats(
  type: 'agent' | 'knowledge' | 'mcp',
  params: UsageStatsQueryParams
): Promise<UsageStats> {
  return request({
    url: `/usage-stats/${type}`,
    method: 'get',
    params: params as unknown as Record<string, unknown>
  })
}
