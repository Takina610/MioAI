<template>
  <div class="mio-bot-message">
    <!-- 内容块按时间顺序顺着显示（ZCode 风格）：文本 → 工具卡 → 文本 → … -->
    <template v-if="blocks.length">
      <template v-for="(block, index) in blocks" :key="index">
        <!-- 文本块 -->
        <MarkdownView
          v-if="block.type === 'text'"
          class="answer-content"
          :content="block.text"
        />

        <!-- 思考块（流式中且为当前末块时自动展开，被后续内容顶替后收起） -->
        <div v-else-if="block.type === 'thinking'" class="thinking-block">
          <div class="block-header" @click="toggleThinking(index)">
            <CaretRightOutlined :rotate="isThinkingExpanded(index) ? 90 : 0" class="caret-icon" />
            <BulbOutlined class="block-icon" />
            <span class="block-title">思考过程</span>
          </div>
          <div v-show="isThinkingExpanded(index)" class="thinking-text">{{ block.text }}</div>
        </div>

        <!-- 工具块（参数实时增长） -->
        <div v-else class="tool-card">
          <div class="tool-row" @click="toggleTool(index)">
            <span class="tool-status">
              <ZcodeSpinner v-if="block.status === 'running'" :size="14" />
              <CheckCircleOutlined v-else class="tool-done" />
            </span>
            <ToolOutlined class="tool-icon" />
            <span class="tool-name">{{ block.tool }}</span>
            <span v-if="block.args" class="tool-args-preview">{{ argsPreview(block.args) }}</span>
            <CaretRightOutlined :rotate="expandedTools.has(index) ? 90 : 0" class="caret-icon tool-caret" />
          </div>
          <div v-if="expandedTools.has(index)" class="tool-detail">
            <div v-if="block.args" class="detail-section">
              <span class="detail-label">参数</span>
              <pre class="detail-content">{{ prettyJson(block.args) }}</pre>
            </div>
            <div v-if="block.result" class="detail-section">
              <span class="detail-label">结果</span>
              <pre class="detail-content">{{ block.result }}</pre>
            </div>
          </div>
        </div>
      </template>
    </template>

    <!-- 历史消息（无块信息）：直接渲染正文 -->
    <MarkdownView v-else-if="content" class="answer-content" :content="content" />

    <!-- 加载指示：流式期间始终显示在消息尾部（ZCode 同款 spinner） -->
    <div v-if="isLoading" class="tail-spinner">
      <ZcodeSpinner :size="16" />
    </div>

    <div v-if="interrupted" class="stream-interrupted">连接中断，本条回答可能不完整</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  CaretRightOutlined,
  BulbOutlined,
  ToolOutlined,
  CheckCircleOutlined
} from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import ZcodeSpinner from '@/components/ZcodeSpinner.vue'
import type { MessageBlock } from '@/types'

interface Props {
  content: string
  blocks?: MessageBlock[]
  isLoading?: boolean
  /** 流式传输异常中断（界面提示回答可能不完整） */
  interrupted?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  blocks: () => [],
  isLoading: false
})

const expandedTools = ref<Set<number>>(new Set())
const manuallyExpandedThinking = ref<Set<number>>(new Set())

/** 当前块流中的最后一个思考块（流式收尾时自动展开的就是它） */
const lastThinkingIndex = computed(() => {
  for (let i = props.blocks.length - 1; i >= 0; i--) {
    if (props.blocks[i].type === 'thinking') return i
  }
  return -1
})

function isThinkingExpanded(index: number): boolean {
  if (manuallyExpandedThinking.value.has(index)) return true
  // 流式中且尚无后续内容块：自动展开；被文本/工具块顶替后自然收起
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

function argsPreview(args: string): string {
  const oneLine = args.replace(/\s+/g, ' ').trim()
  return oneLine.length > 80 ? oneLine.slice(0, 80) + '…' : oneLine
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

.block-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
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
    margin: 6px 0 0 24px;
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

// 工具块
.tool-card {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 10px;
  overflow: hidden;

  .tool-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 14px;
    cursor: pointer;
    transition: background 0.2s;

    &:hover {
      background: #f7f8fa;
    }
  }

  .tool-status {
    width: 16px;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .tool-done {
    color: #00b42a;
    font-size: 14px;
  }

  .tool-icon {
    font-size: 13px;
    color: #86909c;
  }

  .tool-name {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 500;
    color: #4e5969;
    font-family: 'Consolas', 'Monaco', monospace;
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

  .tool-caret {
    flex-shrink: 0;
  }

  .tool-detail {
    border-top: 1px dashed #eef0f3;
    padding: 10px 14px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    background: #fafbfc;

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
        background: #f2f3f5;
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

// 消息尾部加载指示：流式期间常显（有内容时跟在内容后，无内容时独立成行）
.tail-spinner {
  padding: 2px 4px;
  min-height: 20px;
  display: flex;
  align-items: center;
}

.stream-interrupted {
  font-size: 12px;
  color: #d48806;
}
</style>
