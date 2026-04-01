import request from '@/utils/request'

export function addAgent(data) {
  return request({
    url: '/agents',
    method: 'post',
    data
  })
}

export function updateAgent(data) {
  return request({
    url: '/agents',
    method: 'put',
    data
  })
}

export function deleteAgent(id) {
  return request({
    url: `/agents/${id}`,
    method: 'delete'
  })
}

export function getAgentById(id) {
  return request({
    url: `/agents/${id}`,
    method: 'get'
  })
}

export function queryAgents(data) {
  return request({
    url: '/agents/list',
    method: 'post',
    data
  })
}

export function getPublicAgents(params) {
  return request({
    url: '/agents/market',
    method: 'get',
    params
  })
}
