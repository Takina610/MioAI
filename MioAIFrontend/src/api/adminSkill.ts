import request from '@/utils/request'
import type { PageResponse } from '@/types'
import type { Skill, SkillQueryRequest } from '@/types/skill'

export interface SkillAdminUpdateRequest {
  id: number
  name?: string
  description?: string
  status?: number
}

export function searchSkills(data: SkillQueryRequest): Promise<PageResponse<Skill>> {
  return request({
    url: '/admin/skills/search',
    method: 'post',
    data
  })
}

export function getSkillById(id: number): Promise<Skill> {
  return request({
    url: `/admin/skills/${id}`,
    method: 'get'
  })
}

export function updateSkill(data: SkillAdminUpdateRequest): Promise<boolean> {
  return request({
    url: '/admin/skills',
    method: 'put',
    data
  })
}

export function deleteSkill(id: number): Promise<boolean> {
  return request({
    url: `/admin/skills/${id}`,
    method: 'delete'
  })
}
