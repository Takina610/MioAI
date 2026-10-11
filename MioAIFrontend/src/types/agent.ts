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
