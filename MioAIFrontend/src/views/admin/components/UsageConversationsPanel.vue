<template>
  <div class="usage-conv-panel">
    <a-table
      :data-source="records"
      :columns="columns"
      row-key="conversationId"
      size="small"
      :loading="loading"
      :pagination="{ pageSize: 8, total }"
      @change="handlePage"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <div class="conv-title-cell">{{ record.data.title }}</div>
          <span class="adm-cell-muted">{{ record.data.conversationId }}</span>
        </template>
        <template v-else-if="column.key === 'user'">
          <div class="cell-member">
            <a-avatar :src="record.userAvatar" :size="20">{{ (record.userName ?? 'U').charAt(0) }}</a-avatar>
            <span>{{ record.userName || (record.data.userId ?? '游客') }}</span>
          </div>
        </template>
        <template v-else-if="column.key === 'lastUsedTime'">
          {{ formatDateTime(record.data.lastUsedTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button
            v-if="record.data.exists && record.data.agentId"
            type="link"
            size="small"
            @click="openShareView(record.data)"
          >
            查看
          </a-button>
          <span v-else class="adm-cell-muted">会话已删除</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import MioMessage from '@/utils/message'
import { formatDateTime } from '@/utils/format'
import { searchConversationsByUsage } from '@/api/adminConversation'
import type { UsageConversationRow, UsageConversationQuery } from '@/api/adminConversation'

const props = defineProps<{
  /** 三选一：知识库 / MCP / 技能 的关联会话 */
  fixedQuery: UsageConversationQuery
}>()

const loading = ref(false)
const records = ref<UsageConversationRow[]>([])
const total = ref(0)

const columns = [
  { title: '会话', key: 'title', ellipsis: true },
  { title: '用户', key: 'user', width: 130 },
  { title: '智能体', key: 'agentName', width: 120, ellipsis: true },
  { title: '最后使用', key: 'lastUsedTime', width: 160 },
  { title: '', key: 'action', width: 110 }
]

/** 复用分享界面查看（单独窗口，公开链接即凭证） */
function openShareView(data: { agentId: number | null; conversationId: string }) {
  if (!data.agentId) return
  window.open(`/share/${data.agentId}/${data.conversationId}`, '_blank')
}

async function load() {
  loading.value = true
  try {
    const res = await searchConversationsByUsage({ current: 1, pageSize: 20, ...props.fixedQuery })
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    MioMessage.error('获取关联会话失败')
  } finally {
    loading.value = false
  }
}

function handlePage() {
  load()
}

onMounted(load)
</script>

<style lang="scss" scoped>
.conv-title-cell {
  color: $text-dark;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-member {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $text-secondary;
  font-size: 13px;
}
</style>
