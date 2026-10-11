import { GROK_GEO } from './engine/geometry'

/** 图标填充：11 色板之一 / 自定义纯色 / 线性渐变 / 径向渐变 */
export type BotFill =
  | { kind: 'palette'; id: BotPaletteId }
  | { kind: 'solid'; color: string }
  | { kind: 'linear'; from: string; to: string; angle?: number }
  | { kind: 'radial'; from: string; to: string }

export type BotPaletteId =
  | 'black' | 'brown' | 'red' | 'orange' | 'yellow' | 'green'
  | 'cyan' | 'blue' | 'violet' | 'magenta' | 'gray'

/** 智能体图标配置（agent.icon 持久化为 JSON 字符串） */
export interface BotIconConfig {
  shape: string
  fill: BotFill
}

/** 色板（取引擎 palette 的 light 面，按 Grok 官方顺序） */
export const BOT_PALETTE: Array<{ id: BotPaletteId; color: string }> = [
  { id: 'black', color: '#000000' },
  { id: 'brown', color: '#A27952' },
  { id: 'red', color: '#FF3E51' },
  { id: 'orange', color: '#FF781C' },
  { id: 'yellow', color: '#FFAF38' },
  { id: 'green', color: '#00C972' },
  { id: 'cyan', color: '#1CC3B0' },
  { id: 'blue', color: '#2A92FE' },
  { id: 'violet', color: '#A97EFE' },
  { id: 'magenta', color: '#FF5EB1' },
  { id: 'gray', color: '#959595' }
]

/** 创建流程可选的 8 个形体 */
export const BOT_SHAPES: Array<{ id: string; label: string }> = [
  { id: 'blob', label: '团子' },
  { id: 'squircle', label: '方圆' },
  { id: 'hex', label: '六角' },
  { id: 'gem', label: '宝石' },
  { id: 'cloud', label: '云朵' },
  { id: 'teardrop', label: '水滴' },
  { id: 'capsule', label: '胶囊' },
  { id: 'leaf', label: '叶片' }
]

/** 默认图标（MioBot / 未配置 icon 的智能体兜底） */
export const DEFAULT_BOT_ICON: BotIconConfig = {
  shape: 'blob',
  fill: { kind: 'palette', id: 'black' }
}

const COLOR_RE = /^#[0-9a-fA-F]{3,8}$|^rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*(,\s*(0|1|0?\.\d+)\s*)?\)$/

/** 颜色值白名单：只放行 hex/rgb(a)，杜绝 url() 等被注入进 SVG 属性 */
export function safeColor(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const v = value.trim()
  return COLOR_RE.test(v) ? v : null
}

/** 解析 agent.icon JSON；非法/缺失返回 null（调用方决定兜底） */
export function parseBotIcon(raw?: string | null): BotIconConfig | null {
  if (!raw) return null
  try {
    const v = JSON.parse(raw)
    if (!v || typeof v.shape !== 'string' || !GROK_GEO.shapes[v.shape] || !v.fill) return null
    const fill = v.fill
    if (fill.kind === 'palette') {
      if (!BOT_PALETTE.some(p => p.id === fill.id)) return null
      return { shape: v.shape, fill: { kind: 'palette', id: fill.id } }
    }
    if (fill.kind === 'solid') {
      const color = safeColor(fill.color)
      return color ? { shape: v.shape, fill: { kind: 'solid', color } } : null
    }
    if (fill.kind === 'linear' || fill.kind === 'radial') {
      const from = safeColor(fill.from)
      const to = safeColor(fill.to)
      if (!from || !to) return null
      const angle = typeof fill.angle === 'number' && Number.isFinite(fill.angle) ? fill.angle : 135
      return fill.kind === 'linear'
        ? { shape: v.shape, fill: { kind: 'linear', from, to, angle } }
        : { shape: v.shape, fill: { kind: 'radial', from, to } }
    }
    return null
  } catch {
    return null
  }
}

export function serializeBotIcon(config: BotIconConfig): string {
  return JSON.stringify(config)
}
