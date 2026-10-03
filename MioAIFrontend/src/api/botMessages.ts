import request from '@/utils/request'
import type { MessageBlock, PlanStep } from '@/types'

/** agent_message 完整持久化的行（含工作过程，刷新/回看原样还原） */
export interface BotMessageRow {
  role: string
  seq: number
  blocks?: MessageBlock[] | null
  plan?: PlanStep[] | null
  durationMs?: number | null
  createTime?: string
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
