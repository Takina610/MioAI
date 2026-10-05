<template>
  <div class="mio-bot-message">
    <!-- 状态行：进行中显示已工作时长（瞬态重试时附提示）；完成后显示总结行并可展开工作过程 -->
    <div v-if="isLoading" class="status-line running">
      <span>已工作 {{ elapsedText }}</span>
      <span v-if="retryNotice" class="retry-notice">{{ retryNotice }}</span>
    </div>
    <div
      v-else-if="processBlocks.length"
      class="status-line done"
      @click="processExpanded = !processExpanded"
    >
      <CheckCircleOutlined class="done-icon" />
      <span>已完成 · 用时 {{ durationText }}</span>
      <CaretRightOutlined :rotate="processExpanded ? 90 : 0" class="caret-icon" />
    </div>

    <!-- 工作过程（进行中顺着流式显示；完成后默认收起，点击状态行展开逐个查看）。
         页面在后台时浏览器抑制 CSS 过渡（会瞬收），故完成瞬间若页面隐藏则先保持展开，
         等用户切回页面可见时再播放收缩动画 -->
    <CollapseTransition :open="isLoading || processExpanded || holdProcessOpen" :seam-gap="PROCESS_SEAM_GAP">
      <div v-if="processBlocks.length" class="process-list">
        <template v-for="(block, index) in processBlocks" :key="index">
          <!-- 文本块（过程中的叙述） -->
          <MarkdownView
            v-if="block.type === 'text'"
            class="answer-content"
            :content="block.text"
          />

          <!-- 思考块（zcode GUI 式）：收起=「图标 思考 · 摘要/时长」单行；展开=竖线内容区（与图标对齐），上方不再重复摘要 -->
          <div v-else-if="block.type === 'thinking'" class="thinking-block">
            <div class="thinking-bar" @click="toggleThinking(index)">
              <BrainIcon :size="13" class="think-icon" :class="{ active: isActiveThinking(index) }" />
              <span class="thinking-label" :class="{ running: isActiveThinking(index) }">{{ isActiveThinking(index) ? '正在思考' : '思考' }}</span>
              <span
                v-if="isActiveThinking(index) && !isThinkingExpanded(index)"
                class="thinking-live"
              >· {{ thinkingTail(block) }}</span>
              <span v-else-if="!isActiveThinking(index)" class="thinking-label">· {{ durationSuffix(block) }}</span>
              <CaretRightOutlined :rotate="isThinkingExpanded(index) ? 90 : 0" class="caret-icon" />
            </div>
            <CollapseTransition :open="isThinkingExpanded(index)">
              <div class="thinking-text">{{ block.text }}</div>
            </CollapseTransition>
          </div>

          <!-- 问答块（AskUserQuestion）：选项卡片，作答提交后锁定展示所选 -->
          <div
            v-else-if="block.type === 'question'"
            class="question-block"
            :class="{ answered: questionDone(block) }"
          >
            <div v-for="(q, qi) in block.questions" :key="qi" class="question-item">
              <div class="question-head">
                <span class="question-chip">{{ q.header }}</span>
                <span class="question-text">{{ q.question }}</span>
              </div>
              <template v-if="!questionDone(block)">
                <div
                  v-for="(opt, oi) in q.options"
                  :key="oi"
                  class="question-option"
                  :class="{ selected: optionSelected(block, qi, optionKey(opt, oi)) }"
                  @click="toggleOption(block, qi, optionKey(opt, oi))"
                >
                  <span class="option-check">{{ optionSelected(block, qi, optionKey(opt, oi)) ? '●' : '○' }}</span>
                  <span v-if="optionKey(opt, oi)" class="option-key">{{ optionKey(opt, oi) }}</span>
                  <span class="option-label">{{ opt.label }}</span>
                  <span v-if="opt.description" class="option-desc">{{ opt.description }}</span>
                </div>
                <pre v-if="previewOf(q, block, qi)" class="option-preview">{{ previewOf(q, block, qi) }}</pre>
                <div
                  class="question-option option-other"
                  :class="{ selected: otherSelected(block, qi) }"
                  @click="focusOther(block, qi)"
                >
                  <span class="option-check">{{ otherSelected(block, qi) ? '●' : '○' }}</span>
                  <input
                    class="option-custom"
                    :value="customOf(block, qi)"
                    placeholder="其他（自定义回答，可引用选项标签如 A）"
                    @input="setCustom(block, qi, ($event.target as HTMLInputElement).value)"
                    @click.stop
                  />
                </div>
              </template>
              <div v-else class="question-answered">
                <CheckCircleOutlined class="answered-icon" />
                <span>{{ answeredText(block, qi) }}</span>
              </div>
            </div>
            <div v-if="!questionDone(block)" class="question-actions">
              <button class="question-submit" :disabled="!submittable(block) || submitting" @click="submitAnswers(block)">
                {{ submitting ? '提交中…' : '提交回答' }}
              </button>
            </div>
          </div>

          <!-- 工具块：语义化行（可点击展开看执行结果）+ 可跳转的来源链接（随过程收起/展开）。
               zcode 约定：图标恒静态，运行态由动词文案扫光表达（animated-gradient-text） -->
          <div v-else class="tool-block">
            <div class="tool-row" :class="{ expandable: block.result }" @click="toggleToolResult(index)">
              <span class="tool-status">
                <component :is="toolIcon(block.tool)" class="tool-icon" />
              </span>
              <span class="tool-verb" :class="{ running: block.status === 'running' }">{{ toolVerb(block) }}</span>
              <span v-if="shownMeta(index, block)" class="tool-meta">{{ shownMeta(index, block) }}</span>
              <CaretRightOutlined
                v-if="block.result && !typing(index, block)"
                :rotate="expandedTools.has(index) ? 90 : 0"
                class="caret-icon tool-caret"
              />
            </div>
            <CollapseTransition :open="expandedTools.has(index)">
              <pre v-if="block.result" class="tool-result">{{ block.result }}</pre>
            </CollapseTransition>

            <!-- 来源链接（DeepSeek 式：点击直接跳转网页） -->
            <div v-if="block.status === 'done' && sourceChips(block).length" class="tool-sources">
              <a
                v-for="(chip, ci) in sourceChips(block)"
                :key="ci"
                :href="chip.url"
                target="_blank"
                rel="noopener noreferrer"
                class="source-chip"
                :title="chip.title"
              >
                <GlobalOutlined class="chip-icon" />
                <span class="chip-title">{{ chip.title || chip.host }}</span>
                <span class="chip-idx">{{ ci + 1 }}</span>
              </a>
            </div>
          </div>
        </template>
      </div>
    </CollapseTransition>

    <!-- 最终回答（最后一个过程块之后的文本；无过程块时渲染全部文本） -->
    <MarkdownView
      v-if="finalText"
      class="answer-content"
      :class="{ 'with-process': processBlocks.length > 0 }"
      :content="finalText"
    />

    <!-- 历史消息（无块信息）：直接渲染正文 -->
    <MarkdownView v-else-if="!processBlocks.length && content" class="answer-content" :content="content" />

    <!-- Agent 本轮产出文件（outputs 目录新文件，点击即下载） -->
    <AttachmentChips
      v-if="outputAttachments.length"
      :items="outputAttachments"
      downloadable
      class="output-attachments"
    />

    <!-- 流式尾部加载动画 -->
    <div v-if="isLoading" class="stream-tail">
      <ZcodeSpinner :size="14" />
    </div>

    <div v-if="interrupted" class="stream-interrupted">连接中断，本条回答可能不完整</div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onUnmounted, ref, watch } from 'vue'
