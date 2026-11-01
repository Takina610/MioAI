<template>
  <div class="adm-page">
    <div class="adm-page-header">
      <span class="adm-page-title">日志审计</span>
    </div>

    <a-tabs v-model:activeKey="activeType" class="logs-tabs">
      <a-tab-pane v-for="type in LOG_TYPE_ORDER" :key="type" :tab="LOG_TYPE_CONFIGS[type].label" />
    </a-tabs>

    <div class="adm-filter-bar">
      <template v-for="field in config.filters" :key="field.key">
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
          v-else-if="field.kind === 'number'"
          v-model:value="filters[field.key]"
          :placeholder="field.placeholder"
          class="adm-filter-item"
          :controls="false"
          @press-enter="handleSearch"
        />
        <a-date-picker
          v-else-if="field.kind === 'date'"
          v-model:value="filters[field.key]"
          :placeholder="field.placeholder"
          class="adm-filter-item"
          value-format="YYYY-MM-DD"
          @change="handleSearch"
        />
        <a-input-search
          v-else
          v-model:value="filters[field.key]"
          :placeholder="field.placeholder"
          :class="field.width === 'search' ? 'adm-filter-search' : 'adm-filter-item'"
          allow-clear
          @search="handleSearch"
          @change="handleKeywordChange(field.key)"
        />
      </template>
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
import { deleteLog, searchLogs } from '@/api/adminLog'
import type { AdminLogQueryRequest, AdminLogRow, AdminLogType } from '@/api/adminLog'
import RecordTable from '@/views/admin/components/RecordTable.vue'
import type { RecordRow } from '@/views/admin/components/recordTypes'
import { LOG_TYPE_CONFIGS, LOG_TYPE_ORDER } from './config'

const activeType = ref<AdminLogType>('usage')

const { loading, records, pagination, search, refresh } = useAdminTable<AdminLogRow, AdminLogQueryRequest>(query =>
  searchLogs(activeType.value, query)
)

const config = computed(() => LOG_TYPE_CONFIGS[activeType.value])

const filters = reactive<AdminLogQueryRequest>({})

function cleanFilters(): AdminLogQueryRequest {
  const payload: AdminLogQueryRequest = {}
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

function handleKeywordChange(key: keyof AdminLogQueryRequest) {
  if (!filters[key]) handleSearch()
}

function handleTypeChange() {
  for (const key of Object.keys(filters)) {
    delete filters[key as keyof AdminLogQueryRequest]
  }
  handleSearch()
}

watch(activeType, handleTypeChange)

function handleTableChange(pager: { current?: number; pageSize?: number }) {
  pagination.current = pager.current ?? pagination.current
  pagination.pageSize = pager.pageSize ?? pagination.pageSize
  refresh()
}

function rowKey(row: RecordRow): number {
  return (row.data as { id: number }).id
}

async function handleDelete(row: RecordRow) {
  try {
    await deleteLog(activeType.value, (row.data as { id: number }).id)
    MioMessage.success('已删除')
    refresh()
  } catch {
    MioMessage.error('删除失败')
  }
}

handleSearch()
</script>

<style lang="scss" scoped>
.logs-tabs {
  :deep(.ant-tabs-nav) {
    margin-bottom: 0;
  }
}
</style>
