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
    <CollapseTransition :open="isLoading || processExpanded || holdProcessOpen">
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
              <span class="thinking-label">思考</span>
              <span
                v-if="isActiveThinking(index) && !isThinkingExpanded(index)"
                class="thinking-live"
              >· {{ thinkingTail(block) }}</span>
              <span v-else-if="!isActiveThinking(index)" class="thinking-label">· {{ durationSuffix(block) }}</span>
              <CaretRightOutlined :rotate="isThinkingExpanded(index) ? 90 : 0" class="caret-icon" />
            </div>
            <CollapseTransition :open="isThinkingExpanded(index)">
              <div :ref="el => setThinkingEl(index, el)" class="thinking-text">{{ block.text }}</div>
            </CollapseTransition>
          </div>

          <!-- 工具块：语义化行 + 可跳转的来源链接（随过程收起/展开） -->
          <div v-else class="tool-block">
            <div class="tool-row">
              <span class="tool-status">
                <ZcodeSpinner v-if="block.status === 'running'" :size="14" />
                <component :is="toolIcon(block.tool)" v-else class="tool-icon" />
              </span>
              <span class="tool-name">{{ toolLabel(block.tool) }}</span>
              <span v-if="shownMeta(index, block)" class="tool-meta">
                {{ shownMeta(index, block) }}<span v-if="typing(index, block)" class="tw-cursor"></span>
              </span>
            </div>

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

    <!-- 流式尾部加载动画 -->
    <div v-if="isLoading" class="stream-tail">
      <ZcodeSpinner :size="14" />
    </div>

    <div v-if="interrupted" class="stream-interrupted">连接中断，本条回答可能不完整</div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, nextTick, onUnmounted, ref, watch } from 'vue'
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
import type { MessageBlock } from '@/types'

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
}

const props = withDefaults(defineProps<Props>(), {
  blocks: () => [],
  isLoading: false,
  createTime: () => new Date()
})

const processExpanded = ref(false)
const expandedThinking = ref<Set<number>>(new Set())

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

/** 过程块 = 最后一个非文本块及其之前的全部（叙述/思考/工具）；其后的是最终回答 */
const lastNonTextIndex = computed(() => {
  for (let i = props.blocks.length - 1; i >= 0; i--) {
    if (props.blocks[i].type !== 'text') return i
  }
  return -1
})

const processBlocks = computed(() =>
  lastNonTextIndex.value >= 0 ? props.blocks.slice(0, lastNonTextIndex.value + 1) : []
)

const finalText = computed(() => {
  if (!props.blocks.length) return ''
  const tail = lastNonTextIndex.value >= 0
    ? props.blocks.slice(lastNonTextIndex.value + 1)
    : props.blocks
  return tail.filter(b => b.type === 'text').map(b => b.text).join('')
})

// ---------- 工具语义化展示 ----------
const TOOL_LABELS: Record<string, string> = {
  managePlan: '任务清单',
  runCommand: '执行命令',
  readFile: '读取文件',
  writeFile: '写入文件',
  editFile: '编辑文件',
  glob: '查找文件',
  grep: '搜索内容',
  searchWeb: '联网搜索',
  fetchUrl: '阅读网页',
  generatePDF: '生成 PDF'
}

/** 未登记的工具（如自定义 MCP）：camelCase 拆词作展示名，不暴露原始方法名 */
function toolLabel(tool: string): string {
  if (TOOL_LABELS[tool]) return TOOL_LABELS[tool]
  const spaced = tool.replace(/([a-z0-9])([A-Z])/g, '$1 $2')
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
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
      return a?.query ? `“${a.query}”` : ''
    case 'scrapeWebPage':
      return hostOf(a?.url) || a?.url || ''
    case 'generatePDF':
      return a?.fileName ? `“${a.fileName}”` : ''
    case 'readFile':
    case 'writeFile':
      return a?.fileName ?? ''
    case 'searchImage':
      return a?.query ? `“${a.query}”` : ''
    case 'executeTerminalCommand':
      return a?.command ? `$ ${a.command}` : ''
    case 'downloadResource':
      return a?.fileName || hostOf(a?.url) || ''
    default: {
      const raw = (block.args ?? '').replace(/\s+/g, ' ').trim()
      return raw ? raw.slice(0, 50) : ''
    }
  }
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

