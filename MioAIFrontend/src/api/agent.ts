import request from '@/utils/request'

import type {
  Agent,
  AgentDetail,
  AgentAddRequest,
  AgentGroupCreateRequest,
  AgentGroupUpdateRequest,
  AgentUpdateRequest,
  AgentQueryRequest,
  AgentUserConfig,
  AgentUserConfigRequest,
  AgentVersion,
  PageResponse
} from '@/types'

export function addAgent(data: AgentAddRequest): Promise<number> {
  return request({
    url: '/agents',
    method: 'post',
    data
  })
}

/** 创建群聊智能体（成员智能体集合），返回群智能体ID */
export function createAgentGroup(data: AgentGroupCreateRequest): Promise<number> {
  return request({
    url: '/agents/group',
    method: 'post',
    data
  })
}

/** 编辑群聊：改名 + 整体替换成员（至少保留 2 个） */
export function updateAgentGroup(data: AgentGroupUpdateRequest): Promise<boolean> {
  return request({
    url: '/agents/group',
    method: 'put',
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

export function getAgentDetail(id: number): Promise<AgentDetail> {
  return request({
    url: `/agents/${id}/detail`,
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

export function publishAgent(agentId: number, data: AgentUpdateRequest): Promise<string> {
  return request({
    url: `/agents/${agentId}/publish`,
    method: 'post',
    data
  })
}

export function getAgentVersions(agentId: number): Promise<AgentVersion[]> {
  return request({
    url: `/agents/${agentId}/versions`,
    method: 'get'
  })
}

export function rollbackAgentVersion(agentId: number, versionId: number): Promise<string> {
  return request({
    url: `/agents/${agentId}/versions/${versionId}/rollback`,
    method: 'post'
  })
}

/** 置顶/取消置顶智能体（左轨置顶分组） */
export function pinAgent(id: number, pinned: boolean): Promise<boolean> {
  return request({
    url: `/agents/${id}/pin`,
    method: 'post',
    params: { pinned }
  })
}

/** 智能体左轨拖动排序：按最终顺序重写 sort_order */
export function reorderAgents(orderedIds: number[]): Promise<boolean> {
  return request({
    url: '/agents/reorder',
    method: 'post',
    data: { orderedIds }
  })
}

/** 从广场模板复制为我的草稿智能体，返回新智能体ID */
export function forkAgent(templateId: number): Promise<number> {
  return request({
    url: `/agents/${templateId}/fork`,
    method: 'post'
  })
}

export function getMyAgentConfig(agentId: number): Promise<AgentUserConfig | null> {
  return request({
    url: `/agents/${agentId}/my-config`,
    method: 'get'
  })
}

export function saveMyAgentConfig(agentId: number, data: AgentUserConfigRequest): Promise<AgentUserConfig> {
  return request({
    url: `/agents/${agentId}/my-config`,
    method: 'put',
    data
  })
}

/** 恢复内置默认：删除当前用户的个性化配置 */
export function resetMyAgentConfig(agentId: number): Promise<boolean> {
  return request({
    url: `/agents/${agentId}/my-config`,
    method: 'delete'
  })
}
