import request from '@/utils/request'
import type {
  McpTool,
  McpToolAddRequest,
  McpToolUpdateRequest,
  McpToolQueryRequest,
  McpValidateRequest,
  McpValidateResult,
  PageResponse
} from '@/types'

export function addMcpTool(data: McpToolAddRequest): Promise<number> {
  return request({
    url: '/mcp',
    method: 'post',
    data
  })
}

export function updateMcpTool(data: McpToolUpdateRequest): Promise<boolean> {
  return request({
    url: '/mcp',
    method: 'put',
    data
  })
}

export function deleteMcpTool(id: number): Promise<boolean> {
  return request({
    url: `/mcp/${id}`,
    method: 'delete'
  })
}

export function getMcpToolById(id: number): Promise<McpTool> {
  return request({
    url: `/mcp/${id}`,
    method: 'get'
  })
}

export function queryMcpTools(data: McpToolQueryRequest): Promise<PageResponse<McpTool>> {
  return request({
    url: '/mcp/list',
    method: 'post',
    data
  })
}

export function getPublicMcpTools(params?: { current?: number; size?: number }): Promise<PageResponse<McpTool>> {
  return request({
    url: '/mcp/market',
    method: 'get',
    params
  })
}

export function validateMcpConfig(data: McpValidateRequest): Promise<McpValidateResult> {
  return request({
    url: '/mcp-tools/validate',
    method: 'post',
    data
  })
}
