import request from '@/utils/request'

export interface ChatConversation {
  id: number
  conversationId: string
  agentId: number
  userId: number
  title?: string
  createTime?: string
  updateTime?: string
}

export interface MessageVO {
  role: string
  content: string
  messageType: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export function getChatIds(): Promise<ChatConversation[]> {
  return request({
    url: '/memory/getChatIds',
    method: 'get'
  })
}

export function getChatIdsPage(current: number = 1, size: number = 10): Promise<PageResponse<ChatConversation>> {
  return request({
    url: '/memory/getChatIdsPage',
    method: 'get',
    params: { current, size }
  })
}

export function getChatHistory(
  conversationId: string,
  options?: { skipErrorMessage?: boolean }
): Promise<MessageVO[]> {
  return request({
    url: `/memory/getChatHistory/${conversationId}`,
    method: 'get',
    skipErrorMessage: options?.skipErrorMessage
  })
}

export function deleteChat(conversationId: string): Promise<boolean> {
  return request({
    url: `/memory/deleteChat/${conversationId}`,
    method: 'post'
  })
}

export function getConversation(conversationId: string): Promise<ChatConversation> {
  return request({
    url: `/memory/getConversation/${conversationId}`,
    method: 'get'
  })
}
