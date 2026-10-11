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
    method: 'post'
  })
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export function vectorizeKnowledgeFiles(kbId: number, token: string, force = false): EventSource {
  const url = `${BASE_URL}/knowledge-bases/create/vectorize/${kbId}?token=${encodeURIComponent(token)}${force ? '&force=true' : ''}`
  return new EventSource(url)
}

export interface Document {
  id: number
  kbId: number
  fileName: string
  fileType: string
  fileSize: number
  filePath: string
  status: number
  statusDesc: string
  createTime: string
  updateTime: string
}

export interface DocumentQueryRequest {
  current: number
  pageSize: number
  kbId?: number
  fileName?: string
  fileType?: string
  status?: number
  userId?: number
}

export function queryDocuments(data: DocumentQueryRequest): Promise<PageResponse<Document>> {
  return request({
    url: '/documents/list',
    method: 'post',
    data
  })
}

export function deleteDocument(id: number): Promise<boolean> {
  return request({
    url: `/documents/${id}`,
    method: 'delete'
  })
}

export interface SimilarityResult {
  id: string
  text: string
  score: number
  fileName: string
  docId?: number
  chunkIndex?: number
  matchType?: 'vector' | 'keyword' | 'rerank' | string
  metadata?: Record<string, unknown>
}

export interface SimilaritySearchRequest {
  query: string
  kbId?: number
  topK?: number
  threshold?: number
  method?: 'vector' | 'hybrid'
  enableRerank?: boolean
}

/**
 * 命中测试（POST body 版）：支持长查询与完整检索配置（混合检索/重排）
 */
export function similaritySearchV2(data: SimilaritySearchRequest): Promise<SimilarityResult[]> {
  return request({
    url: '/documents/similaritySearch',
    method: 'post',
    data
  })
}

export interface DocumentChunk {
  chunkId: string
  chunkIndex: number
  content: string
  length: number
  fileName: string
}

/**
 * 查看文档解析出的全部分块（切片）
 */
export function getDocumentChunks(id: number): Promise<DocumentChunk[]> {
  return request({
    url: `/documents/${id}/chunks`,
    method: 'get'
  })
}

/**
 * 单文档重新解析：状态重置为待处理，随后打开 vectorize SSE 流水线执行
 */
export function revectorizeDocument(id: number): Promise<boolean> {
  return request({
    url: `/documents/${id}/revectorize`,
    method: 'post'
  })
}
