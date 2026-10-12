<template>
  <a-table
    :columns="columns"
    :data-source="documents"
    :loading="loading"
    :pagination="pagination"
    :scroll="{ x: 'max-content' }"
    row-key="id"
    class="doc-table"
    @change="emit('change', $event)"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'fileName'">
        <a-button type="link" class="file-name-btn" @click="emit('preview', record)">
          {{ record.fileName }}
        </a-button>
      </template>
      <template v-else-if="column.key === 'fileSize'">
        {{ formatFileSize(record.fileSize) }}
      </template>
      <template v-else-if="column.key === 'status'">
        <span class="status-cell" :class="`status-${record.status}`">
          <i class="status-dot" />
          {{ record.statusDesc }}
        </span>
      </template>
      <template v-else-if="column.key === 'createTime'">
        {{ formatLocaleDateTime(record.createTime) }}
      </template>
      <template v-else-if="column.key === 'action'">
        <div class="action-group">
          <a-button type="link" size="small" class="action-btn" @click="emit('chunks', record)">
            切片
          </a-button>
          <a-popconfirm
            title="重新解析该文档？"
            ok-text="解析"
            cancel-text="取消"
            @confirm="emit('revectorize', record)"
          >
            <a-button type="link" size="small" class="action-btn">
              重解析
            </a-button>
          </a-popconfirm>
          <a-popconfirm
            title="确定要删除这个文档吗？"
            ok-text="确定"
            cancel-text="取消"
            @confirm="emit('delete', record.id)"
          >
            <a-button type="link" size="small" class="action-btn danger">
              删除
            </a-button>
          </a-popconfirm>
        </div>
      </template>
    </template>
  </a-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Document } from '@/api/knowledgeBase'
import { formatFileSize, formatLocaleDateTime } from '@/utils/format'

const props = withDefaults(defineProps<{
  documents: Document[]
  loading?: boolean
  pagination?: object
  /** 是否显示操作列 */
  showActions?: boolean
}>(), {
  loading: false,
  pagination: () => ({}),
  showActions: false
})

const emit = defineEmits<{
  (e: 'change', pag: { current: number; pageSize: number }): void
  (e: 'preview', record: Document): void
  (e: 'chunks', record: Document): void
  (e: 'revectorize', record: Document): void
  (e: 'delete', docId: number): void
}>()

const columns = computed(() => {
  const base = [
    { title: '文档名称', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
    { title: '类型', dataIndex: 'fileType', key: 'fileType', width: 90 },
    { title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 100 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 }
  ]
  return props.showActions
    ? [...base, { title: '操作', key: 'action', width: 190 }]
    : base
})
</script>

<style lang="scss" scoped>
.doc-table {
  :deep(.ant-table) {
    background: transparent;
  }

  :deep(.ant-table-thead > tr > th) {
    background: transparent;
    border-bottom-color: $border-warm-subtle;
    font-size: 12px;
    font-weight: 500;
    color: $text-muted;

    &::before {
      display: none;
    }
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom-color: $border-warm-subtle;
  }

  :deep(.ant-table-tbody > tr:hover > td) {
    background: #f5f3ec;
  }

  :deep(.ant-pagination) {
    .ant-pagination-item-active {
      border-color: $primary-color;

      a {
        color: $primary-color;
      }
    }
  }
}

.file-name-btn {
  padding: 0;
  height: auto;
  font-size: 14px;
  color: $text-dark;
  font-weight: 500;

  &:hover {
    color: $primary-color;
  }
}

.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: $text-secondary;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: $border-warm;
  }

  &.status-1 .status-dot {
    background: $primary-color;
    animation: status-pulse 1.6s ease-in-out infinite;
  }

  &.status-2 .status-dot {
    background: $primary-color;
  }

  &.status-3 .status-dot {
    background: $danger-color;
  }
}

@keyframes status-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba($primary-color, 0.35);
  }

  50% {
    box-shadow: 0 0 0 4px rgba($primary-color, 0);
  }
}

.action-group {
  display: flex;
  align-items: center;
  gap: 2px;

  .action-btn {
    padding: 0 6px;
    color: $text-secondary;

    &:hover {
      color: $primary-color;
    }

    &.danger:hover {
      color: $danger-color;
    }
  }
}
</style>
