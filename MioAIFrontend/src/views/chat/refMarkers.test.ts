import { describe, it, expect } from 'vitest'
import {
  refToMarker,
  preprocessMarkers,
  parseRefMarkers,
  buildRefMarkerPrefix,
  parseRefParts,
  MARKER_SENTINEL
} from './refMarkers'
import type { ChatRefItem } from '@/types'

describe('refToMarker', () => {
  it('把各类型引用编码为 canonical 标记文本', () => {
    expect(refToMarker({ type: 'session', id: 'c1', name: '会话A' })).toBe('[#会话A](#sess_c1)')
    expect(refToMarker({ type: 'kb', kbId: 7, name: '知识库' })).toBe('[#知识库](#kb_7)')
    expect(refToMarker({ type: 'skill', name: 'search' })).toBe('[$search](#skill_search)')
    expect(refToMarker({ type: 'mcp', name: 'github' })).toBe('[$github](#mcp_github)')
    expect(refToMarker({ type: 'compact' })).toBe('/compact')
  })

  it('缺名时回退到默认名', () => {
    expect(refToMarker({ type: 'session', id: 'c1' })).toBe('[#会话](#sess_c1)')
    expect(refToMarker({ type: 'kb', kbId: 1 })).toBe('[#知识库](#kb_1)')
  })
})

describe('preprocessMarkers / parseRefMarkers', () => {
  it('把标记替换为哨兵占位并返回顺序一致的引用清单', () => {
    const content = '看 [#会话A](#sess_c1) 和 [$搜索](#skill_search)'
    const { text, items } = preprocessMarkers(content)
    // 捕获组保留标记自带的前缀（#/$），由渲染层负责剥离
    expect(items).toEqual([
      { type: 'session', id: 'c1', name: '#会话A' },
      { type: 'skill', name: 'search' }
    ])
    expect(text).toContain(`${MARKER_SENTINEL}0\uE001`)
    expect(text).toContain(`${MARKER_SENTINEL}1\uE001`)
    expect(text).not.toContain('](#sess_')
  })

  it('parseRefMarkers 只取引用清单', () => {
    expect(parseRefMarkers('纯文本')).toEqual([])
    expect(parseRefMarkers('[#库1](#kb_3)')).toEqual([{ type: 'kb', kbId: 3, name: '#库1' }])
  })
})

describe('buildRefMarkerPrefix', () => {
  it('跳过 compact 并拼接其余引用', () => {
    const refs: ChatRefItem[] = [
      { type: 'session', id: 'c1', name: 'A' },
      { type: 'compact' },
      { type: 'kb', kbId: 2, name: 'B' }
    ]
    expect(buildRefMarkerPrefix(refs)).toBe('[#A](#sess_c1) [#B](#kb_2)')
  })

  it('空清单返回空串', () => {
    expect(buildRefMarkerPrefix([])).toBe('')
  })
})

describe('parseRefParts', () => {
  it('把标记与裸 /compact 拆为类型化片段', () => {
    expect(parseRefParts('hi [#A](#sess_c1) /compact')).toEqual([
      { type: 'text', text: 'hi ' },
      { type: 'session', name: 'A' },
      { type: 'text', text: ' ' },
      { type: 'command', name: 'compact' }
    ])
  })

  it('剥离名字自带的 #/$ 前缀', () => {
    expect(parseRefParts('[#A](#sess_c1)')).toEqual([{ type: 'session', name: 'A' }])
    expect(parseRefParts('[$S](#skill_S)')).toEqual([{ type: 'skill', name: 'S' }])
  })

  it('纯文本原样返回', () => {
    expect(parseRefParts('hello world')).toEqual([{ type: 'text', text: 'hello world' }])
  })
})
