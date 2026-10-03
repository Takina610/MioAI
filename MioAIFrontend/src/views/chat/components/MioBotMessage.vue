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

          <!-- 工具块 -->
          <div v-else class="tool-block">
            <div class="tool-row" @click="toggleTool(index)">
              <span class="tool-status">
                <ZcodeSpinner v-if="block.status === 'running'" :size="14" />
                <span v-else class="tool-dot"></span>
              </span>
              <span class="tool-name">{{ toolLabel(block.tool) }}</span>
              <span v-if="argsPreview(block.args)" class="tool-args-preview">({{ argsPreview(block.args) }})</span>
              <span v-if="block.status === 'done'" class="tool-done-hint">· 已完成</span>
              <CaretRightOutlined :rotate="expandedTools.has(index) ? 90 : 0" class="caret-icon tool-caret" />
            </div>
            <CollapseTransition :open="expandedTools.has(index)">
              <div class="tool-detail">
                <div v-if="block.args" class="detail-section">
                  <span class="detail-label">参数</span>
                  <pre class="detail-content">{{ prettyJson(block.args) }}</pre>
                </div>
                <div v-if="block.result" class="detail-section">
                  <span class="detail-label">结果</span>
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
import { computed, onUnmounted, ref, watch } from 'vue'
import {
  CaretRightOutlined,
  BulbOutlined,
  CheckCircleOutlined
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

// ---------- 工具行的语义化名称 ----------
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

function argsPreview(args: string | undefined): string {
  if (!args) return ''
  const oneLine = args.replace(/\s+/g, ' ').trim()
  return oneLine.length > 60 ? oneLine.slice(0, 60) + '…' : oneLine
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
  }

  .block-icon {
    font-size: 13px;
    color: #d48806;
  }
}

// 工具块：单行时间线条目（无卡片边框），点开可见参数与结果
.tool-block {
  user-select: none;

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
    width: 15px;
    flex-shrink: 0;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .tool-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: $primary-color;
  }

  .tool-name {
    flex-shrink: 0;
    font-size: 13px;
    color: $primary-color;
  }

  .tool-args-preview {
    flex: 1;
    min-width: 0;
    font-size: 12px;
    color: #a9aeb8;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    font-family: 'Consolas', 'Monaco', monospace;
  }

  .tool-done-hint {
    flex-shrink: 0;
    font-size: 12px;
    color: #c9cdd4;
  }

  .tool-caret {
    flex-shrink: 0;
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
</style>
