import type { Token } from 'markdown-it'
import { markdown, sanitizeHtml } from './parser'

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, ch => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch] as string
  ))
}

export interface MdBlock {
  /** 块源文哈希 + 序号，作为 v-for key：源文不变的块不会触发 DOM 更新 */
  key: string
  html: string
}

/** 已完成块的渲染缓存：流式过程中除尾块外全部命中，避免整篇重渲染 */
const blockHtmlCache = new Map<string, string>()
const MAX_CACHE_ENTRIES = 800

const FENCE_RE = /^\s{0,3}(`{3,}|~{3,})(.*)$/
const HEADING_RE = /^(\s{0,3})(#{1,6})(?=[^\s#])/
const ORDERED_RE = /^(\s{0,3})(\d{1,9})[.、](?=[^\s.、\d])/
const BULLET_RE = /^(\s{0,3})(-{1,2})(?=[^\s\-\d>])/

/** 修复大模型常见的松散标记（###无空格 / -无空格 / 1.无空格 / 1、），围栏代码块内不动 */
export function normalizeLooseMarkdown(src: string): string {
  if (!src) return src
  const lines = src.split('\n')
  let inFence = false
  let fenceChar = ''
  let fenceLen = 0

  for (let i = 0; i < lines.length; i++) {
    const fence = lines[i].match(FENCE_RE)
    if (fence) {
      const char = fence[1][0]
      if (!inFence) {
        inFence = true
        fenceChar = char
        fenceLen = fence[1].length
        continue
      }
      if (char === fenceChar && fence[1].length >= fenceLen && !fence[2].trim()) {
        inFence = false
        continue
      }
    }
    if (!inFence) {
      const line = lines[i]
      if (HEADING_RE.test(line)) {
        lines[i] = line.replace(HEADING_RE, '$1$2 ')
      } else if (ORDERED_RE.test(line)) {
        lines[i] = line.replace(ORDERED_RE, '$1$2. ')
      } else if (BULLET_RE.test(line)) {
        lines[i] = line.replace(BULLET_RE, '$1$2 ')
      }
    }
  }
  return lines.join('\n')
}

/** 按 markdown-it 顶层 token 把文档切块：段落/标题/列表/表格/引用/代码块各自成块 */
function splitTopLevelBlocks(tokens: Token[]): Token[][] {
  const groups: Token[][] = []
  let current: Token[] = []

  for (const tok of tokens) {
    if (tok.level === 0 && tok.nesting === 1 && current.length > 0) {
      groups.push(current)
      current = []
    }
    current.push(tok)
    const standalone = tok.nesting === 0 && tok.type !== 'inline'
    if (tok.level === 0 && (tok.nesting === -1 || standalone)) {
      groups.push(current)
      current = []
    }
  }
  if (current.length > 0) groups.push(current)
  return groups
}

function blockSource(group: Token[], lines: string[]): string {
  for (const tok of group) {
    if (tok.map) {
      return lines.slice(tok.map[0], tok.map[1]).join('\n')
    }
  }
  return group.map(t => t.content).join('\n')
}

function hashKey(s: string): string {
  let h = 5381
  for (let i = 0; i < s.length; i++) {
    h = ((h << 5) + h + s.charCodeAt(i)) | 0
  }
  return (h >>> 0).toString(36) + '_' + s.length.toString(36)
}

/**
 * 流式增量渲染：切块后逐块渲染并缓存，只有内容变化的块返回新 HTML。
 * 配合 v-html 分块挂载，流式时已完成块的 DOM 不再重建，避免整篇闪动与元素跳变。
 */
export function renderBlocks(src: string): MdBlock[] {
  const source = normalizeLooseMarkdown(src)
  const env: Record<string, unknown> = {}
  const tokens = markdown.parse(source, env)
  const lines = source.split('\n')
  const groups = splitTopLevelBlocks(tokens)

  const blocks: MdBlock[] = []
  for (let i = 0; i < groups.length; i++) {
    const group = groups[i]
    const blockSrc = blockSource(group, lines)
    let html = blockHtmlCache.get(blockSrc)
    if (html === undefined) {
      try {
        html = sanitizeHtml(markdown.renderer.render(group, markdown.options, env))
      } catch (e) {
        console.error('Markdown 块渲染失败:', e)
        html = `<pre>${escapeHtml(blockSrc)}</pre>`
      }
      if (blockHtmlCache.size >= MAX_CACHE_ENTRIES) blockHtmlCache.clear()
      blockHtmlCache.set(blockSrc, html)
    }
    blocks.push({ key: `${hashKey(blockSrc)}-${i}`, html })
  }
  return blocks
}
