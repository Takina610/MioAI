import type { PaginationParams } from './common'
import type { KnowledgeBase } from './knowledgeBase'
import type { McpTool } from './mcpTool'

export interface Agent {
  id: number
  userId: number
  name: string
  description?: string
  avatar?: string
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
}

export interface AgentAddRequest {
  name: string
  description?: string
  avatar?: string
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
