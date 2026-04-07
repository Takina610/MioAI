<template>
  <div class="mcp-manage">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h2>MCP管理 {{ mcpList.length }}</h2>
        </div>
        <div class="header-right" v-if="userStore.isLoggedIn">
          <a-button type="primary" @click="showCreateModal">
            <PlusOutlined /> 添加MCP服务
          </a-button>
        </div>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content">
      <template v-if="!userStore.isLoggedIn">
        <div class="login-prompt">
          <p class="prompt-title">登录以使用</p>
          <p class="prompt-desc">您当前处于未登录状态，登录后可使用完整服务</p>
          <a-button type="primary" @click="$emit('login-required')">
            登录
          </a-button>
        </div>
      </template>

      <template v-else>
        <div class="mcp-list">
          <a-row :gutter="[16, 16]">
            <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="mcp in mcpList" :key="mcp.id">
              <div class="mcp-card" @click="goToDetail(mcp.id)">
                <div class="card-header">
                  <div class="icon-wrapper">
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
                  <a-button class="delete-btn" danger @click="handleDelete(mcp)">
                    <DeleteOutlined /> 删除
                  </a-button>
                </div>
              </div>
            </a-col>
          </a-row>

          <div class="empty-container" v-if="mcpList.length === 0">
            <img src="@/assets/mcp.png" alt="empty" class="empty-image" />
            <p class="empty-desc">创建MCP服务，即刻连接智能</p>
            <div class="empty-hint">前往右上角创建MCP服务</div>
          </div>
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

defineEmits<{
  (e: 'login-required'): void
}>()

const router = useRouter()
const userStore = useUserStore()
const loading = ref<boolean>(false)
const modalVisible = ref<boolean>(false)
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

function showCreateModal(): void {
  editingMcp.value = null
  modalVisible.value = true
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

function goToDetail(id: number): void {
  router.push(`/dashboard/mcp/${id}`)
}

onMounted(() => {
  fetchMcpTools()
})
</script>

<style lang="scss" scoped>
.mcp-manage {
  height: 100%;
  .page-header {
    .header-content {
      padding: 16px 24px;
      text-align: center;
      display: flex;
      justify-content: space-between;
      align-items: flex-start;

      .header-left {
        h2 {
          font-size: 24px;
          font-weight: 600;
          color: #202124;
          margin-bottom: 8px;
        }
      }

      .header-right {
        :deep(.ant-btn-primary) {
          background: $primary-color;
          border-color: $primary-color;

          &:hover {
            background: darken($primary-color, 10%);
            border-color: darken($primary-color, 10%);
          }
        }
      }
    }

    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .page-content {
    height: calc(100% - 132px);
    overflow-y: auto;
    padding: 0 24px;
  }

  .login-prompt {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 400px;
    text-align: center;

    .prompt-title {
      font-size: 20px;
      font-weight: 600;
      color: #202124;
      margin-bottom: 12px;
    }

    .prompt-desc {
      font-size: 14px;
      color: #5f6368;
      margin-bottom: 24px;
    }

    :deep(.ant-btn-primary) {
      background: $primary-color;
      border-color: $primary-color;

      &:hover {
        background: darken($primary-color, 10%);
        border-color: darken($primary-color, 10%);
      }
    }
  }

  .mcp-list {
    margin-top: 24px;
  }

  .empty-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 400px;
    text-align: center;

    .empty-image {
      width: 900px;
      height: auto;
      object-fit: contain;
    }

    .empty-desc {
      font-size: 16px;
      color: #666;
      margin-bottom: 8px;
    }

    .empty-hint {
      font-size: 14px;
      color: #999;
    }
  }

  .mcp-card {
    background: #fff;
    border-radius: 12px;
    padding: 20px;
    transition: all 0.3s;
    border: 1px solid #f0f0f0;
    cursor: pointer;
    position: relative;

    &:hover {
      border-color: $primary-color;
      box-shadow: $shadow-medium;

      .card-footer {
        opacity: 0;
      }

      .card-actions {
        opacity: 1;
        visibility: visible;
      }
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 16px;

      .icon-wrapper {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        background: rgba($primary-color, 0.1);
        display: flex;
        align-items: center;
        justify-content: center;

        .anticon {
          font-size: 24px;
          color: $primary-color;
        }
      }
    }

    .card-title {
      font-size: 16px;
      font-weight: 600;
      color: $text-dark;
      margin-bottom: 8px;
    }

    .card-desc {
      font-size: 13px;
      color: #666;
      margin-bottom: 16px;
      display: -webkit-box;
      line-clamp: 2;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .card-footer {
      display: flex;
      gap: 8px;
      transition: opacity 0.3s;
    }

    .card-actions {
      display: flex;
      gap: 8px;
      opacity: 0;
      visibility: hidden;
      transition: all 0.3s;
      position: absolute;
      bottom: 10px;
      left: 20px;
      right: 20px;

      .delete-btn {
        flex: 1;
        height: 36px;
        font-size: 14px;
        border-radius: 8px;
      }
    }
  }
}
</style>
