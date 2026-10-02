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
