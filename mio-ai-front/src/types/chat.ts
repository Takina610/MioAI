export interface ChatMessageRequest {
  chatId: string
  agentId: number
  content: string
}

export interface ChatVO {
  chatId: string
  message: string
  agentId: number
  userId: number
}
