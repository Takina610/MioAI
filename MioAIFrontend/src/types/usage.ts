export interface MetricValue {
  value: number
  unit: string
  trend: number
}

export interface TimePoint {
  time: string
  fullTime?: string
  value: number
}

export interface NameValue {
  id: string
  name: string
  avatar?: string
  /** bot-icon 配置 JSON（shape+fill），智能体榜单渲染真实图标 */
  icon?: string | null
  value: number
  /** 调用/轮次（日明细等场景与 value 并列展示） */
  calls?: number
}

export interface UsageStats {
  metrics: Record<string, MetricValue>
  trend: TimePoint[]
  trendMap: Record<string, TimePoint[]>
  prevTrendMap: Record<string, TimePoint[]>
  distribution: NameValue[]
  rank: NameValue[]
}

export interface ContributionDay {
  date: string
  tokens: number
  calls: number
}

export interface ContributionSummary {
  totalTokens: number
  peakTokens: number
  peakDate?: string | null
  longestChatMs: number
  currentStreak: number
  longestStreak: number
}

export interface ContributionData {
  days: ContributionDay[]
  summary: ContributionSummary
}

export interface TokenTrendPoint {
  date: string
  tokens: number
}

export interface TokenTrendSeries {
  id: string
  name: string
  avatar?: string | null
  totalTokens: number
  totalCalls: number
  points: TokenTrendPoint[]
}

export interface TokenTrendData {
  dimension: 'model' | 'agent'
  series: TokenTrendSeries[]
}

export interface UsageProfile {
  registerTime?: string
  registerDays: number
  activeDays: number
  totalTurns: number
  totalConversations: number
  totalMessages: number
  totalTokens: number
  avgDailyTokens: number
  favoriteModel?: string | null
  favoriteModelTokens: number
  favoriteAgentId?: number | null
  favoriteAgentName?: string | null
  favoriteAgentAvatar?: string | null
  favoriteAgentTokens: number
  agentCount: number
  knowledgeCount: number
  mcpCount: number
  skillCount: number
  toolCallCount: number
  retrievalCount: number
  skillUseCount: number
  activityCount: number
}

/** 使用去向节点（name 为图内唯一键，displayName 为展示名） */
export interface UsageFlowNode {
  name: string
  displayName: string
  /** 0=用户 1=智能体 2=目标（个人视图无用户层，整体前移） */
  layer: number
  value: number
}

export interface UsageFlowLink {
  source: string
  target: string
  value: number
}

export interface UsageFlow {
  nodes: UsageFlowNode[]
  links: UsageFlowLink[]
}

/** 使用构成节点：一级分类 → 二级细分 */
export interface UsageDrillNode {
  id: string
  name: string
  value: number
  children?: UsageDrillNode[]
}

/** 散点事件：time 为 epoch millis */
export interface UsageEventPoint {
  time: number
  value: number
  /** 1-成功 0-失败 */
  status: number
  agentName: string
  model?: string | null
  targetName: string
  userName?: string | null
}

export interface UsageAnalytics {
  type: string
  valueLabel: string
  valueUnit: string
  measureLabel: string
  measureUnit: string
  flow: UsageFlow
  drill: UsageDrillNode[]
  events: UsageEventPoint[]
  totalEvents: number
}

export interface UsageHourBucket {
  hour: number
  measure: number
  calls: number
}

export interface UsageDayDetail {
  date: string
  tokens: number
  inputTokens: number
  outputTokens: number
  calls: number
  conversations: number
  chatDurationMs: number
  hours: UsageHourBucket[]
  agents: NameValue[]
  models: NameValue[]
  agentCalls: number
  agentCount: number
  knowledgeCalls: number
  knowledgeCount: number
  mcpCalls: number
  mcpCount: number
  skillCalls: number
  skillCount: number
}
