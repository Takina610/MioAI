import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { attachmentsOf, useChatAttachments } from './useChatAttachments'
import type { ChatMessage, MessageBlock } from '@/types'

vi.mock('@/api/chat', () => ({
  uploadAttachment: vi.fn(() => Promise.resolve({ path: 'p/1.png', name: '1.png', size: 10 })),
  deleteAttachment: vi.fn(() => Promise.resolve())
}))

vi.mock('./usePendingCardAnimations', () => ({
  usePendingCardAnimations: () => ({
    renderCards: ref([]),
    leavingKeys: ref(new Set()),
    setCardRef: () => () => {},
    beginSilentSwap: () => {}
  })
}))

const { uploadAttachment } = await import('@/api/chat')

/** 在组件上下文里挂载组合式（生命周期/响应式生效） */
function mountComposable<T>(factory: () => T): { api: T; unmount: () => void } {
  let captured!: T
  const Comp = defineComponent({
    setup() {
      captured = factory()
      return () => null
    }
  })
  const wrapper = mount(Comp, { global: { plugins: [createPinia()] } })
  return { api: captured, unmount: () => wrapper.unmount() }
}

function userMsgWithAttachments(items: Array<{ path: string; name: string; size: number }>): ChatMessage {
  const block = { type: 'attachments', side: 'input', items } as unknown as MessageBlock
  return { id: 'm1', role: 'user', content: 'hi', blocks: [block], createTime: new Date() }
}

describe('attachmentsOf', () => {
  it('只取 input 侧的附件块条目', () => {
    const msg: ChatMessage = {
      id: 'm', role: 'user', content: '', createTime: new Date(),
      blocks: [
        { type: 'attachments', side: 'input', items: [{ path: 'a', name: 'a', size: 1 }] } as never,
        { type: 'attachments', side: 'output', items: [{ path: 'b', name: 'b', size: 1 }] } as never,
        { type: 'text', text: 'x' } as never
      ]
    }
    expect(attachmentsOf(msg).map(a => a.path)).toEqual(['a'])
  })

  it('无 blocks 返回空数组', () => {
    expect(attachmentsOf({ id: 'm', role: 'user', content: '', createTime: new Date() })).toEqual([])
  })
})

describe('useChatAttachments', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.setItem('token', 'x')
    vi.clearAllMocks()
  })
  afterEach(() => localStorage.clear())

  function setup(initialChatId = 'chat-1') {
    const chatId = ref(initialChatId)
    const input = ref('')
    const messages = new Map<string, ChatMessage[]>()
    const { api } = mountComposable(() => useChatAttachments({
      currentChatId: () => chatId.value,
      routeConversationId: () => undefined,
      inputMessage: () => input.value,
      setInputMessage: v => { input.value = v },
      setCurrentChatId: id => { chatId.value = id },
      getMessages: id => messages.get(id) ?? []
    }))
    return { api, chatId, input, messages }
  }

  it('上传成功后记录 item 并置为 done', async () => {
    const { api } = setup()
    await api.handleAddFiles([new File(['x'], '1.png', { type: 'image/png' })])
    // 等 promise 链落定
    await new Promise(r => setTimeout(r, 0))
    expect(uploadAttachment).toHaveBeenCalledTimes(1)
    expect(api.pendingAttachments.value.length).toBe(1)
    expect(api.pendingAttachments.value[0]!.status).toBe('done')
    expect(api.readyAttachments()).toEqual([{ path: 'p/1.png', name: '1.png', size: 10 }])
  })

  it('上传失败置为 error，不计入 ready', async () => {
    vi.mocked(uploadAttachment).mockRejectedValueOnce(new Error('boom'))
    const { api } = setup()
    await api.handleAddFiles([new File(['x'], 'bad.txt', { type: 'text/plain' })])
    await new Promise(r => setTimeout(r, 0))
    expect(api.pendingAttachments.value[0]!.status).toBe('error')
    expect(api.readyAttachments()).toEqual([])
    expect(api.hasUploadError()).toBe(true)
  })

  it('移除待传附件会从列表去掉', async () => {
    const { api } = setup()
    await api.handleAddFiles([new File(['x'], '1.png')])
    await new Promise(r => setTimeout(r, 0))
    const key = api.pendingAttachments.value[0]!.key
    api.handleRemovePending(key)
    expect(api.pendingAttachments.value).toEqual([])
  })

  it('切换会话隔离草稿与附件', async () => {
    const { api, chatId, input } = setup('chat-1')
    await api.handleAddFiles([new File(['x'], '1.png')])
    await new Promise(r => setTimeout(r, 0))
    input.value = '会话一的草稿'
    await nextTick()

    // 切到 chat-2：草稿与附件都应为空
    chatId.value = 'chat-2'
    await nextTick()
    expect(input.value).toBe('')
    expect(api.pendingAttachments.value).toEqual([])

    // 切回 chat-1：恢复
    chatId.value = 'chat-1'
    await nextTick()
    expect(input.value).toBe('会话一的草稿')
    expect(api.pendingAttachments.value.length).toBe(1)
  })

  it('编辑开始时载入原附件；取消时清空', () => {
    const { api, messages } = setup('chat-1')
    messages.set('chat-1', [userMsgWithAttachments([{ path: 'p/a.png', name: 'a.png', size: 5 }])])
    api.handleEditStart(0)
    expect(api.editAttachments.value.length).toBe(1)
    expect(api.editAttachments.value[0]!.key).toBe('p/a.png')
    api.handleEditCancel()
    expect(api.editAttachments.value).toEqual([])
  })
})
