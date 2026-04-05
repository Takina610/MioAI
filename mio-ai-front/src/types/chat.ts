export interface ChatMessageRequest {
  conversationId: string
  agentId: number
  content: string
}

export interface ChatVO {
  chatId: string
  message: string
  agentId: number
  userId: number
}
