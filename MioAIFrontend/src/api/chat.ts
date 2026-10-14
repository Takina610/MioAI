import axios, { type AxiosProgressEvent } from 'axios'
import type { AttachmentItem, ChatMessageRequest, ChatRefItem, ContextUsageData, ModelSelection } from '@/types'
import request from '@/utils/request'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

// 定义参数类型
interface ConnectSSEParams {
  [key: string]: string | number | boolean
}

type OnMessageCallback = (data: string) => void
type OnErrorCallback = (error: Event) => void

/** 连接 SSE（GET + query 参数；token 缺省时后端按游客处理） */
export const connectSSE = (
  url: string,
  params: ConnectSSEParams,
  onMessage?: OnMessageCallback,
  onError?: OnErrorCallback
): EventSource => {
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(String(params[key]))}`)
    .join('&')

  const eventSource = new EventSource(`${BASE_URL}${url}?${queryString}`)

  eventSource.onmessage = (event: MessageEvent) => {
    onMessage?.(event.data)
  }

  eventSource.onerror = (error: Event) => {
    onError?.(error)
    eventSource.close()
  }

  return eventSource
}

/** 指定模型实际支持的思考档位（后端按模型能力探测，前端据此如实渲染档位选择器）；
 * 不带参数=引擎默认模型；source=custom 时探测用户自己的供应商端点。
 * 冷缓存串行探测较慢（最长约 1 分钟），放宽超时；失败静默（前端有兜底档位） */
export const getReasoningEfforts = async (
  selection?: Partial<ModelSelection>,
  provider?: { baseUrl: string; apiKey: string; apiFormat: string } | null
): Promise<{ efforts: string[] }> => {
  const params: Record<string, unknown> = {}
  if (selection?.source) params.source = selection.source
  if (selection?.model) params.model = selection.model
  // 供应商端点配置仅存浏览器本地，随请求瞬态经过后端（不落库）
  if (selection?.source === 'custom' && provider) {
    params.providerBaseUrl = provider.baseUrl
    params.providerApiKey = provider.apiKey
    params.providerApiFormat = provider.apiFormat
  }
  return request.get('/bot/efforts', { params, timeout: 120000, skipErrorMessage: true } as never)
}

/** 会话上下文容量估算（窗口大小 + 按类别近似 token）；失败静默（非关键指标）。
 *  窗口按当前模型解析：自定义模型声明值随请求携带，免费档由后端按 model 查目录 */
export const getContextUsage = async (
  chatId: string,
  agentId: number,
  model?: { source?: string; model?: string; contextWindow?: number } | null
): Promise<ContextUsageData> => {
  const params: Record<string, unknown> = { chatId, agentId }
  if (model?.source) params.source = model.source
  if (model?.model) params.model = model.model
  if (model?.contextWindow && model.contextWindow > 0) params.contextWindow = model.contextWindow
  return request.get<ContextUsageData>('/bot/context-usage', {
    params,
    skipErrorMessage: true
  } as never)
}

/** 会话内切换对话模型：后端以 system 行落库（居中提示，刷新后保留） */
export const recordModelSwitch = async (chatId: string, from: string, to: string): Promise<void> => {
  await request.post('/bot/model-switch', { chatId, from, to }, { skipErrorMessage: true } as never)
}

/**
 * 与智能体对话（统一走 MioBot 流式 Agent 引擎，SSE 信封返回全过程）：
 * MioBot（agentId=1，系统内置）与用户自定义智能体共用 /bot/chat。
 * skipUserPersist：重新生成场景，用户消息已在历史中，后端不再重复落库
 * refs：输入框 @ / 引用的上下文清单（JSON），服务端注入模型上下文并随 user 行持久化
 */
export const chatWithMioBot = (
  content: string,
  chatId: string,
  agentId: number,
  token: string,
  opts: {
    skipUserPersist?: boolean
    reasoningEffort?: string
    groupSeq?: number
    attachments?: AttachmentItem[]
    refs?: ChatRefItem[]
    /** 当前对话模型选择（OpenCode 免费档 / 自定义供应商模型），后端按此路由 */
    modelSelection?: ModelSelection | null
    /** 自定义供应商端点配置（仅存浏览器本地，随请求瞬态经过后端） */
    provider?: { baseUrl: string; apiKey: string; apiFormat: string } | null
    /** 当前模型上下文窗口（token）：与容量指示同源，后端按此设压缩阈值 */
    contextWindow?: number
  } = {}
): EventSource => {
  const params: ConnectSSEParams = { content, chatId, agentId, token }
  if (opts.skipUserPersist) {
    params.skipUserPersist = true
  }
  if (opts.reasoningEffort) {
    params.reasoningEffort = opts.reasoningEffort
  }
  if (opts.contextWindow && opts.contextWindow > 0) {
    params.contextWindow = opts.contextWindow
  }
  if (opts.groupSeq != null) {
    params.groupSeq = opts.groupSeq
  }
  if (opts.attachments?.length) {
    params.attachments = JSON.stringify(opts.attachments)
  }
  if (opts.refs?.length) {
    params.refs = JSON.stringify(opts.refs)
  }
  if (opts.modelSelection) {
    params.modelSource = opts.modelSelection.source
    if (opts.modelSelection.model) {
      params.model = opts.modelSelection.model
    }
  }
  if (opts.modelSelection?.source === 'custom' && opts.provider) {
    // 供应商端点配置仅存浏览器本地，随请求瞬态经过后端（不落库）
    params.providerBaseUrl = opts.provider.baseUrl
    params.providerApiKey = opts.provider.apiKey
    params.providerApiFormat = opts.provider.apiFormat
  }
  return connectSSE('/bot/chat', params)
}

/** 上传会话附件：SFTP 直落沙箱工作区 uploads/<chatId>/，返回附件引用（随消息发送） */
export const uploadAttachment = async (
  file: File,
  chatId: string,
  onProgress?: (percent: number) => void
): Promise<AttachmentItem> => {
  const form = new FormData()
  form.append('file', file)
  form.append('chatId', chatId)
  return request.post('/bot/attachment', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    onUploadProgress: (event: AxiosProgressEvent) => {
      if (onProgress && event.total) {
        onProgress(event.loaded / event.total)
      }
    }
  } as never)
}

/** 撤回刚上传的附件（输入卡片点 X）：同步删除沙箱上的文件 */
export const deleteAttachment = async (path: string): Promise<boolean> => {
  return request.delete('/bot/attachment', { params: { path } } as never)
}

/** 下载 Agent 产出的暂存文件（outputs/...）：blob 保存为浏览器下载 */
export const downloadAttachment = async (path: string, name: string): Promise<void> => {
  const response = await axios.get(`${import.meta.env.VITE_API_BASE_URL || ''}/bot/attachment/download`, {
    params: { path },
    responseType: 'blob',
    headers: { token: localStorage.getItem('token') || '' },
    timeout: 120000
  })
  const url = URL.createObjectURL(response.data as Blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

/** 截断会话历史（编辑消息/重新生成共用）：保留 seq <= keepThroughSeq 的消息并重建记忆 */
export const truncateConversation = async (
  conversationId: string,
  keepThroughSeq: number,
  groupKey?: number
): Promise<{ keptMessages: number; userContent?: string }> => {
  return request.post(`/bot/truncate/${conversationId}`, null, {
    params: groupKey != null ? { keepThroughSeq, groupKey } : { keepThroughSeq }
  })
}

/** 回答 Agent 的提问（AskUserQuestion）：解锁该会话挂起的问答门闸 */
export const answerQuestion = async (
  chatId: string,
  id: string,
  answers: Array<{ index: number; selections: string[]; custom?: string }>
): Promise<boolean> => {
  const result = await request.post<{ resolved: boolean }>('/bot/answer', { chatId, id, answers })
  return Boolean(result?.resolved)
}

/** 动态 UI 块交互状态持久化（任务清单勾选）：按块 id 定位并更新项状态 */
export const patchUiBlock = async (
  chatId: string,
  blockId: string,
  itemIndex: number,
  status: string
): Promise<boolean> => {
  const result = await request.post<boolean>('/bot/ui-state', { chatId, blockId, itemIndex, status })
  return Boolean(result)
}

/** 会话是否正在后端执行（孤儿回合恢复轮询据此快速失败） */
export const isChatActive = async (chatId: string): Promise<boolean> => {
  const result = await request.get<boolean>(`/bot/active/${chatId}`, { skipErrorMessage: true } as never)
  return Boolean(result)
}

/** 当前归属正在执行的轮次（chatId+智能体+开始时间）：侧边栏执行动画数据源 + 左轨活动绑定
 *  （agentId 供会话→智能体映射，startedAt 供多会话并发时"最新会话优先"裁决），跨刷新/切页保持 */
export interface ActiveTurnInfo {
  chatId: string
  agentId?: number
  startedAt?: number
}

export const getActiveTurns = async (): Promise<ActiveTurnInfo[]> => {
  const result = await request.get<ActiveTurnInfo[]>('/bot/active-chats', { skipErrorMessage: true } as never)
  return Array.isArray(result) ? result : []
}


/** 中断正在执行的会话轮次：后端置停止标记并打断引擎，已流出的部分过程落库后回 stopped 事件 */
export const stopChat = async (chatId: string): Promise<boolean> => {
  const result = await request.post<boolean>('/bot/stop', null, {
    params: { chatId }
  })
  return Boolean(result)
}

/**
 * 断线重连回放：接管正在执行会话的事件流，服务端先回放 lastSeq 之后的事件再续播直播。
 * 会话不在执行时服务端回一条 replay_unavailable 事件即结束（调用方回退落库还原）
 */
export const reconnectChatStream = (
  chatId: string,
  lastSeq: number,
  token: string
): EventSource => connectSSE('/bot/chat/stream', { chatId, lastSeq, token })

/** 生成会话标题；失败返回 null（调用方保留首条消息兜底标题，不覆盖为占位） */
export const generateTitle = async (
  agentId: number,
  conversationId: string,
  content: string
): Promise<string | null> => {
  try {
    const response = await request.post<string>('/summary', {
      conversationId,
      agentId,
      content
    } satisfies ChatMessageRequest)
    return response || null
  } catch (error) {
    console.error('Failed to generate title:', error)
    return null
  }
}
