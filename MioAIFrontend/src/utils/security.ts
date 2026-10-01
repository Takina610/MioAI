import DOMPurify from 'dompurify'
import { marked } from 'marked'

const purifyConfig = {
  ALLOWED_TAGS: [
    'p', 'br', 'hr',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'strong', 'b', 'em', 'i', 'u', 'strike', 'del',
    'a', 'img',
    'ul', 'ol', 'li',
    'blockquote', 'pre', 'code',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'div', 'span', 'sup', 'sub'
  ],
  ALLOWED_ATTR: [
    'href', 'title', 'target', 'rel',
    'src', 'alt', 'width', 'height',
    'class', 'id',
    'colspan', 'rowspan', 'align'
  ],
  ALLOW_DATA_ATTR: false,
  SANITIZE_DOM: true
}

/**
 * 安全地渲染 Markdown 内容
 * 1. 使用 marked 解析 Markdown
 * 2. 使用 DOMPurify 过滤危险 HTML
 */
export function safeMarkdown(content: string): string {
  if (!content) return ''
  try {
    marked.setOptions({
      breaks: true,
      gfm: true
    })
    const rawHtml = marked.parse(content) as string
    return DOMPurify.sanitize(rawHtml, purifyConfig) as string
  } catch (e) {
    console.error('Markdown parse error:', e)
    return escapeHtml(content).replace(/\n/g, '<br>')
  }
}

/**
 * 纯文本 HTML 转义（用于非 Markdown 场景）
 */
export function escapeHtml(text: string): string {
  if (!text) return ''
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

/**
 * 快速安全检查：检测是否包含潜在的 XSS 攻击向量
 */
export function containsXssRisk(content: string): boolean {
  if (!content) return false
  const xssPatterns = [
    /<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi,
    /javascript:/gi,
    /on\w+\s*=/gi,
    /<iframe\b/gi,
    /<object\b/gi,
    /<embed\b/gi,
    /<form\b/gi
  ]
  return xssPatterns.some(pattern => pattern.test(content))
}
