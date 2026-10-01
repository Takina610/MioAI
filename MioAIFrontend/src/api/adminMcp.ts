import request from '@/utils/request'
import type { PageResponse } from '@/types'
import type { McpTool, McpToolQueryRequest } from '@/types/mcpTool'

export interface McpAdminUpdateRequest {
  id: number
  name?: string
  description?: string
  status?: number
  isPublic?: number
}

export function searchMcpTools(data: McpToolQueryRequest & { name?: string }): Promise<PageResponse<McpTool>> {
  return request({
    url: '/admin/mcp/search',
    method: 'post',
    data
  })
}

export function getMcpToolById(id: number): Promise<McpTool> {
  return request({
    url: `/admin/mcp/${id}`,
    method: 'get'
  })
}

export function updateMcpTool(data: McpAdminUpdateRequest): Promise<boolean> {
  return request({
    url: '/admin/mcp',
    method: 'put',
    data
  })
}

export function deleteMcpTool(id: number): Promise<boolean> {
  return request({
    url: `/admin/mcp/${id}`,
    method: 'delete'
  })
}
