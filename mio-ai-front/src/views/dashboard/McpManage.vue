<template>
  <div class="mcp-manage">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h2>MCP管理</h2>
        </div>
        <div class="header-right" v-if="userStore.isLoggedIn">
          <a-button type="primary" @click="showCreateModal">
            <PlusOutlined /> 添加MCP工具
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
              <div class="mcp-card">
                <div class="card-header">
                  <div class="icon-wrapper">
                    <ToolOutlined />
                  </div>
                  <a-dropdown :trigger="['click']">
                    <a-button type="text" class="more-btn">
                      <MoreOutlined />
                    </a-button>
                    <template #overlay>
                      <a-menu>
                        <a-menu-item key="edit" @click="showEditModal(mcp)">
                          <EditOutlined /> 编辑
                        </a-menu-item>
                        <a-menu-item key="delete" @click="handleDelete(mcp)">
                          <DeleteOutlined /> 删除
                        </a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                </div>
                <h3 class="card-title">{{ mcp.name }}</h3>
                <p class="card-desc">{{ mcp.description || '暂无描述' }}</p>
                <div class="card-info">
                  <div class="info-item">
                    <span class="label">服务:</span>
                    <span class="value">{{ mcp.serverName }}</span>
                  </div>
                </div>
                <div class="card-footer">
                  <a-tag :color="mcp.status === 1 ? 'green' : 'default'">
                    {{ mcp.status === 1 ? '启用' : '禁用' }}
                  </a-tag>
                  <a-tag :color="mcp.isPublic === 1 ? 'blue' : 'default'">
                    {{ mcp.isPublic === 1 ? '公开' : '私有' }}
                  </a-tag>
                </div>
              </div>
            </a-col>
          </a-row>

          <a-empty v-if="!loading && mcpList.length === 0" description="暂无MCP工具" />
        </div>

        <a-modal
          :open="modalVisible"
          @update:open="modalVisible = $event"
          :title="editingMcp ? '编辑MCP工具' : '添加MCP工具'"
          :confirm-loading="submitLoading"
          @ok="handleSubmit"
          @cancel="resetForm"
          width="600px"
        >
          <a-form
            ref="formRef"
            :model="formData"
            :rules="rules"
            layout="vertical"
          >
            <a-form-item name="name" label="名称">
              <a-input :value="formData.name" @update:value="formData.name = $event" placeholder="请输入工具名称" />
            </a-form-item>
            <a-form-item name="description" label="描述">
              <a-textarea
                :value="formData.description"
                @update:value="formData.description = $event"
                placeholder="请输入描述"
                :rows="3"
              />
            </a-form-item>
            <a-form-item name="serverName" label="服务名称">
              <a-input :value="formData.serverName" @update:value="formData.serverName = $event" placeholder="请输入MCP服务名称" />
            </a-form-item>
            <a-form-item name="toolName" label="工具名称">
              <a-input :value="formData.toolName" @update:value="formData.toolName = $event" placeholder="请输入工具名称" />
            </a-form-item>
            <a-form-item name="inputSchema" label="输入Schema">
              <a-textarea
                :value="formData.inputSchema"
                @update:value="formData.inputSchema = $event"
                placeholder="请输入JSON格式的输入Schema"
                :rows="4"
              />
            </a-form-item>
            <a-form-item name="isPublic" label="是否公开">
              <a-switch :checked="formData.isPublic" @update:checked="formData.isPublic = $event" :checked-value="1" :un-checked-value="0" />
            </a-form-item>
          </a-form>
        </a-modal>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useUserStore } from '@/store/user'
import { addMcpTool, queryMcpTools, updateMcpTool, deleteMcpTool } from '@/api/mcpTool'
import type { McpTool, McpToolAddRequest, McpToolUpdateRequest, PageResponse } from '@/types'
import { PlusOutlined, MoreOutlined, EditOutlined, DeleteOutlined, ToolOutlined } from '@ant-design/icons-vue'

defineEmits<{
  (e: 'login-required'): void
}>()

interface FormData {
  id?: number
  name: string
  description: string
  serverName: string
  toolName: string
  inputSchema: string
  isPublic: number
}

const userStore = useUserStore()
const loading = ref<boolean>(false)
const submitLoading = ref<boolean>(false)
const modalVisible = ref<boolean>(false)
const editingMcp = ref<McpTool | null>(null)
const mcpList = ref<McpTool[]>([])
const formRef = ref<FormInstance | null>(null)

const formData = reactive<FormData>({
  name: '',
  description: '',
  serverName: '',
  toolName: '',
  inputSchema: '',
  isPublic: 0
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  serverName: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  toolName: [{ required: true, message: '请输入工具名称', trigger: 'blur' }]
}

async function fetchMcpTools(): Promise<void> {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const res: PageResponse<McpTool> = await queryMcpTools({ current: 1, pageSize: 100 })
    mcpList.value = res.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showCreateModal(): void {
  editingMcp.value = null
  resetForm()
  modalVisible.value = true
}

function showEditModal(mcp: McpTool): void {
  editingMcp.value = mcp
  Object.assign(formData, {
    id: mcp.id,
    name: mcp.name,
    description: mcp.description,
    serverName: mcp.serverName,
    toolName: (mcp as McpTool & { toolName?: string }).toolName,
    inputSchema: mcp.config,
    isPublic: mcp.isPublic
  })
  modalVisible.value = true
}

function resetForm(): void {
  formRef.value?.resetFields()
  Object.assign(formData, {
    name: '',
    description: '',
    serverName: '',
    toolName: '',
    inputSchema: '',
    isPublic: 0
  })
}

async function handleSubmit(): Promise<void> {
  try {
    await formRef.value?.validate()
    submitLoading.value = true
    
    if (editingMcp.value) {
      await updateMcpTool({ ...formData, id: editingMcp.value.id } as McpToolUpdateRequest)
      message.success('更新成功')
    } else {
      await addMcpTool(formData as McpToolAddRequest)
      message.success('添加成功')
    }
    
    modalVisible.value = false
    resetForm()
    fetchMcpTools()
  } catch (e) {
    console.error(e)
  } finally {
    submitLoading.value = false
  }
}

function handleDelete(mcp: McpTool): void {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除工具「${mcp.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    async onOk() {
      await deleteMcpTool(mcp.id)
      message.success('删除成功')
      fetchMcpTools()
    }
  })
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
    display: flex;
    justify-content: center;
    flex-direction: column;
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

  .mcp-card {
    background: #fff;
    border-radius: 12px;
    padding: 20px;
    transition: all 0.3s;
    border: 1px solid #f0f0f0;

    &:hover {
      transform: translateY(-4px);
      box-shadow: $shadow-medium;
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

      .more-btn {
        color: #999;

        &:hover {
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

    .card-info {
      margin-bottom: 16px;

      .info-item {
        font-size: 13px;
        color: #666;

        .label {
          color: #999;
          margin-right: 4px;
        }

        .value {
          color: $text-dark;
        }
      }
    }

    .card-footer {
      display: flex;
      gap: 8px;
    }
  }
}
</style>
