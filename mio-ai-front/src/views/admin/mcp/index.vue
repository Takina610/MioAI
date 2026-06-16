<template>
  <div class="admin-page">
    <a-card title="MCP 管理" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="mcpList"
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

interface McpItem {
  id: number
  name: string
  description: string
  endpoint: string
  createTime: string
}

const loading = ref(false)
const mcpList = ref<McpItem[]>([])

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '服务端点', dataIndex: 'endpoint', key: 'endpoint', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150 }
]

const pagination: TablePaginationConfig = {
  pageSize: 10,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`
}

async function fetchMcpList() {
  loading.value = true
  try {
    // TODO: 替换为实际的 MCP 列表 API
    // const res = await listMcps()
    // mcpList.value = res
    message.info('MCP 列表 API 待接入')
  } finally {
    loading.value = false
  }
}

function handleView(record: McpItem) {
  message.info(`查看 MCP: ${record.name}`)
}

function handleDelete(record: McpItem) {
  message.info(`删除 MCP: ${record.name}`)
}

onMounted(() => {
  fetchMcpList()
})
</script>

<style lang="scss" scoped>
.admin-page {
  .ant-card {
    border-radius: 8px;
  }
}
</style>
