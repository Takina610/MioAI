export interface ChatMessageRequest {
  conversationId: string
  agentId: number
  content: string
}

/** Agent 执行过程中的单次工具调用（tool_use 与 tool_result 按 id/顺序配对） */
export interface ToolEvent {
  /** 与 tool_result 配对的调用 id（端点未返回时为空，按顺序兜底配对） */
  id?: string
  tool: string
  args?: string
  status: 'running' | 'done'
  result?: string
}

/** AskUserQuestion 的单个选项 */
export interface QuestionOption {
  label: string
  description: string
  preview?: string
}

/** AskUserQuestion 的单个问题 */
export interface QuestionItem {
  question: string
  header: string
  multiSelect?: boolean
  options: QuestionOption[]
}

/** 提交后的答案（按问题下标） */
export interface QuestionAnswer {
  index: number
  selections: string[]
}

/**
 * 消息内按时间序排列的内容块（ZCode 风格：文本/思考/工具顺着流式顺序显示，
 * 不再把过程信息堆在回答上方）。
 */
export type MessageBlock =
  | { type: 'text'; text: string }
  | { type: 'thinking'; text: string; startedAt?: number; durationMs?: number }
  | (ToolEvent & { type: 'tool' })
  | {
      type: 'question'
      id: string
      status: 'pending' | 'answered'
      questions: QuestionItem[]
      answers?: QuestionAnswer[]
    }

/** 任务清单步骤（SSE plan 事件快照，渲染于输入框上方） */
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
  /** 按时间序的内容块（流式渲染用；历史消息无块时回退渲染 content） */
  blocks?: MessageBlock[]
  /** 最近一次任务清单快照（融合在输入框上方展示） */
  plan?: PlanStep[]
  /** 本条回复耗时（毫秒，完成后展示） */
  durationMs?: number
  /** 流式传输异常中断（已有部分内容时置位，界面提示回答可能不完整） */
  interrupted?: boolean
  /** 瞬态失败自动重试中的提示（后端 retry 事件；下一段内容到达即清除） */
  retryNotice?: string
  /** 本消息的历史版本（旧→新，不含当前正文）：编辑/重新生成时旧回复存档于此 */
  history?: ChatMessage[]
  /** 当前显示的版本号（从 1 计数，最大 = history.length + 1 即最新正文） */
  activeVersion?: number
  /** 回复完成后的用量统计 */
  usage?: ChatMessageUsage
}

export interface ChatSession {
  id: string
  title: string
  updateTime: Date
  hasMessage: boolean
}