// ---------- 打字机效果 ----------
// 流式中的工具行元信息逐字显示（带光标）；历史还原直接完整显示
const TYPE_INTERVAL_MS = 28
const revealMap = ref<Record<number, number>>({})
let revealTimer: number | undefined

watch(
  () => props.isLoading,
  (loading) => {
    if (loading && revealTimer === undefined) {
      revealTimer = window.setInterval(advanceReveal, TYPE_INTERVAL_MS)
    }
  },
  { immediate: true }
)

watch(
  processBlocks,
  (blocks) => {
    for (let i = 0; i < blocks.length; i++) {
      const b = blocks[i]
      if (b.type === 'tool' && !(i in revealMap.value)) {
        // 已完成的工具块直接显示完整信息：切换会话再切回（组件重建）时不重放打字机
        revealMap.value[i] = props.isLoading && b.status === 'running'
          ? 0
          : toolMeta(b).length
      }
    }
  },
  { immediate: true }
)

function advanceReveal(): void {
  const blocks = processBlocks.value
  let pending = false
  for (let i = 0; i < blocks.length; i++) {
    const b = blocks[i]
    if (b.type !== 'tool') continue
    const full = toolMeta(b).length
    const shown = revealMap.value[i] ?? full
    if (shown < full) {
      revealMap.value[i] = Math.min(full, shown + 1)
      pending = true
    }
  }
  if (!pending && !props.isLoading && revealTimer !== undefined) {
    clearInterval(revealTimer)
    revealTimer = undefined
  }
}

onUnmounted(() => {
  if (revealTimer !== undefined) clearInterval(revealTimer)
})

function shownMeta(index: number, block: ToolBlock): string {
  const full = toolMeta(block)
  const shown = revealMap.value[index]
  return shown === undefined ? full : full.slice(0, shown)
}

function typing(index: number, block: ToolBlock): boolean {
  const full = toolMeta(block).length
  const shown = revealMap.value[index] ?? full
  return shown < full
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
  if (next.has(index) && isActiveThinking(index)) {
    nextTick(() => scrollThinkingToBottom(index))
  }
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

const thinkingEls = new Map<number, HTMLElement>()
function setThinkingEl(index: number, el: unknown): void {
  if (el instanceof HTMLElement) {
    thinkingEls.set(index, el)
  } else {
    thinkingEls.delete(index)
  }
}

function scrollThinkingToBottom(index: number): void {
  const el = thinkingEls.get(index)
  if (el) el.scrollTop = el.scrollHeight
}

// 展开中的实时思考：内容增长时自动滚到最底（zcode 行为）
watch(
  () => {
    const block = props.blocks[props.blocks.length - 1]
    return block?.type === 'thinking' && block.durationMs == null ? block.text.length : -1
  },
  (len) => {
    if (len < 0) return
    const index = props.blocks.length - 1
    if (isThinkingExpanded(index)) {
      scrollThinkingToBottom(index)
    }
  }
)
</script>

<style lang="scss" scoped>
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
  user-select: none;
  animation: tool-in 0.18s ease;

  .tool-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 3px 8px;
    margin: 0 -8px;
    border-radius: 6px;
    min-width: 0;
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

  .tool-name {
    flex-shrink: 0;
    font-size: 13px;
    color: $primary-color;
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

  .tw-cursor {
    display: inline-block;
    width: 1px;
    height: 12px;
    margin-left: 1px;
    vertical-align: -1px;
    background: $primary-color;
    animation: blink 0.8s step-end infinite;
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

.answer-content {
  font-size: 14px;
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

@keyframes blink {
  50% {
    opacity: 0;
  }
}
</style>
