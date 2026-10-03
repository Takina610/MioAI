<template>
  <div class="mio-bot-message">
    <!-- 状态行：进行中显示已工作时长；完成后显示总结行并可展开工作过程 -->
    <div v-if="isLoading" class="status-line running">
      <ZcodeSpinner :size="13" />
      <span>已工作 {{ elapsedText }}</span>
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

    <!-- 工作过程（进行中顺着流式显示；完成后默认收起，点击状态行展开逐个查看） -->
    <CollapseTransition :open="isLoading || processExpanded">
      <div v-if="processBlocks.length" class="process-list">
        <template v-for="(block, index) in processBlocks" :key="index">
          <!-- 文本块（过程中的叙述） -->
          <MarkdownView
            v-if="block.type === 'text'"
            class="answer-content"
            :content="block.text"
          />

          <!-- 思考块 -->
          <div v-else-if="block.type === 'thinking'" class="thinking-block">
            <div class="block-header" @click="toggleThinking(index)">
              <CaretRightOutlined :rotate="isThinkingExpanded(index) ? 90 : 0" class="caret-icon" />
              <BulbOutlined class="block-icon" />
              <span class="block-title">思考过程</span>
            </div>
            <CollapseTransition :open="isThinkingExpanded(index)">
              <div class="thinking-text">{{ block.text }}</div>
            </CollapseTransition>
          </div>

          <!-- 工具块：语义化行 + 友好结果卡片，点开可看原始参数/结果 -->
          <div v-else class="tool-block">
            <div class="tool-row" @click="toggleTool(index)">
              <span class="tool-status">
                <ZcodeSpinner v-if="block.status === 'running'" :size="14" />
                <component :is="toolIcon(block.tool)" v-else class="tool-icon" />
              </span>
              <span class="tool-name">{{ toolLabel(block.tool) }}</span>
              <span v-if="shownMeta(index, block)" class="tool-meta">
                {{ shownMeta(index, block) }}<span v-if="typing(index, block)" class="tw-cursor"></span>
              </span>
              <CaretRightOutlined :rotate="expandedTools.has(index) ? 90 : 0" class="caret-icon tool-caret" />
            </div>

            <!-- 友好结果卡片（联网搜索/阅读网页） -->
            <div v-if="block.status === 'done' && resultCards(block).length" class="tool-results">
              <div v-for="(card, ci) in resultCards(block)" :key="ci" class="result-card">
                <div class="rc-title">{{ card.title }}</div>
                <div v-if="card.snippet" class="rc-snippet">{{ card.snippet }}</div>
                <div v-if="card.host" class="rc-host">{{ card.host }}</div>
              </div>
              <div v-if="extraResultCount(block)" class="rc-more">还有 {{ extraResultCount(block) }} 条结果，点击行展开查看</div>
            </div>

            <CollapseTransition :open="expandedTools.has(index)">
              <div class="tool-detail">
                <div v-if="block.args" class="detail-section">
                  <span class="detail-label">调用参数</span>
                  <pre class="detail-content">{{ prettyJson(block.args) }}</pre>
                </div>
                <div v-if="block.result" class="detail-section">
                  <span class="detail-label">返回结果</span>
                  <pre class="detail-content">{{ block.result }}</pre>
                </div>
              </div>
            </CollapseTransition>
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

    <div v-if="interrupted" class="stream-interrupted">连接中断，本条回答可能不完整</div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onUnmounted, ref, watch } from 'vue'
import type { Component } from 'vue'
import {
  CaretRightOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  OrderedListOutlined,
  SearchOutlined,
  ReadOutlined,
  FilePdfOutlined,
  FileTextOutlined,
  EditOutlined,
  PictureOutlined,
  CodeOutlined,
  DownloadOutlined,
  ToolOutlined
} from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import ZcodeSpinner from '@/components/ZcodeSpinner.vue'
import CollapseTransition from '@/components/CollapseTransition.vue'
import type { MessageBlock } from '@/types'

