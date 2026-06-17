import request from '@/utils/request'
import type { PageResponse, KnowledgeBase, KnowledgeBaseQueryRequest } from '@/types'
import type { Document, DocumentQueryRequest } from '@/api/knowledgeBase'

export interface KnowledgeAdminUpdateRequest {
  id: number
  name?: string
  description?: string
  status?: number
  isPublic?: number
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
