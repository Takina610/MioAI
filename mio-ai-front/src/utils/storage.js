const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'

const Storage = {
  getToken() {
    return localStorage.getItem(TOKEN_KEY) || ''
  },

  setToken(token) {
    localStorage.setItem(TOKEN_KEY, token)
  },

  removeToken() {
    localStorage.removeItem(TOKEN_KEY)
  },

  getUserInfo() {
    const info = localStorage.getItem(USER_INFO_KEY)
    return info ? JSON.parse(info) : null
  },

  setUserInfo(info) {
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
  },

  removeUserInfo() {
    localStorage.removeItem(USER_INFO_KEY)
  },

  clear() {
    this.removeToken()
    this.removeUserInfo()
  }
}

export default Storage
