import request from '@/utils/request'

export function userRegister(data) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

export function userLogin(data) {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

export function userLogout() {
  return request({
    url: '/user/logout',
    method: 'post'
  })
}

export function getLoginUser() {
  return request({
    url: '/user/get/login',
    method: 'get'
  })
}

export function getUserVOById(id) {
  return request({
    url: `/user/get/vo/${id}`,
    method: 'get'
  })
}

export function updateUser(data) {
  return request({
    url: '/user/update',
    method: 'post',
    data
  })
}
