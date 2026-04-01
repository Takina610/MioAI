import request from '@/utils/request'

export function addMcpTool(data) {
  return request({
    url: '/mcp-tools',
    method: 'post',
    data
  })
}

export function updateMcpTool(data) {
  return request({
    url: '/mcp-tools',
    method: 'put',
    data
  })
}

export function deleteMcpTool(id) {
  return request({
    url: `/mcp-tools/${id}`,
    method: 'delete'
  })
}

export function getMcpToolById(id) {
  return request({
    url: `/mcp-tools/${id}`,
    method: 'get'
  })
}

export function queryMcpTools(data) {
  return request({
    url: '/mcp-tools/list',
    method: 'post',
    data
  })
}

export function getPublicMcpTools(params) {
  return request({
    url: '/mcp-tools/market',
    method: 'get',
    params
  })
}
