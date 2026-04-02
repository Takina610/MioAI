import request from '@/utils/request'
import type {
  Agent,
  AgentAddRequest,
  AgentUpdateRequest,
  AgentQueryRequest,
  PageResponse
} from '@/types'

export function addAgent(data: AgentAddRequest): Promise<number> {
  return request({
    url: '/agents',
    method: 'post',
    data
  })
}

export function updateAgent(data: AgentUpdateRequest): Promise<boolean> {
  return request({
    url: '/agents',
    method: 'put',
    data
  })
}

export function deleteAgent(id: number): Promise<boolean> {
  return request({
    url: `/agents/${id}`,
    method: 'delete'
  })
}

export function getAgentById(id: number): Promise<Agent> {
  return request({
    url: `/agents/${id}`,
    method: 'get'
  })
}

export function queryAgents(data: AgentQueryRequest): Promise<PageResponse<Agent>> {
  return request({
    url: '/agents/list',
    method: 'post',
    data
  })
}

export function getPublicAgents(params?: { current?: number; size?: number }): Promise<PageResponse<Agent>> {
  return request({
    url: '/agents/market',
    method: 'get',
    params
  })
}
