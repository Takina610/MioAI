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
          <div v-else class="message-text" v-html="formatMessage(msg.content)"></div>
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
import { safeMarkdown } from '@/utils/security'
import { CopyOutlined, CheckOutlined } from '@ant-design/icons-vue'
import type { ChatMessage } from '@/types'
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

function formatMessage(content: string): string {
  return safeMarkdown(content)
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

          :deep(pre) {
            background: #f6f8fa;
            border-radius: 6px;
            padding: 12px 16px;
            overflow-x: auto;
            margin: 8px 0;

            code {
              font-family: 'Consolas', 'Monaco', monospace;
              font-size: 13px;
            }
          }

          :deep(code) {
            background: #f6f8fa;
            padding: 2px 6px;
            border-radius: 4px;
            font-family: 'Consolas', 'Monaco', monospace;
            font-size: 13px;
          }

          :deep(p) {
            margin: 0 0 8px 0;

            &:last-child {
              margin-bottom: 0;
            }
          }

          :deep(ul), :deep(ol) {
            margin: 8px 0;
            padding-left: 20px;
          }

          :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
            margin: 12px 0 8px 0;
            font-weight: 600;
          }

          :deep(a) {
            color: $primary-color;
            text-decoration: none;

            &:hover {
              text-decoration: underline;
            }
          }

          :deep(blockquote) {
            border-left: 4px solid $primary-color;
            padding-left: 12px;
            margin: 8px 0;
            color: #666;
          }
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

        :deep(pre) {
          background: rgba(255, 255, 255, 0.1);
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

:deep(ul), :deep(ol) {
  list-style: decimal;
}

:deep(table) {
  border: 1px solid #ccc;
}
:deep(table) td,
:deep(table) th {
  border-bottom: 1px solid #ccc;
  border-right: 1px solid #ccc;
  padding: 5px 10px;
}
:deep(table) th {
  // border-bottom: 2px solid #ccc;
  text-align: center;
  background: #dee8ee;
}
:deep(table) th:last-child {
  border-right: none;
}
:deep(table) td:last-child {
  border-right: none;
}

:deep(table) tr:last-child td {
  border-bottom: none;
}
:deep(table) tr:nth-child(even) {
  background: #eff3f5;
}
/* blockquote 样式 */
:deep(blockquote) {
  display: block;
  border-left: 8px solid #d0e5f2;
  padding: 5px 10px;
  margin: 10px 0;
  line-height: 1.4;
  font-size: 100%;
  background-color: #f1f1f1;
}
</style>
