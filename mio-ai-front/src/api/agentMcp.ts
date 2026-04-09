import request from '@/utils/request'

export interface AgentMcpAddRequest {
  agentId: number
  mcpId: number
  enabled?: number
  configOverride?: string
}

export function addAgentMcp(data: AgentMcpAddRequest): Promise<number> {
  return request({
    url: '/agent-mcp',
    method: 'post',
    data
  })
}

export function removeAgentMcpByMcpId(agentId: number, mcpId: number): Promise<boolean> {
  return request({
    url: `/agent-mcp/agent/${agentId}/mcp/${mcpId}`,
    method: 'delete'
  })
}

export function getMcpIdsByAgentId(agentId: number): Promise<number[]> {
  return request({
    url: `/agent-mcp/agent/${agentId}`,
    method: 'get'
  })
}
