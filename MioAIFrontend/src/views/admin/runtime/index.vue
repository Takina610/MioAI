<template>
  <div class="adm-page">
    <div class="adm-page-header">
      <span class="adm-page-title">运行数据</span>
    </div>

    <a-tabs v-model:activeKey="activeType" class="runtime-tabs">
      <a-tab-pane v-for="type in RUNTIME_TYPE_ORDER" :key="type" :tab="RUNTIME_TYPE_CONFIGS[type].label" />
    </a-tabs>

    <div class="adm-filter-bar">
      <template v-for="field in structuredFilters" :key="field.key">
        <a-select
          v-if="field.kind === 'select'"
          v-model:value="filters[field.key]"
          :placeholder="field.placeholder"
          allow-clear
          class="adm-filter-item"
          @change="handleSearch"
        >
          <a-select-option v-for="opt in field.options" :key="String(opt.value)" :value="opt.value">
            {{ opt.label }}
          </a-select-option>
        </a-select>
        <a-input-number
          v-else
          v-model:value="filters[field.key]"
          :placeholder="field.placeholder"
          class="adm-filter-item"
          :controls="false"
          @press-enter="handleSearch"
        />
      </template>
      <a-input-search
        v-if="hasKeyword"
        v-model:value="filters.keyword"
        placeholder="关键词"
        class="adm-filter-search"
        allow-clear
        @search="handleSearch"
        @change="handleKeywordChange"
      />
      <a-button type="primary" @click="handleSearch">查询</a-button>
    </div>

    <div class="adm-table-card">
      <RecordTable
        :columns="config.columns"
        :rows="records"
        :loading="loading"
        :pagination="pagination"
        :expand="config.expand"
        deletable
        :row-key="rowKey"
        @change="handleTableChange"
        @delete="handleDelete"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import MioMessage from '@/utils/message'
import { useAdminTable } from '@/composables/admin/useAdminTable'
import { deleteRuntime, searchRuntime } from '@/api/adminRuntime'
import type { AdminRuntimeQueryRequest, AdminRuntimeRow, AdminRuntimeType } from '@/api/adminRuntime'
import RecordTable from '@/views/admin/components/RecordTable.vue'
import type { RecordRow } from '@/views/admin/components/recordTypes'
import { RUNTIME_TYPE_CONFIGS, RUNTIME_TYPE_ORDER } from './config'

const activeType = ref<AdminRuntimeType>('group-members')

const { loading, records, pagination, search, refresh } = useAdminTable<AdminRuntimeRow, AdminRuntimeQueryRequest>(
  query => searchRuntime(activeType.value, query)
)

const config = computed(() => RUNTIME_TYPE_CONFIGS[activeType.value])
const hasKeyword = computed(() => config.value.filters.some(field => field.key === 'keyword'))
const structuredFilters = computed(() => config.value.filters.filter(field => field.kind !== 'keyword'))

const filters = reactive<AdminRuntimeQueryRequest>({})

function cleanFilters(): AdminRuntimeQueryRequest {
  const payload: AdminRuntimeQueryRequest = {}
  for (const field of config.value.filters) {
    const value = filters[field.key]
    if (value !== undefined && value !== null && value !== '') {
      payload[field.key] = value as never
    }
  }
  return payload
}

function handleSearch() {
  pagination.current = 1
  search(cleanFilters())
}

function handleKeywordChange() {
  if (!filters.keyword) handleSearch()
}

function handleTypeChange() {
  for (const key of Object.keys(filters)) {
    delete filters[key as keyof AdminRuntimeQueryRequest]
  }
  handleSearch()
}

watch(activeType, handleTypeChange)

function handleTableChange(pager: { current?: number; pageSize?: number }) {
  pagination.current = pager.current ?? pagination.current
  pagination.pageSize = pager.pageSize ?? pagination.pageSize
  refresh()
}

function rowKey(row: RecordRow): number | string {
  const data = row.data as { id?: number; taskId?: string }
  return data.taskId ?? data.id ?? 0
}

async function handleDelete(row: RecordRow) {
  const data = row.data as { id?: number; taskId?: string }
  const id = data.taskId ?? data.id
  if (id === undefined) return
  try {
    await deleteRuntime(activeType.value, id)
    MioMessage.success('已删除')
    refresh()
  } catch {
    MioMessage.error('删除失败')
  }
}

handleSearch()
</script>

<style lang="scss" scoped>
.runtime-tabs {
  :deep(.ant-tabs-nav) {
    margin-bottom: 0;
  }
}
</style>
