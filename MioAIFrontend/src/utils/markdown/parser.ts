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
/** 代码块统一外壳：header 显示语言 + 复制按钮（复制由 MarkdownView 事件委托处理） */
function codeBlockWrap(lang: string, inner: string): string {
  const langLabel = lang ? escapeHtml(lang) : escapeHtml('文本')
  return `<div class="md-code-wrap"><div class="md-code-header"><span class="md-code-lang">${langLabel}</span><span class="md-copy-btn">复制</span></div>${inner}</div>`
}

/** 无语言标注围栏的自动探测：常见语言子集 + 采纳阈值。
 *  实测多行真实代码 relevance ≥ 4~12，单行命令被 hljs 误判成 sql 之类只有 ≤3——
 *  阈值挡掉垃圾结果，shell 单行命令由下方命令词启发兜底 */
const AUTO_LANG_SUBSET = [
  'bash', 'shell', 'java', 'python', 'javascript', 'typescript', 'json', 'yaml',
  'sql', 'dockerfile', 'go', 'rust', 'c', 'cpp', 'xml', 'ini', 'toml', 'http', 'css'
]
const AUTO_LANG_MIN_RELEVANCE = 4

/** 常见 CLI 命令词：模型输出裸围栏包 shell 命令时最常见，hljs 对单行命令探测不出来 */
const SHELL_COMMANDS = new Set([
  'docker', 'docker-compose', 'git', 'npm', 'pnpm', 'yarn', 'bun', 'node', 'npx',
  'curl', 'wget', 'sudo', 'apt', 'apt-get', 'yum', 'dnf', 'brew',
  'kubectl', 'helm', 'mvn', 'gradle', 'java', 'javac', 'jar',
  'python', 'python3', 'pip', 'pip3', 'node', 'deno', 'go', 'cargo', 'dotnet',
  'mysql', 'psql', 'pg_ctl', 'redis-cli', 'mongosh',
  'ssh', 'scp', 'sftp', 'tar', 'zip', 'unzip', 'gzip', 'gunzip',
  'chmod', 'chown', 'systemctl', 'service', 'journalctl', 'mount',
  'grep', 'sed', 'awk', 'cat', 'ls', 'cd', 'mkdir', 'rmdir', 'rm', 'cp', 'mv',
  'touch', 'ln', 'find', 'xargs', 'echo', 'export', 'source', 'env', 'which',
  'make', 'cmake', 'gcc', 'g++', 'javac', 'ffmpeg', 'nginx', 'systemctl',
  'ping', 'ip', 'ifconfig', 'netstat', 'ss', 'tcpdump', 'nohup', 'setsid',
  'tail', 'head', 'less', 'wc', 'sort', 'uniq', 'tee', 'date', 'sleep',
  'useradd', 'usermod', 'passwd', 'kill', 'pkill', 'ps', 'top', 'htop', 'free', 'df', 'du'
])

/** 裸围栏内容是否像 shell 命令：无 CJK、行数有限、首个有效行以 CLI 命令词开头（容忍 $ 提示符前缀） */
function looksLikeShell(code: string): boolean {
  if (/[\u4e00-\u9fff]/.test(code)) return false
  const lines = code.split('\n').map(l => l.trim()).filter(l => l && !l.startsWith('#'))
  if (lines.length === 0 || lines.length > 12) return false
  const first = lines[0].replace(/^[$❯>]\s*/, '').split(/\s+/)[0]
  return SHELL_COMMANDS.has(first)
}

export const markdown = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true
}).use(katex, { throwOnError: false, delimiters: 'all', mathFence: true })
  .use(cjkFriendly)
  .use(tasklists, { enabled: false })

// 覆盖 fence 渲染规则：高亮 + 外壳完全自控。
// （不能用 options.highlight 回调——markdown-it 对非 <pre 开头的返回值会再包一层
//  <pre><code>，导致外壳 div 嵌进 code 内被 DOMPurify 剥掉）
markdown.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const info = (token.info || '').trim().split(/\s+/)[0] || ''
  const code = token.content
  let lang = info.toLowerCase()
  let highlighted: string | null = null
  if (lang && hljs.getLanguage(lang)) {
    try {
      highlighted = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
    } catch {
      highlighted = null
    }
  }
  if (highlighted === null) {
    // 未标注语言（模型常发裸 ```）：shell 命令启发 → 常见语言自动探测，都落空按纯文本展示。
    // info 存在但 hljs 不认识（如 mermaid）：保留原标签，不参与探测
    if (!lang) {
      if (looksLikeShell(code)) {
        lang = 'bash'
      }
      if (lang && hljs.getLanguage(lang)) {
        try {
          highlighted = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        } catch {
          highlighted = null
        }
      }
      if (highlighted === null) {
        try {
          const auto = hljs.highlightAuto(code, AUTO_LANG_SUBSET)
          if (auto.relevance >= AUTO_LANG_MIN_RELEVANCE && auto.language) {
            lang = auto.language
            highlighted = auto.value
          }
        } catch {
          // 探测失败：按纯文本展示
        }
      }
    }
  }
  const label = lang || (highlighted ? '' : '文本')
  const codeHtml = highlighted !== null
    ? `<pre class="md-code"><code class="hljs language-${escapeHtml(lang)}">${highlighted}</code></pre>`
    : `<pre class="md-code"><code>${escapeHtml(code)}</code></pre>`
  return codeBlockWrap(label, codeHtml) + '\n'
}

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
