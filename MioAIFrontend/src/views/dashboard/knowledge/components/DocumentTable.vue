<template>
  <a-table
    :columns="columns"
    :data-source="documents"
    :loading="loading"
    :pagination="pagination"
    row-key="id"
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
        <a-tag :color="getStatusColor(record.status)">
          {{ record.statusDesc }}
        </a-tag>
      </template>
      <template v-else-if="column.key === 'createTime'">
        {{ formatLocaleDateTime(record.createTime) }}
      </template>
      <template v-else-if="column.key === 'action'">
        <a-popconfirm
          title="确定要删除这个文档吗？"
          ok-text="确定"
          cancel-text="取消"
          @confirm="emit('delete', record.id)"
        >
          <a-button type="link" danger size="small">
            <DeleteOutlined /> 删除
          </a-button>
        </a-popconfirm>
      </template>
    </template>
  </a-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DeleteOutlined } from '@ant-design/icons-vue'
import type { Document } from '@/api/knowledgeBase'
import { formatFileSize, formatLocaleDateTime } from '@/utils/format'

const props = withDefaults(defineProps<{
  documents: Document[]
  loading?: boolean
  pagination?: object
  /** 是否显示删除操作列 */
  showActions?: boolean
}>(), {
  loading: false,
  pagination: () => ({}),
  showActions: false
})

const emit = defineEmits<{
  (e: 'change', pag: { current: number; pageSize: number }): void
  (e: 'preview', record: Document): void
  (e: 'delete', docId: number): void
}>()

const columns = computed(() => {
  const base = [
    { title: '文档名称', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
    { title: '文件类型', dataIndex: 'fileType', key: 'fileType', width: 100 },
    { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize', width: 120 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 }
  ]
  return props.showActions
    ? [...base, { title: '操作', key: 'action', width: 100 }]
    : base
})

function getStatusColor(status: number): string {
  const colors: Record<number, string> = {
    0: 'default',
    1: 'processing',
    2: 'success',
    3: 'error'
  }
  return colors[status] || 'default'
}
</script>

<style lang="scss" scoped>
:deep(.ant-table) {
  .ant-table-thead > tr > th {
    background: #fafafa;
    font-weight: 600;
    color: #5f6368;
  }
}

.file-name-btn {
  padding: 0;
  height: auto;
  font-size: 14px;
  color: $primary-color;

  &:hover {
    color: darken($primary-color, 10%);
  }
}
</style>
