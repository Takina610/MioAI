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
          <!-- assistant 按内容块顺序渲染（文本/思考/工具顺着显示），流式期间分支稳定不切换 -->
          <MioBotMessage
            v-if="msg.role === 'assistant'"
            :content="msg.content"
            :blocks="msg.blocks"
            :is-loading="isLoading"
            :duration-ms="msg.durationMs"
            :create-time="msg.createTime"
            :interrupted="msg.interrupted"
          />
          <template v-else>
            <MarkdownView class="message-text" :content="msg.content" />
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
import MioBotMessage from './MioBotMessage.vue'

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

/** 用户是否贴在底部（留少量阈值）：流式跟随只在贴底时拉滚动，滚上去阅读时不打扰 */
function isNearBottom(): boolean {
  if (!messagesRef.value) {
    return true
  }
  const el = messagesRef.value
  return el.scrollHeight - el.scrollTop - el.clientHeight < 120
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

defineExpose({ scrollToBottom, isNearBottom })
</script>

<style lang="scss" scoped>
.chat-messages {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
  // 组合器已是常规流布局，不再需要为悬浮输入框预留大片底部空间
  padding-bottom: 24px;

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
        /* 允许 flex 项收缩到内容以下：宽表格/代码块由内部滚动，不撑破气泡产生页面横向滚动 */
        min-width: 0;
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
