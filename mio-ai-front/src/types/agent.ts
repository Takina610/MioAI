import type { PaginationParams } from './common'

export interface Agent {
  id: number
  userId: number
  name: string
  description?: string
  avatar?: string
  type: number
  typeDesc?: string
  systemPrompt?: string
  status: number
  statusDesc?: string
  isPublic: number
  usageCount: number
  version?: string
  createTime: string
  updateTime: string
}

export interface AgentAddRequest {
  name: string
  description?: string
  avatar?: string
  type?: number
  systemPrompt?: string
  status?: number
  isPublic?: number
  version?: string
}

export interface AgentUpdateRequest {
  id: number
  name?: string
  description?: string
  avatar?: string
  type?: number
  systemPrompt?: string
  status?: number
  isPublic?: number
  version?: string
}

export interface AgentQueryRequest extends PaginationParams {
  name?: string
  type?: number
  status?: number
  isPublic?: number
  userId?: number
}