import type { Component } from 'vue'
import {
  CaretRightOutlined,
  CheckCircleOutlined,
  OrderedListOutlined,
  SearchOutlined,
  ReadOutlined,
  FilePdfOutlined,
  FileTextOutlined,
  EditOutlined,
  PictureOutlined,
  CodeOutlined,
  ToolOutlined,
  GlobalOutlined,
  FileAddOutlined,
  FileSearchOutlined
} from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import ZcodeSpinner from '@/components/ZcodeSpinner.vue'
import CollapseTransition from '@/components/CollapseTransition.vue'
import BrainIcon from '@/components/BrainIcon.vue'
import AttachmentChips from './AttachmentChips.vue'
import { answerQuestion } from '@/api/chat'
import type { AttachmentItem, MessageBlock } from '@/types'

type QuestionBlock = Extract<MessageBlock, { type: 'question' }>

interface Props {
  content: string
  blocks?: MessageBlock[]
  isLoading?: boolean
  /** 本条回复耗时（毫秒），usage/持久化提供） */
  durationMs?: number
  /** 消息创建时间（进行中据此计算已工作时长） */
  createTime?: Date
  /** 流式传输异常中断（界面提示回答可能不完整） */
  interrupted?: boolean
  /** 瞬态失败自动重试提示（后端 retry 事件，内容恢复即清除） */
  retryNotice?: string
  /** 所属会话 id（提交问答答案用） */
  chatId?: string
}

