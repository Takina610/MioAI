<template>
  <div class="adm-page">
    <div class="adm-page-header">
      <span class="adm-page-title">用户</span>
      <a-button type="primary" @click="openCreate">新增用户</a-button>
    </div>

    <div class="adm-filter-bar">
      <a-input-search
        v-model:value="keyword"
        placeholder="用户名或账号"
        class="adm-filter-search"
        allow-clear
        @search="handleSearch"
        @change="handleKeywordChange"
      />
      <a-select v-model:value="roleFilter" placeholder="全部角色" allow-clear class="adm-filter-item" @change="handleSearch">
        <a-select-option value="admin">管理员</a-select-option>
        <a-select-option value="user">普通用户</a-select-option>
      </a-select>
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
          <template v-if="column.key === 'user'">
            <div class="cell-user">
              <a-avatar :src="record.userAvatar" :size="28">{{ record.userName?.charAt(0) || 'U' }}</a-avatar>
              <div class="cell-user-text">
                <div class="adm-cell-primary">{{ record.userName || '未设置昵称' }}</div>
                <div class="adm-cell-muted">@{{ record.userAccount }}</div>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'userRole'">
            <span class="adm-dot" :class="record.userRole === 'admin' ? 'is-success' : ''">
              {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
            </span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-switch
              :checked="record.status !== 0"
              checked-children="正常"
              un-checked-children="停用"
              @change="(checked: boolean) => handleStatusToggle(record, checked)"
            />
          </template>
          <template v-else-if="column.key === 'userProfile'">
            <span class="adm-cell-muted">{{ record.userProfile || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'createTime'">
            <span class="adm-cell-muted">{{ formatDate(record.createTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="0">
              <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-button type="link" size="small" @click="drillDown(record, 'agent')">智能体</a-button>
              <a-button type="link" size="small" @click="drillDown(record, 'conversation')">会话</a-button>
              <a-popconfirm title="删除该用户？" ok-text="删除" cancel-text="取消" @confirm="handleDelete(record.id)">
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
import { useRouter } from 'vue-router'
import MioMessage from '@/utils/message'

import { formatDate } from '@/utils/format'
import { useAdminTable } from '@/composables/useAdminTable'
import { deleteUser, searchUsers, updateUser } from '@/api/adminUser'
import type { UserAdminVO } from '@/api/adminUser'
import RecordDrawer from './RecordDrawer.vue'

const keyword = ref('')
const roleFilter = ref<string | undefined>(undefined)

const { loading, records, pagination, search, refresh } = useAdminTable<UserAdminVO, { current?: number; pageSize?: number; keyword?: string; userRole?: string }>(
  query => searchUsers({ ...query, keyword: keyword.value || undefined, userRole: roleFilter.value || undefined })
)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '用户', key: 'user', width: 200 },
  { title: '角色', key: 'userRole', width: 100 },
  { title: '状态', key: 'status', width: 90 },
  { title: '简介', key: 'userProfile', ellipsis: true },
  { title: '创建时间', key: 'createTime', width: 110 },
  { title: '', key: 'action', width: 190 }
]

/** 跨页下钻：跳到智能体/会话页并按该用户预过滤 */
function drillDown(record: UserAdminVO, target: string) {
  const ownerName = record.userName || ''
  if (target === 'agent') {
    router.push({ path: '/admin/agent', query: ownerName ? { ownerName } : {} })
  } else {
    router.push({ path: '/admin/conversation', query: { userId: String(record.id) } })
  }
}

const router = useRouter()

async function handleStatusToggle(record: UserAdminVO, checked: boolean) {
  try {
    await updateUser({ id: record.id, status: checked ? 1 : 0 })
    record.status = checked ? 1 : 0
    MioMessage.success(checked ? '已启用' : '已停用')
  } catch {
    MioMessage.error('操作失败')
  }
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
const activeRecord = ref<UserAdminVO | null>(null)

function openDetail(record: UserAdminVO) {
  drawerMode.value = 'detail'
  activeRecord.value = record
  drawerOpen.value = true
}

function openEdit(record: UserAdminVO) {
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
    await deleteUser(id)
    MioMessage.success('已删除')
    refresh()
  } catch {
    MioMessage.error('删除失败')
  }
}

handleSearch()
</script>

<style lang="scss" scoped>
.cell-user {
  display: flex;
  align-items: center;
  gap: 10px;

  .cell-user-text {
    min-width: 0;
  }
}
</style>
