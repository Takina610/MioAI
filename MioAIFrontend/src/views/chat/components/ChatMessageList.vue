<template>
  <div class="chat-messages" ref="messagesRef" v-if="messages.length > 0">
    <div class="messages-wrapper">
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="message"
        :class="msg.role"
        @mouseenter="handleMouseEnter(msg.id)"
        @mouseleave="hoverMessageId = ''"
      >
        <div class="message-content">
          <div v-if="msg.role === 'assistant' && isLoading && !msg.content" class="message-loading">
            <span></span><span></span><span></span>
          </div>
          <MioManusMessage
            v-else-if="msg.role === 'assistant' && msg.segments"
            :content="msg.content"
            :segments="msg.segments"
            :is-loading="isLoading"
          />
          <template v-else>
            <MarkdownView class="message-text" :content="msg.content" />
            <div v-if="msg.role === 'assistant' && msg.interrupted" class="stream-interrupted">
              连接中断，本条回答可能不完整
            </div>
          </template>
          <div class="message-actions">
            <div class="copy-area" v-show="!isLoading && hoverMessageId === msg.id && msg.content">
              <a-tooltip :title="copiedMessageId === msg.id ? '已复制' : '复制'">
                <a-button type="text" size="small" class="copy-btn" :class="{ 'copied': copiedMessageId === msg.id }" @click="copyMessage(msg.content, msg.id)">
                  <CheckOutlined v-if="copiedMessageId === msg.id" />
                  <CopyOutlined v-else />
                </a-button>
              </a-tooltip>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, CheckOutlined } from '@ant-design/icons-vue'
import type { ChatMessage } from '@/types'
import MarkdownView from '@/components/MarkdownView.vue'
import MioManusMessage from './MioManusMessage.vue'

defineProps<{
  messages: ChatMessage[]
  isLoading: boolean
}>()

const messagesRef = ref<HTMLElement | null>(null)
const hoverMessageId = ref<string>('')
const copiedMessageId = ref<string>('')

function scrollToBottom(): void {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function handleMouseEnter(msgId: string): void {
  hoverMessageId.value = msgId
  if (copiedMessageId.value && copiedMessageId.value !== msgId) {
    copiedMessageId.value = ''
  }
}

async function copyMessage(content: string, messageId: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(content)
    copiedMessageId.value = messageId
  } catch (e) {
    console.error(e)
    message.error('复制失败')
  }
}

defineExpose({ scrollToBottom })
</script>

<style lang="scss" scoped>
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  padding-bottom: 120px;

  .messages-wrapper {
    max-width: 800px;
    margin: 0 auto;

    .message {
      display: flex;
      gap: 16px;
      margin-bottom: 24px;

      &.user {
        flex-direction: row-reverse;

        .message-content {
          align-items: flex-end;
        }
      }

      .message-avatar {
        flex-shrink: 0;

        .agent-avatar-msg {
          background: $primary-color;
        }
      }

      .message-content {
        flex: 1;
        display: flex;
        flex-direction: column;
        position: relative;

        .message-text {
          max-width: 70%;
          padding: 12px 16px;
          border-radius: 12px;
          font-size: 14px;
          line-height: 1.6;
          word-break: break-word;
        }

        .stream-interrupted {
          font-size: 12px;
          color: #d48806;
          margin-top: 4px;
        }

        .message-loading {
          display: flex;
          gap: 6px;
          padding: 8px 0;

          span {
            width: 8px;
            height: 8px;
            background: $primary-color;
            border-radius: 50%;
            animation: loading-bounce 1.4s infinite ease-in-out both;

            &:nth-child(1) {
              animation-delay: -0.32s;
            }

            &:nth-child(2) {
              animation-delay: -0.16s;
            }

            &:nth-child(3) {
              animation-delay: 0s;
            }
          }
        }

        .message-actions {
          min-height: 35px;
          margin-top: 8px;
          display: flex;
          gap: 8px;
          align-items: center;

          .copy-btn {
            color: #86909c;
            padding: 4px 8px;
            height: auto;
            font-size: 14px;
            transition: all 0.2s;

            &:hover {
              color: $primary-color;
              background: rgba($primary-color, 0.08);
            }

            &.copied {
              color: #52c41a;
            }
          }
        }
      }

      &.user .message-text {
        background: $primary-color;
        color: #fff;

        :deep(code) {
          background: rgba(255, 255, 255, 0.2);
        }

        :deep(pre.md-code) {
          background: rgba(255, 255, 255, 0.1);
          border-color: transparent;

          code {
            color: #fff;
          }
        }

        :deep(a) {
          color: #fff;
          text-decoration: underline;
        }

        :deep(blockquote) {
          background: rgba(255, 255, 255, 0.12);
          color: rgba(255, 255, 255, 0.9);
          border-left-color: rgba(255, 255, 255, 0.6);
        }
      }

      &.assistant .message-text {
        background: transparent;
        color: #202124;
        max-width: 100%;
        padding: 0;
      }
    }
  }
}

@keyframes loading-bounce {
  0%, 80%, 100% {
    transform: scale(0);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
