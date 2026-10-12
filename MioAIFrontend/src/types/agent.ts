import type { PaginationParams } from './common'
import type { KnowledgeBase } from './knowledgeBase'
import type { McpTool } from './mcpTool'
import type { Skill } from './skill'

export interface Agent {
  id: number
  userId: number
  name: string
  description?: string
  avatar?: string
  /** 图标配置 JSON（bot-icon/types.ts 的 BotIconConfig），缺失时回退 avatar */
  icon?: string
  type: number
  typeDesc?: string
  systemPrompt?: string
  /** 开场白（新会话首屏展示） */
  openingMessage?: string
  /** 推荐问题（点击直接发送） */
  openingQuestions?: string[]
  /** 采样温度（null/undefined=引擎默认） */
  temperature?: number | null
  status: number
  statusDesc?: string
  isPublic: number
  usageCount: number
  version?: string
  createTime: string
  updateTime: string
}

export interface AgentDetail extends Agent {
  knowledgeBases: KnowledgeBase[]
  mcpTools: McpTool[]
  skills: Skill[]
  /** 内置智能体：当前用户已保存个性化配置 */
  personalized?: boolean
}

export interface AgentAddRequest {
  name: string
  description?: string
  avatar?: string
  icon?: string
  type?: number
  systemPrompt?: string
  status?: number
  isPublic?: number
  version?: string
}

export interface AgentUpdateRequest {
  id: number
  name?: string
  description?: string
  avatar?: string
  icon?: string
  type?: number
  systemPrompt?: string
  openingMessage?: string
  /** 推荐问题；temperature 传 -1 表示清除自定义温度回退引擎默认 */
  openingQuestions?: string[]
  temperature?: number | null
  status?: number
  isPublic?: number
  version?: string
}

export interface AgentQueryRequest extends PaginationParams {
  name?: string
  type?: number
  status?: number
  isPublic?: number
  userId?: number
}

/** 智能体发布版本快照 */
export interface AgentVersion {
  id: number
  agentId: number
  /** v1.0 格式 */
  version: string
  name?: string
  description?: string
  avatar?: string
  icon?: string
  systemPrompt?: string
  openingMessage?: string
  openingQuestions?: string[]
  temperature?: number | null
  /** 回滚来源版本号（回滚产生的版本才有） */
  rollbackFrom?: string
  createTime: string
  bindings?: AgentVersionBinding[]
}

export interface AgentVersionBinding {
  type: 'kb' | 'mcp' | 'skill'
  targetId: number
  name?: string
  targetDeleted?: boolean
}

/** 内置智能体的用户级个性化配置（字段覆盖语义：null=跟随内置默认） */
export interface AgentUserConfig {
  agentId: number
  systemPrompt?: string
  openingMessage?: string
  openingQuestions?: string[]
  temperature?: number | null
  icon?: string
}

export interface AgentUserConfigRequest {
  systemPrompt?: string
  openingMessage?: string
  openingQuestions?: string[]
  temperature?: number | null
  icon?: string
}
