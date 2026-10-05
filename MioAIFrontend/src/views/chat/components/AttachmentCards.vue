<template>
  <div class="attachment-cards">
    <AttachmentCard
      v-for="item in items"
      :key="item.key"
      :item="item"
      :variant="variant"
      :downloadable="downloadable"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 消息区附件列表：输入卡同款单卡组件的薄封装（样式单一来源，杜绝两份 CSS 漂移）。
 * 用户消息附件用 card 变体（与输入框同尺寸），Agent 产出保持 chip 行式可下载。
 */
import AttachmentCard from './AttachmentCard.vue'
import type { AttachmentDisplay } from '@/types'

defineProps<{
  items: AttachmentDisplay[]
  /** card=与输入框同款大卡 / chip=行式紧凑 */
  variant?: 'card' | 'chip'
  /** 可下载（Agent 产出）：整条点击即下载 */
  downloadable?: boolean
}>()
</script>

<style lang="scss" scoped>
.attachment-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

}
</style>
