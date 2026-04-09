<template>
  <div class="agent-manage">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h2>智能体管理 {{ agentList.length }}</h2>
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
              <div class="agent-card" @click="goToEdit(agent.id)">
                <div class="card-header">
                  <div class="header-left">
                    <a-avatar :size="40" :src="agent.avatar">
                      {{ agent.name?.charAt(0)?.toUpperCase() }}
                    </a-avatar>
                    <h3 class="card-title">{{ agent.name }}</h3>
                  </div>
                  <a-tag :color="getStatusColor(agent.status)" class="status-tag">
                    {{ getStatusName(agent.status) }}
                  </a-tag>
                </div>
                <p class="card-desc">{{ agent.description || '暂无描述' }}</p>
                <div class="card-footer">
                  <span class="update-time">更新于 {{ formatDateTime(agent.updateTime || agent.createTime) }}</span>
                </div>
                <div class="card-actions" @click.stop>
                  <a-button class="action-btn" @click="goToEdit(agent.id)">
                    <SettingOutlined /> 配置
                  </a-button>
                  <a-button class="action-btn primary-btn" @click="startChat(agent)">
                    <MessageOutlined /> 开始对话
                  </a-button>
                  <a-dropdown :trigger="['hover']" placement="bottomLeft">
                    <a-button class="action-btn more-btn">
                     <MoreOutlined />
                    </a-button>
                    <template #overlay>
                      <a-menu>
                        <a-menu-item key="edit" @click="showEditModal(agent.id)">
                          <EditOutlined /> 修改应用信息
                        </a-menu-item>
                        <a-menu-item key="delete" @click="handleDelete(agent)" danger>
                          <DeleteOutlined /> 删除应用
                        </a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
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

        <AgentCreateModal
          v-model:visible="createModalVisible"
          mode="create"
          @success="handleCreateSuccess"
        />

        <AgentCreateModal
          v-model:visible="editModalVisible"
          mode="edit"
          :agent-id="editingAgentId"
          @success="handleEditSuccess"
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
import { queryAgents, deleteAgent } from '@/api/agent'
import type { Agent, PageResponse } from '@/types'
import {
  PlusOutlined,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined,
  MessageOutlined,
  SettingOutlined
} from '@ant-design/icons-vue'
import AgentCreateModal from '@/components/AgentCreateModal.vue'

defineEmits<{
  (e: 'login-required'): void
}>()

const router = useRouter()
const userStore = useUserStore()
const loading = ref<boolean>(false)
const createModalVisible = ref<boolean>(false)
const editModalVisible = ref<boolean>(false)
const editingAgentId = ref<number | undefined>(undefined)
const agentList = ref<Agent[]>([])

function getStatusName(status: number): string {
  const statuses: Record<number, string> = { 0: '草稿', 1: '已发布' }
  return statuses[status] || '未知'
}

function getStatusColor(status: number): string {
  const colors: Record<number, string> = { 0: 'orange', 1: 'green' }
  return colors[status] || 'default'
}

function formatDateTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}/${month}/${day} ${hours}:${minutes}:${seconds}`
}

async function fetchAgents(): Promise<void> {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const res: PageResponse<Agent> = await queryAgents({ current: 1, pageSize: 100, type: 1 })
    agentList.value = res.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showCreateModal(): void {
  createModalVisible.value = true
}

function showEditModal(agentId: number): void {
  editingAgentId.value = agentId
  editModalVisible.value = true
}

function handleCreateSuccess(agentId?: number | void): void {
  if (agentId) {
    router.push(`/dashboard/agent/${agentId}`)
  }
}

function handleEditSuccess(): void {
  editModalVisible.value = false
  editingAgentId.value = undefined
  fetchAgents()
}

function goToEdit(agentId: number): void {
  router.push(`/dashboard/agent/${agentId}`)
}

function handleDelete(agent: Agent): void {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除智能体「${agent.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    okButtonProps: { danger: true },
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
    overflow-y: auto;
    padding: 0 24px;
  }

  .login-prompt {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: calc(100vh - 300px);
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
    min-height: calc(100vh - 300px);
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
      margin-bottom: 12px;

      .header-left {
        display: flex;
        align-items: center;
        gap: 12px;
        flex: 1;
        min-width: 0;
      }

      .card-title {
        font-size: 15px;
        font-weight: 600;
        color: $text-dark;
        margin: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        flex: 1;
      }

      .status-tag {
        flex-shrink: 0;
        margin-left: 8px;
      }
    }

    .card-desc {
      padding-bottom: 12px;
      font-size: 13px;
      color: #666;
      margin-bottom: 12px;
      display: -webkit-box;
      line-clamp: 2;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      text-overflow: ellipsis;
      border-bottom: 1px solid #f0f0f0;
    }

    .card-footer {
      display: flex;
      justify-content: flex-start;
      align-items: center;
      height: 36px;
      transition: opacity 0.3s;

      .update-time {
        font-size: 12px;
        color: #999;
      }
    }

    .card-actions {
      display: flex;
      gap: 8px;
      opacity: 0;
      visibility: hidden;
      transition: all 0.3s;
      position: absolute;
      bottom: 20px;
      left: 20px;
      right: 20px;
      height: 36px;

      .action-btn {
        flex: 1;
        height: 36px;
        font-size: 14px;
        gap: 4px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .primary-btn {
        background: $primary-color;
        border: 1px solid $primary-color;
        color: #fff;

        &:hover {
          background: darken($primary-color, 10%);
          border-color: darken($primary-color, 10%);
          color: #fff;
        }
      }

      .more-btn {
        flex: 0 0 36px;
        padding: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 20px;
        rotate: 90deg;
      }
    }
  }
}
</style>
