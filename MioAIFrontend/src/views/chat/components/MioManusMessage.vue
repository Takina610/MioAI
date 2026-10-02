<template>
  <div class="mio-manus-message">
    <!-- 无 type 信息且只有单段/无段，直接渲染 -->
    <template v-if="!useSegmentRender">
      <MarkdownView class="manus-message-text" :content="content" />
      <div v-if="interrupted" class="stream-interrupted">连接中断，本条回答可能不完整</div>
    </template>

    <!-- 组合渲染：有 type 信息 或 多段 -->
    <div v-else class="manus-segments">
      <!-- 思考过程（可折叠：推理流式时自动展开，出答案后收起） -->
      <div v-if="thinkingSegments.length > 0 || isThinking" class="thinking-section">
        <div class="thinking-header" @click="thinkingExpanded = !thinkingExpanded">
          <CaretRightOutlined :rotate="thinkingExpanded ? 90 : 0" class="thinking-icon" />
          <span class="thinking-title">思考过程 ({{ thinkingSegments.length }} 步)</span>
        </div>
        <div v-show="thinkingExpanded" class="thinking-content">
          <div
            v-for="(segment, index) in thinkingSegments"
            :key="index"
            class="segment-item"
          >
            <div class="segment-step">
              <span class="step-badge" :class="{ 'step-badge-tool': segment.type === 'tool_call' || segment.type === 'tool_result' }">
                <template v-if="segment.type === 'tool_call' || segment.type === 'tool_result'">🔧</template>
                <template v-else>{{ index + 1 }}</template>
              </span>
              <MarkdownView class="segment-text" :content="segmentText(segment)" />
              <span v-if="segment.tool" class="segment-tool">{{ segment.tool }}</span>
            </div>
          </div>
          <!-- 正在思考中 -->
          <div v-if="isThinking" class="thinking-loading">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>

      <!-- 最终结果：合并为一个完整 Markdown 文档渲染，避免跨段结构被割裂 -->
      <div v-if="finalContent" class="final-answer">
        <MarkdownView class="final-content manus-message-text" :content="finalContent" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CaretRightOutlined } from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import type { MessageSegment } from '@/types'

interface Props {
  content: string
  segments?: MessageSegment[]
  isLoading?: boolean
  /** 流式传输异常中断（界面提示回答可能不完整） */
  interrupted?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  segments: () => [],
  isLoading: false
})

const thinkingExpanded = ref(false)

// 判断是否有 type 信息（来自实时 SSE 信封）
const hasTypeInfo = computed(() => {
  return props.segments?.some(s => s.type !== undefined) ?? false
})

// 是否使用分段渲染：有 type 信息时始终使用；无 type 时只有多段才使用
const useSegmentRender = computed(() => {
  if (hasTypeInfo.value) return true
  return props.segments.length > 1
})

// 是否还在思考中（正在加载且还没有最终结果）
const isThinking = computed(() => {
  return props.isLoading && !finalContent.value
})

const thinkingSegments = computed(() => {
  if (props.segments.length === 0) return []

  if (hasTypeInfo.value) {
    // 有 type 信息：除最终回复外都归入思考过程（含工具调用/结果与未知类型的兜底）
    return props.segments.filter(s => s.type !== 'final')
  }

  // 无 type 信息（历史消息）：除最后一段外都是思考过程
  return props.segments.slice(0, -1)
})

const finalContent = computed(() => {
  if (props.segments.length === 0) {
    return props.content
  }

  if (hasTypeInfo.value) {
    // 有 type 信息：优先取 final 段（MioManus 整段协议）；无则正文即为答案（answer 增量协议）
    const finalText = props.segments
      .filter(s => s.type === 'final')
      .map(s => s.content)
      .join('')
    return finalText || props.content
  }

  // 无 type 信息（历史消息）：最后一段作为最终结果
  return props.segments[props.segments.length - 1].content
})

// 思考阶段自动展开，出答案后收起；用户仍可手动切换
watch(isThinking, (thinking) => {
  thinkingExpanded.value = thinking
})

function segmentText(segment: MessageSegment): string {
  if (segment.type === 'tool_call') {
    return segment.args ? '调用工具\n```json\n' + segment.args + '\n```' : '调用工具'
  }
  if (segment.type === 'tool_result') {
    return segment.content || '执行完成'
  }
  return segment.content
}
</script>

<style scoped lang="scss">
.mio-manus-message {
  width: 100%;
}

.stream-interrupted {
  font-size: 12px;
  color: #d48806;
  margin-top: 4px;
}

.manus-segments {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.thinking-section {
  background: #f5f5f5;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  overflow: hidden;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;

  &:hover {
    background: #e8e8e8;
  }
}

.thinking-icon {
  font-size: 12px;
  color: #666;
  transition: transform 0.2s;
}

.thinking-title {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.thinking-content {
  padding: 0 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.thinking-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0 0 32px;

  span {
    display: inline-block;
    width: 6px;
    height: 6px;
    background: #999;
    border-radius: 50%;
    animation: bounce 1.4s infinite ease-in-out both;

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

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.segment-item {
  .segment-step {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  .step-badge {
    flex-shrink: 0;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: #1890ff;
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 2px;

    &.step-badge-tool {
      background: #faad14;
      font-size: 11px;
    }
  }

  .segment-tool {
    flex-shrink: 0;
    align-self: flex-start;
    margin-top: 3px;
    font-size: 12px;
    color: #d48806;
    font-family: 'Consolas', 'Monaco', monospace;
  }

  .segment-text {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    line-height: 1.6;
    color: #444;
  }
}

.final-answer {
  width: 100%;
  padding: 8px 4px;
}

.final-content,
.manus-message-text {
  font-size: 14px;
}
</style>
