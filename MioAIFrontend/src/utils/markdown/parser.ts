import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import { katex } from '@mdit/plugin-katex'
import cjkFriendly from 'markdown-it-cjk-friendly'
import tasklists from 'markdown-it-task-lists'
import hljs from 'highlight.js/lib/common'

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, ch => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch] as string
  ))
}

/** 主流 AI Chat 渲染配置：GFM 表格/删除线、软换行转 <br>、链接识别、代码高亮、LaTeX 公式。
 *  html 放行裸 HTML（模型常用 <br> 换行表格单元格），危险内容由下方 DOMPurify 白名单过滤；
 *  cjkFriendly 修复全角标点相邻时 **加粗** 不解析（CommonMark 侧翼规则的 CJK 缺陷）；
 *  tasklists 渲染 - [ ] 任务列表复选框；katex delimiters:all 同时支持 $…$ 与 \(…\)/\[…\] 定界符 */
export const markdown = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true,
  highlight(code, lang): string {
    const language = lang && hljs.getLanguage(lang) ? lang : ''
    if (language) {
      try {
        return `<pre class="md-code"><code class="hljs language-${language}">${hljs.highlight(code, { language, ignoreIllegals: true }).value}</code></pre>`
      } catch {
        // 高亮失败时回退到转义输出
      }
    }
    const langClass = lang ? ` class="language-${escapeHtml(lang)}"` : ''
    return `<pre class="md-code"><code${langClass}>${escapeHtml(code)}</code></pre>`
  }
}).use(katex, { throwOnError: false, delimiters: 'all', mathFence: true })
  .use(cjkFriendly)
  .use(tasklists, { enabled: false })

// 聊天内的外部链接（CDN 等）与同源文件代理链接（/api/file/...，PDF 等）
// 一律新标签打开，不挤占当前会话页；外链不带 referrer（与手动复制链接直接打开一致）
const defaultLinkOpen = markdown.renderer.rules.link_open
  ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const href = String(tokens[idx].attrGet('href') ?? '')
  const external = /^https?:\/\//i.test(href)
  if (external || href.startsWith('/api/file/')) {
    tokens[idx].attrSet('target', '_blank')
    if (external) {
      tokens[idx].attrSet('rel', 'noopener noreferrer')
      tokens[idx].attrSet('referrerpolicy', 'no-referrer')
    }
  }
  return defaultLinkOpen(tokens, idx, options, env, self)
}

/** KaTeX 输出的 MathML/HTML 标签与属性 */
const KATEX_TAGS = [
  'math', 'semantics', 'annotation', 'annotation-xml', 'menclose', 'merror',
  'mfrac', 'mi', 'mmultiscripts', 'mn', 'mo', 'mover', 'mpadded', 'mphantom',
  'mroot', 'mrow', 'ms', 'mspace', 'msqrt', 'mstyle', 'msub', 'msubsup',
  'msup', 'mtable', 'mtd', 'mtext', 'mtr', 'munder', 'munderover', 'mprescripts', 'none'
]
const KATEX_ATTRS = [
  'style', 'aria-hidden', 'encoding', 'mathvariant', 'mathsize', 'display',
  'displaystyle', 'scriptlevel', 'lspace', 'rspace', 'stretchy', 'symmetric',
  'fence', 'separator', 'columnalign', 'rowalign', 'columnlines', 'rowlines',
  'movablelimits', 'accent', 'accentunder', 'bevelled', 'notation',
  'linethickness', 'depth', 'voffset', 'open', 'close', 'stretchy'
]

const SANITIZE_OPTIONS = {
  ALLOWED_TAGS: [
    'p', 'br', 'hr',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'strong', 'b', 'em', 'i', 'u', 'strike', 'del', 's',
    'a', 'img',
    'ul', 'ol', 'li',
    'blockquote', 'pre', 'code',
    'details', 'summary',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'div', 'span', 'sup', 'sub',
    'input', // 任务列表复选框（disabled）
    ...KATEX_TAGS
  ],
  ALLOWED_ATTR: [
    'href', 'title', 'target', 'rel', 'referrerpolicy',
    'src', 'alt', 'width', 'height',
    'class', 'id',
    'colspan', 'rowspan', 'align',
    'type', 'checked', 'disabled',
    'open', // <details> 展开态
    ...KATEX_ATTRS
  ],
  ALLOW_DATA_ATTR: false
}

/** 过滤危险 HTML（Markdown 输出统一经过这里才能进 v-html） */
export function sanitizeHtml(html: string): string {
  return String(DOMPurify.sanitize(html, SANITIZE_OPTIONS))
}

/** 整篇渲染：非流式场景（文件预览、分享页等） */
export function renderMarkdown(src: string): string {
  if (!src) return ''
  try {
    return sanitizeHtml(markdown.render(src))
  } catch (e) {
    console.error('Markdown 渲染失败:', e)
    return `<pre>${escapeHtml(src)}</pre>`
  }
}
