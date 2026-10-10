import request from '@/utils/request'

export function addAgentSkill(data: { agentId: number; skillId: number; enabled?: number }): Promise<number> {
  return request({
    url: '/agent-skill',
    method: 'post',
    data
  })
}

export function removeAgentSkillBySkillId(agentId: number, skillId: number): Promise<boolean> {
  return request({
    url: `/agent-skill/agent/${agentId}/skill/${skillId}`,
    method: 'delete'
  })
}

export function getSkillIdsByAgentId(agentId: number): Promise<number[]> {
  return request({
    url: `/agent-skill/agent/${agentId}`,
    method: 'get'
  })
}
