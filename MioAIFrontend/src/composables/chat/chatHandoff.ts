import type { AttachmentItem } from '@/types'

/**
 * 首页 → 对话页的一次性交接。
 *
 * 首页无法直接发消息（会话状态、流式接收、消息列表都在对话页），
 * 所以首页只负责把「发什么、给谁、用哪个会话 id」准备好，
 * 跳转后由对话页在智能体就绪时取走并走它自己那条发送链路。
 *
 * 用模块级单例而非 URL 参数：内容可能很长，附件是对象数组，都不适合塞进 query。
 */
export interface ChatHandoff {
  /** 目标智能体：游客=内置 MioBot(1)，登录用户=自己的专属 MioBot */
  agentId: number
  /** 首条消息及附件所属的会话 id（附件上传时就按它落沙箱） */
  chatId: string
  content: string
  attachments: AttachmentItem[]
}

let pending: ChatHandoff | null = null

export function setChatHandoff(next: ChatHandoff): void {
  pending = next
}

/** 只读查看（消费前判条件用，不消耗交接） */
export function peekChatHandoff(): ChatHandoff | null {
  return pending
}

/** 取走交接（只生效一次）：对话页消费后即清空，避免返回/刷新时重复发送 */
export function takeChatHandoff(): ChatHandoff | null {
  const current = pending
  pending = null
  return current
}
