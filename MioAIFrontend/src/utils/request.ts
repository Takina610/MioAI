import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import router from '@/router'
import type { BaseResponse } from '@/types'

/** 登录态失效事件：store 监听后同步清理内存态，页面随之降级为游客视图 */
export const UNAUTHORIZED_EVENT = 'mio:unauthorized'

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
        window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
        showErrorMessage(res.message || '未登录')
        // 仅在需要登录的页面强制回首页；公开页面（首页/对话页）就地降级为游客态
        if (router.currentRoute.value.meta.requiresAuth) {
          window.location.href = '/'
        }
      } else {
        const cfg = response.config as RequestConfig | undefined
        if (!cfg?.skipErrorMessage) {
          showErrorMessage(res.message || '请求失败')
        }
      }
      // 挂上业务错误码，调用方可按 code 分支处理（如会话不存在时自动回新对话）
      const bizError = new Error(res.message || '请求失败') as Error & { code?: number }
      bizError.code = res.code
      return Promise.reject(bizError)
    }
  },
  (error) => {
    const cfg = error?.config as RequestConfig | undefined
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
          if (!cfg?.skipErrorMessage) showErrorMessage('没有权限访问')
          break
        case 404:
          if (!cfg?.skipErrorMessage) showErrorMessage('请求资源不存在')
          break
        case 500:
          if (!cfg?.skipErrorMessage) showErrorMessage('服务器错误')
          break
        default:
          if (!isLoggingOut && !cfg?.skipErrorMessage) {
            showErrorMessage(error.message || '网络错误')
          }
      }
    } else if (!isLoggingOut && !cfg?.skipErrorMessage) {
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
