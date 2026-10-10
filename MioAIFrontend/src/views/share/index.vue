<template>
  <div class="share-page" ref="pageRef">
    <div class="share-container" ref="containerRef">
      <div class="conversation-card">
        <div class="card-header">
          <h1 class="conversation-title">{{ conversationTitle || '分享对话' }}</h1>
          <p class="conversation-meta">
            {{ formattedDate }} · 内容由 AI 生成，不能完全保障真实
          </p>
        </div>

        <div class="card-divider"></div>

        <div class="card-content" ref="contentRef">
          <div class="chat-messages" ref="messagesRef" v-if="messages.length > 0">
            <div class="messages-wrapper">
              <div
                v-for="msg in messages"
                :key="msg.id"
                class="message"
                :class="msg.role"
                @mouseenter="hoverMessageId = msg.id"
                @mouseleave="hoverMessageId = ''"
              >
                <div class="message-content">
                  <MarkdownView v-if="msg.content" class="message-text" :content="msg.content" />
                  <div class="message-actions">
                    <div class="copy-area" v-show="hoverMessageId === msg.id && msg.content">
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

          <div class="conversation-loading" v-if="loading">
            <a-spin size="large" />
            <p>加载中...</p>
          </div>
        </div>
      </div>

      <div class="action-buttons fixed">
        <button class="text-btn" @click="goToChat">
          <MessageOutlined />
          <div class="text">
            {{ agentInfo?.name || 'MioBot' }}继续聊
          </div>
        </button>
      </div>

      <div class="footer-section">
        <p class="copyright">{{ agentInfo?.name || 'MioBot' }} • 你的AI助手</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getConversation } from '@/api/chatMemory'
import { getBotMessages } from '@/api/botMessages'
import { getAgentById } from '@/api/agent'
import type { Agent, ChatMessage, MessageBlock } from '@/types'
import { CopyOutlined, CheckOutlined, MessageOutlined } from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import { textOfBlocks } from '@/views/chat/composables/useChatMessages'

const route = useRoute()
const router = useRouter()

const loading = ref<boolean>(true)
const conversationTitle = ref<string>('')
const hoverMessageId = ref<string>('')
const copiedMessageId = ref<string>('')
const messages = ref<ChatMessage[]>([])
const agentInfo = ref<Agent | null>(null)
const conversationDate = ref<Date>(new Date())
const agentId = ref<number>(0)

watch(
  () => [route.params.agentId, route.params.conversationId],
  ([agentIdParam, conversationId]) => {
    if (agentIdParam && conversationId && typeof agentIdParam === 'string' && typeof conversationId === 'string') {
      loadShareData(agentIdParam, conversationId)
    }
  },
  { immediate: true }
)

