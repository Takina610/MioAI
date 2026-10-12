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
  /** 模型自定的短标签（A/B/C、1/2/3 或紧凑词），用户可能以引用它作答 */
  key?: string
  label: string
  description: string
  preview?: string
}

/** AskUserQuestion 的单个问题 */
export interface QuestionItem {
  question: string
  header: string
  /** 兼容旧数据的遗留字段：问答恒为单选（自定义回答也是单选成员之一） */
  multiSelect?: boolean
  options: QuestionOption[]
}

/** 知识库摘要行的单条命中片段（snippet 已由后端截断为可读摘要） */
export interface KbContextHit {
  fileName?: string
  snippet: string
  score?: number
}

/** 知识库摘要行的单库分组 */
export interface KbContextGroup {
  kbId: number
  kbName: string
  hits: KbContextHit[]
}

/** MCP 摘要行的单个工具（真实注册名/描述） */
export interface McpContextTool {
  name: string
  description?: string
}

/** MCP 摘要行的单个服务 */
export interface McpContextServer {
  id?: number
  name: string
  description?: string
  tools: McpContextTool[]
}

/** 技能摘要行的单个技能 */
export interface SkillContextItem {
  name: string
  description?: string
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
  | {
      /** 附件块：input=用户随消息上传，output=Agent 本轮产出（可下载） */
      type: 'attachments'
      side: 'input' | 'output'
      items: AttachmentItem[]
    }
  | {
      /** 知识库检索摘要：本轮实际注入的命中，按库分组（可展开看片段） */
      type: 'kb'
      groups: KbContextGroup[]
    }
  | {
      /** MCP 装配摘要：本轮真实注册的服务/工具清单 + 连接失败项（可展开） */
      type: 'mcp'
      servers: McpContextServer[]
      failures?: string[]
    }
  | {
      /** 技能装配摘要：本轮物化进沙箱的技能清单（单行不展开） */
      type: 'skill'
      skills: SkillContextItem[]
    }
  | {
      /** 用户主动中断标记（持久化行尾；加载历史时转为消息 stopped 态，不参与渲染） */
      type: 'stopped'
    }

/** 会话附件（沙箱工作区内相对路径）：path 形如 uploads/<chatId>/xxx 或 outputs/<chatId>/xxx */
export interface AttachmentItem {
  path: string
  name: string
  size: number
}

/** 附件卡片的展示形态（输入框待传区与消息区共用） */
export interface AttachmentDisplay {
  key: string
  name: string
  size: number
  /** uploading=进度环；error=上传失败（仅待传区出现）；done=图标或缩略图 */
  status: 'uploading' | 'done' | 'error'
  progress?: number
  /** 图片类型的缩略图地址（本地 objectURL 或沙箱下载接口） */
  previewSrc?: string
}

/** 输入框待传附件记录（index.vue 维护，含上传状态） */
export interface PendingAttachment extends AttachmentDisplay {
  status: 'uploading' | 'done' | 'error'
  /** 上传成功后的沙箱引用（发送时随消息带给后端） */
  item?: AttachmentItem
  /** 本地文件预览 URL（图片类型），移除时需 revoke */
  localPreviewUrl?: string
  /** 编辑会话中新追加的附件（发送/取消时区别处理沙箱文件） */
  addedDuringEdit?: boolean
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
  /** 用户主动中断（状态行显示"已停止"，保留工作过程但无折叠入口） */
  stopped?: boolean
  /** 瞬态失败自动重试中的提示（后端 retry 事件；内容恢复即转入 retryHistory） */
  retryNotice?: string
  /** 本轮已完成的重新连接记录（zcode 式：重连成功后保留静态行，标记该轮经历过重连） */
  retryHistory?: string[]
  /** 本轮 MCP 工具装配失败提示（瞬态，不随历史持久化） */
  mcpNotice?: string
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

/** 子代理面板的定位键：消息 id + 显示版本 + Agent 块键（id 优先，无 id 回退块下标） */
export interface SubagentPanelTarget {
  messageId: string
  version: number
  blockKey: string
}

/** 上下文容量估算：后端干跑装配管线的近似 token 分项（知识库上下文随消息生成，不含在内） */
export interface ContextUsageData {
  /** 模型上下文窗口（token） */
  windowTokens: number
  /** 下一请求的近似总占用 */
  usedTokens: number
  categories: {
    /** 会话历史（含工具结果与提醒） */
    messages: number
    /** 内置工具 schema */
    systemTools: number
    /** MCP 工具 schema */
    mcpTools: number
    /** 系统提示词（身份+环境+动态段） */
    systemPrompt: number
    /** 技能清单注入段 */
    skills: number
    /** 用户环境附件（AGENTS.md 等）+ 跨会话记忆索引 */
    other: number
  }
  /** 平均缓存命中率（0-1；会话无缓存明细用量时为 null，面板隐藏该行） */
  cacheRate?: number | null
}
