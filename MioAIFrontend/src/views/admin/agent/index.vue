<template>
  <div class="adm-page">
    <div class="adm-page-header">
      <span class="adm-page-title">智能体</span>
      <a-button type="primary" @click="openCreate">新增智能体</a-button>
    </div>

    <div class="adm-filter-bar">
      <a-input-search
        v-model:value="keyword"
        placeholder="智能体名称"
        class="adm-filter-search"
        allow-clear
        @search="handleSearch"
        @change="handleKeywordChange"
      />
      <a-select v-model:value="statusFilter" mode="multiple" placeholder="全部状态" allow-clear class="adm-filter-item" @change="handleSearch">
        <a-select-option :value="0">草稿</a-select-option>
        <a-select-option :value="1">已发布</a-select-option>
        <a-select-option :value="2">禁用</a-select-option>
      </a-select>
      <a-select v-model:value="typeFilter" mode="multiple" placeholder="全部类型" allow-clear class="adm-filter-item" @change="handleSearch">
        <a-select-option :value="1">智能体</a-select-option>
        <a-select-option :value="2">群聊</a-select-option>
        <a-select-option :value="3">MioBot</a-select-option>
      </a-select>
      <a-input-search
        v-model:value="ownerName"
        placeholder="归属用户名"
        class="adm-filter-item"
        allow-clear
        @search="handleSearch"
        @change="handleKeywordChange"
      />
    </div>

    <div class="adm-table-card">
      <a-table
        :data-source="records"
        :columns="columns"
        row-key="id"
        size="middle"
        :loading="loading"
        :scroll="{ x: 960 }"
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
          <template v-if="column.key === 'name'">
            <div class="cell-agent">
              <BotIcon
                v-if="iconConfig(record)"
                :shape="iconConfig(record)!.shape"
                :fill="iconConfig(record)!.fill"
                :size="30"
                :live="false"
              />
              <a-avatar v-else :src="record.avatar" :size="28">{{ record.name?.charAt(0) || 'A' }}</a-avatar>
              <div class="cell-agent-text">
                <div class="adm-cell-primary">
                  {{ record.name }}
                  <a-tag v-if="record.type === 3" color="#2aa1a9" class="miobot-tag">MioBot</a-tag>
                </div>
                <div class="adm-cell-muted">{{ record.description || '-' }}</div>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'type'">
            {{ record.type === 2 ? '群聊' : record.type === 3 ? 'MioBot' : '智能体' }}
          </template>
          <template v-else-if="column.key === 'owner'">
            <div class="cell-owner">
              <span>{{ record.userName || '-' }}</span>
              <span class="adm-cell-muted">#{{ record.userId }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <span v-if="record.status === 0" class="adm-dot">草稿</span>
            <a-switch
              v-else
              :checked="record.status === 1"
              checked-children="发布"
              un-checked-children="禁用"
              @change="(checked: boolean) => handleStatusToggle(record, checked)"
            />
          </template>
          <template v-else-if="column.key === 'isPublic'">
            <span class="adm-dot" :class="record.isPublic === 1 ? 'is-success' : ''">
              {{ record.isPublic === 1 ? '公开' : '私有' }}
            </span>
          </template>
          <template v-else-if="column.key === 'usageCount'">
            {{ record.usageCount ?? 0 }}
          </template>
          <template v-else-if="column.key === 'updateTime'">
            <span class="adm-cell-muted">{{ formatDateTime(record.updateTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="0">
              <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm
                title="删除该智能体？其会话将一并清理。"
                ok-text="删除"
                cancel-text="取消"
                @confirm="handleDelete(record.id)"
              >
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <RecordDrawer
      v-model:open="drawerOpen"
      :mode="drawerMode"
      :record="activeRecord"
      @saved="refresh"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import MioMessage from '@/utils/message'
import { formatDateTime } from '@/utils/format'
import { useAdminTable } from '@/composables/admin/useAdminTable'
import { deleteAgent, searchAgents, updateAgent } from '@/api/adminAgent'
import type { Agent, AgentQueryRequest } from '@/types'
import BotIcon from '@/components/agent/bot-icon/BotIcon.vue'
import { parseBotIcon, type BotIconConfig } from '@/components/agent/bot-icon/types'
import RecordDrawer from './RecordDrawer.vue'

const route = useRoute()
const keyword = ref('')
const ownerName = ref((route.query.ownerName as string) || '')
const statusFilter = ref<number[] | undefined>(undefined)
const typeFilter = ref<number[] | undefined>(undefined)

const { loading, records, pagination, search, refresh } = useAdminTable<Agent, AgentQueryRequest>(query =>
  searchAgents({
    ...query,
    name: keyword.value || undefined,
    statusList: statusFilter.value?.length ? statusFilter.value : undefined,
    typeList: typeFilter.value?.length ? typeFilter.value : undefined,
    ownerName: ownerName.value || undefined
  })
)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '智能体', key: 'name', width: 280 },
  { title: '类型', key: 'type', width: 80 },
  { title: '归属用户', key: 'owner', width: 150 },
  { title: '状态', key: 'status', width: 90 },
  { title: '公开', key: 'isPublic', width: 80 },
  { title: '更新时间', key: 'updateTime', width: 160 },
  { title: '', key: 'action', width: 150 }
]

/** bot-icon 配置解析：非法/缺失返回 null 回退头像 */
function iconConfig(record: Agent): BotIconConfig | null {
  return parseBotIcon(record.icon)
}

function handleSearch() {
  pagination.current = 1
  search()
}

function handleKeywordChange() {
  if (!keyword.value) handleSearch()
}

function handleTableChange(pager: { current?: number; pageSize?: number }) {
  pagination.current = pager.current ?? pagination.current
  pagination.pageSize = pager.pageSize ?? pagination.pageSize
  refresh()
}

const drawerOpen = ref(false)
const drawerMode = ref<'detail' | 'form'>('form')
const activeRecord = ref<Agent | null>(null)

function openDetail(record: Agent) {
  drawerMode.value = 'detail'
  activeRecord.value = record
  drawerOpen.value = true
}

function openEdit(record: Agent) {
  drawerMode.value = 'form'
  activeRecord.value = record
  drawerOpen.value = true
}

function openCreate() {
  drawerMode.value = 'form'
  activeRecord.value = null
  drawerOpen.value = true
}

async function handleDelete(id: number) {
  try {
    await deleteAgent(id)
    MioMessage.success('已删除')
    refresh()
  } catch {
    MioMessage.error('删除失败')
  }
}

async function handleStatusToggle(record: Agent, checked: boolean) {
  try {
    await updateAgent({ id: record.id, status: checked ? 1 : 2 })
    record.status = checked ? 1 : 2
    MioMessage.success(checked ? '已发布' : '已禁用')
  } catch {
    MioMessage.error('操作失败')
  }
}

handleSearch()
</script>

<style lang="scss" scoped>
.cell-owner {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.miobot-tag {
  margin-left: 6px;
  font-size: 11px;
  line-height: 16px;
}

.cell-agent {
  display: flex;
  align-items: center;
  gap: 10px;

  .cell-agent-text {
    min-width: 0;

    .adm-cell-muted {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>
