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
  /** MCP 工具的描述（后端 tool_use 附带；内置工具无此字段） */
  desc?: string
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
      /** 群聊成员消息：成员以平等身份在消息流作答（委派执行的回答直接外流） */
      type: 'member'
      id: string
      memberId: number
      memberName: string
      /** bot-icon 配置 JSON（缺失回退首字圆形头像） */
      memberIcon?: string
      status: 'running' | 'done'
      failed?: boolean
      text: string
    }
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
      /** 技能使用行（zcode 式）：模型读取了沙箱技能文件，一行「技能 <名>」，展开看 SKILL.md 内容 */
      type: 'skill_use'
      name: string
      content: string
    }
  | {
      /** 模型流瞬断重连记录（仅前端存活态，不持久化）：同一episode原地更新 n/m，内容恢复后定格 */
      type: 'retry'
      label: string
      active?: boolean
    }
  | {
      /** 用户主动中断标记（持久化行尾；加载历史时转为消息 stopped 态，不参与渲染） */
      type: 'stopped'
    }
  | {
      /** 动态 UI 块（render_ui 工具产出，协议 v=1）：与 text 块按时间序混合渲染进回答区 */
      type: 'ui'
      /** 会话内全局唯一（交互寻址：/bot/ui-state 按 id 定位；targetId 原位更新寻址） */
      id: string
      v?: number
      /** targetId 原位替换事件标记（历史加载时忽略；流式期据此跨消息按 id 替换） */
      update?: boolean
      kind: 'cards' | 'chart' | 'table' | 'tasks' | 'form' | 'actions' | 'status' | 'timeline' | 'quote' | 'metric' | 'choice' | 'accordion' | 'math' | 'image' | 'image_list' | 'image_gallery' | 'figure' | (string & {})
      title?: string
      cards?: UiCardItem[]
      chartType?: 'bar' | 'line' | 'pie' | 'scatter'
      categories?: string[]
      series?: UiChartSeries[]
      columns?: string[]
      rows?: string[][]
      items?: UiTaskItem[] | UiImageMeta[] | UiMetricItem[]
      fields?: UiFormField[]
      submitLabel?: string
      actions?: UiActionItem[]
      steps?: UiStatusStep[]
      events?: UiTimelineEvent[]
      /** kind=quote：正文 */
      text?: string
      author?: string
      source?: string
      options?: UiChoiceOption[]
      /** kind=accordion：折叠分节 */
      sections?: UiAccordionSection[]
      /** kind=math：LaTeX 源码（KaTeX） */
      tex?: string
      display?: boolean
      /** kind=image / figure：单图载荷 */
      image?: UiImageMeta
      /** kind=image_gallery：图集 */
      images?: UiImageMeta[]
      /** kind=figure：图说明 */
      caption?: string
      ratio?: 'auto' | '16/9' | '4/3' | '1/1' | '3/4'
      fit?: 'cover' | 'contain'
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

/** 动态 UI 卡片（kind=cards 载荷） */
export interface UiCardItem {
  title: string
  value?: string
  desc?: string
  trend?: 'up' | 'down' | 'flat'
}

/** 动态 UI 图表系列（kind=chart 载荷） */
export interface UiChartSeries {
  name: string
  data: number[]
}

/** 动态 UI 任务项（kind=tasks 载荷，status 可被用户勾选更新并持久化） */
export interface UiTaskItem {
  text: string
  status: 'todo' | 'doing' | 'done'
}

/** 动态 UI 表单字段（kind=form 载荷）：用户填写后编排为消息提交回对话 */
export interface UiFormField {
  label: string
  type: 'text' | 'textarea' | 'number' | 'select'
  options?: string[]
  placeholder?: string
  required?: boolean
}

/** 动态 UI 操作按钮（kind=actions 载荷）：点击即把 prompt 作为用户消息发送 */
export interface UiActionItem {
  label: string
  prompt: string
  style?: 'primary' | 'default'
  /** 有影响/不可逆操作：先弹确认框，确认后才发送 prompt */
  confirm?: boolean
}

/** 动态 UI 状态步骤（kind=status 载荷，静态进度面板） */
export interface UiStatusStep {
  text: string
  status: 'todo' | 'doing' | 'done' | 'failed'
}

/** 动态 UI 时间线事件（kind=timeline 载荷） */
export interface UiTimelineEvent {
  time?: string
  title: string
  desc?: string
}

/** 动态 UI KPI 项（kind=metric 载荷） */
export interface UiMetricItem {
  label: string
  value: string
  unit?: string
  trend?: 'up' | 'down' | 'flat'
}

/** 动态 UI 单选胶囊（kind=choice 载荷）：点击即把 prompt（缺省 label）作为用户消息发送 */
export interface UiChoiceOption {
  label: string
  prompt?: string
}

/** 动态 UI 折叠分节（kind=accordion 载荷） */
export interface UiAccordionSection {
  title: string
  text: string
}

/**
 * 动态 UI 图片元数据（image/image_list/image_gallery/figure 载荷单元）：
 * url 必须是本会话 image_search 工具结果登记过的 https 地址（后端防伪造核对），
 * 作者/授权照实透传可为空，前端不推断不编造。
 */
export interface UiImageMeta {
  url: string
  thumbnailUrl?: string
  title?: string
  alt?: string
  caption?: string
  desc?: string
  sourceUrl?: string
  sourceName?: string
  author?: string
  license?: string
  width?: number
  height?: number
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
  /** 瞬态失败自动重试中的提示（与 blocks 内 retry 行双写，宠物情绪引擎据此显示疑惑态） */
  retryNotice?: string
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
