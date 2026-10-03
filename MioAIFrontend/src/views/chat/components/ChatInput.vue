<template>
  <div class="chat-center-area" :class="{ 'has-messages': hasMessages }">
    <div class="welcome-section" v-if="!hasMessages && !loading">
      <img :src="agentAvatar || '/favicon.ico'" alt="Agent" class="welcome-avatar" />
      <h2 class="welcome-title">我能帮什么忙吗，{{ userStore.userName }}？</h2>
    </div>
    <div class="chat-input-wrapper">
      <div class="chat-input-container">
        <div class="input-box">
          <a-textarea
            v-model:value="value"
            :placeholder="`给 ${agentName || 'MioBot'} 发送消息`"
            :auto-size="{ minRows: 1, maxRows: 6 }"
            @pressEnter="handleEnter"
            class="chat-textarea"
          />
          <a-button
            type="primary"
            shape="circle"
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
import { ArrowUpOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  modelValue: string
  agentName?: string
  agentAvatar?: string
  hasMessages: boolean
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'send'): void
}>()

const userStore = useUserStore()

const value = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v)
})

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

  &.has-messages {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    flex: none;
    justify-content: flex-end;
    padding: 16px 24px 24px;
    background: linear-gradient(to top, #fff 80%, transparent);

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
      border-radius: 50%;
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

    .chat-input-container {
      width: 100%;

      .input-box {
        display: flex;
        align-items: center;
        gap: 12px;
        background: #fff;
        border: 1px solid #e5e6eb;
        border-radius: 24px;
        padding: 14px 18px;
        box-shadow: 0 4px 12px rgba(242, 243, 245, 1);
        transition: all 0.2s;

        &:focus-within {
          border-color: $primary-color;
          box-shadow: 0 4px 16px rgba(42, 161, 169, 0.2);
        }

        .chat-textarea {
          flex: 1;
          border: none;
          background: transparent;
          resize: none;
          font-size: 16px;
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

        .send-btn {
          width: 40px;
          height: 40px;
          min-width: 40px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: $primary-color;
          border-color: $primary-color;
          flex-shrink: 0;

          &:hover:not(:disabled) {
            background: darken($primary-color, 10%);
            border-color: darken($primary-color, 10%);
          }

          &:disabled {
            background: #c9cdd4;
            border-color: #c9cdd4;
          }

          :deep(.anticon) {
            font-size: 18px;
          }
        }
      }
    }
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
        .input-box {
          padding: 12px 14px;
          border-radius: 20px;

          .chat-textarea {
            font-size: 14px;
          }

          .send-btn {
            width: 36px;
            height: 36px;
            min-width: 36px;

            :deep(.anticon) {
              font-size: 16px;
            }
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
