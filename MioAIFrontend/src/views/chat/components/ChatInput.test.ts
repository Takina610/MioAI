import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ChatInput from './ChatInput.vue'

// 组件级契约测试：拆分只动内部结构，对外 props/emits 必须保持不变。
// 三个调用方（chat/index、ChatMessageList、HomeChatDemo）依赖同一套契约。

const antStubs = {
  'a-tooltip': { template: '<div><slot /></div>' },
  'a-dropdown': { template: '<div><slot /><slot name="overlay" /></div>' },
  'a-menu': { template: '<div><slot /></div>' },
  'a-sub-menu': { template: '<div><slot /></div>' },
  'a-menu-item': { template: '<div><slot /></div>' },
  'a-menu-divider': { template: '<div />' },
  'a-button': {
    props: ['disabled'],
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
  }
}

function mountInput(props: Record<string, unknown> = {}) {
  return mount(ChatInput, {
    props: { modelValue: '', hasMessages: false, loading: false, ...props },
    global: { plugins: [createPinia()], stubs: antStubs }
  })
}

describe('ChatInput 契约', () => {
  it('空态展示欢迎语，不发请求的情况下可渲染', () => {
    const wrapper = mountInput()
    expect(wrapper.find('.welcome-title').exists()).toBe(true)
    expect(wrapper.find('.chat-editor').exists()).toBe(true)
  })

  it('有消息时隐藏欢迎区', () => {
    const wrapper = mountInput({ hasMessages: true })
    expect(wrapper.find('.welcome-title').exists()).toBe(false)
  })

  it('嵌入模式带 embedded 类', () => {
    const wrapper = mountInput({ embedded: true })
    expect(wrapper.classes()).toContain('embedded')
  })

  it('有文本时发送按钮可用；点击上抛 send 带 refs 数组', async () => {
    const wrapper = mountInput({ modelValue: '你好' })
    const btn = wrapper.find('.send-btn')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    const sent = wrapper.emitted('send')
    expect(sent).toBeTruthy()
    expect(Array.isArray(sent![0]![0])).toBe(true)
  })

  it('生成中点击发送按钮转为停止', async () => {
    const wrapper = mountInput({ modelValue: 'x', loading: true })
    await wrapper.find('.send-btn').trigger('click')
    expect(wrapper.emitted('stop')).toBeTruthy()
    expect(wrapper.emitted('send')).toBeFalsy()
  })

  it('编辑模式（cancelable）显示取消与发送 pill，取消上抛', async () => {
    const wrapper = mountInput({ modelValue: 'x', cancelable: true })
    expect(wrapper.find('.cancel-btn').exists()).toBe(true)
    await wrapper.find('.cancel-btn').trigger('click')
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('上传按钮点击触发隐藏 file input', async () => {
    const wrapper = mountInput()
    const input = wrapper.find('input[type="file"]')
    expect(input.exists()).toBe(true)
    // 模拟选择文件
    const file = new File(['a'], 'a.txt', { type: 'text/plain' })
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    expect(wrapper.emitted('add-files')).toBeTruthy()
  })

  it('上下文容量指示仅在非嵌入且提供数据时渲染', () => {
    const usage = {
      windowTokens: 1000,
      usedTokens: 100,
      categories: { messages: 60, systemTools: 10, mcpTools: 5, systemPrompt: 20, skills: 3, other: 2 }
    }
    const normal = mountInput({ contextUsage: usage })
    expect(normal.findComponent({ name: 'ContextUsageIndicator' }).exists()).toBe(true)
    const embedded = mountInput({ contextUsage: usage, embedded: true })
    expect(embedded.findComponent({ name: 'ContextUsageIndicator' }).exists()).toBe(false)
  })
})
