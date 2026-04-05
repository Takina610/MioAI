import request from '@/utils/request'
import type {
  KnowledgeBase,
  KnowledgeBaseAddRequest,
  KnowledgeBaseUpdateRequest,
  KnowledgeBaseQueryRequest,
  PageResponse
} from '@/types'

export function addKnowledgeBase(data: KnowledgeBaseAddRequest): Promise<number> {
  return request({
    url: '/knowledge-bases',
    method: 'post',
    data
  })
}

export function updateKnowledgeBase(data: KnowledgeBaseUpdateRequest): Promise<boolean> {
  return request({
    url: '/knowledge-bases',
    method: 'put',
    data
  })
}

export function deleteKnowledgeBase(id: number): Promise<boolean> {
  return request({
    url: `/knowledge-bases/${id}`,
    method: 'delete'
  })
}

export function getKnowledgeBaseById(id: number): Promise<KnowledgeBase> {
  return request({
    url: `/knowledge-bases/${id}`,
    method: 'get'
  })
}

export function queryKnowledgeBases(data: KnowledgeBaseQueryRequest): Promise<PageResponse<KnowledgeBase>> {
  return request({
    url: '/knowledge-bases/list',
    method: 'post',
    data
  })
}

export function getPublicKnowledgeBases(params?: { current?: number; size?: number }): Promise<PageResponse<KnowledgeBase>> {
  return request({
    url: '/knowledge-bases/market',
    method: 'get',
    params
  })
}

export interface UploadResult {
  docId: number
  fileName: string
  fileUrl: string
  fileSize: number
  status: 'success' | 'error'
  errorMsg?: string
}

export function uploadKnowledgeFiles(kbId: number, files: File[]): Promise<UploadResult[]> {
  const formData = new FormData()
  files.forEach(file => {
    formData.append('files', file)
  })
  return request({
    url: `/knowledge-bases/create/upload/${kbId}`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function cancelKnowledgeCreation(kbId: number): Promise<boolean> {
  return request({
    url: `/knowledge-bases/create/cancel/${kbId}`,
    method: 'delete'
  })
}
