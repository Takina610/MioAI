import request from '@/utils/request'
import type { MessageBlock, PlanStep } from '@/types'

/** agent_message 完整持久化的行（含工作过程，刷新/回看原样还原） */
/** 归档的历史版本（后端 agent_message_version，挂在所属轮次的 assistant 行上） */
export interface BotMessageVersion {
  versionIndex: number
  userText?: string
  blocks?: MessageBlock[] | null
  plan?: PlanStep[] | null
  durationMs?: number | null
}

export interface BotMessageRow {
  role: string
  seq: number
  blocks?: MessageBlock[] | null
  plan?: PlanStep[] | null
  durationMs?: number | null
  createTime?: string
  /** 轮次组锚（编辑重发的 user 行沿用被编辑轮次的组） */
  groupSeq?: number | null
  /** 该轮归档的旧版本（旧→新），恢复为消息的 history 供 <n/n> 切换 */
  versions?: BotMessageVersion[] | null
}

/** 会话的完整消息（工具调用/任务清单等工作过程）；老会话无记录时返回空数组 */
export function getBotMessages(
  conversationId: string,
  options?: { skipErrorMessage?: boolean }
): Promise<BotMessageRow[]> {
  return request({
    url: `/bot/messages/${conversationId}`,
    method: 'get',
    skipErrorMessage: options?.skipErrorMessage
  })
}
