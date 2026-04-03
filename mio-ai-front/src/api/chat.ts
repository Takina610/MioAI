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

// AI恋爱大师聊天
export const chatWithCSApp = (content: string, chatId: string, agentId: number, token: string  // 添加 token 参数
): EventSource => {
  return connectSSE('/cs/chat', { content, chatId, agentId, token })
}

export default {
  chatWithCSApp
}

// export function connectSSE(
//   url: string,
//   data: ChatMessageRequest,
//   onMessage: (chunk: string) => void,
//   onError: (error: Error) => void,
//   onComplete: () => void
// ): AbortController | null {
//   const token = localStorage.getItem('token')
//   const fullUrl = `${BASE_URL}${url}`
  
//   const controller = new AbortController()
  
//   fetch(fullUrl, {
//     method: 'POST',
//     headers: {
//       'Content-Type': 'application/json',
//       'Accept': 'text/event-stream',
//       'token': token || ''
//     },
//     body: JSON.stringify(data),
//     signal: controller.signal
//   })
//     .then(response => {
//       if (!response.ok) {
//         throw new Error(`HTTP error! status: ${response.status}`)
//       }

//       const reader = response.body?.getReader()
//       const decoder = new TextDecoder()

//       if (!reader) {
//         throw new Error('Response body is null')
//       }

//       let buffer = ''
      
//       const readChunk = (): Promise<void> => {
//         return reader.read().then(({ done, value }) => {
//           if (done) {
//             onComplete()
//             return
//           }

//           buffer += decoder.decode(value, { stream: true })
//           const lines = buffer.split('\n')
//           buffer = lines.pop() || ''
          
//           for (const line of lines) {
//             const trimmedLine = line.trim()
//             if (!trimmedLine || trimmedLine.startsWith(':')) continue
            
//             if (trimmedLine.startsWith('data:')) {
//               let eventData = trimmedLine.slice(5).trim()
              
//               if (eventData === '[DONE]') {
//                 onComplete()
//                 return
//               } else if (eventData) {
//                 onMessage(eventData)
//               }
//             }
//           }

//           return readChunk()
//         })
//       }

//       return readChunk()
//     })
//     .catch(error => {
//       if (error.name !== 'AbortError') {
//         onError(error as Error)
//       }
//     })

//   return controller
// }

// export function chatWithCSApp(
//   data: ChatMessageRequest,
//   onMessage: (chunk: string) => void,
//   onError: (error: Error) => void,
//   onComplete: () => void
// ): AbortController | null {
//   return connectSSE('/cs/chat', data, onMessage, onError, onComplete)
// }

// export function chatWithMioManus(
//   data: ChatMessageRequest,
//   onMessage: (chunk: string) => void,
//   onError: (error: Error) => void,
//   onComplete: () => void
// ): AbortController | null {
//   return connectSSE('/mio/chat', data, onMessage, onError, onComplete)
// }