const props = withDefaults(defineProps<Props>(), {
  blocks: () => [],
  isLoading: false,
  createTime: () => new Date()
})

const processExpanded = ref(false)
const expandedThinking = ref<Set<number>>(new Set())
const expandedTools = ref<Set<number>>(new Set())

/** .mio-bot-message 的 flex gap：工作过程折叠层的接缝补偿（抵消 display 切换时 gap 的瞬移） */
const PROCESS_SEAM_GAP = 12

function toggleToolResult(index: number): void {
  const next = new Set(expandedTools.value)
  next.has(index) ? next.delete(index) : next.add(index)
  expandedTools.value = next
}

// ---------- 问答块（AskUserQuestion）的草稿与提交 ----------

/** 每个问答块的作答草稿：按块 id 存各问题的已选标签与自定义文本 */
const questionDrafts = ref<
  Record<string, { selections: Record<number, string[]>; custom: Record<number, string> }>
>({})
const submitting = ref(false)
/** 本端已提交的块（SSE answered 广播到达前乐观锁定） */
const submittedIds = ref<Set<string>>(new Set())

function draftOf(block: QuestionBlock): { selections: Record<number, string[]>; custom: Record<number, string> } {
  if (!questionDrafts.value[block.id]) {
    questionDrafts.value[block.id] = { selections: {}, custom: {} }
  }
  return questionDrafts.value[block.id]
}

function questionDone(block: QuestionBlock): boolean {
  return block.status === 'answered' || submittedIds.value.has(block.id)
}

function optionSelected(block: QuestionBlock, questionIndex: number, label: string): boolean {
  return (draftOf(block).selections[questionIndex] ?? []).includes(label)
}

/** 选项作答键：优先模型给的 key，旧数据回退 label */
function optionKey(opt: QuestionBlock['questions'][number]['options'][number], index: number): string {
  return opt.key || opt.label || String(index + 1)
}

/** 单选：点击预置选项即唯一选中，并清空"其他" */
function toggleOption(block: QuestionBlock, questionIndex: number, key: string): void {
  const draft = draftOf(block)
  const current = draft.selections[questionIndex] ?? []
  draft.selections[questionIndex] = current.includes(key) ? [] : [key]
  if (draft.selections[questionIndex].length) {
    draft.custom[questionIndex] = ''
  }
}

/** "其他"是否为当前选中（有自定义文本且未选预置项） */
function otherSelected(block: QuestionBlock, questionIndex: number): boolean {
  const draft = draftOf(block)
  return !(draft.selections[questionIndex] ?? []).length && !!(draft.custom[questionIndex] ?? '').trim()
}

/** 点击"其他"行：聚焦输入框并即时成为唯一选中 */
function focusOther(block: QuestionBlock, questionIndex: number): void {
  const draft = draftOf(block)
  draft.selections[questionIndex] = []
}

function customOf(block: QuestionBlock, questionIndex: number): string {
  return draftOf(block).custom[questionIndex] ?? ''
}

function setCustom(block: QuestionBlock, questionIndex: number, value: string): void {
  const draft = draftOf(block)
  draft.custom[questionIndex] = value
  if (value.trim()) {
    // 输入自定义即选中"其他"：清除预置选项（单选互斥）
    draft.selections[questionIndex] = []
  }
}

/** 当前悬选选项的预览（单选显示已选项 preview；多选显示最近选中项） */
function previewOf(q: QuestionBlock['questions'][number], block: QuestionBlock, questionIndex: number): string | undefined {
  const selected = draftOf(block).selections[questionIndex] ?? []
  if (!selected.length) return undefined
  const option = q.options.find((opt, i) => selected.includes(opt.key || opt.label || String(i + 1)))
  return option?.preview
}

function submittable(block: QuestionBlock): boolean {
  return block.questions.some((_, qi) => {
    const draft = draftOf(block)
    return (draft.selections[qi] ?? []).length > 0 || (draft.custom[qi] ?? '').trim().length > 0
  })
}

