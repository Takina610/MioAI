import request from '@/utils/request'
import type { PageResponse } from '@/types'
import type {
  Skill,
  SkillAddRequest,
  SkillUpdateRequest,
  SkillQueryRequest,
  GithubSkill,
  GithubSkillPreview
} from '@/types/skill'

export function addSkill(data: SkillAddRequest): Promise<number> {
  return request({
    url: '/skills',
    method: 'post',
    data,
    timeout: 300000
  })
}

export function updateSkill(data: SkillUpdateRequest): Promise<boolean> {
  return request({
    url: '/skills',
    method: 'put',
    data
  })
}

export function deleteSkill(id: number): Promise<boolean> {
  return request({
    url: `/skills/${id}`,
    method: 'delete'
  })
}

export function getSkillById(id: number): Promise<Skill> {
  return request({
    url: `/skills/${id}`,
    method: 'get'
  })
}

export function querySkills(data: SkillQueryRequest): Promise<PageResponse<Skill>> {
  return request({
    url: '/skills/list',
    method: 'post',
    data
  })
}

export function getPublicSkills(params?: { current?: number; size?: number }): Promise<PageResponse<Skill>> {
  return request({
    url: '/skills/market',
    method: 'get',
    params
  })
}

export function previewGithubSkills(url: string): Promise<GithubSkillPreview> {
  return request({
    url: '/skills/github/preview',
    method: 'post',
    data: { url }
  })
}

export function importGithubSkills(url: string, skillPaths: string[]): Promise<GithubSkill[]> {
  return request({
    url: '/skills/github/import',
    method: 'post',
    data: { url, skillPaths },
    timeout: 300000
  })
}
