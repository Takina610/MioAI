<template>
  <div class="ui-block">
    <div v-if="block.title" class="ui-block-title">{{ block.title }}</div>
    <component :is="resolved" v-if="resolved" :block="block" :chat-id="chatId" />
    <!-- 协议降级：未知 kind 渲染为可折叠原始数据，不整条崩掉 -->
    <details v-else class="ui-block-fallback">
      <summary>未支持的内容块（{{ block.kind }}）</summary>
      <pre>{{ fallbackJson }}</pre>
    </details>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import type { MessageBlock } from '@/types'
import UiCards from './UiCards.vue'
import UiChart from './UiChart.vue'
import UiTable from './UiTable.vue'
import UiTasks from './UiTasks.vue'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

/** kind → 组件白名单：协议允许的类型之外一律走降级渲染 */
const REGISTRY: Record<string, Component> = {
  cards: UiCards,
  chart: UiChart,
  table: UiTable,
  tasks: UiTasks
}

const resolved = computed<Component | null>(() => REGISTRY[props.block.kind] ?? null)

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
