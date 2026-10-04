<template>
  <div class="chat-center-area" :class="{ 'has-messages': hasMessages }">
    <div class="welcome-section" v-if="!hasMessages && !loading">
      <img :src="agentAvatar || '/logo.png'" alt="Agent" class="welcome-avatar" />
      <h2 class="welcome-title">我能帮什么忙吗，{{ userStore.userName }}？</h2>
    </div>
    <!-- 与输入框融合的上区（任务清单等，共享同一容器边框） -->
    <div class="chat-input-wrapper">
      <div class="chat-input-container">
        <slot name="above-input" />
        <div class="input-main">
          <a-textarea
            v-model:value="value"
            :placeholder="`给 ${agentName || 'MioBot'} 发送消息`"
            :auto-size="{ minRows: 1, maxRows: 8 }"
            @pressEnter="handleEnter"
            class="chat-textarea"
          />
        </div>
        <!-- zcode 式底部工具栏：左思考等级、右发送 -->
        <div class="input-toolbar">
            <a-dropdown :trigger="['click']" placement="topLeft">
            <div class="effort-selector" @click.prevent>
              <BrainIcon :size="14" class="effort-icon" :class="{ dimmed: effort === 'none' }" />
              <span class="effort-label">{{ effortLabel }}</span>
              <DownOutlined class="effort-caret" />
            </div>
            <template #overlay>
              <a-menu :selected-keys="[effort]" @click="onEffortClick">
                <a-menu-item v-for="opt in effortOptions" :key="opt.value">
                  <BrainIcon :size="13" class="menu-brain" :class="{ dimmed: opt.value === 'none' }" />
                  <span>{{ opt.label }}</span>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>

          <a-button
            type="primary"
            class="send-btn"
            :disabled="!value.trim() || loading"
            :loading="loading"
            @click="emit('send')"
          >
            <ArrowUpOutlined v-if="!loading" />
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import { ArrowUpOutlined, DownOutlined } from '@ant-design/icons-vue'
import BrainIcon from '@/components/BrainIcon.vue'

/** 思考档位全量标签（实际渲染哪些档由后端按模型能力返回） */
const EFFORT_LABELS: Record<string, string> = {
  max: '极限',
  xhigh: '超高',
  high: '最高',
  medium: '中等',
  low: '较低',
  minimal: '极低',
  none: '关闭'
}

/** 探测失败时的兜底档位 */
const FALLBACK_EFFORTS = ['high', 'medium', 'low', 'none']

const props = defineProps<{
  modelValue: string
  agentName?: string
  agentAvatar?: string
  hasMessages: boolean
  loading: boolean
  /** 思考强度（后端返回的该模型支持的档位之一） */
  effort?: string
  /** 当前模型支持的思考档位（按能力探测，如实渲染） */
  supportedEfforts?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:effort', value: string): void
  (e: 'send'): void
}>()

const userStore = useUserStore()

const value = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v)
})

const effortOptions = computed(() =>
  (props.supportedEfforts?.length ? props.supportedEfforts : FALLBACK_EFFORTS)
    .map(v => ({ value: v, label: EFFORT_LABELS[v] ?? v }))
    .reverse()
)

const effort = computed(() => props.effort || 'high')

const effortLabel = computed(
  () => effortOptions.value.find(o => o.value === effort.value)?.label ?? EFFORT_LABELS[effort.value] ?? effort.value
)

function onEffortClick({ key }: { key: string | number }): void {
  emit('update:effort', String(key))
}

function handleEnter(e: KeyboardEvent): void {
  if (!e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
}
</script>

<style lang="scss" scoped>
.chat-center-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 24px;
  width: 100%;
  box-sizing: border-box;

  // 有消息时组合器固定在底部（常规流布局，消息区不再被遮挡）
  &.has-messages {
    flex: none;
    padding: 20px 24px 20px;

    .welcome-section {
      display: none;
    }
  }

  .welcome-section {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 16px;
    margin-bottom: 32px;

    .welcome-avatar {
      width: 48px;
      height: 48px;
      border-radius: 25%;
      object-fit: cover;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      flex-shrink: 0;
    }

    .welcome-title {
      font-size: 24px;
      font-weight: 600;
      color: #1d2129;
      margin: 0;
      white-space: nowrap;
    }
  }

  .chat-input-wrapper {
    width: 100%;
    max-width: 800px;

    // 融合容器：任务清单（插槽）与输入框共处一个边框内
    .chat-input-container {
      width: 100%;
      background: #fff;
      border: 1px solid #e5e6eb;
      border-radius: 16px;
      box-shadow: 0 4px 12px rgba(242, 243, 245, 1);
      transition: all 0.2s;

      &:focus-within {
        border-color: $primary-color;
        box-shadow: 0 4px 16px rgba(42, 161, 169, 0.2);
      }

      .input-main {
        padding: 14px 16px 6px;

        .chat-textarea {
          width: 100%;
          border: none;
          background: transparent;
          resize: none;
          font-size: 15px;
          line-height: 1.5;
          color: #1d2129;

          &:focus {
            outline: none;
            box-shadow: none;
          }

          &::placeholder {
            color: #86909c;
          }

          :deep(.ant-input) {
            border: none;
            background: transparent;
            padding: 0;

            &:focus {
              outline: none;
              box-shadow: none;
            }
          }
        }
      }

      // zcode 式工具栏：左思考等级 / 右发送（圆角方形）
      .input-toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 6px 10px 10px 12px;
      }

      .effort-selector {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 5px 10px;
        border-radius: 10px;
        font-size: 13px;
        color: #4e5969;
        cursor: pointer;
        user-select: none;
        transition: background 0.2s;

        &:hover {
          background: #f2f3f5;
        }

        .effort-icon {
          color: $primary-color;

          &.dimmed {
            color: #c9cdd4;
          }
        }

        .effort-caret {
          font-size: 10px;
          color: #86909c;
        }
      }

      .send-btn {
        width: 34px;
        height: 34px;
        min-width: 34px;
        padding: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 10px;
        background: $primary-color;
        border-color: $primary-color;
        flex-shrink: 0;

        &:hover:not(:disabled) {
          background: darken($primary-color, 10%);
          border-color: darken($primary-color, 10%);
        }

        &:disabled {
          background: #e5e6eb;
          border-color: #e5e6eb;
          color: #c9cdd4;
        }

        :deep(.anticon) {
          font-size: 15px;
          display: flex;
        }
      }
    }
  }
}

.menu-brain {
  color: $primary-color;
  margin-right: 8px;
  vertical-align: -2px;

  &.dimmed {
    color: #c9cdd4;
  }
}

@media (max-width: 768px) {
  .chat-center-area {
    padding: 16px;

    .welcome-section {
      gap: 12px;

      .welcome-avatar {
        width: 40px;
        height: 40px;
      }

      .welcome-title {
        font-size: 20px;
      }
    }

    .chat-input-wrapper {
      .chat-input-container {
        .input-main {
          padding: 12px 14px 4px;

          .chat-textarea {
            font-size: 14px;
          }
        }

        .send-btn {
          width: 32px;
          height: 32px;
          min-width: 32px;

          :deep(.anticon) {
            font-size: 14px;
          }
        }
      }
    }
  }
}

@media (max-width: 480px) {
  .chat-center-area {
    .welcome-section {
      .welcome-title {
        font-size: 20px;
      }
    }
  }
}
</style>
