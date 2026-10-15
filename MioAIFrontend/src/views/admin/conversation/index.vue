<template>
  <div class="adm-page">
    <div class="adm-page-header">
      <span class="adm-page-title">会话</span>
    </div>

    <div class="adm-filter-bar">
      <a-input-search
        v-model:value="filters.keyword"
        placeholder="标题或会话 ID"
        class="adm-filter-search"
        allow-clear
        @search="handleSearch"
        @change="handleKeywordChange"
      />
      <a-input-number
        v-model:value="filters.userId"
        placeholder="用户 ID"
        class="adm-filter-item"
        :controls="false"
        @press-enter="handleSearch"
      />
      <a-input-number
        v-model:value="filters.agentId"
        placeholder="智能体 ID"
        class="adm-filter-item"
        :controls="false"
        @press-enter="handleSearch"
      />
      <a-input
        v-model:value="filters.ownerName"
        placeholder="用户名"
        class="adm-filter-item"
        allow-clear
        @press-enter="handleSearch"
      />
      <a-input
        v-model:value="filters.agentName"
        placeholder="智能体名称"
        class="adm-filter-item"
        allow-clear
        @press-enter="handleSearch"
      />
      <a-input
        v-model:value="filters.messageKeyword"
        placeholder="消息内容"
        class="adm-filter-item"
        allow-clear
        @press-enter="handleSearch"
        @change="handleMessageKeywordChange"
      />
      <a-date-picker v-model:value="filters.startDate" placeholder="开始日期" value-format="YYYY-MM-DD" class="adm-filter-item" @change="handleSearch" />
      <a-date-picker v-model:value="filters.endDate" placeholder="结束日期" value-format="YYYY-MM-DD" class="adm-filter-item" @change="handleSearch" />
      <a-button type="primary" @click="handleSearch">查询</a-button>
    </div>

    <div class="adm-table-card">
      <a-table
        :data-source="records"
        :columns="columns"
        row-key="id"
        size="middle"
        :loading="loading"
        :pagination="{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showTotal: (total: number) => `共 ${total} 条`
        }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <div class="conv-title">{{ record.data.title || '未命名会话' }}</div>
            <span class="adm-id-chip">{{ record.data.conversationId }}</span>
          </template>
          <template v-else-if="column.key === 'user'">
            <div class="cell-entity">
              <a-avatar :src="record.userAvatar" :size="22">{{ record.data.userId ?? '?' }}</a-avatar>
              <span>{{ record.userName || record.data.userId || '游客' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'agent'">
            <div class="cell-entity">
              <a-avatar :src="record.agentAvatar" :size="22">{{ record.agentName?.charAt(0) || 'A' }}</a-avatar>
              <span>{{ record.agentName || record.data.agentId }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'pinned'">
            <span v-if="record.data.pinned === 1" class="adm-dot is-success">置顶</span>
          </template>
          <template v-else-if="column.key === 'updateTime'">
            <span class="adm-cell-muted">{{ formatDateTime(record.data.updateTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="0">
              <a-button type="link" size="small" @click="openDrawer(record.data)">详情</a-button>
              <a-button type="link" size="small" @click="openShareView(record.data)">查看</a-button>
              <a-popconfirm
                title="删除该会话？消息、文件将一并清理。"
                ok-text="删除"
                cancel-text="取消"
                @confirm="handleDelete(record.data.conversationId)"
              >
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <MessageDrawer
      v-model:open="drawerOpen"
      :conversation="activeConversation"
      @changed="refresh"
    />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import MioMessage from '@/utils/message'
import { formatDateTime } from '@/utils/format'
import { useAdminTable } from '@/composables/useAdminTable'
import {
  deleteConversation,
  searchConversations
} from '@/api/adminConversation'
import type { ConversationQueryRequest, ConversationRecord, ConversationRow } from '@/api/adminConversation'
import MessageDrawer from './MessageDrawer.vue'

const route = useRoute()
const filters = reactive<ConversationQueryRequest>({
  keyword: '',
  userId: route.query.userId ? Number(route.query.userId) : undefined,
  agentId: undefined,
  ownerName: (route.query.ownerName as string) || '',
  agentName: '',
  messageKeyword: '',
  startDate: undefined,
  endDate: undefined
})

const { loading, records, pagination, search, refresh } = useAdminTable<ConversationRow, ConversationQueryRequest>(
  searchConversations
)

const columns = [
  { title: '标题', key: 'title', ellipsis: true },
  { title: '用户', key: 'user', width: 170 },
  { title: '智能体', key: 'agent', width: 170 },
  { title: '置顶', key: 'pinned', width: 70 },
  { title: '更新时间', key: 'updateTime', width: 160 },
  { title: '', key: 'action', width: 170 }
]

function handleSearch() {
  search({
    keyword: filters.keyword || undefined,
    userId: filters.userId ?? undefined,
    agentId: filters.agentId ?? undefined,
    ownerName: filters.ownerName || undefined,
    agentName: filters.agentName || undefined,
    messageKeyword: filters.messageKeyword || undefined,
    startDate: filters.startDate || undefined,
    endDate: filters.endDate || undefined
  })
}

function handleKeywordChange() {
  if (!filters.keyword) handleSearch()
}

function handleMessageKeywordChange() {
  if (!filters.messageKeyword) handleSearch()
}

function handleTableChange(pager: { current?: number; pageSize?: number }) {
  pagination.current = pager.current ?? pagination.current
  pagination.pageSize = pager.pageSize ?? pagination.pageSize
  refresh()
}

const drawerOpen = ref(false)
const activeConversation = ref<ConversationRecord | null>(null)

function openDrawer(conversation: ConversationRecord) {
  activeConversation.value = conversation
  drawerOpen.value = true
}

/** 复用分享界面查看：新窗口打开分享页（公开链接即凭证，管理员可看任意会话） */
function openShareView(conversation: ConversationRecord) {
  window.open(`/share/${conversation.agentId}/${conversation.conversationId}`, '_blank')
}

async function handleDelete(conversationId: string) {
  try {
    await deleteConversation(conversationId)
    MioMessage.success('会话已删除')
    refresh()
  } catch {
    MioMessage.error('删除失败')
  }
}

search()
</script>

<style lang="scss" scoped>
.conv-title {
  color: $text-dark;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}

.cell-entity {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $text-secondary;
  font-size: 13px;
}
</style>
