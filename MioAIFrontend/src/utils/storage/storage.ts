import type { UserVO } from '@/types'

const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'

interface StorageInterface {
  getToken: () => string
  setToken: (token: string) => void
  removeToken: () => void
  getUserInfo: () => UserVO | null
  setUserInfo: (info: UserVO) => void
  removeUserInfo: () => void
  clear: () => void
}

const Storage: StorageInterface = {
  getToken(): string {
    return localStorage.getItem(TOKEN_KEY) || ''
  },

  setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token)
  },

  removeToken(): void {
    localStorage.removeItem(TOKEN_KEY)
  },

  getUserInfo(): UserVO | null {
    const info = localStorage.getItem(USER_INFO_KEY)
    return info ? JSON.parse(info) : null
  },

  setUserInfo(info: UserVO): void {
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
  },

  removeUserInfo(): void {
    localStorage.removeItem(USER_INFO_KEY)
  },

  clear(): void {
    this.removeToken()
    this.removeUserInfo()
  }
}

export default Storage
