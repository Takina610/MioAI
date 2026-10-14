import type { ModelEntry } from '@/types'

/**
 * 主流模型基础数据预设（智能配置）：按模型 id 模糊匹配自动填写
 * 上下文窗口 / 最大输出 / 视觉输入。数据为公开资料的近似值，可手动修改。
 */
export interface ModelPreset {
  contextWindow: number
  maxOutputTokens?: number
  vision?: boolean
}

/** 顺序匹配，首个命中生效 */
const PRESET_RULES: Array<{ match: RegExp; preset: ModelPreset }> = [
  { match: /gpt-5/i, preset: { contextWindow: 400_000, maxOutputTokens: 128_000, vision: true } },
  { match: /gpt-4\.1/i, preset: { contextWindow: 1_000_000, maxOutputTokens: 32_000, vision: true } },
  { match: /gpt-4o|gpt-4\.?5/i, preset: { contextWindow: 128_000, maxOutputTokens: 16_384, vision: true } },
  { match: /o[34](-mini|-pro)?/i, preset: { contextWindow: 200_000, maxOutputTokens: 100_000, vision: true } },
  { match: /claude-(opus|sonnet|haiku)-?[456]/i, preset: { contextWindow: 200_000, maxOutputTokens: 64_000, vision: true } },
  { match: /gemini-[23]/i, preset: { contextWindow: 1_000_000, maxOutputTokens: 65_536, vision: true } },
  { match: /deepseek-v4/i, preset: { contextWindow: 1_000_000, maxOutputTokens: 65_536 } },
  { match: /deepseek/i, preset: { contextWindow: 128_000, maxOutputTokens: 8_192 } },
  { match: /qwen3?\.?[0-9]*-?(max|plus|flash|turbo)?/i, preset: { contextWindow: 256_000, maxOutputTokens: 32_768 } },
  { match: /glm-[456]/i, preset: { contextWindow: 200_000, maxOutputTokens: 128_000, vision: true } },
  { match: /kimi-k?[0-9]/i, preset: { contextWindow: 256_000, maxOutputTokens: 32_768 } },
  { match: /minimax|m[2-9]/i, preset: { contextWindow: 200_000, maxOutputTokens: 32_768 } },
  { match: /grok-[34]/i, preset: { contextWindow: 256_000, maxOutputTokens: 32_768, vision: true } },
  { match: /llama-?[4-9]|nemotron/i, preset: { contextWindow: 128_000, maxOutputTokens: 8_192 } },
  { match: /mimo|step-?[456]|fledge|longcat|ling/i, preset: { contextWindow: 200_000, maxOutputTokens: 32_768 } }
]

/** 按模型 id 匹配预设；无命中返回 null（保持表单为空） */
export function matchModelPreset(modelId: string): ModelPreset | null {
  const id = modelId.toLowerCase()
  for (const rule of PRESET_RULES) {
    if (rule.match.test(id)) {
      return { ...rule.preset }
    }
  }
  return null
}

/** token 数 → 紧凑徽标文案（1.1M / 500K / 128K / 8192） */
export function formatTokenBadge(tokens?: number): string | null {
  if (!tokens || tokens <= 0) {
    return null
  }
  if (tokens >= 1_000_000) {
    const m = tokens / 1_000_000
    return (m >= 10 ? Math.round(m) : Math.round(m * 10) / 10) + 'M'
  }
  if (tokens >= 1_000) {
    return Math.round(tokens / 1_000) + 'K'
  }
  return String(tokens)
}

/** 条目是否为视觉模型（vision 标记或输入类型含 image） */
export function isVisionModel(entry: Pick<ModelEntry, 'vision' | 'inputs'>): boolean {
  return !!entry.vision || (entry.inputs ?? []).includes('image')
}
