import request from '@/utils/request'

export interface AgentKnowledgeAddRequest {
  agentId: number
  kbId: number
  retrievalConfig?: string
  enabled?: number
}

export function addAgentKnowledge(data: AgentKnowledgeAddRequest): Promise<number> {
  return request({
    url: '/agent-knowledge',
    method: 'post',
    data
  })
}

export function removeAgentKnowledgeByKbId(agentId: number, kbId: number): Promise<boolean> {
  return request({
    url: `/agent-knowledge/agent/${agentId}/kb/${kbId}`,
    method: 'delete'
  })
}

export function getKbIdsByAgentId(agentId: number): Promise<number[]> {
  return request({
    url: `/agent-knowledge/agent/${agentId}`,
    method: 'get'
  })
}

export interface AgentKnowledgeBinding {
  id: number
  agentId: number
  kbId: number
  kbName: string
  topK: number
  threshold: number
  method: 'vector' | 'hybrid' | string
  enableRerank: boolean
  rerankTopN: number
  enabled: number
  createTime: string
}

/**
 * 智能体的全部知识库绑定详情（含解析后的检索配置）
 */
export function getAgentKbBindings(agentId: number): Promise<AgentKnowledgeBinding[]> {
  return request({
    url: `/agent-knowledge/agent/${agentId}/bindings`,
    method: 'get'
  })
}

export interface AgentKnowledgeUpdateRequest {
  topK?: number
  threshold?: number
  method?: 'vector' | 'hybrid'
  enableRerank?: boolean
  rerankTopN?: number
  enabled?: number
}

/**
 * 更新绑定的检索配置与启用开关
 */
export function updateAgentKnowledgeBinding(id: number, data: AgentKnowledgeUpdateRequest): Promise<boolean> {
  return request({
    url: `/agent-knowledge/${id}`,
    method: 'put',
    data
  })
}