const formattedDate = computed(() => {
  const d = conversationDate.value
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year} 年 ${month} 月 ${day} 日`
})

async function loadShareData(agentIdParam: string, conversationId: string): Promise<void> {
  loading.value = true
  try {
    agentId.value = Number(agentIdParam)
    try {
      agentInfo.value = await getAgentById(agentId.value)
    } catch {
      agentInfo.value = null
    }

    const conversationRes = await getConversation(conversationId)
    if (conversationRes && conversationRes.title) {
      conversationTitle.value = conversationRes.title
      document.title = `${conversationRes.title} - MioAI`
    }

    if (conversationRes && conversationRes.createTime) {
      conversationDate.value = new Date(conversationRes.createTime)
    }

    const rows = await getBotMessages(conversationId)

    if (!rows || rows.length === 0) {
      router.push('/404')
      return
    }

    messages.value = rows.map((row, index) => {
      const blocks = (row.blocks ?? undefined) as MessageBlock[] | undefined
      return {
        id: `${conversationId}_${row.seq ?? index}`,
        role: row.role === 'user' ? 'user' : 'assistant',
        content: textOfBlocks(blocks ?? []),
        createTime: row.createTime ? new Date(row.createTime) : new Date(),
        blocks
      } as ChatMessage
    })

    if (!conversationTitle.value) {
      const firstUser = rows.find(r => r.role === 'user')
      const firstUserContent = firstUser ? textOfBlocks(firstUser.blocks ?? []) : ''
      if (firstUserContent) {
        conversationTitle.value = firstUserContent.slice(0, 30) + (firstUserContent.length > 30 ? '...' : '')
        document.title = `${conversationTitle.value} - MioAI`
      }
    }
  } catch (e) {
    console.error('Failed to load share data:', e)
    message.error('加载分享内容失败')
  } finally {
    loading.value = false
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

function goToChat(): void {
  router.push(`/chat/${agentId.value || 1}`)
}
</script>

<style lang="scss" scoped>
.share-page {
  // 原背景图 bg.jpg 未随仓库提交，构建时报 ENOENT；先回退为纯色背景
  background: #f0eee6;
  min-height: 100vh;
  background-attachment: fixed;

  display: flex;
  justify-content: center;
  padding: 40px 20px 20px;
  overflow: auto;
}

.share-container {
  width: 100%;
  max-width: 900px;
  display: flex;
  flex-direction: column;
  padding-bottom: 80px;
}

.conversation-card {
  background: #fff;
  border-radius: 25px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08), 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.card-header {
  padding: 28px 32px 24px;

  .conversation-title {
    font-size: 30px;
    font-weight: 600;
    color: #141413;
    margin: 0 0 12px 0;
    line-height: 1.4;
  }

  .conversation-meta {
    font-size: 13px;
    color: #a5a29a;
    margin: 0;
  }
}

.card-divider {
  height: 1px;
  background: #e8e6dc;
  margin: 0 32px;
}

.card-content {
  padding: 24px 32px;

  .chat-messages {
    .messages-wrapper {
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

        .message-content {
          flex: 1;
          /* 允许 flex 项收缩：宽表格/代码块由内部滚动，避免被卡片 overflow:hidden 剪掉或撑破版心 */
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

            :deep(pre) {
              background: #f5f3ec;
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
              background: #f5f3ec;
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
              border-left: 3px solid #e8e6dc;
              padding-left: 12px;
              margin: 8px 0;
              color: #6e6b62;
            }
          }

          .message-actions {
            min-height: 35px;
            margin-top: 8px;
            display: flex;
            gap: 8px;
            align-items: center;

            .copy-btn {
              color: #8c8a82;
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
        }
      }
    }
  }
}

.conversation-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  color: #8c8a82;

  p {
    margin-top: 16px;
    font-size: 14px;
  }
}

.footer-section {
  margin-top: 24px;
  text-align: center;

  .copyright {
    font-size: 12px;
    color: #8c8a82;
    margin: 0;
  }
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 16px;
  margin-top: 40px;
  padding-bottom: 40px;

  &.fixed {
    position: fixed;
    bottom: 0;
    left: 0;
    width: 100%;
    z-index: 1000;

    margin: 0;
    padding: 12px 0;
  }

  .text-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 40px;
    padding: 0 24px;
    border: none;
    background: $primary-color;
    border-radius: 20px;
    cursor: pointer;
    font-size: 15px;
    font-weight: 500;
    color: #fff;
    transition: all 0.2s;

    &:hover {
      background: darken($primary-color, 8%);
    }

    .text {
      margin-left: 8px;
    }
  }
}

@media (max-width: 768px) {
  .share-page {
    padding: 20px 16px 100px;
  }

  .card-header {
    padding: 20px 20px 16px;

    .conversation-title {
      font-size: 18px;
    }

    .conversation-meta {
      font-size: 12px;
    }
  }

  .card-divider {
    margin: 0 20px;
  }

  .card-content {
    padding: 20px;
  }

  .footer-section {
    .action-buttons {
      .text-btn {
        height: 36px;
        padding: 0 20px;
        font-size: 14px;
      }
    }
  }
}

:deep(ul), :deep(ol) {
  list-style: decimal;
}

:deep(table) {
  border: 1px solid #c5c2b8;
}
:deep(table) td,
:deep(table) th {
  border-bottom: 1px solid #c5c2b8;
  border-right: 1px solid #c5c2b8;
  padding: 5px 10px;
}
:deep(table) th {
  text-align: center;
  background: #ece9de;
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
  background: #f0ede4;
}
/* blockquote 样式 */
:deep(blockquote) {
  display: block;
  border-left: 8px solid #e3f1f2;
  padding: 5px 10px;
  margin: 10px 0;
  line-height: 1.4;
  font-size: 100%;
  background-color: #f0ede4;
}
</style>
