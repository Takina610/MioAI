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
  /** 流式传输异常中断（已有部分内容时置位，界面提示回答可能不完整） */
  interrupted?: boolean
}

export interface ChatSession {
  id: string
  title: string
  updateTime: Date
  hasMessage: boolean
}