async function submitAnswers(block: QuestionBlock): Promise<void> {
  if (!props.chatId || submitting.value) return
  submitting.value = true
  try {
    const answers = block.questions.map((_, qi) => {
      const draft = draftOf(block)
      return {
        index: qi,
        selections: draft.selections[qi] ?? [],
        custom: (draft.custom[qi] ?? '').trim() || undefined
      }
    })
    await answerQuestion(props.chatId, block.id, answers)
    submittedIds.value = new Set([...submittedIds.value, block.id])
  } catch (error) {
    console.error('提交回答失败:', error)
  } finally {
    submitting.value = false
  }
}

function answeredText(block: QuestionBlock, questionIndex: number): string {
  const answered = block.answers?.find(item => item.index === questionIndex)
  const fromBroadcast = answered?.selections ?? []
  if (fromBroadcast.length) return fromBroadcast.join('、')
  const draft = questionDrafts.value[block.id]
  const selections = draft?.selections[questionIndex] ?? []
  const custom = (draft?.custom[questionIndex] ?? '').trim()
  return [...selections, ...(custom ? [custom] : [])].join('、') || '未作答'
}

// 完成于后台标签页时暂缓收缩：等页面重新可见再收（动画才不会被浏览器吞掉）
const holdProcessOpen = ref(false)
let holdVisibleListener: (() => void) | null = null

watch(
  () => props.isLoading,
  (loading) => {
    if (!loading && typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      holdProcessOpen.value = true
      const onVisible = () => {
        if (document.visibilityState !== 'visible') return
        holdProcessOpen.value = false
        document.removeEventListener('visibilitychange', onVisible)
        holdVisibleListener = null
      }
      if (holdVisibleListener) {
        document.removeEventListener('visibilitychange', holdVisibleListener)
      }
      document.addEventListener('visibilitychange', onVisible)
      holdVisibleListener = onVisible
    }
  }
)

onUnmounted(() => {
  if (holdVisibleListener) {
    document.removeEventListener('visibilitychange', holdVisibleListener)
    holdVisibleListener = null
  }
})

/** 过程块 = 最后一个非文本块及其之前的全部（叙述/思考/工具）；其后的是最终回答。
 * 附件块不属过程：不参与过程/正文分界，单独渲染在正文下方（Agent 产出可下载） */
const lastNonTextIndex = computed(() => {
  for (let i = props.blocks.length - 1; i >= 0; i--) {
    const type = props.blocks[i].type
    if (type !== 'text' && type !== 'attachments') return i
  }
  return -1
})

const processBlocks = computed(() =>
  lastNonTextIndex.value >= 0
    ? props.blocks.slice(0, lastNonTextIndex.value + 1).filter(b => b.type !== 'attachments')
    : []
)

const outputAttachments = computed<AttachmentItem[]>(() =>
  props.blocks
    .filter((b): b is Extract<MessageBlock, { type: 'attachments' }> => b.type === 'attachments')
    .filter(b => b.side === 'output')
    .flatMap(b => b.items)
)

const finalText = computed(() => {
  if (!props.blocks.length) return ''
  const tail = lastNonTextIndex.value >= 0
    ? props.blocks.slice(lastNonTextIndex.value + 1)
    : props.blocks
  return tail.filter(b => b.type === 'text').map(b => b.text).join('')
})

// ---------- 工具语义化展示 ----------
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

/** 工具行动词：运行中→进行式（扫光），完成→过去式；Explore 子代理沿用 zcode 的「查阅」 */
function toolVerb(block: ToolBlock): string {
  if (block.tool === 'Agent' && parseArgs(block)?.subagent_type === 'Explore') {
    return '查阅'
  }
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
  generatePDF: FilePdfOutlined
})

function toolIcon(tool: string): Component {
  return TOOL_ICONS[tool] ?? ToolOutlined
}

type ToolBlock = Extract<MessageBlock, { type: 'tool' }>

function parseArgs(block: ToolBlock): Record<string, any> | null {
  try {
    return JSON.parse(block.args || '')
  } catch {
    return null
  }
}

