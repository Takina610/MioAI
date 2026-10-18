/** ECharts 共享主题：暖米色系配色 + 统一字体/网格/提示框（与 Apex 图表同源 token） */

export const ECHARTS_PALETTE = ['#2aa1a9', '#d98f45', '#8b6fd6', '#4f9e63', '#263749', '#c0453a']

export const ECHARTS_FONT_FAMILY =
  "'AnthropicSans Text', 'PingFang SC', 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif"

export const ECHARTS_AXIS_COLOR = '#8c8a82'
export const ECHARTS_SPLIT_LINE = '#e8e6dc'
export const ECHARTS_TEXT_COLOR = '#141413'
export const ECHARTS_TIP_BG = '#faf9f5'
export const ECHARTS_TIP_BORDER = '#e8e6dc'

/** 通用 tooltip 样式（暖米底 + 细边框 + 轻阴影） */
export function chartTooltip(): Record<string, unknown> {
  return {
    backgroundColor: ECHARTS_TIP_BG,
    borderColor: ECHARTS_TIP_BORDER,
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: ECHARTS_TEXT_COLOR, fontSize: 12, fontFamily: ECHARTS_FONT_FAMILY },
    extraCssText: 'box-shadow: 0 2px 8px rgba(20,20,19,0.08); border-radius: 8px;'
  }
}

/** 坐标轴通用样式 */
export function axisStyle(): Record<string, unknown> {
  return {
    axisLine: { lineStyle: { color: ECHARTS_SPLIT_LINE } },
    axisTick: { show: false },
    axisLabel: { color: ECHARTS_AXIS_COLOR, fontSize: 11, fontFamily: ECHARTS_FONT_FAMILY },
    splitLine: { lineStyle: { color: ECHARTS_SPLIT_LINE, type: 'dashed' } }
  }
}

/** 坐标轴大数缩写（完整数值在 tooltip 里） */
export function compactAxis(value: number): string {
  if (Math.abs(value) >= 1_000_000_000) {
    return trim(value / 1_000_000_000) + 'B'
  }
  if (Math.abs(value) >= 1_000_000) {
    return trim(value / 1_000_000) + 'M'
  }
  if (Math.abs(value) >= 1_000) {
    return trim(value / 1_000) + 'K'
  }
  return String(value)
}

function trim(value: number): string {
  return value.toFixed(value >= 100 ? 0 : 1).replace(/\.0$/, '')
}

/** 主色的透明度梯度：多层级节点/系列不引入新色相 */
export function tealAlpha(alpha: number): string {
  return `rgba(42, 161, 169, ${alpha})`
}

/** HTML 转义（tooltip 拼串防注入） */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
