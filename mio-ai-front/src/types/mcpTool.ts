import type { PaginationParams } from './common'

export interface McpTool {
  id: number
  userId: number
  name: string
  description?: string
  serverName: string
  config?: string
  status: number
  statusDesc?: string
  isPublic: number
  usageCount: number
  lastUsedTime?: string
  createTime: string
  updateTime: string
}

export interface McpToolAddRequest {
  name: string
  description?: string
  serverName: string
  config?: string
  status?: number
  isPublic?: number
}

export interface McpToolUpdateRequest {
  id: number
  name?: string
  description?: string
  serverName?: string
  config?: string
  status?: number
  isPublic?: number
}

export interface McpToolQueryRequest extends PaginationParams {
  name?: string
  serverName?: string
  status?: number
  isPublic?: number
  userId?: number
}
