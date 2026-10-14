<template>
  <div class="summary-chips" v-if="summary">
    <div class="chip">
      <div class="chip-label">累计 Token 数</div>
      <div class="chip-value">{{ formatCompact(summary.totalTokens) }}</div>
    </div>
    <div class="chip">
      <div class="chip-label">峰值 Token 数</div>
      <div class="chip-value" :title="peakTitle">{{ formatCompact(summary.peakTokens) }}</div>
    </div>
    <div class="chip">
      <div class="chip-label">最长聊天时长</div>
      <div class="chip-value">{{ formatDuration(summary.longestChatMs) }}</div>
    </div>
    <div class="chip">
      <div class="chip-label">当前连续天数</div>
      <div class="chip-value">{{ summary.currentStreak }}<span class="unit">天</span></div>
    </div>
    <div class="chip">
      <div class="chip-label">最长连续天数</div>
      <div class="chip-value">{{ summary.longestStreak }}<span class="unit">天</span></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ContributionSummary } from '@/types'
import { formatCompact, formatDuration } from '../composables/format'

const props = defineProps<{
  summary: ContributionSummary | null
}>()

const peakTitle = computed(() =>
  props.summary?.peakDate ? `峰值出现在 ${props.summary.peakDate}` : undefined
)
</script>

<style lang="scss" scoped>
.summary-chips {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 16px;

  .chip {
    background: #f5f3ec;
    border-radius: 10px;
    padding: 12px 14px;

    .chip-label {
      font-size: 12px;
      color: #6e6b62;
      margin-bottom: 4px;
    }

    .chip-value {
      font-size: 20px;
      font-weight: 600;
      color: #141413;
      font-variant-numeric: tabular-nums;
      line-height: 1.2;

      .unit {
        font-size: 12px;
        font-weight: 400;
        color: #8c8a82;
        margin-left: 2px;
      }
    }
  }
}

@media (max-width: 1200px) {
  .summary-chips {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
