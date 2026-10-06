<template>
  <div class="attachment-cards" :class="{ 'as-card-wrap': variant === 'card' }">
    <AttachmentCard
      v-for="item in items"
      :key="item.key"
      :ref="refSetter ? refSetter(item.key) : undefined"
      :item="item"
      :variant="variant"
      :downloadable="downloadable"
      :large-image="allImages"
      compact
      :removable="removable"
      @remove="$emit('remove', $event)"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 消息区附件列表：输入卡同款单卡组件的薄封装（样式单一来源，杜绝两份 CSS 漂移）。
 * 用户消息附件用 card 变体（与输入框同尺寸），Agent 产出保持行式可下载。
 * 纯图片时图片卡放大展示（largeImage 下传，对齐 DeepSeek 单图大预览）。
 */
import { computed } from 'vue'
import AttachmentCard from './AttachmentCard.vue'
import { isImageName } from '../attachmentUtils'
import type { AttachmentDisplay } from '@/types'

const props = defineProps<{
  items: AttachmentDisplay[]
  /** card=与输入框同款大卡 / chip=行式紧凑 */
  variant?: 'card' | 'chip'
  /** 可下载（Agent 产出）：整条点击即下载 */
  downloadable?: boolean
  /** 可移除（编辑态附件）：X 触发 remove 事件 */
  removable?: boolean
  /** v-for 动态 ref 登记器（编辑态动画需要元素引用） */
  refSetter?: (key: string) => (el: unknown) => void
}>()

defineEmits<{
  (e: 'remove', key: string): void
}>()

/** 纯图片列表：图片卡放大 */
const allImages = computed(
  () => props.items.length > 0 && props.items.every(item => isImageName(item.name)),
)
</script>

<style lang="scss" scoped>
.attachment-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  // 卡片变体的间距由卡片自身 margin（12px）提供，避免与 flex gap 叠加成 24px
  &.as-card-wrap {
    gap: 0;
  }
}
</style>
