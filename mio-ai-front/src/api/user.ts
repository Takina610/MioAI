import request from '@/utils/request'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserVO,
  UpdateUserRequest
} from '@/types'

export function userRegister(data: RegisterRequest): Promise<unknown> {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

export function userLogin(data: LoginRequest): Promise<LoginResponse> {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

export function userLogout(): Promise<unknown> {
  return request({
    url: '/user/logout',
    method: 'post'
  })
}

export function getLoginUser(): Promise<UserVO> {
  return request({
    url: '/user/get/login',
    method: 'get'
  })
}

export function getUserVOById(id: number): Promise<UserVO> {
  return request({
    url: `/user/get/vo/${id}`,
    method: 'get'
  })
}

export function updateUser(data: UpdateUserRequest): Promise<boolean> {
  return request({
    url: '/user/update',
    method: 'post',
    data
  })
}
