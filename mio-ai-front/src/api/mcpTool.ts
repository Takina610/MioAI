import request from '@/utils/request'
import type {
  McpTool,
  McpToolAddRequest,
  McpToolUpdateRequest,
  McpToolQueryRequest,
  PageResponse
} from '@/types'

export function addMcpTool(data: McpToolAddRequest): Promise<number> {
  return request({
    url: '/mcp-tools',
    method: 'post',
    data
  })
}

export function updateMcpTool(data: McpToolUpdateRequest): Promise<boolean> {
  return request({
    url: '/mcp-tools',
    method: 'put',
    data
  })
}

export function deleteMcpTool(id: number): Promise<boolean> {
  return request({
    url: `/mcp-tools/${id}`,
    method: 'delete'
  })
}

export function getMcpToolById(id: number): Promise<McpTool> {
  return request({
    url: `/mcp-tools/${id}`,
    method: 'get'
  })
}

export function queryMcpTools(data: McpToolQueryRequest): Promise<PageResponse<McpTool>> {
  return request({
    url: '/mcp-tools/list',
    method: 'post',
    data
  })
}

export function getPublicMcpTools(params?: { current?: number; size?: number }): Promise<PageResponse<McpTool>> {
  return request({
    url: '/mcp-tools/market',
    method: 'get',
    params
  })
}
