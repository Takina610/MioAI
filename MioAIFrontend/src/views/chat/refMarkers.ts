import type { ChatRefItem } from '@/types'

/**
 * 引用标记的编解码（zcode 同构）：引用在消息文本里本质是 link 标记
 * （[#会话名](#sess_id) / [#库名](#kb_id) / [$技能名](#skill_名) / [$服务名](#mcp_名)），
 * 前端渲染时把标记替换为彩色 token，复制/编辑/持久化的都是标记文本本身。
 */

export function refToMarker(ref: ChatRefItem): string {
  switch (ref.type) {
    case 'session':
      return `[#${ref.name ?? '会话'}](#sess_${ref.id})`
    case 'kb':
      return `[#${ref.name ?? '知识库'}](#kb_${ref.kbId})`
    case 'skill':
      return `[$${ref.name ?? '技能'}](#skill_${ref.name ?? ''})`
    case 'mcp':
      return `[$${ref.name ?? 'MCP'}](#mcp_${ref.name ?? ''})`
    case 'compact':
      return '/compact'
  }
}

export const MARKER_RE = /\[([^\]]+)\]\(#(sess|kb|skill|mcp)_([^)]+)\)/g

/** 占位符哨兵（私有区字符，markdown 解析不会触碰） */
export const MARKER_SENTINEL = '\uE000'

/**
 * 标记文本 → 渲染文本：标记摘为占位符（\uE000<序号>\uE001），并按序返回引用清单。
 * 渲染组件据此在占位符位置插入 token 节点（不依赖 markdown 的 link 语法解析）。
 */
export function preprocessMarkers(content: string): { text: string; items: ChatRefItem[] } {
  const items: ChatRefItem[] = []
  MARKER_RE.lastIndex = 0
  const text = (content ?? '').replace(MARKER_RE, (_, name: string, kind: string, target: string) => {
    if (kind === 'sess') {
      items.push({ type: 'session', id: target, name })
    } else if (kind === 'kb') {
      const kbId = Number(target)
      items.push({ type: 'kb', kbId: Number.isFinite(kbId) ? kbId : undefined, name })
    } else if (kind === 'skill') {
      items.push({ type: 'skill', name: target })
    } else {
      items.push({ type: 'mcp', name: target })
    }
    return `${MARKER_SENTINEL}${items.length - 1}\uE001`
  })
  return { text, items }
}

/** 从消息文本中解析引用标记（编辑重发时还原引用清单） */
export function parseRefMarkers(content: string): ChatRefItem[] {
  return preprocessMarkers(content).items
}

/** 把引用清单编排为标记前缀（发送时拼在用户文字前） */
export function buildRefMarkerPrefix(refs: ChatRefItem[]): string {
  return refs
    .filter(r => r.type !== 'compact')
    .map(refToMarker)
    .join(' ')
}

export type RefPart =
  | { type: 'text'; text: string }
  | { type: 'session'; name: string }
  | { type: 'kb'; name: string }
  | { type: 'skill'; name: string }
  | { type: 'mcp'; name: string }
  | { type: 'command'; name: string }

/**
 * 消息文本 → 渲染片段序列（zcode parseMentionMarkdown 对位）：
 * link 标记（[#名](#sess_id) 等）与裸命令 token（/compact）解析为类型化片段，
 * 其余保持原文本；用户消息据此以「纯文本 + token 混排」渲染，不走 markdown。
 */
export function parseRefParts(content: string): RefPart[] {
  const parts: RefPart[] = []
  const linkRe = /\[([^\]]+)\]\(#(sess|kb|skill|mcp)_([^)]+)\)/g
  const commandRe = /(^|\s)(\/compact)(?=\s|$)/g
  let cursor = 0
  linkRe.lastIndex = 0
  let m: RegExpExecArray | null
  const pushText = (segment: string): void => {
    // 段内识别裸 /compact 命令 token
    commandRe.lastIndex = 0
    let c = 0
    let cm: RegExpExecArray | null
    while ((cm = commandRe.exec(segment)) !== null) {
      const prefix = cm[1] ?? ''
      const start = (cm.index ?? 0) + prefix.length
      if (start > c) {
        parts.push({ type: 'text', text: segment.slice(c, start) })
      }
      parts.push({ type: 'command', name: cm[2]!.slice(1) })
      c = start + cm[2]!.length
    }
    if (c < segment.length) {
      parts.push({ type: 'text', text: segment.slice(c) })
    }
  }
  while ((m = linkRe.exec(content)) !== null) {
    const [, name, kind, target] = m
    const start = m.index ?? 0
    if (start > cursor) {
      pushText(content.slice(cursor, start))
    }
    if (kind === 'sess') {
      parts.push({ type: 'session', name: name.startsWith('#') ? name.slice(1) : name })
    } else if (kind === 'kb') {
      parts.push({ type: 'kb', name: name.startsWith('#') ? name.slice(1) : name })
    } else if (kind === 'skill') {
      parts.push({ type: 'skill', name: name.startsWith('$') ? name.slice(1) : name })
    } else {
      parts.push({ type: 'mcp', name: name.startsWith('$') ? name.slice(1) : name })
    }
    cursor = start + m[0].length
  }
  if (cursor < content.length) {
    pushText(content.slice(cursor))
  }
  return parts
}
