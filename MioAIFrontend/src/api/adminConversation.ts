import request from '@/utils/request'
import type { PageResponse } from '@/types'

export interface ConversationRecord {
  id: number
  userId: number | null
  conversationId: string
  agentId: number
  title: string
  pinned: number
  createTime: string
  updateTime: string
}

export interface ConversationRow {
  data: ConversationRecord
  userName: string | null
  userAvatar: string | null
  agentName: string | null
  agentAvatar: string | null
}

export interface ConversationQueryRequest {
  current?: number
  pageSize?: number
  keyword?: string
  userId?: number
  agentId?: number
  /** 归属用户名模糊 */
  ownerName?: string
  /** 智能体名称模糊 */
  agentName?: string
  /** 创建时间范围（yyyy-MM-dd） */
  startDate?: string
  endDate?: string
  pinned?: number
  /** 消息内容关键词（搜索消息正文后回溯会话） */
  messageKeyword?: string
}

export interface MessageRecord {
  id: number
  conversationId: string
  agentId: number | null
  userId: number | null
  role: 'user' | 'assistant' | string
  seq: number
  blocks: string
  plan: string | null
  durationMs: number | null
  groupSeq: number | null
  createTime: string
}

export interface ChatFileRecord {
  id: number
  conversationId: string
  userId: number | null
  agentId: number | null
  kind: number
  fileName: string
  path: string
  fileSize: number
  status: number
  createTime: string
}

export interface UsageConversationQuery {
  current?: number
  pageSize?: number
  /** 按知识库关联（检索记录） */
  kbId?: number
  /** 按 MCP 关联（工具调用记录） */
  mcpId?: number
  /** 按技能关联（使用记录） */
  skillId?: number
}

export interface UsageConversationRow {
  data: {
    conversationId: string
    exists: boolean
    title: string
    agentId: number | null
    pinned: number | null
    updateTime: string | null
    userId: number | null
    lastUsedTime: string | null
  }
  userName: string | null
  userAvatar: string | null
  agentName: string | null
  agentAvatar: string | null
}

/** 按使用记录聚合关联会话：知识库（检索）/ MCP（工具调用）/ 技能（使用） */
export function searchConversationsByUsage(data: UsageConversationQuery): Promise<PageResponse<UsageConversationRow>> {
  return request({
    url: '/admin/conversations/by-usage',
    method: 'post',
    data
  })
}

export function searchConversations(data: ConversationQueryRequest): Promise<PageResponse<ConversationRow>> {
  return request({
    url: '/admin/conversations/search',
    method: 'post',
    data
  })
}

export function getConversationMessages(
  conversationId: string,
  params: { current?: number; pageSize?: number }
): Promise<PageResponse<MessageRecord>> {
  return request({
    url: `/admin/conversations/${conversationId}/messages`,
    method: 'get',
    params
  })
}

export function deleteConversation(conversationId: string): Promise<boolean> {
  return request({
    url: `/admin/conversations/${conversationId}`,
    method: 'delete'
  })
}

export function deleteConversationMessage(conversationId: string, messageId: number): Promise<boolean> {
  return request({
    url: `/admin/conversations/${conversationId}/messages/${messageId}`,
    method: 'delete'
  })
}

export function getConversationFiles(conversationId: string): Promise<ChatFileRecord[]> {
  return request({
    url: `/admin/conversations/${conversationId}/files`,
    method: 'get'
  })
}

export function deleteConversationFile(fileId: number): Promise<boolean> {
  return request({
    url: `/admin/conversations/files/${fileId}`,
    method: 'delete'
  })
}
