<template>
  <div class="mio-manus-message">
    <!-- 无 type 信息且只有单段/无段，直接渲染 -->
    <div v-if="!useSegmentRender" class="manus-message-text" v-html="formatContent(content)"></div>

    <!-- 组合渲染：有 type 信息 或 多段 -->
    <div v-else class="manus-segments">
      <!-- 思考过程（可折叠） -->
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
              <span class="step-badge">{{ index + 1 }}</span>
              <div class="segment-text" v-html="formatContent(segment.content)"></div>
            </div>
          </div>
          <!-- 正在思考中 -->
          <div v-if="isThinking" class="thinking-loading">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>

      <!-- 最终结果 -->
      <div v-if="finalSegments.length > 0" class="final-answer">
        <div
          v-for="(segment, index) in finalSegments"
          :key="index"
          class="final-content manus-message-text"
          v-html="formatContent(segment.content)"
        ></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { safeMarkdown } from '@/utils/security'
import { CaretRightOutlined } from '@ant-design/icons-vue'

interface MessageSegment {
  content: string
  type?: 'thinking' | 'action' | 'final'
}

interface Props {
  content: string
  segments?: MessageSegment[]
  isLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  segments: () => [],
  isLoading: false
})

const thinkingExpanded = ref(false)

// 判断是否有 type 信息（来自实时 SSE）
const hasTypeInfo = computed(() => {
  return props.segments?.some(s => s.type !== undefined) ?? false
})

// 是否使用分段渲染：有 type 信息时始终使用；无 type 时只有多段才使用
const useSegmentRender = computed(() => {
  if (hasTypeInfo.value) return true
  return props.segments && props.segments.length > 1
})

// 是否还在思考中（正在加载且没有最终结果）
const isThinking = computed(() => {
  return props.isLoading && finalSegments.value.length === 0
})

const thinkingSegments = computed(() => {
  if (!props.segments || props.segments.length === 0) return []

  if (hasTypeInfo.value) {
    // 有 type 信息：thinking 和 action 都归入思考过程
    return props.segments.filter(s => s.type === 'thinking' || s.type === 'action')
  }

  // 无 type 信息（历史消息）：除最后一段外都是思考过程
  return props.segments.slice(0, -1)
})

const finalSegments = computed(() => {
  if (!props.segments || props.segments.length === 0) {
    return [{ content: props.content }]
  }

  if (hasTypeInfo.value) {
    // 有 type 信息：只取 final 类型
    return props.segments.filter(s => s.type === 'final')
  }

  // 无 type 信息（历史消息）：最后一段作为最终结果
  return [props.segments[props.segments.length - 1]]
})

function formatContent(content: string): string {
  return safeMarkdown(content)
}
</script>

<style scoped lang="scss">
.mio-manus-message {
  width: 100%;
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
  }

  .segment-text {
    flex: 1;
    font-size: 13px;
    line-height: 1.6;
    color: #444;

    :deep(p) {
      margin: 0 0 8px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    :deep(pre) {
      background: #f0f0f0;
      padding: 10px;
      border-radius: 6px;
      overflow-x: auto;
      font-size: 12px;
    }

    :deep(code) {
      background: #f0f0f0;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 12px;
    }
  }
}

.final-answer {
  width: 100%;
  padding: 8px 4px;
}

.final-content {
  :deep(p) {
    margin: 0 0 10px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(pre) {
    background: #f6f8fa;
    padding: 12px;
    border-radius: 6px;
    overflow-x: auto;
  }

  :deep(code) {
    background: #f0f0f0;
    padding: 2px 6px;
    border-radius: 4px;
  }
}

.manus-message-text {
  :deep(p) {
    margin: 0 0 10px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(pre) {
    background: #f6f8fa;
    padding: 12px;
    border-radius: 6px;
    overflow-x: auto;
  }

  :deep(code) {
    background: #f0f0f0;
    padding: 2px 6px;
    border-radius: 4px;
  }
}
</style>
