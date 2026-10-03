export interface ChatMessageRequest {
  conversationId: string
  content: string
}

/** Agent 执行过程中的单次工具调用（tool_call 与 tool_result 按 id/顺序配对） */
export interface ToolEvent {
  /** 与 tool_result 配对的调用 id（端点未返回时为空，按顺序兜底配对） */
  id?: string
  tool: string
  args?: string
  status: 'running' | 'done'
  result?: string
}

/** 任务清单步骤（SSE plan 事件快照） */
export interface PlanStep {
  index: number
  description: string
  status: 'not_started' | 'in_progress' | 'done' | 'failed' | (string & {})
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
  /** 推理/思考增量累积文本 */
  thinking?: string
  /** 按时间序排列的工具调用（已配对结果） */
  tools?: ToolEvent[]
  /** 最近一次任务清单快照 */
  plan?: PlanStep[]
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
