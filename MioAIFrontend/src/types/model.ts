/** 模型设置：OpenCode 免费模型（反代）+ 自定义供应商 + 当前对话模型选择 */

/** 模型来源：opencode-免费档反代 / custom-自定义供应商 */
export type ModelSource = 'opencode' | 'custom'

/** API 格式：anthropic-messages / openai chat completions / openai responses */
export type ApiFormat = 'anthropic' | 'openai' | 'responses'

export const API_FORMAT_LABELS: Record<ApiFormat, string> = {
  anthropic: 'Anthropic Messages (v1/messages)',
  openai: 'Chat Completions (/chat/completions)',
  responses: 'Responses (/responses)'
}

/** 供应商下的模型条目（旧数据可能只有 id 字符串，API 层已规范化） */
export interface ModelEntry {
  id: string
  /** 上下文窗口（token） */
  contextWindow?: number
  /** 最大输出 token */
  maxOutputTokens?: number
  /** 支持图像输入（聊天菜单显示"视觉"角标） */
  vision?: boolean
  /** 输入类型：text 恒定；image/video/pdf */
  inputs?: string[]
  /** 模型能力：structured_output/web_search/system_message */
  capabilities?: string[]
  /** 声明的推理等级（从低到高，声明后聊天页直接采用，不再探测） */
  reasoningLevels?: string[]
  /** 是否在聊天页可选 */
  enabled: boolean
}

/** OpenCode 免费模型（反代元数据） */
export interface FreeModel {
  id: string
  vision: boolean
  /** 上下文窗口（token，0/缺省=元数据缺失） */
  contextWindow?: number
  /** 是否推理模型（false=不支持思考，档位仅"关闭"） */
  reasoning?: boolean
}

/** 自定义供应商 */
export interface ModelProvider {
  id: number
  userId: number
  name: string
  baseUrl: string
  apiFormat: string
  apiKey: string
  /** 模型条目列表（服务端为 JSON 字符串，API 层已解析规范化） */
  models: ModelEntry[]
  createTime?: string
  updateTime?: string
}

/** 自定义供应商保存请求（models 直接传条目对象） */
export interface ModelProviderPayload {
  id?: number
  name: string
  baseUrl: string
  apiFormat: ApiFormat
  apiKey: string
  models: ModelEntry[]
}

/** 当前对话模型选择（user_id 由服务端从登录态推导） */
export interface ModelSelection {
  source: ModelSource
  providerId?: number | null
  model: string
}

/** 连通性测试结果 */
export interface ConnectivityTestResult {
  ok: boolean
  latencyMs: number
  message: string
}
