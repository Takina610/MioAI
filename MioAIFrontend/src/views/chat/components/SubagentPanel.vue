<template>
  <aside class="subagent-panel">
    <header class="panel-header">
      <span class="panel-badge">子智能体</span>
      <span class="panel-title" :title="transcript.description">{{ transcript.description || transcript.agentType }}</span>
      <button class="panel-close" title="关闭" @click="emit('close')">
        <CloseOutlined />
      </button>
    </header>

    <div ref="bodyRef" class="panel-body">
      <!-- 派发任务（只读展示，与主消息流的用户气泡同风格） -->
      <section v-if="transcript.prompt || !transcript.promptPartial" class="panel-prompt">
        <div class="prompt-label">任务 · {{ transcript.agentType }}</div>
        <div class="prompt-bubble">{{ transcript.prompt }}</div>
      </section>

      <!-- 子代理工作过程（镜像工具行，时间序） -->
      <section v-if="transcript.entries.length" class="panel-process">
        <ToolRow
          v-for="(entry, i) in transcript.entries"
          :key="entry.id ?? i"
          :block="entry"
          :streaming="transcript.running"
          panel-mode
        />
      </section>

      <!-- 最终报告 -->
      <MarkdownView v-if="transcript.finalText" class="panel-final" :content="transcript.finalText" />

      <!-- 流式尾部动画 / 收尾信息 -->
      <div v-if="transcript.running" class="panel-tail">
        <ZcodeSpinner :size="14" />
      </div>
      <div v-else-if="usageText" class="panel-usage">{{ usageText }}</div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { CloseOutlined } from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import ZcodeSpinner from '@/components/ZcodeSpinner.vue'
import ToolRow from './ToolRow.vue'
import { formatDuration } from '../toolDisplay'
import type { SubagentTranscript } from '../subagentTranscript'

/**
 * 子代理只读对话面板（zcode 右栏对位）：
 * 展示派发任务、过程工具行与最终报告，仅浏览——无输入、不可编辑。
 * 内容由父级从消息块流实时重建，流式期间自动贴底跟随。
 */
const props = defineProps<{
  transcript: SubagentTranscript
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const bodyRef = ref<HTMLElement | null>(null)

const usageText = computed(() => {
  if (props.transcript.background) return '后台任务 · 结果由主任务取回'
  const usage = props.transcript.usage
  if (!usage) return ''
  const parts: string[] = []
  if (usage.tokens) parts.push(`${usage.tokens} tokens`)
  if (usage.toolUses) parts.push(`工具 ${usage.toolUses} 次`)
  if (usage.durationMs) parts.push(`用时 ${formatDuration(usage.durationMs)}`)
  return parts.join(' · ')
})

function isNearBottom(): boolean {
  const el = bodyRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 120
}

function scrollToBottom(): void {
  const el = bodyRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

/** 流式期间的贴底跟随：用户滚上去阅读时不打扰（isNearBottom 实时判断） */
watch(
  () => [props.transcript.entries.length, props.transcript.finalText.length, props.transcript.prompt.length],
  async () => {
    if (!props.transcript.running || !isNearBottom()) return
    await nextTick()
    scrollToBottom()
  }
)

onMounted(() => {
  // 进行中定位到最新内容，已完成的从头阅读
  if (props.transcript.running) {
    nextTick(scrollToBottom)
  }
})
</script>

<style lang="scss" scoped>
.subagent-panel {
  // 定宽：外壳（.subagent-drawer）动画宽度时本组件只被裁切不回流，内容不闪变
  width: clamp(360px, 36vw, 560px);
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $bg-ivory;
  overflow: hidden;
}

.panel-header {
  height: 64px;
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px 0 16px;
  border-bottom: 1px solid $border-warm-subtle;

  .panel-badge {
    flex-shrink: 0;
    padding: 1px 8px;
    border-radius: 999px;
    background: rgba(42, 161, 169, 0.1);
    color: $primary-color;
    font-size: 11px;
    line-height: 18px;
    white-space: nowrap;
  }

  .panel-title {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    font-weight: 600;
    color: #141413;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .panel-close {
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: #8c8a82;
    font-size: 13px;
    cursor: pointer;
    transition: background 0.2s, color 0.2s;

    &:hover {
      background: #f0ede4;
      color: #141413;
    }
  }
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 18px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  @include thin-scrollbar;
}

.panel-prompt {
  .prompt-label {
    margin-bottom: 6px;
    font-size: 12px;
    color: #8c8a82;
    user-select: none;
  }

  .prompt-bubble {
    padding: 12px 16px;
    background: #ece9de;
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.6;
    color: #141413;
    white-space: pre-wrap;
    word-break: break-word;
    user-select: text;
  }
}

.panel-process {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel-final {
  font-size: 14px;
  padding: 0 2px;
}

.panel-tail {
  display: flex;
  align-items: center;
  padding: 0 2px;
  user-select: none;
}

.panel-usage {
  font-size: 12px;
  color: #8c8a82;
  user-select: none;
}
</style>
