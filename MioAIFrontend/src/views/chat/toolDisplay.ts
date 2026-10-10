import { markRaw } from 'vue'
import type { Component } from 'vue'
import type { MessageBlock } from '@/types'
import {
  OrderedListOutlined,
  SearchOutlined,
  ReadOutlined,
  FilePdfOutlined,
  FileTextOutlined,
  EditOutlined,
  CodeOutlined,
  ToolOutlined,
  GlobalOutlined,
  FileAddOutlined,
  FileSearchOutlined,
  RobotOutlined
} from '@ant-design/icons-vue'

export type ToolBlock = Extract<MessageBlock, { type: 'tool' }>

export interface SourceChip {
  title: string
  url: string
  host: string
}

// ---------- 工具语义化展示（主消息流与子代理面板共用） ----------
// 动词文案照搬 zcode zh-CN 词表（chat.toolCall.*）：[进行中, 完成]，
// 运行态动词带渐变扫光，完成后切换为过去式并恢复静态浅色
const TOOL_VERBS: Record<string, [string, string]> = {
  Bash: ['正在执行', '已执行'],
  executeTerminalCommand: ['正在执行', '已执行'],
  runCommand: ['正在执行', '已执行'],
  Read: ['正在读取', '已读取'],
  readFile: ['正在读取', '已读取'],
  Write: ['正在写入', '已写入'],
  writeFile: ['正在写入', '已写入'],
  Edit: ['正在编辑', '已编辑'],
  editFile: ['正在编辑', '已编辑'],
  Grep: ['正在搜索', '已搜索'],
  grep: ['正在搜索', '已搜索'],
  searchImage: ['正在搜索', '已搜索'],
  Glob: ['正在查找', '已查找'],
  glob: ['正在查找', '已查找'],
  WebSearch: ['正在搜索', '已搜索'],
  searchWeb: ['正在搜索', '已搜索'],
  WebFetch: ['正在获取', '已获取'],
  fetchUrl: ['正在获取', '已获取'],
  scrapeWebPage: ['正在获取', '已获取'],
  downloadResource: ['正在下载', '已下载'],
  generatePDF: ['正在生成', '已生成'],
  Agent: ['子智能体', '子智能体'],
  AskUserQuestion: ['正在提问', '已提问'],
  TodoWrite: ['更新中', '待办'],
  TodoRead: ['读取中', '已读取'],
  managePlan: ['更新中', '任务清单'],
  TaskOutput: ['正在获取任务输出', '已获取任务输出'],
  TaskStop: ['停止任务', '已停止']
}

/** 工具行动词：运行中→进行式（扫光），完成→过去式；Agent 行恒为「子智能体」（zcode GUI 同款） */
export function toolVerb(block: ToolBlock): string {
  const pair = TOOL_VERBS[block.tool]
  if (pair) return block.status === 'running' ? pair[0] : pair[1]
  // 未登记工具（自定义 MCP 等）走 zcode 通用状态词
  return block.status === 'running' ? '执行中' : '已执行'
}

const TOOL_ICONS: Record<string, Component> = markRaw({
  managePlan: OrderedListOutlined,
  runCommand: CodeOutlined,
  readFile: FileTextOutlined,
  writeFile: FileAddOutlined,
  editFile: EditOutlined,
  glob: FileSearchOutlined,
  grep: SearchOutlined,
  searchWeb: GlobalOutlined,
  fetchUrl: ReadOutlined,
  generatePDF: FilePdfOutlined,
  // 子智能体行用机器人图标（zcode 同款语义），不用通用工具扳手
  Agent: RobotOutlined
})

export function toolIcon(tool: string): Component {
  return TOOL_ICONS[tool] ?? ToolOutlined
}

export function parseArgs(block: ToolBlock): Record<string, any> | null {
  try {
    return JSON.parse(block.args || '')
  } catch {
    return null
  }
}

