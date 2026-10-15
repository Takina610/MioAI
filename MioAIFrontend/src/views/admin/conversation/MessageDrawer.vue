<template>
  <a-drawer
    :open="open"
    :title="conversation?.title || '会话详情'"
    width="660"
    destroy-on-close
    @close="$emit('update:open', false)"
  >
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="messages" tab="消息">
        <a-spin :spinning="messagesLoading">
          <div class="msg-list">
            <div v-for="msg in messages" :key="msg.id" class="msg-item">
              <div class="msg-head">
                <span class="msg-role" :class="msg.role">{{ msg.role === 'user' ? '用户' : msg.role === 'assistant' ? '助手' : msg.role }}</span>
                <span class="msg-meta">#{{ msg.seq }}</span>
                <span class="msg-meta">{{ formatDateTime(msg.createTime) }}</span>
                <span v-if="msg.durationMs" class="msg-meta">{{ (msg.durationMs / 1000).toFixed(1) }}s</span>
                <a-popconfirm
                  title="删除这条消息？"
                  ok-text="删除"
                  cancel-text="取消"
                  @confirm="handleDeleteMessage(msg.id)"
                >
                  <a-button type="link" size="small" danger class="msg-del"><DeleteOutlined /></a-button>
                </a-popconfirm>
              </div>
              <div class="msg-body">{{ messageText(msg.blocks) }}</div>
            </div>
            <a-empty v-if="!messagesLoading && messages.length === 0" description="会话内没有消息" />
          </div>
          <div class="drawer-pagination">
            <a-pagination
              v-model:current="messagePage.current"
              v-model:page-size="messagePage.pageSize"
              :total="messagePage.total"
              size="small"
              @change="fetchMessages"
            />
          </div>
        </a-spin>
      </a-tab-pane>
      <a-tab-pane key="files" tab="文件">
        <a-spin :spinning="filesLoading">
          <a-table
            :data-source="files"
            :columns="fileColumns"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'kind'">
                {{ record.kind === 1 ? '用户上传' : 'Agent 产出' }}
              </template>
              <template v-else-if="column.key === 'fileSize'">
                {{ formatFileSize(record.fileSize) }}
              </template>
              <template v-else-if="column.key === 'status'">
                <span class="adm-dot" :class="record.status === 1 ? 'is-success' : 'is-danger'">
                  {{ record.status === 1 ? '正常' : '待删除' }}
                </span>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-popconfirm
                  title="删除该文件？"
                  ok-text="删除"
                  cancel-text="取消"
                  @confirm="handleDeleteFile(record.id)"
                >
                  <a-button type="link" size="small" danger><DeleteOutlined /></a-button>
                </a-popconfirm>
              </template>
            </template>
          </a-table>
        </a-spin>
      </a-tab-pane>
    </a-tabs>
  </a-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { DeleteOutlined } from '@ant-design/icons-vue'
import MioMessage from '@/utils/message'
import { formatDateTime, formatFileSize, truncateText } from '@/utils/format'
import {
  deleteConversationFile,
  deleteConversationMessage,
  getConversationFiles,
  getConversationMessages
} from '@/api/adminConversation'
import type { ChatFileRecord, ConversationRecord, MessageRecord } from '@/api/adminConversation'

const props = defineProps<{
  open: boolean
  conversation: ConversationRecord | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'changed'): void
}>()

const activeTab = ref<'messages' | 'files'>('messages')
const messages = ref<MessageRecord[]>([])
const messagesLoading = ref(false)
const messagePage = reactive({ current: 1, pageSize: 50, total: 0 })
const files = ref<ChatFileRecord[]>([])
const filesLoading = ref(false)

const fileColumns = [
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: '来源', key: 'kind', width: 90 },
  { title: '大小', key: 'fileSize', width: 80 },
  { title: '状态', key: 'status', width: 80 },
  { title: '', key: 'action', width: 48 }
]

watch(
  () => props.open,
  open => {
    if (open && props.conversation) {
      activeTab.value = 'messages'
      messagePage.current = 1
      fetchMessages()
      fetchFiles()
    }
  }
)

async function fetchMessages() {
  if (!props.conversation) return
  messagesLoading.value = true
  try {
    const res = await getConversationMessages(props.conversation.conversationId, {
      current: messagePage.current,
      pageSize: messagePage.pageSize
    })
    messages.value = res.records ?? []
    messagePage.total = res.total ?? 0
  } catch {
    MioMessage.error('获取消息失败')
  } finally {
    messagesLoading.value = false
  }
}

async function fetchFiles() {
  if (!props.conversation) return
  filesLoading.value = true
  try {
    files.value = (await getConversationFiles(props.conversation.conversationId)) ?? []
  } catch {
    MioMessage.error('获取文件失败')
  } finally {
    filesLoading.value = false
  }
}

async function handleDeleteMessage(messageId: number) {
  if (!props.conversation) return
  try {
    await deleteConversationMessage(props.conversation.conversationId, messageId)
    MioMessage.success('已删除')
    fetchMessages()
  } catch {
    MioMessage.error('删除失败')
  }
}

async function handleDeleteFile(fileId: number) {
  try {
    await deleteConversationFile(fileId)
    MioMessage.success('已删除')
    fetchFiles()
  } catch {
    MioMessage.error('删除失败')
  }
}

/** blocks JSON → 预览文本：text 全文，thinking/工具/其他块标注类型 */
function messageText(blocksJson: string): string {
  if (!blocksJson) return '(空)'
  let blocks: Array<Record<string, unknown>> = []
  try {
    const parsed = JSON.parse(blocksJson)
    blocks = Array.isArray(parsed) ? parsed : [parsed]
  } catch {
    return truncateText(blocksJson, 300)
  }
  const parts = blocks.map(block => {
    const type = String(block.type ?? 'text')
    if (type === 'text' || type === 'thinking') {
      return String(block.text ?? '')
    }
    if (type === 'tool') {
      return `[工具 ${block.name ?? block.toolName ?? ''}]`
    }
    if (type === 'attachments') return '[附件]'
    if (type === 'kb') return '[知识库命中]'
    if (type === 'mcp') return '[MCP 装配]'
    if (type === 'member') return `[成员 ${block.memberName ?? ''}] ${block.text ?? ''}`
    if (type === 'question') return '[提问]'
    return `[${type}]`
  })
  return truncateText(parts.filter(Boolean).join('\n'), 400)
}
</script>

<style lang="scss" scoped>
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;

  .msg-item {
    border: 1px solid $border-warm-subtle;
    border-radius: 8px;
    padding: 10px 12px;
    background: $bg-ivory;

    .msg-head {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;

      .msg-role {
        font-size: 12px;
        font-weight: 600;
        color: $primary-color;

        &.user {
          color: #4f9e63;
        }
      }

      .msg-meta {
        font-size: 12px;
        color: $text-muted;
      }

      .msg-del {
        margin-left: auto;
        opacity: 0;
        transition: opacity 0.2s;
      }
    }

    &:hover .msg-del {
      opacity: 1;
    }

    .msg-body {
      font-size: 13px;
      color: $text-secondary;
      line-height: 1.6;
      white-space: pre-wrap;
      word-break: break-word;
      max-height: 180px;
      overflow-y: auto;
    }
  }
}

.drawer-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
