import type { ApiFormat, ModelEntry } from '@/types'

/**
 * 供应商模板（照搬 ZCode builtin 供应商配置）：
 * 端点、协议与官方模型清单来自 ZCode 的 zcode-builtin.json——
 * 模型 ID 用官方原始大小写（如 GLM-5.3-Flash，BigModel 端点大小写敏感），
 * 选模板即预填 BaseURL / API 格式 / 模型清单，用户只需填 API Key。
 */
export interface ProviderTemplate {
  id: string
  name: string
  group: '智谱' | 'OpenCode' | '其他主流'
  apiFormat: ApiFormat
  baseUrl: string
  /** API Key 获取页（提示用户去哪拿 key） */
  apiKeyUrl?: string
  models: ModelEntry[]
}

const m = (id: string, vision = false): ModelEntry => ({ id, enabled: true, ...(vision ? { vision: true, inputs: ['text', 'image'] } : {}) })

const GLM_STANDARD: ModelEntry[] = [
  m('GLM-5.3'), m('GLM-5.3-Flash'), m('GLM-5V-Turbo', true), m('GLM-5.1'), m('GLM-5.1-Highspeed'),
  m('GLM-5'), m('GLM-5-Turbo'), m('GLM-4.7'), m('GLM-4.7-FlashX'), m('GLM-4.7-Flash'),
  m('GLM-4.6'), m('GLM-4.5-Air'), m('GLM-4.5'), m('GLM-4.6V', true), m('GLM-4.6V-Flash', true),
]

const GO_CHAT_MODELS: ModelEntry[] = [
  m('glm-5.3-flash'), m('glm-5.3'), m('kimi-k3'), m('kimi-k2.7-code'),
  m('deepseek-v4.1-flash'), m('deepseek-v4-pro'), m('mimo-v2.5'), m('mimo-v2.5-pro'),
  m('glm-5.2'), m('glm-5.1'), m('kimi-k2.6'), m('deepseek-v4-flash'), m('deepseek-v4-flash-vision-exp', true),
]

const GO_MESSAGES_MODELS: ModelEntry[] = [
  m('minimax-m3'), m('qwen3.8-max'), m('qwen3.8-flash'), m('minimax-m2.7'), m('minimax-m2.5'),
  m('qwen3.7-max'), m('qwen3.7-plus'), m('qwen3.6-plus'),
]

const GO_RESPONSES_MODELS: ModelEntry[] = [m('gpt-5.6-luna'), m('grok-4.6')]

const ZEN_RESPONSES_MODELS: ModelEntry[] = [
  m('gpt-6-astra'), m('gpt-5.6-sol'), m('gpt-5.6-terra'), m('gpt-5.6-luna'), m('gpt-5.5'),
  m('gpt-5.5-pro'), m('gpt-5.4'), m('gpt-5.4-pro'), m('gpt-5.4-mini'), m('gpt-5.4-nano'),
  m('gpt-5.3-codex'), m('gpt-5.3-codex-spark'), m('gpt-5.2'), m('gpt-5.1'),
]

const ZEN_MESSAGES_MODELS: ModelEntry[] = [
  m('claude-fable-5-1'), m('claude-fable-5'), m('qwen3.7-max'), m('qwen3.6-plus'), m('qwen3.5-plus'),
  m('claude-opus-5'), m('claude-sonnet-5'), m('claude-haiku-4-5'), m('claude-opus-4-8'),
  m('claude-opus-4-7'), m('claude-opus-4-6'), m('claude-opus-4-5'), m('claude-sonnet-4-6'), m('claude-sonnet-4-5'),
]

const ZEN_CHAT_MODELS: ModelEntry[] = [
  m('kimi-k3'), m('minimax-m3'), m('deepseek-v4-pro'), m('glm-5.2'), m('big-pickle'),
  m('mimo-v2.5-free'), m('hy3-free'), m('ling-3.0-flash-fin-free'), m('nemotron-3-ultra-free'),
  m('muse-spark-1.2-contributor-free'), m('minimax-m2.7'), m('deepseek-v4-flash'), m('glm-5.1'),
  m('nemotron-3.5-lightning-free'),
]

