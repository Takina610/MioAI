<template>
  <div class="agent-manage">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h2>智能体管理</h2>
        </div>
        <div class="header-right" v-if="userStore.isLoggedIn">
          <a-button type="primary" @click="showCreateModal">
            <PlusOutlined /> 创建智能体
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
        <div class="agent-list">
          <a-row :gutter="[16, 16]">
            <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.id">
              <div class="agent-card">
                <div class="card-header">
                  <a-avatar :size="48" :src="agent.avatar">
                    {{ agent.name?.charAt(0)?.toUpperCase() }}
                  </a-avatar>
                  <a-dropdown :trigger="['click']" @click.stop>
                    <a-button type="text" class="more-btn">
                      <MoreOutlined />
                    </a-button>
                    <template #overlay>
                      <a-menu>
                        <a-menu-item key="chat" @click="startChat(agent)">
                          <MessageOutlined /> 开始对话
                        </a-menu-item>
                        <a-menu-item key="edit" @click="showEditModal(agent)">
                          <EditOutlined /> 编辑
                        </a-menu-item>
                        <a-menu-item key="delete" @click="handleDelete(agent)">
                          <DeleteOutlined /> 删除
                        </a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                </div>
                <h3 class="card-title">{{ agent.name }}</h3>
                <p class="card-desc">{{ agent.description || '暂无描述' }}</p>
                <div class="card-footer">
                  <a-tag :color="getTypeColor(agent.type)">
                    {{ getTypeName(agent.type) }}
                  </a-tag>
                  <span class="create-time">{{ formatDate(agent.createTime) }}</span>
                </div>
                <div class="card-actions">
                  <a-button type="primary" size="small" @click="startChat(agent)">
                    开始对话
                  </a-button>
                </div>
              </div>
            </a-col>
          </a-row>
          
          <div class="empty-container" v-if="agentList.length === 0">
            <img src="@/assets/agent.png" alt="empty" class="empty-image" />
            <p class="empty-desc">你还没有智能体应用</p>
            <p class="empty-hint">前往右上角创建智能体应用</p>
          </div>
        </div>

        <a-modal
          :open="modalVisible"
          @update:open="modalVisible = $event"
          :title="editingAgent ? '编辑智能体' : '创建智能体'"
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
              <a-input :value="formData.name" @update:value="formData.name = $event" placeholder="请输入智能体名称" />
            </a-form-item>
            <a-form-item name="description" label="描述">
              <a-textarea
                :value="formData.description"
                @update:value="formData.description = $event"
                placeholder="请输入描述"
                :rows="3"
              />
            </a-form-item>
            <a-form-item name="type" label="类型">
              <a-select :value="formData.type" @update:value="formData.type = $event" placeholder="请选择类型">
                <a-select-option :value="0">内置智能体</a-select-option>
                <a-select-option :value="1">自定义智能体</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item name="systemPrompt" label="系统提示词">
              <a-textarea
                :value="formData.systemPrompt"
                @update:value="formData.systemPrompt = $event"
                placeholder="请输入系统提示词"
                :rows="5"
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
import { useRouter } from 'vue-router'
import { message, Modal, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useUserStore } from '@/store/user'
import { addAgent, queryAgents, updateAgent, deleteAgent } from '@/api/agent'
import type { Agent, AgentAddRequest, AgentUpdateRequest, PageResponse } from '@/types'
import {
  PlusOutlined,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined,
  MessageOutlined
} from '@ant-design/icons-vue'

defineEmits<{
  (e: 'login-required'): void
}>()

interface FormData {
  id?: number
  name: string
  description: string
  type: number
  systemPrompt: string
  isPublic: number
}

const router = useRouter()
const userStore = useUserStore()
const loading = ref<boolean>(false)
const submitLoading = ref<boolean>(false)
const modalVisible = ref<boolean>(false)
const editingAgent = ref<Agent | null>(null)
const agentList = ref<Agent[]>([])
const formRef = ref<FormInstance | null>(null)

const formData = reactive<FormData>({
  name: '',
  description: '',
  type: 1,
  systemPrompt: '',
  isPublic: 0
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function getTypeName(type: number): string {
  const types: Record<number, string> = { 0: '内置', 1: '自定义' }
  return types[type] || '未知'
}

function getTypeColor(type: number): string {
  const colors: Record<number, string> = { 0: 'blue', 1: 'green' }
  return colors[type] || 'default'
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

async function fetchAgents(): Promise<void> {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const res: PageResponse<Agent> = await queryAgents({ current: 1, pageSize: 100 })
    agentList.value = res.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showCreateModal(): void {
  editingAgent.value = null
  resetForm()
  modalVisible.value = true
}

function showEditModal(agent: Agent): void {
  editingAgent.value = agent
  Object.assign(formData, {
    id: agent.id,
    name: agent.name,
    description: agent.description,
    type: agent.type,
    systemPrompt: agent.systemPrompt,
    isPublic: agent.isPublic
  })
  modalVisible.value = true
}

function resetForm(): void {
  formRef.value?.resetFields()
  Object.assign(formData, {
    name: '',
    description: '',
    type: 1,
    systemPrompt: '',
    isPublic: 0
  })
}

async function handleSubmit(): Promise<void> {
  try {
    await formRef.value?.validate()
    submitLoading.value = true
    
    if (editingAgent.value) {
      await updateAgent({ ...formData, id: editingAgent.value.id } as AgentUpdateRequest)
      message.success('更新成功')
    } else {
      await addAgent(formData as AgentAddRequest)
      message.success('创建成功')
    }
    
    modalVisible.value = false
    resetForm()
    fetchAgents()
  } catch (e) {
    console.error(e)
  } finally {
    submitLoading.value = false
  }
}

function handleDelete(agent: Agent): void {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除智能体「${agent.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    async onOk() {
      await deleteAgent(agent.id)
      message.success('删除成功')
      fetchAgents()
    }
  })
}

function startChat(agent: Agent): void {
  router.push(`/chat/${agent.id}`)
}

onMounted(() => {
  fetchAgents()
})
</script>

<style lang="scss" scoped>
.agent-manage {
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

  .agent-list {
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
      width: 400px;
      height: auto;
      object-fit: contain;
      margin-bottom: -80px;
      position: relative;
      z-index: 1;
    }

    .empty-desc {
      font-size: 16px;
      color: #666;
      margin-bottom: 8px;
      position: relative;
      z-index: 1;
    }

    .empty-hint {
      font-size: 14px;
      color: #999;
      position: relative;
      z-index: 1;
    }
  }

  .agent-card {
    background: #fff;
    border-radius: 12px;
    padding: 20px;
    cursor: pointer;
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

    .card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .create-time {
        font-size: 12px;
        color: #999;
      }
    }

    .card-actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 12px;

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
}
</style>
