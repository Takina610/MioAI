import request from '@/utils/request'

export interface ChatConversation {
  id: number
  conversationId: string
  agentId: number
  userId: number
  title?: string
  createTime?: string
  updateTime?: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export function getChatIds(agentId: number | string): Promise<ChatConversation[]> {
  return request({
    url: `/memory/getChatIds/${agentId}`,
    method: 'get'
  })
}

export function getChatIdsPage(agentId: number | string, current: number = 1, size: number = 10, keyword?: string): Promise<PageResponse<ChatConversation>> {
  return request({
    url: `/memory/getChatIdsPage/${agentId}`,
    params: { current, size, keyword: keyword || undefined },
    method: 'get'
  })
}

export function deleteChat(conversationId: string): Promise<boolean> {
  return request({
    url: `/memory/deleteChat/${conversationId}`,
    method: 'post'
  })
}

/** 置顶/取消置顶会话：置顶会话排在列表最前 */
export function pinChat(conversationId: string, pinned: boolean): Promise<boolean> {
  return request({
    url: `/memory/pinChat/${conversationId}`,
    method: 'post',
    params: { pinned }
  })
}

export function getConversation(conversationId: string): Promise<ChatConversation> {
  return request({
    url: `/memory/getConversation/${conversationId}`,
    method: 'get'
  })
}

/** 分享页公开读取：标题/时间 + 消息文本块 + 智能体展示信息（链接即凭证，ID 对匹配即可读） */
export interface SharedAgentBrief {
  id: number
  /** 所有者用户 id：分享页判断"别人的智能体"（广场引导）用 */
  userId?: number
  name?: string
  avatar?: string
  icon?: string
  type?: number
  members?: Array<{ id: number; name?: string; icon?: string; avatar?: string }>
}

export interface SharedConversation {
  title?: string
  createTime?: string
  agent?: SharedAgentBrief
  messages: Array<{
    role: string
    seq?: number
    blocks?: unknown[]
    durationMs?: number
    createTime?: string
  }>
}

export function getSharedConversation(agentId: number | string, conversationId: string): Promise<SharedConversation> {
  return request({
    url: `/memory/share/${agentId}/${conversationId}`,
    method: 'get',
    skipErrorMessage: true
  } as never)
}

/** 跨智能体的最近会话（输入框 @ 引用数据源）：附带归属智能体名/类型 */
export interface AllSessionItem {
  conversationId: string
  title?: string
  agentId?: number
  agentName?: string
  agentType?: number
  updateTime?: string
}

export function getAllSessions(limit: number = 200): Promise<AllSessionItem[]> {
  return request({
    url: '/memory/allSessions',
    method: 'get',
    params: { limit },
    skipErrorMessage: true
  } as never)
}
