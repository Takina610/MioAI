<template>
  <div class="mcp-market">
    <div class="page-header">
      <h2>MCP广场</h2>
      <p class="desc">探索公开的MCP工具</p>
    </div>

    <a-table
      :columns="columns"
      :data-source="mcpList"
      :loading="loading"
      :pagination="{ pageSize: 10 }"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <div class="tool-name">
            <ToolOutlined class="tool-icon" />
            <span>{{ record.name }}</span>
          </div>
        </template>
        <template v-if="column.key === 'isPublic'">
          <a-tag :color="record.isPublic === 1 ? 'green' : 'orange'">
            {{ record.isPublic === 1 ? '公开' : '私有' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-button type="link" size="small" @click="viewTool(record)">查看详情</a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ToolOutlined } from '@ant-design/icons-vue'
import { getPublicMcpTools } from '@/api/mcpTool'

const loading = ref(false)
const mcpList = ref([])

const columns = [
  { title: '工具名称', key: 'name', dataIndex: 'name' },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '类型', dataIndex: 'type' },
  { title: '创建者', dataIndex: 'userName' },
  { title: '操作', key: 'action', width: 120 }
]

onMounted(() => {
  fetchMcpTools()
})

async function fetchMcpTools() {
  loading.value = true
  try {
    const res = await getPublicMcpTools()
    mcpList.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function viewTool(tool) {
  console.log('查看工具:', tool)
}
</script>

<style lang="scss" scoped>
.mcp-market {
  .page-header {
    margin-bottom: 24px;

    h2 {
      font-size: 24px;
      font-weight: 600;
      color: #202124;
      margin-bottom: 8px;
    }

    .desc {
      color: #5f6368;
      font-size: 14px;
    }
  }

  .tool-name {
    display: flex;
    align-items: center;
    gap: 8px;

    .tool-icon {
      color: #2aa1a9;
    }
  }
}
</style>
