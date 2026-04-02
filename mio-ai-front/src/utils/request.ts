import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import type { BaseResponse } from '@/types'

interface RequestConfig extends AxiosRequestConfig {
  params?: Record<string, unknown>
  data?: unknown
  skipErrorMessage?: boolean
}

let isLoggingOut = false
let lastErrorMessage = ''
let messageTimer: ReturnType<typeof setTimeout> | null = null

export function setLoggingOut(value: boolean): void {
  isLoggingOut = value
}

function showErrorMessage(msg: string): void {
  if (isLoggingOut) return
  
  if (messageTimer) {
    clearTimeout(messageTimer)
  }
  
  if (lastErrorMessage === msg) return
  lastErrorMessage = msg
  
  message.error(msg)
  
  messageTimer = setTimeout(() => {
    lastErrorMessage = ''
    messageTimer = null
  }, 2000)
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers.token = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response: AxiosResponse<BaseResponse>): AxiosResponse | Promise<never> => {
    const res = response.data
    if (res.code === 0) {
      return res.data as never
    } else {
      if (res.code === 40100) {
        if (isLoggingOut) {
          return Promise.reject(new Error('logging out'))
        }
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        showErrorMessage(res.message || '未登录')
        window.location.href = '/'
      } else {
        showErrorMessage(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  (error) => {
    if (error.response) {
      switch (error.response.status) {
        case 401:
          if (!isLoggingOut) {
            showErrorMessage('登录已过期，请重新登录')
          }
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          if (!isLoggingOut) {
            window.location.href = '/'
          }
          break
        case 403:
          showErrorMessage('没有权限访问')
          break
        case 404:
          showErrorMessage('请求资源不存在')
          break
        case 500:
          showErrorMessage('服务器错误')
          break
        default:
          if (!isLoggingOut) {
            showErrorMessage(error.message || '网络错误')
          }
      }
    } else if (!isLoggingOut) {
      showErrorMessage('网络连接失败')
    }
    return Promise.reject(error)
  }
)

export default request as {
  <T = unknown>(config: RequestConfig): Promise<T>
  get<T = unknown>(url: string, config?: RequestConfig): Promise<T>
  post<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T>
  put<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: RequestConfig): Promise<T>
}
