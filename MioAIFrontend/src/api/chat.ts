import type { ChatMessageRequest } from '@/types'
import request from '@/utils/request'

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

export const chatWithMioManus = (content: string, chatId: string, agentId: number, token: string
): EventSource => {
  return connectSSE('/mio/chat', { content, chatId, agentId, token })
}

export const chatWithDefaultAgent = (content: string, chatId: string, agentId: number, userId: number | null
): EventSource => {
  return connectSSE('/chat', { content, chatId, agentId, userId: userId ?? '' })
}

export const chatWithCustomAgent = (content: string, chatId: string, agentId: number, token: string
): EventSource => {
  return connectSSE('/custom/chat', { content, chatId, agentId, token })
}

export const generateTitle = async (
  agentId: number,
  conversationId: string,
  content: string
): Promise<string> => {
  try {
    const response = await request.post<string>('/summary', {
      conversationId,
      agentId,
      content
    })
    return response || '新对话'
  } catch (error) {
    console.error('Failed to generate title:', error)
    return '新对话'
  }
}

// 走本地大模型的情况
export interface ChatStreamController {
  close: () => void
}
export const chatWithStream = (
  content: string,
  chatId: string,
  agentId: number,
  token: string,
  userId: number | null,
  history: Array<{ role: string; content: string }>,
  onMessage: (data: string) => void,
  onError: (error: any) => void
): ChatStreamController => {
  const provider = localStorage.getItem('ai-model-provider') || 'dashscope'

  if (provider === 'ollama') {
    const abortController = new AbortController()

    fetch('http://localhost:11434/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: localStorage.getItem('ollama-model') || 'deepseek-r1:1.5b',
        messages: history,
        stream: true
      }),
      signal: abortController.signal
    }).then(async (response) => {
      if (!response.ok) {
        const errText = await response.text().catch(() => 'Ollama 请求失败')
        onError(new Error(errText))
        return
      }
      if (!response.body) {
        onError(new Error('无响应体'))
        return
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let lineBuffer = ''

      const handleLine = (line: string): void => {
        if (!line.trim()) return
        try {
          const data = JSON.parse(line)
          if (data.message?.content) {
            onMessage(data.message.content)
          }
          if (data.done) {
            onMessage('[DONE]')
          }
        } catch {
          // 忽略解析失败的行
        }
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        lineBuffer += decoder.decode(value, { stream: true })
        const lines = lineBuffer.split('\n')
        // 最后一段可能被 TCP 分包截断，留到下一轮拼接，避免丢 token
        lineBuffer = lines.pop() ?? ''

        for (const line of lines) {
          handleLine(line)
        }
      }
      if (lineBuffer) {
        handleLine(lineBuffer)
      }
    }).catch(onError)

    return { close: () => abortController.abort() }
  }

  // 原有的 SSE 逻辑
  const isDefaultAgent = agentId === 1
  let url = ''
  let params: Record<string, any> = {}

  if (isDefaultAgent) {
    url = '/chat'
    params = { content, chatId, agentId, userId: userId ?? '' }
  } else if (agentId === 2) {
    url = '/cs/chat'
    params = { content, chatId, agentId, token }
  } else if (agentId === 3) {
    url = '/mio/chat'
    params = { content, chatId, agentId, token }
  } else {
    url = '/custom/chat'
    params = { content, chatId, agentId, token }
  }

  const es = connectSSE(url, params)
  es.onmessage = (event: MessageEvent) => {
    const data = event.data
    if (data === '[DONE]') {
      onMessage('[DONE]')
    } else {
      onMessage(data)
    }
  }
  es.onerror = onError

  return { close: () => es.close() }
}

export default {
  chatWithCSApp,
  chatWithDefaultAgent,
  chatWithMioManus,
  chatWithCustomAgent,
  generateTitle,
  chatWithStream
}
