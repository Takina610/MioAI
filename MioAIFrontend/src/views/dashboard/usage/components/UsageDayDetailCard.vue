<template>
  <a-card class="day-detail-card">
    <template #title>
      <span class="card-title">{{ dayjs(date).format('M月D日') }} 明细</span>
    </template>
    <template #extra>
      <a-button type="text" size="small" class="close-btn" @click="emit('close')">
        <template #icon><CloseOutlined /></template>
      </a-button>
    </template>
    <UsageDayDetail
      :detail="detail"
      :loading="loading"
      :measure-label="measureLabel"
      :measure-unit="measureUnit"
    />
  </a-card>
</template>

<script setup lang="ts">
import { CloseOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import type { UsageDayDetail as UsageDayDetailData } from '@/types'
import UsageDayDetail from './UsageDayDetail.vue'

defineProps<{
  date: string
  detail: UsageDayDetailData | null
  loading: boolean
  measureLabel: string
  measureUnit: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()
</script>

<style lang="scss" scoped>
.day-detail-card {
  border-radius: 12px;

  :deep(.ant-card-head-title) {
    font-weight: 600;
  }

  .card-title {
    font-variant-numeric: tabular-nums;
  }

  .close-btn {
    color: $text-muted;
  }
}
</style>
