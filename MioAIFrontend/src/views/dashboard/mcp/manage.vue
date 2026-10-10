<template>
  <div class="dash-page">
    <PageHeader :title="`MCP管理 ${mcpList.length}`">
      <template #actions>
        <a-button v-if="userStore.isLoggedIn" type="primary" @click="showCreateModal">
          <PlusOutlined /> 添加MCP服务
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content">
      <LoginPrompt v-if="!userStore.isLoggedIn" @login="emit('login-required')" />

      <template v-else>
        <a-row v-if="mcpList.length > 0" :gutter="[16, 16]">
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
                <a-tag :color="mcp.status === 1 ? 'green' : 'default'">
                  {{ mcp.status === 1 ? '启用' : '禁用' }}
                </a-tag>
                <a-tag :color="mcp.isPublic === 1 ? 'blue' : 'default'">
                  {{ mcp.isPublic === 1 ? '公开' : '私有' }}
                </a-tag>
              </div>
              <div class="card-actions" @click.stop>
                <a-button class="action-btn danger-btn" danger @click="handleDelete(mcp)">
                  <DeleteOutlined /> 删除
                </a-button>
              </div>
            </div>
          </a-col>
        </a-row>

        <div v-else class="dash-empty">
          <a-empty description="暂无MCP服务" />
        </div>

        <McpCreateModal
          v-model:visible="modalVisible"
          :editing-mcp="editingMcp"
          @submit="handleSubmit"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import { addMcpTool, queryMcpTools, updateMcpTool, deleteMcpTool } from '@/api/mcpTool'
import type { McpTool, McpToolAddRequest, PageResponse } from '@/types'
import { PlusOutlined, ToolOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import McpCreateModal from '@/components/McpCreateModal.vue'
import PageHeader from '../components/PageHeader.vue'
import LoginPrompt from '../components/LoginPrompt.vue'

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const modalVisible = ref(false)
const editingMcp = ref<McpTool | null>(null)
const mcpList = ref<McpTool[]>([])

async function fetchMcpTools(): Promise<void> {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const res: PageResponse<McpTool> = await queryMcpTools(
      { current: 1, pageSize: 100, userId: userStore.userInfo?.id })
    mcpList.value = res.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function handleSubmit(data: McpToolAddRequest): Promise<void> {
  try {
    if (editingMcp.value) {
      await updateMcpTool({ ...data, id: editingMcp.value.id })
      message.success('更新成功')
    } else {
      await addMcpTool(data)
      message.success('添加成功')
    }

    modalVisible.value = false
    editingMcp.value = null
    fetchMcpTools()
  } catch (e) {
    console.error(e)
    message.error('操作失败')
  }
}

function handleDelete(mcp: McpTool): void {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除工具「${mcp.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    okButtonProps: { danger: true },
    async onOk() {
      await deleteMcpTool(mcp.id)
      message.success('删除成功')
      fetchMcpTools()
    }
  })
}

function showCreateModal(): void {
  editingMcp.value = null
  modalVisible.value = true
}

function goToDetail(id: number): void {
  router.push(`/dashboard/mcp/${id}`)
}

onMounted(() => {
  fetchMcpTools()
})
</script>

<style lang="scss" scoped>
.mcp-card {
  cursor: pointer;

  &:hover .card-footer {
    opacity: 0;
  }

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
    gap: 8px;
    transition: opacity 0.3s;
  }

  .card-actions {
    bottom: 10px;
  }
}
</style>
