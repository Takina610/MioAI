import type { PaginationParams } from './common'

export interface McpTool {
  id: number
  userId: number
  userName?: string
  name: string
  description?: string
  config?: string
  toolInfo?: string
  status: number
  statusDesc?: string
  isPublic: number
  createTime: string
  updateTime: string
}

export interface McpToolAddRequest {
  name: string
  description?: string
  config?: string
  toolInfo?: string
  status?: number
}

export interface McpToolUpdateRequest {
  id: number
  name?: string
  description?: string
  config?: string
  toolInfo?: string
  status?: number
  isPublic?: number
}

export interface McpToolQueryRequest extends PaginationParams {
  status?: number
  userId?: number
}

export interface McpValidateRequest {
  config: string
}

export interface McpToolInfo {
  name: string
  description: string
  inputSchema?: string
}

export interface McpServerInfo {
  name?: string
  version?: string
  protocolVersion?: string
}

export interface McpValidateResult {
  success: boolean
  errorMessage?: string
  errorType?: 'CONFIG_INVALID' | 'CONNECTION_FAILED' | 'AUTH_FAILED' | 'TIMEOUT' | 'UNKNOWN'
  warnings?: string[]
  tools?: McpToolInfo[]
  serverInfo?: McpServerInfo
}
