<template>
  <!-- 思考块（zcode GUI 式）：收起=「图标 思考 · 摘要/时长」单行；展开=竖线内容区（与图标对齐），
       上方不再重复摘要。展开态组件内部自持（每块独立折叠） -->
  <div class="thinking-block">
    <div class="thinking-bar" @click="expanded = !expanded">
      <BrainIcon :size="13" class="think-icon" :class="{ active: active }" />
      <span class="thinking-label" :class="{ running: active }">{{ active ? '正在思考' : '思考' }}</span>
      <span v-if="active && !expanded" class="thinking-live">· {{ tail }}</span>
      <span v-else-if="!active" class="thinking-label">· {{ suffix }}</span>
      <CaretRightOutlined :rotate="expanded ? 90 : 0" class="caret-icon" />
    </div>
    <CollapseTransition :open="expanded">
      <div v-if="expanded" class="thinking-text">{{ block.text }}</div>
    </CollapseTransition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CaretRightOutlined } from '@ant-design/icons-vue'
import CollapseTransition from '@/components/CollapseTransition.vue'
import BrainIcon from '@/components/BrainIcon.vue'
import { formatDuration } from '../toolDisplay'

type ThinkingBlockType = { text: string; durationMs?: number }

const props = defineProps<{
  block: ThinkingBlockType
  /** 流式进行中的思考（图标呼吸 + 文案扫光 + 实时摘要） */
  active?: boolean
}>()

const expanded = ref(false)

/** 单行实时摘要：取思考文本的最后一段（最新内容），超长截头保尾 */
const tail = computed(() => {
  const lines = props.block.text.split('\n').map(l => l.trim()).filter(Boolean)
  const last = lines[lines.length - 1] ?? ''
  return last.length > 90 ? '…' + last.slice(-90) : last
})

/** 收起态标签后缀：「持续了 X 秒」；不足 1 秒沿用 zcode 的「持续了几秒」 */
const suffix = computed(() => {
  if (props.block.durationMs == null) return ''
  const seconds = Math.round(props.block.durationMs / 1000)
  if (seconds < 1) return '持续了几秒'
  if (seconds < 60) return `持续了 ${seconds} 秒`
  return `持续了 ${formatDuration(props.block.durationMs)}`
})
</script>

<style lang="scss" scoped>
.caret-icon {
  font-size: 11px;
  color: #8c8a82;
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
    color: #8c8a82;
    font-size: 13px;
    transition: background 0.2s;

    &:hover {
      background: #f0ede4;
    }

    .think-icon {
      flex-shrink: 0;
      color: #8c8a82;

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
        @include animated-gradient-text(#8c8a82, rgba(140,138,130, 0.25));
      }
    }
  }

  // 竖线从内容第一行贯穿到最后一行，x 位置与上方图标中心对齐（图标 13px → 中心 ≈ 6px）
  .thinking-text {
    margin: 4px 0 6px 6px;
    padding: 2px 0 4px 14px;
    border-left: 2px solid #e8e6dc;
    font-size: 13px;
    line-height: 1.65;
    color: #8c8a82;
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
</style>