interface Props {
  content: string
  blocks?: MessageBlock[]
  isLoading?: boolean
  /** 本条回复耗时（毫秒，usage/持久化提供） */
  durationMs?: number
  /** 消息创建时间（进行中据此计算已工作时长） */
  createTime?: Date
  /** 流式传输异常中断（界面提示回答可能不完整） */
  interrupted?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  blocks: () => [],
  isLoading: false,
  createTime: () => new Date()
})

const processExpanded = ref(false)
const expandedTools = ref<Set<number>>(new Set())
const manuallyExpandedThinking = ref<Set<number>>(new Set())

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
  searchWeb: '联网搜索',
  scrapeWebPage: '阅读网页',
  generatePDF: '生成 PDF',
  readFile: '读取文件',
  writeFile: '写入文件',
  searchImage: '搜索图片',
  executeTerminalCommand: '执行命令',
  downloadResource: '下载资源'
}

/** 未登记的工具（如自定义 MCP）：camelCase 拆词作展示名，不暴露原始方法名 */
function toolLabel(tool: string): string {
  if (TOOL_LABELS[tool]) return TOOL_LABELS[tool]
  const spaced = tool.replace(/([a-z0-9])([A-Z])/g, '$1 $2')
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

const TOOL_ICONS: Record<string, Component> = markRaw({
  managePlan: OrderedListOutlined,
  searchWeb: SearchOutlined,
  scrapeWebPage: ReadOutlined,
  generatePDF: FilePdfOutlined,
  readFile: FileTextOutlined,
  writeFile: EditOutlined,
  searchImage: PictureOutlined,
  executeTerminalCommand: CodeOutlined,
  downloadResource: DownloadOutlined
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

interface ResultCard {
  title: string
  snippet: string
  host: string
}

/** 联网搜索结果 → 卡片（title/snippet/url 的 JSON 行；最多展示 3 张） */
function searchResultCards(result?: string): ResultCard[] {
  if (!result) return []
  const pick = (part: string, key: string): string => {
    const m = part.match(new RegExp(`"${key}"\\s*:\\s*"((?:[^"\\\\]|\\\\.)*)"`))
    return m ? m[1].replace(/\\"/g, '"').replace(/\\n/g, ' ') : ''
  }
  const cards: ResultCard[] = []
  for (const part of result.split(/\},\s*\{/)) {
    const title = pick(part, 'title')
    if (!title) continue
    const url = pick(part, 'url') || pick(part, 'link')
    cards.push({ title, snippet: pick(part, 'snippet'), host: hostOf(url) })
    if (totalSearchResults(result) && cards.length >= 3) break
  }
  return cards.slice(0, 3)
}

function totalSearchResults(result: string): number {
  return result.split(/\},\s*\{/).filter(p => /"title"\s*:/.test(p)).length
}

function extraResultCount(block: ToolBlock): number {
  if (block.tool !== 'searchWeb' || !block.result) return 0
  return Math.max(0, totalSearchResults(block.result) - 3)
}

/** 阅读网页 → 卡片（HTML 提取 <title> 与正文摘要） */
function scrapeCard(block: ToolBlock): ResultCard | null {
  const result = block.result
  if (!result || result.startsWith('抓取网页错误')) return null
  const a = parseArgs(block)
  const host = hostOf(a?.url)
  const titleMatch = result.match(/<title[^>]*>([^<]*)<\/title>/i)
  const text = result
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!text) return null
  return {
    title: titleMatch ? titleMatch[1].trim() : host,
    snippet: text.slice(0, 110),
    host
  }
}

const resultCardsCache = new Map<string, ResultCard[]>()
function resultCards(block: ToolBlock): ResultCard[] {
  // 以工具+结果内容为键缓存解析结果（无 id 的兜底块也不会互相串卡）
  const key = `${block.tool}|${block.result?.length ?? 0}|${block.result?.slice(0, 50) ?? ''}`
  const cached = resultCardsCache.get(key)
  if (cached) return cached
  let cards: ResultCard[] = []
  if (block.tool === 'searchWeb') {
    cards = searchResultCards(block.result)
  } else if (block.tool === 'scrapeWebPage') {
    const card = scrapeCard(block)
    cards = card ? [card] : []
  }
  resultCardsCache.set(key, cards)
  return cards
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
        revealMap.value[i] = props.isLoading ? 0 : toolMeta(b).length
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

// ---------- 展开/收起 ----------
const lastThinkingIndex = computed(() => {
  for (let i = props.blocks.length - 1; i >= 0; i--) {
    if (props.blocks[i].type === 'thinking') return i
  }
  return -1
})

function isThinkingExpanded(index: number): boolean {
  if (manuallyExpandedThinking.value.has(index)) return true
  return props.isLoading && index === lastThinkingIndex.value
}

function toggleThinking(index: number): void {
  const next = new Set(manuallyExpandedThinking.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  manuallyExpandedThinking.value = next
}

function toggleTool(index: number): void {
  const next = new Set(expandedTools.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  expandedTools.value = next
}

function prettyJson(args: string): string {
  try {
    return JSON.stringify(JSON.parse(args), null, 2)
  } catch {
    return args
  }
}
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

.block-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  margin: 0 -8px;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  color: #86909c;
  font-size: 13px;
  transition: background 0.2s;

  &:hover {
    background: #f2f3f5;
  }
}

.caret-icon {
  font-size: 11px;
  color: #86909c;
  transition: transform 0.2s;
}

// 思考块
.thinking-block {
  .thinking-text {
    margin: 6px 0 0 20px;
    padding: 10px 14px;
    background: #f7f8fa;
    border-left: 3px solid #e5e6eb;
    border-radius: 0 8px 8px 0;
    font-size: 13px;
    line-height: 1.6;
    color: #6b7280;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 260px;
    overflow-y: auto;
    @include thin-scrollbar;
  }

  .block-icon {
    font-size: 13px;
    color: #d48806;
  }
}

// 工具块：时间线条目 + 友好结果卡片
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
    cursor: pointer;
    transition: background 0.2s;
    min-width: 0;

    &:hover {
      background: #f7f8fa;
    }
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

  .tool-caret {
    flex-shrink: 0;
  }

  // 结果卡片（搜索/阅读网页）：标题加粗 + 摘要灰字截断 + 站点
  .tool-results {
    margin: 4px 0 0 24px;
    display: flex;
    flex-direction: column;
    gap: 6px;

    .result-card {
      padding: 8px 12px;
      background: #f7f8fa;
      border-radius: 10px;
      cursor: pointer;
      transition: background 0.2s;
      user-select: none;

      &:hover {
        background: #f2f3f5;
      }

      .rc-title {
        font-size: 13px;
        font-weight: 600;
        color: #1d2129;
        line-height: 1.5;
        display: -webkit-box;
        -webkit-line-clamp: 1;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .rc-snippet {
        margin-top: 2px;
        font-size: 12px;
        color: #86909c;
        line-height: 1.5;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .rc-host {
        margin-top: 4px;
        font-size: 11px;
        color: #a9aeb8;
        font-family: 'Consolas', 'Monaco', monospace;
      }
    }

    .rc-more {
      font-size: 12px;
      color: #a9aeb8;
      padding-left: 4px;
    }
  }

  .tool-detail {
    margin: 4px 0 0 23px;
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    background: #f7f8fa;
    border-radius: 8px;
    user-select: text;

    .detail-section {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .detail-label {
        font-size: 12px;
        color: #86909c;
      }

      .detail-content {
        margin: 0;
        padding: 8px 10px;
        background: #fff;
        border-radius: 6px;
        font-size: 12px;
        line-height: 1.5;
        color: #4e5969;
        font-family: 'Consolas', 'Monaco', monospace;
        white-space: pre-wrap;
        word-break: break-word;
        max-height: 240px;
        overflow-y: auto;
        @include thin-scrollbar;
      }
    }
  }
}

.answer-content {
  font-size: 14px;
  padding: 0 4px;
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
