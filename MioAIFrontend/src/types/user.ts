export interface User {
  id: number
  userName: string
  userAccount: string
  userAvatar?: string
  userProfile?: string
  userRole: string
  createTime: string
  updateTime: string
}

export interface UserVO {
  id: number
  userName: string
  userAccount: string
  userAvatar?: string
  userProfile?: string
  userRole: string
  createTime: string
}

export interface LoginRequest {
  userAccount: string
  userPassword: string
}

export interface RegisterRequest {
  userAccount: string
  userPassword: string
  checkPassword: string
}

export interface LoginResponse {
  token: string
  id: number
  userName: string
  userAccount: string
  userAvatar?: string
  userProfile?: string
  userRole: string
}

export interface UpdateUserRequest {
  id?: number
  userName?: string
  userProfile?: string
}

export interface PasswordUpdateRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}
