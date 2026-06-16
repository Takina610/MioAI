<template>
  <div class="admin-page">
    <a-card title="知识库管理" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="knowledgeList"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleView(record)">查看</a-button>
              <a-button type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { TablePaginationConfig } from 'ant-design-vue'

interface KnowledgeItem {
  id: number
  name: string
  description: string
  createTime: string
}

const loading = ref(false)
const knowledgeList = ref<KnowledgeItem[]>([])

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150 }
]

const pagination: TablePaginationConfig = {
  pageSize: 10,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`
}

async function fetchKnowledgeList() {
  loading.value = true
  try {
    // TODO: 替换为实际的知识库列表 API
    // const res = await listKnowledgeBases()
    // knowledgeList.value = res
    message.info('知识库列表 API 待接入')
  } finally {
    loading.value = false
  }
}

function handleView(record: KnowledgeItem) {
  message.info(`查看知识库: ${record.name}`)
}

function handleDelete(record: KnowledgeItem) {
  message.info(`删除知识库: ${record.name}`)
}

onMounted(() => {
  fetchKnowledgeList()
})
</script>

<style lang="scss" scoped>
.admin-page {
  .ant-card {
    border-radius: 8px;
  }
}
</style>
