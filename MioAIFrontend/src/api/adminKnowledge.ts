import request from '@/utils/request'
import type { PageResponse, KnowledgeBase, KnowledgeBaseQueryRequest } from '@/types'
import type { Document, DocumentQueryRequest } from '@/api/knowledgeBase'

export interface KnowledgeAdminUpdateRequest {
  id: number
  name?: string
  description?: string
  status?: number
  isPublic?: number
  chunkSize?: number
  chunkOverlap?: number
}

export interface KnowledgeAdminCreateRequest {
  name: string
  description?: string
  isPublic?: number
  chunkSize?: number
  chunkOverlap?: number
}

export function searchKnowledgeBases(data: KnowledgeBaseQueryRequest): Promise<PageResponse<KnowledgeBase>> {
  return request({
    url: '/admin/knowledge/search',
    method: 'post',
    data
  })
}

export function getKnowledgeBaseById(id: number): Promise<KnowledgeBase> {
  return request({
    url: `/admin/knowledge/${id}`,
    method: 'get'
  })
}

export function listDocuments(kbId: number, data: DocumentQueryRequest): Promise<PageResponse<Document>> {
  return request({
    url: `/admin/knowledge/${kbId}/documents`,
    method: 'post',
    data
  })
}

export function updateKnowledgeBase(data: KnowledgeAdminUpdateRequest): Promise<boolean> {
  return request({
    url: '/admin/knowledge',
    method: 'put',
    data
  })
}

export function deleteKnowledgeBase(id: number): Promise<boolean> {
  return request({
    url: `/admin/knowledge/${id}`,
    method: 'delete'
  })
}

export function deleteAdminDocument(kbId: number, docId: number): Promise<boolean> {
  return request({
    url: `/admin/knowledge/${kbId}/documents/${docId}`,
    method: 'delete'
  })
}

export function createKnowledgeBase(data: KnowledgeAdminCreateRequest): Promise<number> {
  return request({
    url: '/admin/knowledge',
    method: 'post',
    data
  })
}

/** 管理员上传文档（落台账，待向量化） */
export function uploadAdminDocuments(kbId: number, files: File[]): Promise<unknown> {
  const form = new FormData()
  files.forEach(f => form.append('files', f))
  return request({
    url: `/admin/knowledge/${kbId}/documents/upload`,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 管理员触发向量化（SSE 进度，EventSource 无法带 header，token 走 query） */
export function adminVectorizeFiles(kbId: number, token: string, force = false): EventSource {
  return new EventSource(
    `/api/admin/knowledge/${kbId}/vectorize?token=${encodeURIComponent(token)}&force=${force}`
  )
}
