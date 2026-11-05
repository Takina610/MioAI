<template>
  <span class="ref-chips">
    <span
      v-for="(item, i) in items"
      :key="`${item.type}_${item.id ?? item.kbId ?? item.name ?? i}`"
      class="ref-token"
      :class="`is-${item.type}`"
    >
      <component :is="iconOf(item.type)" class="token-icon" />
      <span class="token-name">{{ item.type === 'compact' ? 'Compact' : item.name || '未命名' }}</span>
      <span v-if="item.type === 'session' && item.agentName" class="token-suffix">{{ item.agentName }}</span>
    </span>
  </span>
</template>

<script setup lang="ts">
import {
  ApiOutlined,
  CompressOutlined,
  DatabaseOutlined,
  MessageOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import type { ChatRefItem } from '@/types'

defineProps<{
  items: ChatRefItem[]
}>()

function iconOf(type: ChatRefItem['type']) {
  switch (type) {
    case 'session':
      return MessageOutlined
    case 'kb':
      return DatabaseOutlined
    case 'skill':
      return ThunderboltOutlined
    case 'mcp':
      return ApiOutlined
    case 'compact':
      return CompressOutlined
  }
}
</script>

<style lang="scss" scoped>
// zcode 式引用 token：内联彩字（图标+名称），无底色边框，视觉上是输入内容/消息内容的一段。
// 每类引用一种颜色，压缩指令是命令而非材料，独立灰调
.ref-chips {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 12px;
  min-width: 0;
  vertical-align: baseline;
}

.ref-token {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
  line-height: 1.6;
  color: $token-session;

  .token-icon {
    font-size: 13px;
    flex-shrink: 0;
    user-select: none;
  }

  .token-name {
    font-weight: 500;
  }

  .token-suffix {
    flex-shrink: 0;
    font-size: 11px;
    opacity: 0.65;
  }

  &.is-kb {
    color: $token-kb;
  }

  &.is-skill {
    color: $token-skill;
  }

  &.is-mcp {
    color: $token-mcp;
  }

  &.is-compact {
    color: $token-compact;
  }
}
</style>
