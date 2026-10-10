export interface SkillFile {
  path: string
  content: string
}

export interface Skill {
  id: number
  userId: number
  userName?: string
  name: string
  description?: string
  content?: string
  files?: SkillFile[]
  sourceUrl?: string
  status: number
  statusDesc?: string
  isPublic: number
  createTime?: string
  updateTime?: string
}

export interface SkillAddRequest {
  name: string
  description?: string
  content?: string
  files?: SkillFile[]
  isPublic?: number
  sourceUrl?: string
}

export interface SkillUpdateRequest {
  id: number
  name?: string
  description?: string
  content?: string
  files?: SkillFile[]
  status?: number
  isPublic?: number
}

export interface SkillQueryRequest {
  current: number
  pageSize: number
  name?: string
  status?: number
  isPublic?: number
  userId?: number
}

export interface GithubSkill {
  path: string
  name: string
  description?: string
  fileCount: number
  totalSize: number
}

export interface GithubSkillPreview {
  owner: string
  repo: string
  branch: string
  totalFound: number
  truncated: boolean
  skills: GithubSkill[]
}
