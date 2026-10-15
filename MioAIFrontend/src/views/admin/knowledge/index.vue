<template>
  <div class="adm-page">
    <div class="adm-page-header">
      <span class="adm-page-title">知识库</span>
      <a-button type="primary" @click="openCreate">新增知识库</a-button>
    </div>

    <div class="adm-filter-bar">
      <a-input-search
        v-model:value="keyword"
        placeholder="知识库名称"
        class="adm-filter-search"
        allow-clear
        @search="handleSearch"
        @change="handleKeywordChange"
      />
      <a-select v-model:value="statusFilter" mode="multiple" placeholder="全部状态" allow-clear class="adm-filter-item" @change="handleSearch">
        <a-select-option :value="1">正常</a-select-option>
        <a-select-option :value="2">禁用</a-select-option>
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
            <span v-if="record.status === 0" class="adm-dot is-pulse">创建中</span>
            <a-switch
              v-else
              :checked="record.status === 1"
              checked-children="正常"
              un-checked-children="禁用"
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
          <template v-else-if="column.key === 'documents'">
            {{ record.documentCount ?? 0 }}
          </template>
          <template v-else-if="column.key === 'chunk'">
            <span class="adm-cell-muted">{{ record.chunkSize ?? '-' }} / {{ record.chunkOverlap ?? '-' }}</span>
          </template>
          <template v-else-if="column.key === 'updateTime'">
            <span class="adm-cell-muted">{{ formatDateTime(record.updateTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="0">
              <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm
                title="删除该知识库？文档与向量数据将一并清理。"
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
import MioMessage from '@/utils/message'
import { formatDateTime } from '@/utils/format'
import { useAdminTable } from '@/composables/useAdminTable'
import { deleteKnowledgeBase, searchKnowledgeBases, updateKnowledgeBase } from '@/api/adminKnowledge'
import type { KnowledgeBase, KnowledgeBaseQueryRequest } from '@/types'
import RecordDrawer from './RecordDrawer.vue'

const keyword = ref('')
const ownerName = ref('')
const statusFilter = ref<number[] | undefined>(undefined)

const { loading, records, pagination, search, refresh } = useAdminTable<KnowledgeBase, KnowledgeBaseQueryRequest>(query =>
  searchKnowledgeBases({
    ...query,
    name: keyword.value || undefined,
    statusList: statusFilter.value?.length ? statusFilter.value : undefined,
    ownerName: ownerName.value || undefined
  })
)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '知识库', key: 'name', width: 240 },
  { title: '归属用户', key: 'owner', width: 150 },
  { title: '状态', key: 'status', width: 90 },
  { title: '公开', key: 'isPublic', width: 80 },
  { title: '文档数', key: 'documents', width: 80 },
  { title: '分块/重叠', key: 'chunk', width: 100 },
  { title: '更新时间', key: 'updateTime', width: 160 },
  { title: '', key: 'action', width: 150 }
]

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
const activeRecord = ref<KnowledgeBase | null>(null)

function openDetail(record: KnowledgeBase) {
  drawerMode.value = 'detail'
  activeRecord.value = record
  drawerOpen.value = true
}

function openEdit(record: KnowledgeBase) {
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
    await deleteKnowledgeBase(id)
    MioMessage.success('已删除')
    refresh()
  } catch {
    MioMessage.error('删除失败')
  }
}

async function handleStatusToggle(record: KnowledgeBase, checked: boolean) {
  try {
    await updateKnowledgeBase({ id: record.id, status: checked ? 1 : 2 })
    record.status = checked ? 1 : 2
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
