import request from '@/utils/request'

export function addKnowledgeBase(data) {
  return request({
    url: '/knowledge-bases',
    method: 'post',
    data
  })
}

export function updateKnowledgeBase(data) {
  return request({
    url: '/knowledge-bases',
    method: 'put',
    data
  })
}

export function deleteKnowledgeBase(id) {
  return request({
    url: `/knowledge-bases/${id}`,
    method: 'delete'
  })
}

export function getKnowledgeBaseById(id) {
  return request({
    url: `/knowledge-bases/${id}`,
    method: 'get'
  })
}

export function queryKnowledgeBases(data) {
  return request({
    url: '/knowledge-bases/list',
    method: 'post',
    data
  })
}

export function getPublicKnowledgeBases(params) {
  return request({
    url: '/knowledge-bases/market',
    method: 'get',
    params
  })
}
