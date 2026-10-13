import type { PaginationParams } from './common'
import type { KnowledgeBase } from './knowledgeBase'
import type { McpTool } from './mcpTool'
import type { Skill } from './skill'

/** 群聊成员概要（type=2 的智能体附带）：堆叠群头像/成员气泡头像渲染 */
export interface AgentMemberBrief {
  id: number
  name?: string
  /** bot-icon 配置 JSON */
  icon?: string
  avatar?: string
}

export interface Agent {
  id: number
  userId: number
  /** 作者用户名（广场列表按需填充；空时回退官方/匿名） */
  userName?: string
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
  /** 置顶（0-否 1-是）：智能体左轨置顶分组 */
  pinned?: number
  usageCount: number
  version?: string
  createTime: string
  updateTime: string
  /** 群聊成员概要（type=2 时后端填充） */
  members?: AgentMemberBrief[]
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

/** 创建群聊智能体：群本体只有名称/描述，能力来自成员智能体集合（type=2） */
export interface AgentGroupCreateRequest {
  name: string
  description?: string
  memberAgentIds: number[]
}

/** 编辑群聊：改名 + 整体替换成员（至少保留 2 个） */
export interface AgentGroupUpdateRequest {
  id: number
  name: string
  memberAgentIds: number[]
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
  icon?: string
  /** 是否发布到广场（0-未发布 1-已发布）：广场的 MioBot 槽位显示自己的 */
  isPublic?: number
}

export interface AgentUserConfigRequest {
  systemPrompt?: string
  icon?: string
  /** 是否发布到广场（不传=保持不变） */
  isPublic?: number
}
