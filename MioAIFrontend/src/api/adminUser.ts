import request from '@/utils/request'
import type { PageResponse } from '@/types'

export interface UserAdminVO {
  id: number
  userAccount: string
  userName: string
  userAvatar: string
  userProfile: string
  userRole: string
  createTime: string
}

export interface UserAdminQueryRequest {
  current?: number
  pageSize?: number
  /** 模糊搜索关键词（匹配用户名或账号） */
  keyword?: string
  userName?: string
  userAccount?: string
  userRole?: string
  sortField?: string
  sortOrder?: string
}

export interface UserAdminUpdateRequest {
  id: number
  userName?: string
  userProfile?: string
  userRole?: string
  userAvatar?: string
}

export function searchUsers(data: UserAdminQueryRequest): Promise<PageResponse<UserAdminVO>> {
  return request({
    url: '/admin/users/search',
    method: 'post',
    data
  })
}

export function getUserById(id: number): Promise<UserAdminVO> {
  return request({
    url: `/admin/users/${id}`,
    method: 'get'
  })
}

export function updateUser(data: UserAdminUpdateRequest): Promise<boolean> {
  return request({
    url: '/admin/users',
    method: 'put',
    data
  })
}

export function deleteUser(id: number): Promise<boolean> {
  return request({
    url: `/admin/users/${id}`,
    method: 'delete'
  })
}
