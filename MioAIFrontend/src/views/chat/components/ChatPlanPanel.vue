<template>
  <Transition name="plan-slide">
    <div v-if="plan && plan.length" class="chat-plan-panel">
      <div class="plan-header">
        <OrderedListOutlined class="plan-icon" />
        <span class="plan-title">任务清单</span>
        <span class="plan-progress">{{ doneCount }}/{{ plan.length }}</span>
      </div>
      <div class="plan-steps">
        <div v-for="step in plan" :key="step.index" class="plan-step" :class="step.status">
          <span class="step-mark">
            <ZcodeSpinner v-if="step.status === 'in_progress'" :size="13" />
            <CheckOutlined v-else-if="step.status === 'done'" />
            <CloseOutlined v-else-if="step.status === 'failed'" />
            <span v-else class="step-dot"></span>
          </span>
          <span class="step-text">{{ step.description }}</span>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  OrderedListOutlined,
  CheckOutlined,
  CloseOutlined
} from '@ant-design/icons-vue'
import ZcodeSpinner from '@/components/ZcodeSpinner.vue'
import type { PlanStep } from '@/types'

const props = defineProps<{
  /** 最近一次任务清单快照 */
  plan?: PlanStep[] | null
}>()

const doneCount = computed(() => props.plan?.filter(s => s.status === 'done').length ?? 0)
</script>

<style lang="scss" scoped>
.chat-plan-panel {
  width: 100%;
  max-width: 800px;
  margin: 0 auto 8px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(6px);
  border: 1px solid #e3e6ea;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  padding: 10px 16px;

  .plan-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;

    .plan-icon {
      color: $primary-color;
      font-size: 14px;
    }

    .plan-title {
      font-size: 13px;
      font-weight: 600;
      color: #4e5969;
    }

    .plan-progress {
      margin-left: auto;
      font-size: 12px;
      color: #86909c;
      font-variant-numeric: tabular-nums;
    }
  }

  .plan-steps {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 180px;
    overflow-y: auto;
  }

  .plan-step {
    display: flex;
    align-items: flex-start;
    gap: 9px;
    font-size: 13px;
    line-height: 1.5;

    .step-mark {
      flex-shrink: 0;
      width: 15px;
      height: 15px;
      margin-top: 1px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
    }

    .step-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      border: 1.5px solid #c9cdd4;
    }

    .step-text {
      color: #4e5969;

      .done & {
        color: #86909c;
        text-decoration: line-through;
        text-decoration-color: rgba(134, 144, 156, 0.5);
      }
    }

    &.in_progress .step-mark {
      color: $primary-color;
    }

    &.done .step-mark {
      color: #00b42a;
    }

    &.failed .step-mark {
      color: #f53f3f;
    }
  }
}

// 出现/收起动画
.plan-slide-enter-active,
.plan-slide-leave-active {
  transition: all 0.25s ease;
}

.plan-slide-enter-from,
.plan-slide-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>
