import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { snapshotHistory, useMessageActions } from './useMessageActions'
import type { ChatMessage } from '@/types'
import type { ChatMessagesApi } from './useChatMessages'

vi.mock('@/api/chat', () => ({
  truncateConversation: vi.fn(() => Promise.resolve()),
  deleteAttachment: vi.fn(() => Promise.resolve())
}))

vi.mock('@/api/chat/messages', () => ({
  getBotMessages: vi.fn(async () => [
    { role: 'user', seq: 1 },
    { role: 'assistant', seq: 2 }
  ])
}))

const { truncateConversation } = await import('@/api/chat')
const { getBotMessages } = await import('@/api/chat/messages')

/** 按场景替换服务端行（默认两行） */
function serverRows(rows: Array<{ role: string; seq: number }>): void {
  vi.mocked(getBotMessages).mockResolvedValue(rows as never)
}

function msg(id: string, role: 'user' | 'assistant', content: string, extra: Partial<ChatMessage> = {}): ChatMessage {
  return { id, role, content, createTime: new Date(), ...extra }
}

/** 最小 ChatMessagesApi 桩（只实现被测路径用到的成员） */
function fakeMessagesApi(store: Map<string, ChatMessage[]>, loading = new Map<string, boolean>()): ChatMessagesApi {
  return {
    chatLoadingMap: ref(loading),
    getChatMessages: (id: string) => store.get(id) ?? [],
    setChatMessages: (id: string, msgs: ChatMessage[]) => { store.set(id, msgs) }
  } as unknown as ChatMessagesApi
}

function mountActions(store: Map<string, ChatMessage[]>, chatId = 'chat-1', loading = new Map<string, boolean>()) {
  let captured!: ReturnType<typeof useMessageActions>
  const sendMessage = vi.fn()
  const currentChatId = ref(chatId)
  const Comp = defineComponent({
    setup() {
      captured = useMessageActions({
        messagesApi: fakeMessagesApi(store, loading),
        currentChatId: () => currentChatId.value,
        sendMessage,
        reasoningEffort: () => 'high',
        currentModel: () => ({ source: 'opencode', providerId: null, model: 'm' }),
        currentProvider: () => null,
        currentModelContext: () => ({ contextWindow: 1000 })
      })
      return () => null
    }
  })
  const wrapper = mount(Comp, { global: { plugins: [createPinia()] } })
  return { api: captured, sendMessage, currentChatId, unmount: () => wrapper.unmount() }
}

describe('snapshotHistory', () => {
  it('非空回复加入历史并清掉嵌套版本', () => {
    const old = msg('a1', 'assistant', 'old', { history: [msg('h', 'assistant', 'x')], activeVersion: 1 })
    const out = snapshotHistory(old)!
    expect(out.length).toBe(1)
    expect(out[0]!.content).toBe('old')
    expect(out[0]!.history).toBeUndefined()
    expect(out[0]!.activeVersion).toBeUndefined()
  })

  it('空回复不留版本', () => {
    expect(snapshotHistory(msg('a1', 'assistant', ''))).toBeUndefined()
    expect(snapshotHistory(undefined)).toBeUndefined()
  })

  it('保留既有历史并追加新快照', () => {
    const prior = [msg('h1', 'assistant', 'first')]
    const out = snapshotHistory(msg('a2', 'assistant', 'second'), prior)!
    expect(out.map(m => m.content)).toEqual(['first', 'second'])
  })

  it('有 blocks 也算非空', () => {
    const withBlocks = msg('a1', 'assistant', '', { blocks: [{ type: 'text', text: 'x' }] })
    expect(snapshotHistory(withBlocks)!.length).toBe(1)
  })
})

