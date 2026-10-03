<template>
  <div class="mio-bot-message">
    <!-- 执行过程：任务清单 + 思考 + 工具调用（流式期间实时更新） -->
    <div v-if="hasProcess" class="process-section">
      <!-- 任务清单面板 -->
      <div v-if="plan && plan.length" class="plan-panel">
        <div class="plan-header">
          <OrderedListOutlined class="plan-icon" />
          <span class="plan-title">任务清单</span>
          <span class="plan-progress">{{ doneCount }}/{{ plan.length }}</span>
        </div>
        <div class="plan-steps">
          <div v-for="step in plan" :key="step.index" class="plan-step" :class="step.status">
            <span class="step-mark">
              <LoadingOutlined v-if="step.status === 'in_progress'" spin />
              <CheckOutlined v-else-if="step.status === 'done'" />
              <CloseOutlined v-else-if="step.status === 'failed'" />
              <span v-else class="step-dot"></span>
            </span>
            <span class="step-text">{{ step.description }}</span>
          </div>
        </div>
      </div>

      <!-- 思考过程（可折叠：流式思考时自动展开，出答案后收起） -->
      <div v-if="thinking" class="thinking-block">
        <div class="block-header" @click="thinkingExpanded = !thinkingExpanded">
          <CaretRightOutlined :rotate="thinkingExpanded ? 90 : 0" class="caret-icon" />
          <BulbOutlined class="block-icon" />
          <span class="block-title">思考过程</span>
        </div>
        <div v-show="thinkingExpanded" class="thinking-text">{{ thinking }}</div>
      </div>

      <!-- 工具调用时间线 -->
      <div v-if="tools && tools.length" class="tools-timeline">
        <div v-for="(tool, index) in tools" :key="tool.id ?? index" class="tool-card">
          <div class="tool-row" @click="toggleTool(index)">
            <span class="tool-status">
              <LoadingOutlined v-if="tool.status === 'running'" spin />
              <CheckCircleOutlined v-else class="tool-done" />
            </span>
            <ToolOutlined class="tool-icon" />
            <span class="tool-name">{{ tool.tool }}</span>
            <span v-if="tool.args" class="tool-args-preview">{{ argsPreview(tool.args) }}</span>
            <CaretRightOutlined :rotate="expandedTools.has(index) ? 90 : 0" class="caret-icon tool-caret" />
          </div>
          <div v-if="expandedTools.has(index)" class="tool-detail">
            <div v-if="tool.args" class="detail-section">
              <span class="detail-label">参数</span>
              <pre class="detail-content">{{ prettyJson(tool.args) }}</pre>
            </div>
            <div v-if="tool.result" class="detail-section">
              <span class="detail-label">结果</span>
              <pre class="detail-content">{{ tool.result }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 最终回答 -->
    <MarkdownView v-if="content" class="answer-content" :content="content" />

    <!-- 流式中：尚无任何输出时的等待指示 -->
    <div v-if="isLoading && !hasOutput" class="message-loading">
      <span></span><span></span><span></span>
    </div>

    <div v-if="interrupted" class="stream-interrupted">连接中断，本条回答可能不完整</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  CaretRightOutlined,
  BulbOutlined,
  ToolOutlined,
  CheckOutlined,
  CheckCircleOutlined,
  CloseOutlined,
  LoadingOutlined,
  OrderedListOutlined
} from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import type { ToolEvent } from '@/types'

interface Props {
  content: string
  thinking?: string
  tools?: ToolEvent[]
  plan?: PlanStepLike[] | null
  isLoading?: boolean
  /** 流式传输异常中断（界面提示回答可能不完整） */
  interrupted?: boolean
}

interface PlanStepLike {
  index: number
  description: string
  status: string
}

const props = withDefaults(defineProps<Props>(), {
  thinking: '',
  tools: () => [],
  plan: null,
  isLoading: false
})

const thinkingExpanded = ref(false)
const expandedTools = ref<Set<number>>(new Set())

const hasProcess = computed(() =>
  (props.plan?.length ?? 0) > 0 || !!props.thinking || (props.tools?.length ?? 0) > 0
)

const hasOutput = computed(() => !!props.content || hasProcess.value)

const doneCount = computed(
  () => props.plan?.filter(s => s.status === 'done').length ?? 0
)

// 思考阶段自动展开，出答案后收起；用户仍可手动切换
watch(
  () => props.isLoading && !props.content,
  (thinking) => {
    thinkingExpanded.value = thinking
  }
)

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
  gap: 16px;
}

.process-section {
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

// 任务清单
.plan-panel {
  background: #f7f8fa;
  border: 1px solid #eef0f3;
  border-radius: 10px;
  padding: 12px 16px;

  .plan-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;

    .plan-icon {
      color: $primary-color;
      font-size: 14px;
    }

    .plan-title {
      font-size: 13px;
      font-weight: 600;
      color: #4e5969;
    }

    .plan-progress {
      margin-left: auto;
      font-size: 12px;
      color: #86909c;
      font-variant-numeric: tabular-nums;
    }
  }

  .plan-steps {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .plan-step {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    font-size: 13px;
    line-height: 1.5;

    .step-mark {
      flex-shrink: 0;
      width: 16px;
      height: 16px;
      margin-top: 1px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
    }

    .step-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      border: 1.5px solid #c9cdd4;
    }

    .step-text {
      color: #4e5969;

      // 已完成步骤弱化
      .done & {
        color: #86909c;
        text-decoration: line-through;
        text-decoration-color: rgba(134, 144, 156, 0.5);
      }
    }

    &.in_progress .step-mark {
      color: $primary-color;
    }

    &.done .step-mark {
      color: #00b42a;
    }

    &.failed .step-mark {
      color: #f53f3f;
    }
  }
}

// 思考过程
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

// 工具调用时间线
.tools-timeline {
  display: flex;
  flex-direction: column;
  gap: 8px;

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
      font-size: 13px;
      color: $primary-color;
      width: 16px;
      display: flex;
      justify-content: center;
    }

    .tool-done {
      color: #00b42a;
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
}

.answer-content {
  font-size: 14px;
  padding: 0 4px;
}

.message-loading {
  display: flex;
  gap: 6px;
  padding: 8px 4px;

  span {
    width: 8px;
    height: 8px;
    background: $primary-color;
    border-radius: 50%;
    animation: loading-bounce 1.4s infinite ease-in-out both;

    &:nth-child(1) {
      animation-delay: -0.32s;
    }

    &:nth-child(2) {
      animation-delay: -0.16s;
    }

    &:nth-child(3) {
      animation-delay: 0s;
    }
  }
}

@keyframes loading-bounce {
  0%,
  80%,
  100% {
    transform: scale(0);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.stream-interrupted {
  font-size: 12px;
  color: #d48806;
}
</style>