export const PROVIDER_TEMPLATES: ProviderTemplate[] = [
  // —— 智谱 ——
  {
    id: 'bigmodel-api', name: 'BigModel Coding Plan', group: '智谱',
    apiFormat: 'anthropic', baseUrl: 'https://open.bigmodel.cn/api/anthropic',
    apiKeyUrl: 'https://bigmodel.cn/coding-plan/personal/overview',
    models: [m('GLM-5.3'), m('GLM-5.3-Flash')],
  },
  {
    id: 'bigmodel-standard-api', name: 'BigModel 开放平台', group: '智谱',
    apiFormat: 'openai', baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    apiKeyUrl: 'https://open.bigmodel.cn/usercenter/apikeys',
    models: GLM_STANDARD,
  },
  {
    id: 'zai-api', name: 'Z.ai（智谱海外）', group: '智谱',
    apiFormat: 'anthropic', baseUrl: 'https://api.z.ai/api/anthropic',
    apiKeyUrl: 'https://z.ai/manage-apikey/apikey-list',
    models: [m('GLM-5.3'), m('GLM-5.3-Flash')],
  },
  // —— OpenCode ——
  {
    id: 'opencode-go-chat', name: 'OpenCode Go（Chat）', group: 'OpenCode',
    apiFormat: 'openai', baseUrl: 'https://opencode.ai/zen/go/v1',
    apiKeyUrl: 'https://opencode.ai/auth',
    models: GO_CHAT_MODELS,
  },
  {
    id: 'opencode-go-messages', name: 'OpenCode Go（Anthropic）', group: 'OpenCode',
    apiFormat: 'anthropic', baseUrl: 'https://opencode.ai/zen/go/v1',
    apiKeyUrl: 'https://opencode.ai/auth',
    models: GO_MESSAGES_MODELS,
  },
  {
    id: 'opencode-go-responses', name: 'OpenCode Go（Responses）', group: 'OpenCode',
    apiFormat: 'responses', baseUrl: 'https://opencode.ai/zen/go/v1',
    apiKeyUrl: 'https://opencode.ai/auth',
    models: GO_RESPONSES_MODELS,
  },
  {
    id: 'opencode-zen-chat', name: 'OpenCode Zen（Chat）', group: 'OpenCode',
    apiFormat: 'openai', baseUrl: 'https://opencode.ai/zen/v1',
    apiKeyUrl: 'https://opencode.ai/zen',
    models: ZEN_CHAT_MODELS,
  },
  {
    id: 'opencode-zen-messages', name: 'OpenCode Zen（Anthropic）', group: 'OpenCode',
    apiFormat: 'anthropic', baseUrl: 'https://opencode.ai/zen/v1',
    apiKeyUrl: 'https://opencode.ai/zen',
    models: ZEN_MESSAGES_MODELS,
  },
  {
    id: 'opencode-zen-responses', name: 'OpenCode Zen（Responses）', group: 'OpenCode',
    apiFormat: 'responses', baseUrl: 'https://opencode.ai/zen/v1',
    apiKeyUrl: 'https://opencode.ai/zen',
    models: ZEN_RESPONSES_MODELS,
  },
  // —— 其他主流 ——
  {
    id: 'moonshot-kimi', name: 'Moonshot Kimi', group: '其他主流',
    apiFormat: 'anthropic', baseUrl: 'https://api.moonshot.cn/anthropic',
    apiKeyUrl: 'https://platform.moonshot.cn/console/api-keys',
    models: [m('kimi-k3'), m('kimi-k2.7-code'), m('kimi-k2.6'), m('kimi-k2.7-code-highspeed'), m('k3'), m('k3-256k')],
  },
  {
    id: 'minimax', name: 'MiniMax', group: '其他主流',
    apiFormat: 'anthropic', baseUrl: 'https://api.minimaxi.com/anthropic',
    apiKeyUrl: 'https://platform.minimaxi.com/user-center/basic-information/interface-key',
    models: [m('MiniMax-M3'), m('MiniMax-M2.7'), m('MiniMax-M2.7-highspeed'), m('MiniMax-M2.5'), m('MiniMax-M2')],
  },
  {
    id: 'deepseek', name: 'DeepSeek', group: '其他主流',
    apiFormat: 'anthropic', baseUrl: 'https://api.deepseek.com/anthropic',
    apiKeyUrl: 'https://platform.deepseek.com/api_keys',
    models: [m('deepseek-flash'), m('deepseek-v4-pro')],
  },
  {
    id: 'qwen-alibaba-model-studio-cn', name: '通义千问（百炼）', group: '其他主流',
    apiFormat: 'anthropic', baseUrl: 'https://dashscope.aliyuncs.com/apps/anthropic',
    apiKeyUrl: 'https://bailian.console.aliyun.com/?apiKey=1',
    models: [m('qwen3.8-max'), m('qwen3.8-flash'), m('qwen3.7-max'), m('qwen3.7-plus'), m('qwen3.7-flash'),
      m('qwen3.6-plus'), m('qwen3.6-flash'), m('qwen3.5-plus'), m('qwen3.5-flash'), m('qwen3-vl-plus', true)],
  },
  {
    id: 'xiaomi-mimo', name: '小米 MiMo', group: '其他主流',
    apiFormat: 'anthropic', baseUrl: 'https://api.xiaomimimo.com/anthropic',
    models: [m('mimo-v2.5-pro'), m('mimo-v2.5')],
  },
]

export const PROVIDER_TEMPLATE_GROUPS = ['智谱', 'OpenCode', '其他主流'] as const
