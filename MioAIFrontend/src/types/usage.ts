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
