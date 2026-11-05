import { h, render as vueRender } from 'vue'
import {
  ApiOutlined,
  CompressOutlined,
  DatabaseOutlined,
  MessageOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import { refToMarker } from './refMarkers'
import type { ChatRefItem } from '@/types'

/**
 * 编辑器内引用 token 的 DOM 工厂（zcode decoratePromptMention 对位）：
 * token 是「装饰图标 + 真实文本节点」——文本完整存在（鼠标可选中、随选区复制），
 * 图标不参与选区；canonical 标记文本由 copy/cut 拦截写入剪贴板。
 */

export function tokenIconOf(type: ChatRefItem['type']) {
  switch (type) {
    case 'session':
      return MessageOutlined
    case 'kb':
      return DatabaseOutlined
    case 'skill':
      return ThunderboltOutlined
    case 'mcp':
      return ApiOutlined
    case 'compact':
      return CompressOutlined
  }
}

/** 展示名剥离标记前缀（存储的标记名带 #/$，界面上按 zcode 约定裸名展示） */
export function tokenDisplayName(item: ChatRefItem): string {
  if (item.type === 'compact') return 'compact'
  const name = item.name ?? ''
  return item.type === 'kb' || item.type === 'session'
    ? name.replace(/^#/, '')
    : name.replace(/^\$/, '')
}

/** 创建输入框内的 token 节点（contenteditable=false，含真实文本；icon 为装饰） */
export function createTokenElement(item: ChatRefItem): {
  wrapper: HTMLElement
  destroy: () => void
} {
  const wrapper = document.createElement('span')
  wrapper.className = `editor-token is-${item.type}`
  wrapper.contentEditable = 'false'
  wrapper.dataset.refType = item.type
  wrapper.dataset.refKey = item.id ?? String(item.kbId ?? '')
  wrapper.dataset.refName = tokenDisplayName(item)
  wrapper.dataset.refCanonical = refToMarker(item)

  const icon = document.createElement('span')
  icon.className = 'token-icon'
  icon.setAttribute('aria-hidden', 'true')
  vueRender(h(tokenIconOf(item.type)), icon)
  wrapper.appendChild(icon)

  wrapper.appendChild(document.createTextNode(tokenDisplayName(item)))
  return {
    wrapper,
    destroy: () => {
      vueRender(null, icon)
    }
  }
}

/** token 节点的 canonical 标记文本（剪贴板输出）；refToMarker 自行补 #/$ 前缀，传裸名 */
export function canonicalOfWrapper(wrapper: HTMLElement): string {
  const type = wrapper.dataset.refType as ChatRefItem['type']
  const name = wrapper.dataset.refName ?? ''
  if (type === 'compact') {
    return refToMarker({ type: 'compact' })
  }
  if (type === 'kb') {
    const kbId = Number(wrapper.dataset.refKey)
    return refToMarker({ type: 'kb', kbId, name })
  }
  if (type === 'skill') {
    return refToMarker({ type: 'skill', name })
  }
  if (type === 'session') {
    return refToMarker({ type: 'session', id: wrapper.dataset.refKey, name })
  }
  return refToMarker({ type: 'mcp', name })
}
