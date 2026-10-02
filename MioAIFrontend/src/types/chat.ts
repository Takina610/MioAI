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

/** 流式回复的分段（thinking/action 来自 MioManus 多步执行过程） */
export interface MessageSegment {
  content: string
  type?: 'thinking' | 'action' | 'final'
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createTime: Date
  segments?: MessageSegment[]
}

export interface ChatSession {
  id: string
  title: string
  updateTime: Date
  hasMessage: boolean
}
