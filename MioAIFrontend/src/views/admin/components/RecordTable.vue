<template>
  <a-table
    :data-source="rows"
    :columns="tableColumns"
    :row-key="rowKey"
    size="middle"
    :loading="loading"
    :scroll="{ x: 960 }"
    :expand-column-width="48"
    :pagination="{
      current: pagination.current,
      pageSize: pagination.pageSize,
      total: pagination.total,
      showSizeChanger: true,
      showTotal: (total: number) => `共 ${total} 条`
    }"
    @change="handleChange"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'action'">
        <a-popconfirm
          title="删除这条记录？"
          ok-text="删除"
          cancel-text="取消"
          @confirm="$emit('delete', record)"
        >
          <a-button type="link" size="small" danger>删除</a-button>
        </a-popconfirm>
      </template>
      <template v-else-if="columnKind(column) === 'user'">
        <div class="cell-entity">
          <a-avatar :src="record.userAvatar || undefined" :size="22">{{ (record.userName ?? '?').charAt(0) }}</a-avatar>
          <span>{{ record.userName || record.data?.userId || '-' }}</span>
        </div>
      </template>
      <template v-else-if="columnKind(column) === 'agent'">
        <div class="cell-entity">
          <a-avatar :src="record.agentAvatar || undefined" :size="22">{{ (record.agentName ?? 'A').charAt(0) }}</a-avatar>
          <span>{{ record.agentName || record.data?.agentId || '-' }}</span>
        </div>
      </template>
      <template v-else-if="columnKind(column) === 'dot'">
        <span class="adm-dot" :class="dotClass(record, column)">
          {{ dotLabel(record, column) }}
        </span>
      </template>
      <template v-else>
        <span :class="columnKind(column) === 'code' ? 'cell-mono' : undefined">
          {{ cellText(record, column) }}
        </span>
      </template>
    </template>
    <template v-if="expand?.length" #expandedRowRender="{ record }">
      <div class="expand-list">
        <div v-for="field in expand" :key="field.title" class="expand-item">
          <span class="expand-title">{{ field.title }}</span>
          <pre v-if="field.mono" class="expand-mono">{{ expandText(record, field.path) }}</pre>
          <span v-else class="expand-text">{{ expandText(record, field.path) ?? '-' }}</span>
        </div>
      </div>
    </template>
  </a-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ColumnKind, ExpandField, LogColumn, RecordRow } from './recordTypes'
import { formatDateTime, truncateText } from '@/utils/format'

const props = defineProps<{
  columns: LogColumn[]
  rows: RecordRow[]
  loading: boolean
  pagination: { current: number; pageSize: number; total: number }
  rowKey: (row: RecordRow) => string | number
  expand?: ExpandField[]
  deletable?: boolean
}>()

const emit = defineEmits<{
  (e: 'change', pager: { current?: number; pageSize?: number }): void
  (e: 'delete', row: RecordRow): void
}>()

const tableColumns = computed(() => {
  const cols: { key: string; title: string; width?: number; ellipsis?: boolean }[] = props.columns.map(col => ({
    key: col.key,
    title: col.title,
    width: col.width,
    ellipsis: col.ellipsis
  }))
  if (props.deletable) {
    cols.push({ key: 'action', title: '', width: 64 })
  }
  return cols
})

function columnKind(column: { key: string | number }): ColumnKind | undefined {
  return props.columns.find(col => col.key === String(column.key))?.kind
}

function columnDef(column: { key: string | number }): LogColumn | undefined {
  return props.columns.find(col => col.key === String(column.key))
}

function getByPath(source: unknown, path?: string[]): unknown {
  if (!path || path.length === 0) return undefined
  let current: unknown = source
  for (const segment of path) {
    if (current === null || current === undefined) return undefined
    current = (current as Record<string, unknown>)[segment]
  }
  return current
}

function cellText(record: RecordRow, column: { key: string | number }): string {
  const def = columnDef(column)
  const raw = getByPath(record, def?.path)
  if (raw === null || raw === undefined || raw === '') return '-'
  switch (def?.format) {
    case 'datetime':
      return formatDateTime(String(raw))
    case 'duration-ms':
      return raw === 0 ? '0ms' : `${raw}ms`
    case 'duration-s':
      return `${((Number(raw) ?? 0) / 1000).toFixed(1)}s`
    case 'number':
      return String(raw)
    default:
      return truncateText(String(raw), 120)
  }
}

function dotDef(record: RecordRow, column: { key: string | number }) {
  const def = columnDef(column)
  const raw = getByPath(record, def?.path)
  return def?.dotLabels?.[String(raw)]
}

function dotLabel(record: RecordRow, column: { key: string | number }) {
  return dotDef(record, column)?.label ?? String(getByPath(record, columnDef(column)?.path) ?? '-')
}

function dotClass(record: RecordRow, column: { key: string | number }) {
  const tone = dotDef(record, column)?.tone
  if (tone === 'success') return 'is-success'
  if (tone === 'danger') return 'is-danger'
  if (tone === 'warn') return 'is-pulse'
  return undefined
}

function expandText(record: RecordRow, path: string[]): string | null {
  const raw = getByPath(record, path)
  if (raw === null || raw === undefined || raw === '') return null
  return String(raw)
}

function handleChange(pager: { current?: number; pageSize?: number }) {
  emit('change', pager)
}
</script>

<style lang="scss" scoped>
.cell-entity {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $text-secondary;
  font-size: 13px;
}

.cell-mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.expand-list {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .expand-item {
    display: flex;
    gap: 10px;

    .expand-title {
      flex-shrink: 0;
      width: 76px;
      font-size: 12px;
      color: $text-muted;
      padding-top: 2px;
    }

    .expand-text {
      font-size: 13px;
      color: $text-secondary;
      word-break: break-all;
    }

    .expand-mono {
      flex: 1;
      margin: 0;
      font-size: 12px;
      line-height: 1.6;
      color: $text-secondary;
      background: rgba(42, 161, 169, 0.05);
      border-radius: 6px;
      padding: 8px 10px;
      max-height: 200px;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-all;
    }
  }
}
</style>
