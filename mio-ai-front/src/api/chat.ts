import type { ChatMessageRequest } from '@/types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export function connectSSE(
  url: string,
  data: ChatMessageRequest,
  onMessage: (chunk: string) => void,
  onError: (error: Error) => void,
  onComplete: () => void
): AbortController | null {
  const token = localStorage.getItem('token')
  const fullUrl = `${BASE_URL}${url}`
  
  const controller = new AbortController()
  
  fetch(fullUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      'token': token || ''
    },
    body: JSON.stringify(data),
    signal: controller.signal
  })
    .then(response => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()

      if (!reader) {
        throw new Error('Response body is null')
      }

      let buffer = ''
      
      const readChunk = (): Promise<void> => {
        return reader.read().then(({ done, value }) => {
          if (done) {
            onComplete()
            return
          }

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          
          for (const line of lines) {
            const trimmedLine = line.trim()
            if (!trimmedLine || trimmedLine.startsWith(':')) continue
            
            if (trimmedLine.startsWith('data:')) {
              let eventData = trimmedLine.slice(5).trim()
              
              if (eventData === '[DONE]') {
                onComplete()
                return
              } else if (eventData) {
                onMessage(eventData)
              }
            }
          }

          return readChunk()
        })
      }

      return readChunk()
    })
    .catch(error => {
      if (error.name !== 'AbortError') {
        onError(error as Error)
      }
    })

  return controller
}

export function chatWithCSApp(
  data: ChatMessageRequest,
  onMessage: (chunk: string) => void,
  onError: (error: Error) => void,
  onComplete: () => void
): AbortController | null {
  return connectSSE('/cs/chat', data, onMessage, onError, onComplete)
}

export function chatWithMioManus(
  data: ChatMessageRequest,
  onMessage: (chunk: string) => void,
  onError: (error: Error) => void,
  onComplete: () => void
): AbortController | null {
  return connectSSE('/mio/chat', data, onMessage, onError, onComplete)
}
