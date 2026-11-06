<template>
  <div class="ui-block">
    <div v-if="block.title" class="ui-block-title">{{ block.title }}</div>
    <component
      :is="resolved"
      v-if="resolved && !renderError"
      :block="block"
      :chat-id="chatId"
      @send="emit('send', $event)"
      @navigate="emit('navigate', $event)"
    />
    <!-- 协议降级：未知 kind 或组件渲染异常 → 可折叠原始数据，不整条崩掉 -->
    <details v-else class="ui-block-fallback">
      <summary>{{ renderError ? '内容块渲染异常' : `未支持的内容块（${block.kind}）` }}</summary>
      <pre>{{ fallbackJson }}</pre>
    </details>
  </div>
</template>

<script setup lang="ts">
import { computed, onErrorCaptured, ref, type Component } from 'vue'
import type { MessageBlock } from '@/types'
import UiCards from './container/UiCards.vue'
import UiChart from './data/UiChart.vue'
import UiTable from './data/UiTable.vue'
import UiTasks from './data/UiTasks.vue'
import UiForm from './interactive/UiForm.vue'
import UiActions from './interactive/UiActions.vue'
import UiStatus from './data/UiStatus.vue'
import UiTimeline from './data/UiTimeline.vue'
import UiQuote from './container/UiQuote.vue'
import UiMetric from './data/UiMetric.vue'
import UiChoice from './interactive/UiChoice.vue'
import UiAccordion from './container/UiAccordion.vue'
import UiMath from './text/UiMath.vue'
import UiImage from './media/UiImage.vue'
import UiImageList from './media/UiImageList.vue'
import UiImageGallery from './media/UiImageGallery.vue'
import UiFigure from './media/UiFigure.vue'
import UiAgentCard from './container/UiAgentCard.vue'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()
const emit = defineEmits<{
  (e: 'send', text: string): void
  /** 站内跳转（agent_card 的按钮）：路径由后端生成，前端只负责导航 */
  (e: 'navigate', path: string): void
}>()

/** kind → 组件白名单：协议允许的类型之外一律走降级渲染 */
const REGISTRY: Record<string, Component> = {
  cards: UiCards,
  chart: UiChart,
  table: UiTable,
  tasks: UiTasks,
  form: UiForm,
  actions: UiActions,
  status: UiStatus,
  timeline: UiTimeline,
  quote: UiQuote,
  metric: UiMetric,
  choice: UiChoice,
  accordion: UiAccordion,
  math: UiMath,
  image: UiImage,
  image_list: UiImageList,
  image_gallery: UiImageGallery,
  figure: UiFigure,
  agent_card: UiAgentCard
}

const resolved = computed<Component | null>(() => REGISTRY[props.block.kind] ?? null)

/** 子组件渲染异常就地降级（阻断向上冒泡避免整条消息消失） */
const renderError = ref(false)
onErrorCaptured(() => {
  renderError.value = true
  return false
})

const fallbackJson = computed(() => {
  try {
    return JSON.stringify(props.block, null, 2)
  } catch {
    return String(props.block)
  }
})
</script>

<style scoped lang="scss">
/* 动态 UI 块统一外壳：所有 kind 共用同一容器质感（ivory 底 + 暖色细边） */
.ui-block {
  margin: 10px 0;
  padding: 14px 16px;
  background: $bg-ivory;
  border: 1px solid $border-warm-subtle;
  border-radius: 10px;
}

.ui-block-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: $text-secondary;
}

.ui-block-fallback {
  font-size: 13px;
  color: $text-muted;

  summary {
    cursor: pointer;
    user-select: none;
  }

  pre {
    margin: 8px 0 0;
    padding: 10px;
    background: $bg-cream;
    border-radius: 6px;
    font-size: 12px;
    line-height: 1.5;
    overflow: auto;
  }
}
</style>
