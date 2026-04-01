import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userLogin, userLogout, getLoginUser, userRegister } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const userName = computed(() => userInfo.value?.userName || '用户')
  const userAvatar = computed(() => userInfo.value?.userAvatar || '')

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  async function login(loginData) {
    const res = await userLogin(loginData)
    setToken(res.token)
    setUserInfo(res)
    return res
  }

  async function register(registerData) {
    const res = await userRegister(registerData)
    return res
  }

  async function logout() {
    try {
      await userLogout()
    } catch (e) {
      console.error('logout error:', e)
    } finally {
      clearUser()
    }
  }

  function clearUser() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  async function fetchUserInfo() {
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
    login,
    register,
    logout,
    clearUser,
    fetchUserInfo
  }
})
