import request from '@/utils/request'
import type { PageResponse } from '@/types'

export type AdminRuntimeType = 'group-members' | 'versions' | 'user-configs' | 'tasks'

export interface GroupMemberRecord {
  id: number
  groupAgentId: number
  memberAgentId: number
  createTime: string
}

export interface AgentVersionRecord {
  id: number
  agentId: number
  version: string
  name: string
  description: string | null
  avatar: string | null
  icon: string | null
  systemPrompt: string | null
  bindingsJson: string | null
  rollbackFrom: string | null
  userId: number | null
  createTime: string
}

export interface AgentUserConfigRecord {
  id: number
  userId: number
  agentId: number
  systemPrompt: string | null
  icon: string | null
  isPublic: number
  createTime: string
  updateTime: string
}

export interface BackgroundTaskRecord {
  taskId: string
  chatId: string
  type: string
  description: string | null
  status: string
  exitCode: number | null
  output: string | null
  detached: number
  startedAt: number | null
  finishedAt: number | null
  deadlineAt: number | null
  createTime: string
}

export type AdminRuntimeRecord =
  | GroupMemberRecord
  | AgentVersionRecord
  | AgentUserConfigRecord
  | BackgroundTaskRecord

export interface AdminRuntimeRow {
  data: AdminRuntimeRecord
  userName: string | null
  userAvatar: string | null
  agentName: string | null
  agentAvatar: string | null
  /** bot-icon 配置 JSON */
  agentIcon?: string | null
  groupName: string | null
}

export interface AdminRuntimeQueryRequest {
  current?: number
  pageSize?: number
  keyword?: string
  userId?: number
  agentId?: number
  groupAgentId?: number
  memberAgentId?: number
  conversationId?: string
  status?: number
  taskStatus?: string
  type?: string
}

export function searchRuntime(
  type: AdminRuntimeType,
  data: AdminRuntimeQueryRequest
): Promise<PageResponse<AdminRuntimeRow>> {
  return request({
    url: `/admin/runtime/${type}/search`,
    method: 'post',
    data
  })
}

export function deleteRuntime(type: AdminRuntimeType, id: number | string): Promise<boolean> {
  return request({
    url: `/admin/runtime/${type}/${id}`,
    method: 'delete'
  })
}

/** 添加群聊成员（群 type=2 + 成员 type=1） */
export function addGroupMember(groupAgentId: number, memberAgentId: number): Promise<number> {
  return request({
    url: '/admin/runtime/group-members',
    method: 'post',
    data: { groupAgentId, memberAgentId }
  })
}
