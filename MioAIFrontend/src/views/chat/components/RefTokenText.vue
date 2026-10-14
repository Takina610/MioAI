<template>
  <span class="ref-token-text">
    <template v-for="(part, i) in parts" :key="i">
      <span v-if="part.type === 'text'" class="part-text">{{ part.text }}</span>
      <span v-else class="ref-token" :class="`is-${part.type}`">
        <component :is="iconOf(part.type)" class="token-icon" />
        <span class="token-name">{{ displayLabel(part) }}</span>
      </span>
    </template>
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
import { computed } from 'vue'
import { parseRefParts, type RefPart } from '../refMarkers'

/**
 * 带引用标记的用户消息渲染（zcode ConversationUserInputContent 对位）：
 * 消息内容本质是含标记的文本（复制/编辑/持久化的都是标记文本），展示时解析为
 * 「纯文本 + 类型化 token」混排——不走 markdown，标记不出现在界面上。
 */
const props = defineProps<{
  content: string
}>()

const parts = computed(() => parseRefParts(props.content ?? ''))

function iconOf(type: RefPart['type']) {
  switch (type) {
    case 'session':
      return MessageOutlined
    case 'kb':
      return DatabaseOutlined
    case 'skill':
      return ThunderboltOutlined
    case 'mcp':
      return ApiOutlined
    case 'command':
      return CompressOutlined
  }
}

/** 展示名剥离标记前缀（命令名不带斜杠，与 zcode 一致） */
function displayLabel(part: RefPart): string {
  if (part.type === 'text') return part.text
  if (part.type === 'command') return part.name
  return part.name
}
</script>

<style lang="scss" scoped>
// 纯文本 + token 混排：文本片段保留空白与换行，token 内联在文字流中
.ref-token-text {
  display: inline;
  min-width: 0;
  white-space: pre-wrap;
  word-break: break-word;

  .part-text {
    display: inline;
  }

  .ref-token {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    vertical-align: baseline;
    margin: 0 2px;
    font-size: 14px;
    line-height: 1.6;
    font-weight: 500;
    color: $token-session;

    // 名称是真实文本节点（可选中复制）；图标是装饰不参与选区
    .token-icon {
      font-size: 13px;
      flex-shrink: 0;
      user-select: none;
      pointer-events: none;
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

    &.is-command {
      color: $token-compact;
    }
  }
}
</style>
