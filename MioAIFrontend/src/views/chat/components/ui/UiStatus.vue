<template>
  <div class="ui-status">
    <div v-for="(step, i) in steps" :key="i" class="ui-status-step" :class="step.status">
      <span class="ui-status-dot">
        <CheckOutlined v-if="step.status === 'done'" />
        <CloseOutlined v-else-if="step.status === 'failed'" />
      </span>
      <span class="ui-status-text">{{ step.text }}</span>
      <span v-if="step.status === 'doing'" class="ui-status-live">进行中</span>
      <span v-else-if="step.status === 'failed'" class="ui-status-live failed">未通过</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CheckOutlined, CloseOutlined } from '@ant-design/icons-vue'
import type { MessageBlock, UiStatusStep } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const steps = computed<UiStatusStep[]>(() =>
  (props.block.steps ?? []).filter(s => s && s.text)
)
</script>

<style scoped lang="scss">
.ui-status {
  display: flex;
  flex-direction: column;
}

.ui-status-step {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0 6px 2px;

  /* 步骤连接线（首尾不伸出） */
  &::before {
    content: '';
    position: absolute;
    left: 8px;
    top: -8px;
    bottom: -2px;
    width: 1px;
    background: $border-warm-subtle;
  }

  &:first-child::before { top: 14px; }
  &:last-child::before { bottom: auto; height: 14px; }
}

.ui-status-dot {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 17px;
  height: 17px;
  flex-shrink: 0;
  background: $bg-ivory;
  border: 1.5px solid $border-warm;
  border-radius: 50%;
  font-size: 10px;
}

.ui-status.done .ui-status-dot {
  background: $primary-color;
  border-color: $primary-color;
  color: #fff;
}

.ui-status.doing .ui-status-dot {
  border-color: $primary-color;
  animation: ui-status-pulse 1.6s ease-in-out infinite;
}

.ui-status.failed .ui-status-dot {
  border-color: $text-muted;
  color: $text-muted;
}

.ui-status-text {
  flex: 1;
  font-size: 13px;
  line-height: 1.6;
  color: $text-secondary;
}

.ui-status.done .ui-status-text {
  color: $text-muted;
}

.ui-status-live {
  font-size: 12px;
  color: $primary-color;
}

.ui-status-live.failed {
  color: $text-muted;
}

@keyframes ui-status-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(42, 161, 169, 0.35); }
  50% { box-shadow: 0 0 0 5px rgba(42, 161, 169, 0); }
}
</style>
