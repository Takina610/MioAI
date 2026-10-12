<template>
  <div class="skill-block">
    <div class="skill-row skill-row-clickable" @click="expanded = !expanded">
      <span class="skill-status">
        <SparklesIcon :size="13" class="skill-icon" />
      </span>
      <span class="skill-verb">技能</span>
      <span class="skill-name">{{ block.name }}</span>
      <CaretRightOutlined :rotate="expanded ? 90 : 0" class="caret-icon" />
    </div>
    <CollapseTransition :open="expanded">
      <pre class="skill-content"><code>&lt;skill_content name=&quot;{{ block.name }}&quot;&gt;
{{ block.content }}
&lt;/skill_content&gt;</code></pre>
    </CollapseTransition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CaretRightOutlined } from '@ant-design/icons-vue'
import CollapseTransition from '@/components/CollapseTransition.vue'
import SparklesIcon from '@/components/SparklesIcon.vue'
import type { MessageBlock } from '@/types'

// 技能使用行（zcode 式）：一行「技能 <名>」，展开看 SKILL.md 内容（模型按需读取技能文件时生成）
defineProps<{
  block: Extract<MessageBlock, { type: 'skill_use' }>
}>()

const expanded = ref(false)
</script>

<style lang="scss" scoped>
.skill-block {
  .skill-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 3px 8px;
    margin: 0 -8px;
    border-radius: 6px;
    min-width: 0;
    user-select: none;

    &.skill-row-clickable {
      cursor: pointer;

      &:hover {
        background: #f0ede4;
      }
    }

    .caret-icon {
      flex-shrink: 0;
      font-size: 10px;
      color: #8c8a82;
      transition: transform 0.2s;
    }
  }

  .skill-status {
    width: 16px;
    flex-shrink: 0;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .skill-icon {
    color: $primary-color;
  }

  .skill-verb {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    color: #5f5d55;
  }

  .skill-name {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: $primary-color;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  // 展开的 SKILL.md 内容：与其他消息展开区同一套浅色样式，含 <skill_content> 包裹
  .skill-content {
    margin: 4px 0 2px 0;
    padding: 8px 12px;
    background: #f5f3ec;
    border-radius: 8px;
    font-size: 12px;
    line-height: 1.55;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    color: #5f5d55;
    white-space: pre-wrap;
    word-break: break-word;
    user-select: text;
    max-height: 320px;
    overflow-y: auto;
    @include thin-scrollbar;
  }
}
</style>
