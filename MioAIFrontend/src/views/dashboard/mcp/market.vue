<template>
  <div class="dash-page">
    <PageHeader :title="`MCP广场 ${mcpList.length}`" />

    <div class="dash-page-content">
      <div v-if="!loading && mcpList.length === 0" class="dash-empty">
        <a-empty description="暂无公开MCP工具" />
      </div>

      <a-row v-else :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="mcp in mcpList" :key="mcp.id">
          <div class="dash-card mcp-card" @click="goToDetail(mcp.id)">
            <div class="card-header">
              <div class="card-icon-wrapper">
                <ToolOutlined />
              </div>
            </div>
            <h3 class="card-title">{{ mcp.name }}</h3>
            <p class="card-desc">{{ mcp.description || '暂无描述' }}</p>
            <div class="card-footer">
              <span class="author">{{ mcp.userName || '匿名' }}</span>
              <a-tag :color="mcp.isPublic === 1 ? 'blue' : 'orange'">
                {{ mcp.isPublic === 1 ? '公开' : '私有' }}
              </a-tag>
            </div>
          </div>
        </a-col>
      </a-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ToolOutlined } from '@ant-design/icons-vue'
import { getPublicMcpTools } from '@/api/mcpTool'
import type { McpTool, PageResponse } from '@/types'
import PageHeader from '../components/PageHeader.vue'

interface McpToolWithUser extends McpTool {
  userName?: string
  type?: string
}

const router = useRouter()
const loading = ref(false)
const mcpList = ref<McpToolWithUser[]>([])

async function fetchMcpTools(): Promise<void> {
  loading.value = true
  try {
    const res: PageResponse<McpToolWithUser> = await getPublicMcpTools()
    mcpList.value = res?.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function goToDetail(id: number): void {
  router.push(`/dashboard/mcp-market/${id}`)
}

onMounted(() => {
  fetchMcpTools()
})
</script>

<style lang="scss" scoped>
.mcp-card {
  cursor: pointer;

  .card-header {
    margin-bottom: 16px;
  }

  .card-title {
    margin-bottom: 8px;
  }

  .card-desc {
    margin-bottom: 16px;
  }

  .card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .author {
      font-size: 12px;
      color: #8c8a82;
    }
  }
}
</style>