describe('useMessageActions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.setItem('token', 'x')
    vi.clearAllMocks()
  })

  it('未登录时任何操作都被拦截', async () => {
    localStorage.clear()
    const store = new Map([['chat-1', [msg('u1', 'user', 'hi')]]])
    const { api, sendMessage } = mountActions(store)
    await api.handleRegenerate()
    expect(sendMessage).not.toHaveBeenCalled()
  })

  it('会话执行中拦截编辑并提示', async () => {
    const store = new Map([['chat-1', [msg('u1', 'user', 'hi')]]])
    const { api, sendMessage } = mountActions(store, 'chat-1', new Map([['chat-1', true]]))
    await api.handleRegenerate()
    expect(sendMessage).not.toHaveBeenCalled()
    expect(truncateConversation).not.toHaveBeenCalled()
  })

  it('重新生成：截断到末条 user 后重发，旧回复作为历史', async () => {
    const store = new Map([['chat-1', [
      msg('u1', 'user', '问题'),
      msg('a1', 'assistant', '回答')
    ]]])
    const { api, sendMessage } = mountActions(store)
    await api.handleRegenerate()
    expect(truncateConversation).toHaveBeenCalledWith('chat-1', 1, 1)
    expect(store.get('chat-1')!.map(m => m.id)).toEqual(['u1'])
    expect(sendMessage).toHaveBeenCalledTimes(1)
    const [content, opts] = sendMessage.mock.calls[0]!
    expect(content).toBe('问题')
    expect(opts.skipUserMessage).toBe(true)
    expect(opts.skipUserPersist).toBe(true)
    expect(opts.history!.map((m: ChatMessage) => m.content)).toEqual(['回答'])
  })

  it('重新生成：没有 user 消息时不做动作', async () => {
    const store = new Map([['chat-1', [msg('a1', 'assistant', '回答')]]])
    const { api, sendMessage } = mountActions(store)
    await api.handleRegenerate()
    expect(sendMessage).not.toHaveBeenCalled()
    expect(truncateConversation).not.toHaveBeenCalled()
  })

  it('编辑：截断到被编辑消息之前并重发，保留引用', async () => {
    serverRows([
      { role: 'user', seq: 1 },
      { role: 'assistant', seq: 2 },
      { role: 'user', seq: 3 },
      { role: 'assistant', seq: 4 }
    ])
    const store = new Map([['chat-1', [
      msg('u1', 'user', '第一问'),
      msg('a1', 'assistant', '第一答'),
      msg('u2', 'user', '第二问'),
      msg('a2', 'assistant', '第二答')
    ]]])
    const { api, sendMessage } = mountActions(store)
    await api.handleEditMessage(2, '改后的问题')
    expect(store.get('chat-1')!.map(m => m.id)).toEqual(['u1', 'a1'])
    expect(sendMessage).toHaveBeenCalledTimes(1)
    const [content, opts] = sendMessage.mock.calls[0]!
    expect(content).toBe('改后的问题')
    expect(opts.history!.map((m: ChatMessage) => m.content)).toEqual(['第二答'])
    expect(opts.groupSeq).toBeDefined()
  })

  it('编辑：目标不是用户消息时报错且不发送', async () => {
    const store = new Map([['chat-1', [msg('a1', 'assistant', 'x')]]])
    const { api, sendMessage } = mountActions(store)
    await api.handleEditMessage(0, 'new')
    expect(sendMessage).not.toHaveBeenCalled()
  })

  it('编辑：新文本含引用标记时还原 refs', async () => {
    const store = new Map([['chat-1', [msg('u1', 'user', '旧'), msg('a1', 'assistant', '答')]]])
    const { api, sendMessage } = mountActions(store)
    await api.handleEditMessage(0, '看 [#会话A](#sess_c1)')
    const opts = sendMessage.mock.calls[0]![1]
    expect(opts.refs).toEqual([{ type: 'session', id: 'c1', name: '#会话A' }])
  })

  it('切换版本只改前端 activeVersion', () => {
    const store = new Map([['chat-1', [msg('a1', 'assistant', '答', { history: [msg('h1', 'assistant', 'old')] })]]])
    const { api } = mountActions(store)
    api.handleSwitchVersion('a1', 0)
    expect(store.get('chat-1')![0]!.activeVersion).toBe(0)
  })

  it('切换版本：消息不存在时不动', () => {
    const store = new Map([['chat-1', [msg('a1', 'assistant', '答')]]])
    const { api } = mountActions(store)
    api.handleSwitchVersion('missing', 2)
    expect(store.get('chat-1')![0]!.activeVersion).toBeUndefined()
  })
})
