import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userLogin, userLogout, getLoginUser, userRegister } from '@/api/user'
import { setLoggingOut, UNAUTHORIZED_EVENT } from '@/utils/request'
import type { LoginRequest, RegisterRequest, LoginResponse, UserVO } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserVO | null>(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed<boolean>(() => !!token.value)
  const userName = computed<string>(() => userInfo.value?.userName || '用户')
  const userAvatar = computed<string>(() => userInfo.value?.userAvatar || '')

  // 登录态在服务端失效（40100）时，把内存态一并清掉，页面立即降级为游客视图；
  // store 与应用同生命周期，监听器无需注销
  window.addEventListener(UNAUTHORIZED_EVENT, () => clearUser())

  function setToken(newToken: string): void {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: UserVO): void {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function setUserAvatar(avatar: string): void {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, userAvatar: avatar }
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    }
  }

  async function login(loginData: LoginRequest): Promise<LoginResponse> {
    const res = await userLogin(loginData)
    setToken(res.token)
    setUserInfo({
      id: res.id,
      userName: res.userName,
      userAccount: res.userAccount,
      userAvatar: res.userAvatar,
      userProfile: res.userProfile,
      userRole: res.userRole,
      createTime: ''
    })
    return res
  }

  async function register(registerData: RegisterRequest): Promise<unknown> {
    const res = await userRegister(registerData)
    return res
  }

  async function logout(): Promise<void> {
    setLoggingOut(true)
    try {
      await userLogout()
    } catch (e) {
      console.error('logout error:', e)
    } finally {
      clearUser()
      setLoggingOut(false)
    }
  }

  function clearUser(): void {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  async function fetchUserInfo(): Promise<UserVO | null> {
    if (!token.value) return null
    try {
      const res = await getLoginUser()
      setUserInfo(res)
      return res
    } catch (e) {
      clearUser()
      throw e
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    userName,
    userAvatar,
    setToken,
    setUserInfo,
    setUserAvatar,
    login,
    register,
    logout,
    clearUser,
    fetchUserInfo
  }
})
