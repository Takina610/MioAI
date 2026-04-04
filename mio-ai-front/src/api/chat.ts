import type { ChatMessageRequest } from '@/types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

// 定义参数类型
interface ConnectSSEParams {
  [key: string]: string | number | boolean;  // 支持多种参数类型
}

// 定义回调函数类型
type OnMessageCallback = (data: string) => void;
type OnErrorCallback = (error: Event) => void;

// 连接 SSE 函数
export const connectSSE = (
  url: string,
  params: ConnectSSEParams,
  onMessage?: OnMessageCallback,
  onError?: OnErrorCallback
): EventSource => {
  // 构建带参数的URL
  const queryParams = { ...params }
  
  const queryString = Object.keys(queryParams)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(String(queryParams[key]))}`)
    .join('&')
  
  const fullUrl = `${BASE_URL}${url}?${queryString}`
  
  // 创建EventSource
  const eventSource = new EventSource(fullUrl)
  
  eventSource.onmessage = (event: MessageEvent) => {
    const data = event.data
    
    // 检查是否是特殊标记
    if (data === '[DONE]') {
      onMessage?.('[DONE]')
    } else {
      // 处理普通消息
      onMessage?.(data)
    }
  }
  
  eventSource.onerror = (error: Event) => {
    onError?.(error)
    eventSource.close()
  }
  
  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

export const chatWithCSApp = (content: string, chatId: string, agentId: number, token: string  // 添加 token 参数
): EventSource => {
  return connectSSE('/cs/chat', { content, chatId, agentId, token })
}

export default {
  chatWithCSApp
}