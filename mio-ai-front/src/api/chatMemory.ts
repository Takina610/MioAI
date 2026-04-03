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

export function getChatIds(agentId: string): Promise<ChatConversation[]> {
  return request({
    url: `/memory/getChatIds/${agentId}`,
    method: 'get'
  })
}

export function getChatHistory(conversationId: string): Promise<MessageVO[]> {
  return request({
    url: `/memory/getChatHistory/${conversationId}`,
    method: 'get'
  })
}

export function deleteChat(conversationId: string): Promise<boolean> {
  return request({
    url: `/memory/deleteChat/${conversationId}`,
    method: 'post'
  })
}
