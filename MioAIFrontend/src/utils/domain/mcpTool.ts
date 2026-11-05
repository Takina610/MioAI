import type { McpToolInfo } from '@/types'

/** 解析 MCP 工具的 toolInfo JSON 字符串，失败返回空数组 */
export function parseMcpTools(toolInfo?: string | null): McpToolInfo[] {
  if (!toolInfo) return []
  try {
    const parsed = JSON.parse(toolInfo)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

/** MCP 工具包含的子工具数量 */
export function countMcpTools(toolInfo?: string | null): number {
  return parseMcpTools(toolInfo).length
}

const ERROR_TYPE_LABELS: Record<string, string> = {
  CONNECTION_FAILED: '连接失败',
  AUTH_FAILED: '认证失败',
  TIMEOUT: '连接超时',
  PROCESS_START_FAILED: '启动失败',
  FORBIDDEN: '无权限'
}

/**
 * 校验失败文案：后端错误消息已自带类型前缀（"认证失败：服务端返回 401…"），
 * 仅在缺失时补前缀，避免"连接失败: 连接失败，请检查…"式重复；CONFIG_INVALID 的
 * 后端消息本身自明（"配置JSON格式无效…"），不加前缀
 */
export function formatValidateError(errorType?: string, errorMessage?: string): string {
  const msg = errorMessage?.trim() || '未知错误'
  const label = ERROR_TYPE_LABELS[errorType || '']
  return label && !msg.startsWith(label) ? `${label}：${msg}` : msg
}
