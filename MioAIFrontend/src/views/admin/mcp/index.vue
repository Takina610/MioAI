<template>
  <div class="adm-page">
    <div class="adm-page-header">
      <span class="adm-page-title">MCP</span>
      <a-button type="primary" @click="openCreate">新增 MCP</a-button>
    </div>

    <div class="adm-filter-bar">
      <a-input-search
        v-model:value="keyword"
        placeholder="MCP 名称"
        class="adm-filter-search"
        allow-clear
        @search="handleSearch"
        @change="handleKeywordChange"
      />
      <a-select v-model:value="statusFilter" mode="multiple" placeholder="全部状态" allow-clear class="adm-filter-item" @change="handleSearch">
        <a-select-option :value="0">未激活</a-select-option>
        <a-select-option :value="1">正常</a-select-option>
        <a-select-option :value="2">异常</a-select-option>
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
        :scroll="{ x: 900 }"
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
            <div class="adm-cell-primary">{{ record.name }}</div>
            <div class="adm-cell-muted">{{ record.description || '-' }}</div>
          </template>
          <template v-else-if="column.key === 'status'">
            <span v-if="record.status === 2" class="adm-dot is-danger">异常</span>
            <a-switch
              v-else
              :checked="record.status === 1"
              checked-children="正常"
              un-checked-children="未激活"
              @change="(checked: boolean) => handleStatusToggle(record, checked)"
            />
          </template>
          <template v-else-if="column.key === 'isPublic'">
            <span class="adm-dot" :class="record.isPublic === 1 ? 'is-success' : ''">
              {{ record.isPublic === 1 ? '公开' : '私有' }}
            </span>
          </template>
          <template v-else-if="column.key === 'owner'">
            <div class="cell-owner">
              <span>{{ record.userName || '-' }}</span>
              <span class="adm-cell-muted">#{{ record.userId }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'toolCount'">
            {{ toolCount(record.toolInfo) }}
          </template>
          <template v-else-if="column.key === 'updateTime'">
            <span class="adm-cell-muted">{{ formatDateTime(record.updateTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="0">
              <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="删除该 MCP？" ok-text="删除" cancel-text="取消" @confirm="handleDelete(record.id)">
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
import MioMessage from '@/utils/message'
import { formatDateTime } from '@/utils/format'
import { countMcpTools } from '@/utils/mcpTool'
import { useAdminTable } from '@/composables/admin/useAdminTable'
import { deleteMcpTool, searchMcpTools, updateMcpTool } from '@/api/adminMcp'
import type { McpTool, McpToolQueryRequest } from '@/types/mcpTool'
import RecordDrawer from './RecordDrawer.vue'

const keyword = ref('')
const ownerName = ref('')
const statusFilter = ref<number[] | undefined>(undefined)

const { loading, records, pagination, search, refresh } = useAdminTable<McpTool, McpToolQueryRequest>(query =>
  searchMcpTools({
    ...query,
    name: keyword.value || undefined,
    statusList: statusFilter.value?.length ? statusFilter.value : undefined,
    ownerName: ownerName.value || undefined
  })
)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: 'MCP', key: 'name', width: 240 },
  { title: '归属用户', key: 'owner', width: 150 },
  { title: '状态', key: 'status', width: 90 },
  { title: '公开', key: 'isPublic', width: 80 },
  { title: '工具数', key: 'toolCount', width: 80 },
  { title: '更新时间', key: 'updateTime', width: 160 },
  { title: '', key: 'action', width: 150 }
]

function toolCount(toolInfo?: string | null) {
  return toolInfo ? countMcpTools(toolInfo) : 0
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
const activeRecord = ref<McpTool | null>(null)

function openDetail(record: McpTool) {
  drawerMode.value = 'detail'
  activeRecord.value = record
  drawerOpen.value = true
}

function openEdit(record: McpTool) {
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
    await deleteMcpTool(id)
    MioMessage.success('已删除')
    refresh()
  } catch {
    MioMessage.error('删除失败')
  }
}

async function handleStatusToggle(record: McpTool, checked: boolean) {
  try {
    await updateMcpTool({ id: record.id, status: checked ? 1 : 0 })
    record.status = checked ? 1 : 0
    MioMessage.success(checked ? '已启用' : '已停用')
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
</style>
