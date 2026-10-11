<template>
  <a-popover v-model:open="open" trigger="click" placement="topRight" :mouse-enter-delay="0.2">
    <button type="button" class="ctx-ring-btn" title="上下文容量">
      <svg width="18" height="18" viewBox="0 0 20 20" aria-hidden="true">
        <circle cx="10" cy="10" r="8" fill="none" stroke="#e2dfd6" stroke-width="2.5" />
        <circle
          cx="10"
          cy="10"
          r="8"
          fill="none"
          :stroke="ringColor"
          stroke-width="2.5"
          stroke-linecap="round"
          :stroke-dasharray="`${CIRCUMFERENCE * clampedRatio} ${CIRCUMFERENCE}`"
          transform="rotate(-90 10 10)"
        />
      </svg>
    </button>
    <template #content>
      <div class="ctx-panel">
        <div class="ctx-panel-header">
          <span class="ctx-panel-title">上下文容量</span>
          <span class="ctx-panel-ratio" :class="level">{{ ratioLabel }}</span>
        </div>
        <div class="ctx-panel-bar">
          <div class="ctx-panel-bar-fill" :class="level" :style="{ width: barPercent + '%' }" />
        </div>
        <div class="ctx-panel-rows">
          <div v-for="row in rows" :key="row.key" class="ctx-panel-row">
            <span class="ctx-dot" :style="{ background: row.color }" />
            <span class="ctx-row-label">{{ row.label }}</span>
            <span class="ctx-row-value">{{ row.percent }}</span>
          </div>
        </div>
      </div>
    </template>
  </a-popover>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ContextUsageData } from '@/types'

const props = defineProps<{
  /** 会话上下文容量估算（无会话/拉取失败为 null，指示器随之隐藏） */
  data: ContextUsageData | null
}>()

const open = ref(false)

/** 环形进度周长（r=8） */
const CIRCUMFERENCE = 2 * Math.PI * 8

const CATEGORY_META: Array<{ key: keyof ContextUsageData['categories']; label: string }> = [
  { key: 'messages', label: '消息' },
  { key: 'systemTools', label: '系统工具' },
  { key: 'mcpTools', label: 'MCP 工具' },
  { key: 'systemPrompt', label: '系统提示词' },
  { key: 'skills', label: '技能' },
  { key: 'other', label: '其他' }
]

const ratio = computed(() =>
  props.data && props.data.windowTokens > 0 ? props.data.usedTokens / props.data.windowTokens : 0
)

const clampedRatio = computed(() => Math.max(0, Math.min(1, ratio.value)))

/** 压力分级：≥90% 危险、≥70% 预警、其余常态 */
const level = computed(() => (ratio.value >= 0.9 ? 'danger' : ratio.value >= 0.7 ? 'warn' : 'normal'))

const ringColor = computed(
  () => ({ normal: '#8c8a82', warn: '#d97706', danger: '#dc2626' })[level.value]
)

const barPercent = computed(() => Math.round(clampedRatio.value * 1000) / 10)

const rows = computed(() => {
  const data = props.data
  if (!data) return []
  const values = CATEGORY_META.map(meta => ({ ...meta, tokens: data.categories[meta.key] ?? 0 }))
  const max = Math.max(...values.map(v => v.tokens), 1)
  return values.map(v => ({
    key: v.key,
    label: v.label,
    percent: v.tokens === 0 ? '0%' : `${((v.tokens / data.windowTokens) * 100).toFixed(1)}%`,
    // 点色随占窗口比例渐隐：占比越大越实，零占用灰显
    color:
      v.tokens === 0
        ? '#d8d5cc'
        : `rgba(42, 161, 169, ${Math.min(1, 0.35 + 0.65 * (v.tokens / max)).toFixed(2)})`
  }))
})

const ratioLabel = computed(() => {
  if (!props.data) return ''
  return `${fmtTokens(props.data.usedTokens)}/${fmtTokens(props.data.windowTokens)} (${percentLabel.value})`
})

const percentLabel = computed(() => `${(ratio.value * 100).toFixed(1)}%`)

/** token 万/亿缩写（与上下文容量面板口径一致） */
function fmtTokens(n: number): string {
  const strip = (s: string) => s.replace(/\.0$/, '')
  if (n >= 1e8) return `${strip((n / 1e8).toFixed(1))}亿`
  if (n >= 1e4) return `${strip((n / 1e4).toFixed(1))}万`
  return String(Math.round(n))
}
</script>

<style lang="scss" scoped>
.ctx-ring-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #f0ede4;
  }
}

.ctx-panel {
  width: 320px;
  user-select: none;

  .ctx-panel-header {
    display: flex;
    align-items: baseline;
    justify-content: space-between;

    .ctx-panel-title {
      font-size: 14px;
      font-weight: 600;
      color: #141413;
    }

    .ctx-panel-ratio {
      font-size: 12px;
      color: #6e6b62;
      font-variant-numeric: tabular-nums;

      &.warn {
        color: #d97706;
      }

      &.danger {
        color: #dc2626;
      }
    }
  }

  .ctx-panel-bar {
    height: 6px;
    margin: 10px 0 12px;
    border-radius: 3px;
    background: #eeeadf;
    overflow: hidden;

    .ctx-panel-bar-fill {
      height: 100%;
      border-radius: 3px;
      transition: width 0.3s, background 0.3s;

      &.normal {
        background: $primary-color;
      }

      &.warn {
        background: #d97706;
      }

      &.danger {
        background: #dc2626;
      }
    }
  }

  .ctx-panel-rows {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .ctx-panel-row {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12.5px;

    .ctx-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .ctx-row-label {
      color: #4a4842;
    }

    .ctx-row-value {
      margin-left: auto;
      color: #141413;
      font-weight: 500;
      font-variant-numeric: tabular-nums;
    }
  }
}
</style>
