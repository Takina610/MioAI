<template>
  <div v-if="plan && plan.length" class="chat-plan-panel">
    <div class="plan-header">
      <OrderedListOutlined class="plan-icon" />
      <span class="plan-title">任务清单</span>
      <span class="plan-progress">{{ doneCount }}/{{ plan.length }}</span>
      <button
        class="plan-toggle"
        :title="collapsed ? '展开任务清单' : '收起任务清单'"
        @click="collapsed = !collapsed"
      >
        <DownOutlined :class="{ 'is-collapsed': collapsed }" />
      </button>
    </div>
    <div v-show="!collapsed" class="plan-steps">
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
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  OrderedListOutlined,
  CheckOutlined,
  CloseOutlined,
  DownOutlined
} from '@ant-design/icons-vue'
import ZcodeSpinner from '@/components/ZcodeSpinner.vue'
import type { PlanStep } from '@/types'

const props = defineProps<{
  /** 最近一次任务清单快照 */
  plan?: PlanStep[] | null
}>()

const collapsed = ref(false)
const doneCount = computed(() => props.plan?.filter(s => s.status === 'done').length ?? 0)

// 换了新清单（新一轮任务开始）时自动展开
watch(() => props.plan?.[0]?.description, () => {
  collapsed.value = false
})
</script>

<style lang="scss" scoped>
// 融合在输入框容器内的任务清单区（无边框卡片，与下方输入框一体）
.chat-plan-panel {
  padding: 10px 16px 8px;
  border-bottom: 1px dashed #eef0f3;

  .plan-header {
    display: flex;
    align-items: center;
    gap: 8px;

    .plan-icon {
      color: $primary-color;
      font-size: 13px;
    }

    .plan-title {
      font-size: 13px;
      font-weight: 600;
      color: #4e5969;
    }

    .plan-progress {
      font-size: 12px;
      color: #86909c;
      font-variant-numeric: tabular-nums;
    }

    .plan-toggle {
      margin-left: auto;
      width: 22px;
      height: 22px;
      display: flex;
      align-items: center;
      justify-content: center;
      border: none;
      background: transparent;
      border-radius: 6px;
      color: #86909c;
      cursor: pointer;
      font-size: 11px;
      transition: all 0.2s;

      &:hover {
        background: #f2f3f5;
        color: $primary-color;
      }

      .is-collapsed {
        transform: rotate(-90deg);
      }

      svg {
        transition: transform 0.2s;
      }
    }
  }

  .plan-steps {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 8px;
    max-height: 168px;
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
</style>