function hostOf(url?: string): string {
  if (!url) return ''
  try {
    return new URL(url).hostname
  } catch {
    return url.replace(/^https?:\/\//, '').slice(0, 40)
  }
}

/** 工具行元信息：从参数提取人话摘要，不暴露原始 JSON */
function toolMeta(block: ToolBlock): string {
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
function partialFirstString(raw: string | undefined): string {
  if (!raw) return ''
  const match = raw.match(/:\s*"([^"]*)"?/)
  if (match && match[1]) {
    const value = match[1].replace(/\\n/g, ' ').trim()
    return value.length > 40 ? `${value.slice(0, 40)}…` : value
  }
  return ''
}

// ---------- 来源链接（DeepSeek 式可跳转源） ----------
interface SourceChip {
  title: string
  url: string
  host: string
}

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
function sourceChips(block: ToolBlock): SourceChip[] {
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

// ---------- 工具行元信息：真流式直显（zcode 方式） ----------
// 不做"收完再回放"的打字机：tool_args 的每个 SSE 增量到达即重算语义摘要，
// 显示速率=模型实际生成速率；运行中且参数仍在流式时带光标
function shownMeta(_index: number, block: ToolBlock): string {
  return toolMeta(block)
}

function typing(_index: number, block: ToolBlock): boolean {
  return props.isLoading && block.status === 'running'
}

// ---------- 时长 ----------
function formatDuration(ms: number): string {
  const totalSeconds = Math.max(0, Math.round(ms / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return minutes > 0 ? `${minutes}分${seconds}秒` : `${seconds}秒`
}

const durationText = computed(() =>
  props.durationMs ? formatDuration(props.durationMs) : '')

// 进行中：每秒跳动计算"已工作 X分Y秒"
const now = ref(Date.now())
let elapsedTimer: number | undefined
watch(
  () => props.isLoading,
  (loading) => {
    if (loading) {
      elapsedTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
    } else if (elapsedTimer !== undefined) {
      clearInterval(elapsedTimer)
      elapsedTimer = undefined
    }
  },
  { immediate: true }
)
onUnmounted(() => {
  if (elapsedTimer !== undefined) clearInterval(elapsedTimer)
})

const elapsedText = computed(() => formatDuration(now.value - props.createTime.getTime()))

// ---------- 思考块（zcode 式单行折叠条） ----------
type ThinkingBlock = Extract<MessageBlock, { type: 'thinking' }>

/** 流式进行中的思考 = 消息末块且尚未收尾（换块/结束时会写入 durationMs） */
function isActiveThinking(index: number): boolean {
  const block = props.blocks[index]
  return props.isLoading && index === props.blocks.length - 1
    && block?.type === 'thinking' && block.durationMs == null
}

function isThinkingExpanded(index: number): boolean {
  return expandedThinking.value.has(index)
}

function toggleThinking(index: number): void {
  const next = new Set(expandedThinking.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  expandedThinking.value = next
}

/** 单行实时摘要：取思考文本的最后一段（最新内容），超长截头保尾 */
function thinkingTail(block: ThinkingBlock): string {
  const lines = block.text.split('\n').map(l => l.trim()).filter(Boolean)
  const tail = lines[lines.length - 1] ?? ''
  return tail.length > 90 ? '…' + tail.slice(-90) : tail
}

/** 收起态标签后缀：「持续了 X 秒」；不足 1 秒沿用 zcode 的「持续了几秒」 */
function durationSuffix(block: ThinkingBlock): string {
  if (block.durationMs == null) return ''
  const seconds = Math.round(block.durationMs / 1000)
  if (seconds < 1) return '持续了几秒'
  if (seconds < 60) return `持续了 ${seconds} 秒`
  return `持续了 ${formatDuration(block.durationMs)}`
}

</script>

<style lang="scss" scoped>
// zcode animated-gradient-text（styles.css）：运行态文案渐变扫光。
// 代替旋转图标——长期运行的工具行里旋转动画持续占用渲染资源，zcode 只让文字扫光
@mixin animated-gradient-text($strong, $soft) {
  display: inline-block;
  background: linear-gradient(
    90deg,
    $strong 0%,
    $strong 34%,
    $soft 50%,
    $strong 66%,
    $strong 100%
  );
  background-size: 300% 100%;
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
  animation: gradient-flow 4s linear infinite;
  will-change: background-position;
  transform: translateZ(0);
  backface-visibility: hidden;
}

@keyframes gradient-flow {
  0% {
    background-position: 100% 0;
  }
  50% {
    background-position: 0% 0;
  }
  100% {
    background-position: 100% 0;
  }
}

.mio-bot-message {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

// 状态行（进行中计时 / 完成总结行）
.status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #86909c;
  user-select: none;

  .retry-notice {
    padding: 1px 8px;
    border-radius: 8px;
    background: #fff7e6;
    color: #d48806;
    font-size: 12px;
  }

  &.done {
    cursor: pointer;
    padding: 4px 8px;
    margin: 0 -8px;
    width: fit-content;
    border-radius: 8px;
    color: #4e5969;
    transition: background 0.2s;

    &:hover {
      background: #f2f3f5;
    }

    .done-icon {
      color: #00b42a;
      font-size: 14px;
    }

    .caret-icon {
      font-size: 11px;
    }
  }
}

.process-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.caret-icon {
  font-size: 11px;
  color: #86909c;
  transition: transform 0.2s;
}

// 思考块（zcode GUI 式：图标+「思考 · 摘要」单行；展开后竖线内容区与图标对齐）
.thinking-block {
  .thinking-bar {
    display: flex;
    align-items: center;
    gap: 6px;
    max-width: 100%;
    padding: 4px 10px;
    margin: 0 -10px;
    border-radius: 8px;
    cursor: pointer;
    user-select: none;
    color: #86909c;
    font-size: 13px;
    transition: background 0.2s;

    &:hover {
      background: #f2f3f5;
    }

    .think-icon {
      flex-shrink: 0;
      color: #86909c;

      // 思考进行中：图标微呼吸提示活跃（加载动画只出现在消息下方）
      &.active {
        color: $primary-color;
        animation: think-pulse 1.6s ease-in-out infinite;
      }
    }

    .thinking-live {
      flex: 1;
      min-width: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .thinking-label {
      flex-shrink: 0;
      white-space: nowrap;

      // 思考进行中：「正在思考」文案扫光（与工具行动词同一套 zcode 动效）
      &.running {
        font-weight: 500;
        @include animated-gradient-text(#86909c, rgba(134, 144, 156, 0.25));
      }
    }
  }

  // 竖线从内容第一行贯穿到最后一行，x 位置与上方图标中心对齐（图标 13px → 中心 ≈ 6px）
  .thinking-text {
    margin: 4px 0 6px 6px;
    padding: 2px 0 4px 14px;
    border-left: 2px solid #e5e6eb;
    font-size: 13px;
    line-height: 1.65;
    color: #86909c;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 280px;
    overflow-y: auto;
    @include thin-scrollbar;
  }
}

@keyframes think-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

// 工具块：时间线条目 + 可跳转来源链接
.tool-block {
  animation: tool-in 0.18s ease;

  .tool-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 3px 8px;
    margin: 0 -8px;
    border-radius: 6px;
    min-width: 0;
    user-select: none;

    &.expandable {
      cursor: pointer;

      &:hover {
        background: #f2f3f5;
      }
    }

    .tool-caret {
      flex-shrink: 0;
      font-size: 10px;
    }
  }

  // 工具执行结果（点击工具行展开）：与工具行左对齐，内容可选中复制
  .tool-result {
    margin: 4px 0 2px 0;
    padding: 8px 12px;
    background: #f7f8fa;
    border-radius: 8px;
    font-size: 12px;
    line-height: 1.55;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    color: #4e5969;
    white-space: pre-wrap;
    word-break: break-word;
    user-select: text;
    max-height: 220px;
    overflow-y: auto;
    @include thin-scrollbar;
  }

  .tool-status {
    width: 16px;
    flex-shrink: 0;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .tool-icon {
    font-size: 13px;
    color: $primary-color;
  }

  // 动词标签（正在执行/已执行…）：zcode kindLabel——运行态扫光 + medium，完成态浅色
  .tool-verb {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    color: #4e5969;

    &.running {
      @include animated-gradient-text($primary-color, rgba(42, 161, 169, 0.25));
    }
  }

  .tool-meta {
    flex: 1;
    min-width: 0;
    font-size: 12px;
    color: #86909c;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  // 来源链接：点击直接跳转网页（随工作过程收起/展开）
  .tool-sources {
    margin-top: 4px;
    display: flex;
    flex-wrap: wrap;
    gap: 6px;

    .source-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      max-width: 240px;
      padding: 4px 10px;
      background: #f7f8fa;
      border-radius: 8px;
      text-decoration: none;
      transition: background 0.2s;

      &:hover {
        background: #eef1f4;

        .chip-title {
          color: $primary-color;
        }
      }

      .chip-icon {
        font-size: 12px;
        color: #86909c;
        flex-shrink: 0;
      }

      .chip-title {
        font-size: 12px;
        color: #4e5969;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .chip-idx {
        flex-shrink: 0;
        min-width: 14px;
        height: 14px;
        padding: 0 3px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 10px;
        color: #86909c;
        background: #e8eaee;
        border-radius: 7px;
      }
    }
  }
}

  // 问答块（AskUserQuestion）：待答=选项卡片；已答=所选摘录
  .question-block {
    margin: 6px 0;
    padding: 10px 12px;
    border: 1px solid #e5e6eb;
    border-radius: 10px;
    background: #fbfcfd;

    &.answered {
      background: #f7faf7;
      border-color: #d9ecd9;
    }
  }

  .question-item {
    & + .question-item {
      margin-top: 10px;
      padding-top: 10px;
      border-top: 1px dashed #e5e6eb;
    }
  }

  .question-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    flex-wrap: wrap;
  }

  .question-chip {
    padding: 1px 8px;
    border-radius: 999px;
    background: rgba(42, 161, 169, 0.1);
    color: $primary-color;
    font-size: 11px;
    line-height: 18px;
    white-space: nowrap;
  }

  .question-text {
    font-size: 13px;
    font-weight: 600;
    color: #1d2129;
  }

  .question-option {
    display: flex;
    align-items: baseline;
    gap: 8px;
    padding: 6px 10px;
    margin: 4px 0;
    border: 1px solid #e5e6eb;
    border-radius: 8px;
    cursor: pointer;
    user-select: none;
    transition: border-color 0.15s, background 0.15s;

    &:hover {
      border-color: rgba(42, 161, 169, 0.45);
    }

    &.selected {
      border-color: $primary-color;
      background: rgba(42, 161, 169, 0.08);
    }
  }

  .option-key {
    padding: 0 7px;
    border: 1px solid rgba(42, 161, 169, 0.35);
    border-radius: 6px;
    color: $primary-color;
    font-size: 11px;
    font-weight: 600;
    line-height: 18px;
    flex-shrink: 0;
  }

  .option-check {
    font-size: 12px;
    color: $primary-color;
    flex-shrink: 0;
  }

  .question-option:not(.selected) .option-check {
    color: #c9cdd4;
  }

  .option-label {
    font-size: 13px;
    color: #1d2129;
    white-space: nowrap;
  }

  .option-desc {
    font-size: 12px;
    color: #86909c;
  }

  .option-preview {
    margin: 4px 0;
    padding: 8px 10px;
    background: #f7f8fa;
    border-radius: 8px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 12px;
    color: #4e5969;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 180px;
    overflow-y: auto;
    @include thin-scrollbar;
  }

  .option-custom {
    flex: 1;
    min-width: 0;
    border: none;
    background: transparent;
    padding: 2px 4px;
    font-size: 12px;
    outline: none;
    user-select: text;
    -webkit-user-select: text;

    &::placeholder {
      color: #c9cdd4;
    }
  }

  .option-other {
    cursor: text;
  }

  .question-answered {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #1d2129;

    .answered-icon {
      color: #00b42a;
      font-size: 14px;
    }
  }

  .question-actions {
    margin-top: 8px;
    text-align: right;
  }

  .question-submit {
    padding: 5px 16px;
    border: none;
    border-radius: 8px;
    background: $primary-color;
    color: #fff;

    &:hover:not(:disabled) {
      background: darken($primary-color, 8%);
    }
    font-size: 12px;
    cursor: pointer;

    &:disabled {
      background: #c9cdd4;
      cursor: not-allowed;
    }
  }

.answer-content {
  font-size: 14px;
  padding: 0 4px;
}

// Agent 产出附件区（正文下方，可下载）
.output-attachments {
  padding: 0 4px;
}

// 流式尾部加载动画
.stream-tail {
  display: flex;
  align-items: center;
  padding: 0 4px;
  user-select: none;
}

.stream-interrupted {
  font-size: 12px;
  color: #d48806;
}

@keyframes tool-in {
  from {
    opacity: 0;
    transform: translateY(-3px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
