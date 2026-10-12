import request from '@/utils/request'

export interface AgentMemoryFile {
  name: string
  size: number
  mtime: number
  index: boolean
}

export function listAgentMemory(agentId: number): Promise<AgentMemoryFile[]> {
  return request({
    url: `/agent-memory/${agentId}`,
    method: 'get'
  })
}

export function getAgentMemoryContent(agentId: number, name: string): Promise<{ name: string; content: string }> {
  return request({
    url: `/agent-memory/${agentId}/content`,
    method: 'get',
    params: { name }
  })
}

export function deleteAgentMemory(agentId: number, name: string): Promise<boolean> {
  return request({
    url: `/agent-memory/${agentId}`,
    method: 'delete',
    params: { name }
  })
}