export function hostOf(url?: string): string {
  if (!url) return ''
  try {
    return new URL(url).hostname
  } catch {
    return url.replace(/^https?:\/\//, '').slice(0, 40)
  }
}

/** 工具行元信息：从参数提取人话摘要，不暴露原始 JSON */
export function toolMeta(block: ToolBlock): string {
  const a = parseArgs(block)
  switch (block.tool) {
    case 'managePlan': {
      const action = a?.action
      if (action === 'create') {
        const n = String(a?.steps ?? '').split('\n').filter(s => s.trim()).length
        return n ? `创建 ${n} 个步骤` : '创建任务清单'
      }
      if (action === 'update') {
        const idx = Number(a?.stepIndex)
        return idx ? `第 ${idx} 步${a?.status === 'done' ? '已完成' : '已更新'}` : '更新任务清单'
      }
      return '更新任务清单'
    }
    case 'searchWeb':
    case 'WebSearch':
      return a?.query ? `“${a.query}”` : ''
    case 'scrapeWebPage':
    case 'WebFetch':
      return hostOf(a?.url) || a?.url || ''
    case 'generatePDF':
      return a?.fileName ? `“${a.fileName}”` : ''
    case 'readFile':
    case 'writeFile':
    case 'Read':
    case 'Write':
    case 'Edit':
      return a?.file_path ?? a?.fileName ?? ''
    case 'searchImage':
      return a?.query ? `“${a.query}”` : ''
    case 'executeTerminalCommand':
    case 'Bash':
      return a?.command ? `$ ${a.command}` : (a?.description ?? '')
    case 'downloadResource':
      return a?.fileName || hostOf(a?.url) || ''
    case 'Glob':
      return a?.pattern ?? ''
    case 'Grep':
      return a?.pattern ?? ''
    case 'TodoRead':
      return ''
    case 'TodoWrite': {
      const items = Array.isArray(a?.todos) ? a.todos.length : 0
      return items ? `${items} 项任务` : ''
    }
    case 'Agent': {
      const description = typeof a?.description === 'string' ? a.description.trim() : ''
      if (description) return description
      return typeof a?.prompt === 'string' && a.prompt ? `“${a.prompt.slice(0, 30)}${a.prompt.length > 30 ? '…' : ''}”` : ''
    }
    case 'AskUserQuestion': {
      // 结构可能是新式 questions 数组或旧式单 question 字段（模型首试常写错）
      const questions: Array<Record<string, unknown>> = Array.isArray(a?.questions)
        ? a.questions
        : (a?.question ? [{ question: a.question }] : [])
      const first = questions.length
        ? String(questions[0].question ?? Object.values(questions[0])[0] ?? '')
        : ''
      const suffix = questions.length > 1 ? ` 等 ${questions.length} 个问题` : ''
      return first ? `“${first}”${suffix}` : partialFirstString(block.args)
    }
    case 'TaskOutput':
    case 'TaskStop':
      return a?.task_id ?? ''
    default: {
      // 语义兜底（zcode 风格）：展示参数里最有意义的一个字符串值，绝不裸显 JSON
      const fromParsed = firstMeaningfulString(a)
      if (fromParsed) return fromParsed
      return partialFirstString(block.args)
    }
  }
}

/** 从已解析参数对象里找第一个有意义的字符串值（浅层，跳过纯布尔/数字键名噪音） */
function firstMeaningfulString(args: Record<string, unknown> | null | undefined): string {
  if (!args) return ''
  const preferred = ['query', 'command', 'path', 'file_path', 'pattern', 'url', 'prompt', 'name', 'description', 'content']
  for (const key of preferred) {
    const value = args[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  for (const value of Object.values(args)) {
    if (typeof value === 'string' && value.trim()) return value.trim()
    if (typeof value === 'number' || typeof value === 'boolean') continue
    if (Array.isArray(value)) {
      const first = value.find(item => item && typeof item === 'object')
      const inner = firstMeaningfulString(first as Record<string, unknown> | undefined)
      if (inner) return inner
    }
  }
  return ''
}

/** 流式中的参数片段还是不完整 JSON：宽松抓第一个字符串值（引号未闭合也算），避免裸显 JSON */
export function partialFirstString(raw: string | undefined): string {
  if (!raw) return ''
  const match = raw.match(/:\s*"([^"]*)"?/)
  if (match && match[1]) {
    const value = match[1].replace(/\\n/g, ' ').trim()
    return value.length > 40 ? `${value.slice(0, 40)}…` : value
  }
  return ''
}

// ---------- 来源链接（DeepSeek 式可跳转源） ----------
const MAX_CHIPS = 6

/** 联网搜索结果 → 来源链接（title/url 的 JSON 行） */
function searchSourceChips(result?: string): SourceChip[] {
  if (!result) return []
  const pick = (part: string, key: string): string => {
    const m = part.match(new RegExp(`"${key}"\\s*:\\s*"((?:[^"\\\\]|\\\\.)*)"`))
    return m ? m[1].replace(/\\"/g, '"').replace(/\\n/g, ' ') : ''
  }
  const chips: SourceChip[] = []
  for (const part of result.split(/\},\s*\{/)) {
    const url = pick(part, 'url') || pick(part, 'link')
    const title = pick(part, 'title')
    if (!url) continue
    chips.push({ title: title || hostOf(url), url, host: hostOf(url) })
    if (chips.length >= MAX_CHIPS) break
  }
  return chips
}

/** 阅读网页 → 单个来源链接（HTML 提取 <title>） */
function scrapeSourceChip(block: ToolBlock): SourceChip[] {
  const a = parseArgs(block)
  const url = a?.url
  if (!url) return []
  const titleMatch = block.result?.match(/<title[^>]*>([^<]*)<\/title>/i)
  return [{ title: titleMatch ? titleMatch[1].trim() : hostOf(url), url, host: hostOf(url) }]
}

const sourceChipsCache = new Map<string, SourceChip[]>()

/** 联网类工具的来源链接（title/url）；其余工具返回空 */
export function sourceChips(block: ToolBlock): SourceChip[] {
  // 以工具+结果内容为键缓存解析结果（无 id 的兜底块也不会互相串卡）
  const key = `${block.tool}|${block.result?.length ?? 0}|${block.result?.slice(0, 50) ?? ''}`
  const cached = sourceChipsCache.get(key)
  if (cached) return cached
  let chips: SourceChip[] = []
  if (block.tool === 'searchWeb') {
    chips = searchSourceChips(block.result)
  } else if (block.tool === 'scrapeWebPage') {
    chips = scrapeSourceChip(block)
  }
  sourceChipsCache.set(key, chips)
  return chips
}

// ---------- 时长 ----------
export function formatDuration(ms: number): string {
  const totalSeconds = Math.max(0, Math.round(ms / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return minutes > 0 ? `${minutes}分${seconds}秒` : `${seconds}秒`
}
