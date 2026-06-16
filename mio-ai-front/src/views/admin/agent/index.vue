<template>
  <div class="admin-page">
    <a-card title="智能体管理" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="agentList"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'avatar'">
            <a-avatar :src="record.avatar" :size="40">
              {{ record.name?.charAt(0) || 'A' }}
            </a-avatar>
          </template>
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

interface AgentItem {
  id: number
  name: string
  description: string
  avatar: string
  systemPrompt: string
  createTime: string
}

const loading = ref(false)
const agentList = ref<AgentItem[]>([])

const columns = [
  { title: '头像', key: 'avatar', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '系统提示词', dataIndex: 'systemPrompt', key: 'systemPrompt', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150 }
]

const pagination: TablePaginationConfig = {
  pageSize: 10,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`
}

async function fetchAgentList() {
  loading.value = true
  try {
    // TODO: 替换为实际的智能体列表 API
    // const res = await listAgents()
    // agentList.value = res
    message.info('智能体列表 API 待接入')
  } finally {
    loading.value = false
  }
}

function handleView(record: AgentItem) {
  message.info(`查看智能体: ${record.name}`)
}

function handleDelete(record: AgentItem) {
  message.info(`删除智能体: ${record.name}`)
}

onMounted(() => {
  fetchAgentList()
})
</script>

<style lang="scss" scoped>
.admin-page {
  .ant-card {
    border-radius: 8px;
  }
}
</style>
