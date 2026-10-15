<template>
  <div class="log-record-panel">
    <div class="panel-filters">
      <a-input-number v-model:value="filters.userId" placeholder="用户 ID" class="adm-filter-item" :controls="false" @press-enter="reload" />
      <a-input-number v-model:value="filters.agentId" placeholder="智能体 ID" class="adm-filter-item" :controls="false" @press-enter="reload" />
      <a-input v-if="showConversation" v-model:value="filters.conversationId" placeholder="会话 ID" class="adm-filter-item" allow-clear @press-enter="reload" />
      <a-input v-if="showModel" v-model:value="filters.model" placeholder="模型" class="adm-filter-item" allow-clear @press-enter="reload" />
      <a-date-picker v-model:value="filters.startDate" placeholder="开始日期" value-format="YYYY-MM-DD" class="adm-filter-item" @change="reload" />
      <a-date-picker v-model:value="filters.endDate" placeholder="结束日期" value-format="YYYY-MM-DD" class="adm-filter-item" @change="reload" />
      <a-button @click="reload">查询</a-button>
    </div>

    <RecordTable
      :columns="config.columns"
      :rows="records"
      :loading="loading"
      :pagination="{ current: page, pageSize, total }"
      :expand="config.expand"
      deletable
      :row-key="rowKey"
      @change="handlePage"
      @delete="handleDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import MioMessage from '@/utils/message'
import { deleteLog, searchLogs } from '@/api/adminLog'
import type { AdminLogQueryRequest, AdminLogRow, AdminLogType } from '@/api/adminLog'
import RecordTable from '@/views/admin/components/RecordTable.vue'
import type { RecordRow } from '@/views/admin/components/recordTypes'
import { LOG_TYPE_CONFIGS } from '@/views/admin/logs/config'

const props = defineProps<{
  /** 日志类型：rag / skill / tool / usage / activity */
  logType: AdminLogType
  /** 固定过滤（如 kbId / skillId） */
  fixedQuery?: AdminLogQueryRequest
  /** 会话/模型筛选仅 rag/usage/tool 有意义 */
  showConversation?: boolean
  showModel?: boolean
}>()

const config = LOG_TYPE_CONFIGS[props.logType]
const loading = ref(false)
const records = ref<AdminLogRow[]>([])
const page = ref(1)
const pageSize = 10
const total = ref(0)

const filters = reactive<AdminLogQueryRequest>({
  userId: undefined,
  agentId: undefined,
  conversationId: undefined,
  model: undefined,
  startDate: undefined,
  endDate: undefined
})

function rowKey(row: RecordRow): number {
  return (row.data as { id: number }).id
}

async function reload() {
  loading.value = true
  try {
    const res = await searchLogs(props.logType, {
      current: page.value,
      pageSize,
      ...props.fixedQuery,
      userId: filters.userId || undefined,
      agentId: filters.agentId || undefined,
      conversationId: filters.conversationId || undefined,
      model: filters.model || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined
    })
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    MioMessage.error('获取记录失败')
  } finally {
    loading.value = false
  }
}

function handlePage(pager: { current?: number; pageSize?: number }) {
  page.value = pager.current ?? page.value
  reload()
}

async function handleDelete(row: RecordRow) {
  try {
    await deleteLog(props.logType, (row.data as { id: number }).id)
    MioMessage.success('已删除')
    reload()
  } catch {
    MioMessage.error('删除失败')
  }
}

watch(() => props.logType, reload)
onMounted(reload)

defineExpose({ reload })
</script>

<style lang="scss" scoped>
.panel-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
</style>
