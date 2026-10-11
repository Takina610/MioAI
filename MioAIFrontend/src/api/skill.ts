import request from '@/utils/request'
import type { PageResponse } from '@/types'
import type {
  Skill,
  SkillQueryRequest,
  GithubSkill,
  GithubSkillPreview,
  SkillZipInstallResult,
  SkillsShSearchResult
} from '@/types/skill'

export function deleteSkill(id: number): Promise<boolean> {
  return request({
    url: `/skills/${id}`,
    method: 'delete'
  })
}

export function querySkills(data: SkillQueryRequest): Promise<PageResponse<Skill>> {
  return request({
    url: '/skills/list',
    method: 'post',
    data
  })
}

export function installSkillZip(file: File): Promise<SkillZipInstallResult> {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/skills/zip',
    method: 'post',
    data: formData,
    // 实例默认 Content-Type 是 application/json，会把 FormData 盖成 JSON 头导致后端判定非 multipart
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function installSkill(id: number): Promise<Skill> {
  return request({
    url: `/skills/${id}/install`,
    method: 'post',
    timeout: 120000
  })
}

export function uninstallSkill(id: number): Promise<Skill> {
  return request({
    url: `/skills/${id}/uninstall`,
    method: 'post'
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
    timeout: 120000
  })
}

export function searchSkillsSh(query: string, limit = 20, offset = 0): Promise<SkillsShSearchResult> {
  return request({
    url: '/skills/skillssh/search',
    method: 'post',
    data: { query, limit, offset },
    timeout: 30000
  })
}

export function installSkillsSh(owner: string, repo: string, skillId: string): Promise<number> {
  return request({
    url: '/skills/skillssh/install',
    method: 'post',
    data: { owner, repo, skillId },
    timeout: 120000
  })
}
