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

/** 流式回复的分段（SSE 信封 type 对应；未知类型按思考步骤兜底展示） */
export interface MessageSegment {
  content: string
  type?: 'thinking' | 'action' | 'final' | 'tool_call' | 'tool_result' | 'error' | (string & {})
  /** tool_call / tool_result 的工具名 */
  tool?: string
  /** tool_call 的参数摘要 */
  args?: string
}

/** SSE usage 尾块：本轮回复的用量统计 */
export interface ChatMessageUsage {
  inputTokens?: number
  outputTokens?: number
  durationMs?: number
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createTime: Date
  segments?: MessageSegment[]
  /** 流式传输异常中断（已有部分内容时置位，界面提示回答可能不完整） */
  interrupted?: boolean
  /** 回复完成后的用量统计 */
  usage?: ChatMessageUsage
}

export interface ChatSession {
  id: string
  title: string
  updateTime: Date
  hasMessage: boolean
}
