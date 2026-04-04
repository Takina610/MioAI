import type { ChatMessageRequest } from '@/types'
import axios from 'axios'

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

export const chatWithCSApp = (content: string, chatId: string, agentId: number, token: string
): EventSource => {
  return connectSSE('/cs/chat', { content, chatId, agentId, token })
}

export const chatWithDefaultAgent = (content: string, chatId: string, agentId: number
): EventSource => {
  return connectSSE('/chat', { content, chatId, agentId })
}

export const generateTitle = async (agentId: number, conversationId: string, content: string): Promise<string> => {
  try {
    console.log(content)
    const response = await axios.get(`${BASE_URL}/summary`, {
      params: {
        agentId,
        conversationId,
        content
      }
    })
    return response.data || '新对话'
  } catch (error) {
    console.error('Failed to generate title:', error)
    return '新对话'
  }
}

export default {
  chatWithCSApp,
  chatWithDefaultAgent,
  generateTitle
}