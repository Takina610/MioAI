import { describe, it, expect } from 'vitest'
import { tokenIconOf, tokenDisplayName, createTokenElement, canonicalOfWrapper } from './tokenDom'
import type { ChatRefItem } from '@/types'

describe('tokenDisplayName', () => {
  it('会话/知识库剥 #，技能/MCP 剥 $', () => {
    expect(tokenDisplayName({ type: 'session', id: 'c', name: '#A' })).toBe('A')
    expect(tokenDisplayName({ type: 'kb', kbId: 1, name: '#B' })).toBe('B')
    expect(tokenDisplayName({ type: 'skill', name: '$S' })).toBe('S')
    expect(tokenDisplayName({ type: 'mcp', name: '$M' })).toBe('M')
  })

  it('compact 固定为 compact', () => {
    expect(tokenDisplayName({ type: 'compact' })).toBe('compact')
  })
})

describe('tokenIconOf', () => {
  it('每种类型都有图标', () => {
    const types: ChatRefItem['type'][] = ['session', 'kb', 'skill', 'mcp', 'compact']
    for (const t of types) {
      expect(tokenIconOf(t)).toBeTruthy()
    }
  })
})

describe('createTokenElement', () => {
  it('生成带 data 属性与展示名的 token 节点', () => {
    const { wrapper } = createTokenElement({ type: 'session', id: 'c9', name: '#会话' })
    expect(wrapper.classList.contains('editor-token')).toBe(true)
    expect(wrapper.classList.contains('is-session')).toBe(true)
    expect(wrapper.contentEditable).toBe('false')
    expect(wrapper.dataset.refType).toBe('session')
    expect(wrapper.dataset.refKey).toBe('c9')
    expect(wrapper.dataset.refName).toBe('会话')
    // canonical 直接由原始 item 编码，名字里的 # 未被剥离（与 canonicalOfWrapper 的还原路径不同）
    expect(wrapper.dataset.refCanonical).toBe('[##会话](#sess_c9)')
    expect(wrapper.textContent).toContain('会话')
    expect(wrapper.querySelector('.token-icon')).not.toBeNull()
  })

  it('destroy 不抛错', () => {
    const { destroy } = createTokenElement({ type: 'compact' })
    expect(() => destroy()).not.toThrow()
  })
})

describe('canonicalOfWrapper', () => {
  it('从 data 属性还原 canonical 标记', () => {
    const { wrapper } = createTokenElement({ type: 'kb', kbId: 5, name: '#库' })
    expect(canonicalOfWrapper(wrapper)).toBe('[#库](#kb_5)')

    const s = createTokenElement({ type: 'skill', name: '$查找' })
    expect(canonicalOfWrapper(s.wrapper)).toBe('[$查找](#skill_查找)')

    const c = createTokenElement({ type: 'compact' })
    expect(canonicalOfWrapper(c.wrapper)).toBe('/compact')
  })
})
