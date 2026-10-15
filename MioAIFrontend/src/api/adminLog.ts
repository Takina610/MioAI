import request from '@/utils/request'
import type { PageResponse } from '@/types'

export type AdminLogType = 'usage' | 'skill' | 'tool' | 'rag' | 'activity' | 'daily'

export interface AgentUsageLogRecord {
  id: number
  agentId: number
  userId: number | null
  conversationId: number | null
  model: string | null
  inputTokens: number
  outputTokens: number
  cachedTokens: number | null
  cost: number | null
  responseTime: number | null
  status: number
  errorMsg: string | null
  createTime: string
}

export interface SkillUsageLogRecord {
  id: number
  userId: number | null
  agentId: number
  conversationId: number | null
  skillId: number
  skillName: string
  createTime: string
}

export interface ToolCallLogRecord {
  id: number
  agentId: number
  userId: number | null
  toolId: number | null
  name: string
  conversationId: number | null
  inputParams: string | null
  outputResult: string | null
  status: number
  executionTime: number | null
  errorMsg: string | null
  createTime: string
}

export interface RagRetrievalLogRecord {
  id: number
  agentId: number
  userId: number | null
  kbId: number | null
  query: string
  retrievedChunks: string | null
  topK: number | null
  scoreThreshold: number | null
  responseTime: number | null
  createTime: string
}

export interface UserActivityLogRecord {
  id: number
  userId: number | null
  module: string
  action: string
  httpMethod: string
  path: string
  summary: string | null
  success: number
  errorMsg: string | null
  costMs: number | null
  ip: string | null
  createTime: string
}

export interface UserStatsDailyRecord {
  id: number
  userId: number
  statDate: string
  inputTokens: number
  outputTokens: number
  callCount: number
  chatDurationMs: number
}

export type AdminLogRecord =
  | AgentUsageLogRecord
  | SkillUsageLogRecord
  | ToolCallLogRecord
  | RagRetrievalLogRecord
  | UserActivityLogRecord
  | UserStatsDailyRecord

export interface AdminLogRow {
  data: AdminLogRecord
  userName: string | null
  userAvatar: string | null
  agentName: string | null
  agentAvatar: string | null
  groupName: string | null
}

export interface AdminLogQueryRequest {
  current?: number
  pageSize?: number
  keyword?: string
  userId?: number
  agentId?: number
  skillId?: number
  kbId?: number
  /** MCP 工具调用记录：按 MCP 工具 id 关联 */
  mcpId?: number
  conversationId?: string
  status?: number
  module?: string
  model?: string
  startDate?: string
  endDate?: string
}

export function searchLogs(type: AdminLogType, data: AdminLogQueryRequest): Promise<PageResponse<AdminLogRow>> {
  return request({
    url: `/admin/logs/${type}/search`,
    method: 'post',
    data
  })
}

export function deleteLog(type: AdminLogType, id: number): Promise<boolean> {
  return request({
    url: `/admin/logs/${type}/${id}`,
    method: 'delete'
  })
}
