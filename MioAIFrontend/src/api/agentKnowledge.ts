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
