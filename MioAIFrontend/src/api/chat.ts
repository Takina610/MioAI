import type { ChatMessageRequest } from '@/types'
import request from '@/utils/request'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

// 定义参数类型
interface ConnectSSEParams {
  [key: string]: string | number | boolean
}

type OnMessageCallback = (data: string) => void
type OnErrorCallback = (error: Event) => void

/** 连接 SSE（GET + query 参数；token 缺省时后端按游客处理） */
export const connectSSE = (
  url: string,
  params: ConnectSSEParams,
  onMessage?: OnMessageCallback,
  onError?: OnErrorCallback
): EventSource => {
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(String(params[key]))}`)
    .join('&')

  const eventSource = new EventSource(`${BASE_URL}${url}?${queryString}`)

  eventSource.onmessage = (event: MessageEvent) => {
    onMessage?.(event.data)
  }

  eventSource.onerror = (error: Event) => {
    onError?.(error)
    eventSource.close()
  }

  return eventSource
}

/** 当前模型实际支持的思考档位（后端探测上游能力，前端据此如实渲染档位选择器）；
 * 失败静默（前端有兜底档位，不该弹全局错误） */
export const getReasoningEfforts = async (): Promise<{ efforts: string[] }> => {
  return request.get('/bot/efforts', { skipErrorMessage: true } as never)
}

/**
 * 与智能体对话（统一走 MioBot 流式 Agent 引擎，SSE 信封返回全过程）：
 * MioBot（agentId=1，系统内置）与用户自定义智能体共用 /bot/chat。
 * skipUserPersist：重新生成场景，用户消息已在历史中，后端不再重复落库
 */
export const chatWithMioBot = (
  content: string,
  chatId: string,
  agentId: number,
  token: string,
  opts: { skipUserPersist?: boolean; reasoningEffort?: string; groupSeq?: number } = {}
): EventSource => {
  const params: ConnectSSEParams = { content, chatId, agentId, token }
  if (opts.skipUserPersist) {
    params.skipUserPersist = true
  }
  if (opts.reasoningEffort) {
    params.reasoningEffort = opts.reasoningEffort
  }
  if (opts.groupSeq != null) {
    params.groupSeq = opts.groupSeq
  }
  return connectSSE('/bot/chat', params)
}

/** 截断会话历史（编辑消息/重新生成共用）：保留 seq <= keepThroughSeq 的消息并重建记忆 */
export const truncateConversation = async (
  conversationId: string,
  keepThroughSeq: number,
  groupKey?: number
): Promise<{ keptMessages: number; userContent?: string }> => {
  return request.post(`/bot/truncate/${conversationId}`, null, {
    params: groupKey != null ? { keepThroughSeq, groupKey } : { keepThroughSeq }
  })
}

/** 回答 Agent 的提问（AskUserQuestion）：解锁该会话挂起的问答门闸 */
export const answerQuestion = async (
  chatId: string,
  id: string,
  answers: Array<{ index: number; selections: string[]; custom?: string }>
): Promise<boolean> => {
  const result = await request.post<{ resolved: boolean }>('/bot/answer', { chatId, id, answers })
  return Boolean(result?.resolved)
}

/** 会话是否正在后端执行（孤儿回合恢复轮询据此快速失败） */
export const isChatActive = async (chatId: string): Promise<boolean> => {
  const result = await request.get<boolean>(`/bot/active/${chatId}`, { skipErrorMessage: true } as never)
  return Boolean(result)
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
    } satisfies ChatMessageRequest)
    return response || '新对话'
  } catch (error) {
    console.error('Failed to generate title:', error)
    return '新对话'
  }
}

// 本地大模型（Ollama 直连）
export interface ChatStreamController {
  close: () => void
}

export const chatWithStream = (
  content: string,
  chatId: string,
  agentId: number,
  token: string,
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

  const es = chatWithMioBot(content, chatId, agentId, token)
  es.onmessage = (event: MessageEvent) => {
    onMessage(event.data)
  }
  es.onerror = onError

  return { close: () => es.close() }
}
