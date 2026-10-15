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
  repoOwner?: string
  repoName?: string
  repoBranch?: string
  skillPath?: string
  docUrl?: string
  installed: number
  isPublic?: number
  owned?: boolean
  status: number
  statusDesc?: string
  createTime?: string
  updateTime?: string
}

export interface SkillQueryRequest {
  current: number
  pageSize: number
  name?: string
  status?: number
  userId?: number
  statusList?: number[]
  ownerName?: string
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

export interface SkillZipInstallResult {
  installedCount: number
  skipped: Array<{ name: string; reason: string }>
}

export interface SkillsShSkill {
  name: string
  owner: string
  repo: string
  installs: number
  repoUrl: string
  installed: boolean
}

export interface SkillsShSearchResult {
  query: string
  total: number
  skills: SkillsShSkill[]
}
