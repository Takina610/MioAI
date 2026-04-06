import type { PaginationParams } from './common'

export interface KnowledgeBase {
  id: number
  userId: number
  userName?: string
  name: string
  description?: string
  chunkSize: number
  chunkOverlap: number
  status: number
  statusDesc?: string
  isPublic: number
  documentCount: number
  totalChunks: number
  storageSize: number
  createTime: string
  updateTime: string
}

export interface KnowledgeBaseAddRequest {
  name: string
  description?: string
  chunkSize?: number
  chunkOverlap?: number
  status?: number
  isPublic?: number
}

export interface KnowledgeBaseUpdateRequest {
  id: number
  name?: string
  description?: string
  chunkSize?: number
  chunkOverlap?: number
  status?: number
  isPublic?: number
}

export interface KnowledgeBaseQueryRequest extends PaginationParams {
  name?: string
  status?: number
  userId?: number
}
