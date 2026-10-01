import request from '@/utils/request'
import type { PageResponse } from '@/types'
import type { Agent, AgentDetail, AgentQueryRequest } from '@/types'

export interface AgentAdminUpdateRequest {
  id: number
  name?: string
  description?: string
  systemPrompt?: string
  status?: number
  isPublic?: number
}

export function searchAgents(data: AgentQueryRequest): Promise<PageResponse<Agent>> {
  return request({
    url: '/admin/agents/search',
    method: 'post',
    data
  })
}

export function getAgentDetail(id: number): Promise<AgentDetail> {
  return request({
    url: `/admin/agents/${id}/detail`,
    method: 'get'
  })
}

export function updateAgent(data: AgentAdminUpdateRequest): Promise<boolean> {
  return request({
    url: '/admin/agents',
    method: 'put',
    data
  })
}

export function deleteAgent(id: number): Promise<boolean> {
  return request({
    url: `/admin/agents/${id}`,
    method: 'delete'
  })
}
