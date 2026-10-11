<template>
  <div class="dash-page">
    <PageHeader :title="`智能体管理 ${agentList.length}`">
      <template #actions>
        <a-button v-if="userStore.isLoggedIn" type="primary" @click="router.push('/dashboard/agent/create')">
          <PlusOutlined /> 创建智能体
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content">
      <LoginPrompt v-if="!userStore.isLoggedIn" @login="emit('login-required')" />

      <template v-else>
        <a-row v-if="agentList.length > 0" :gutter="[16, 16]">
          <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.id">
            <div class="dash-card agent-card" @click="goToEdit(agent.id)">
              <div class="card-header">
                <div class="header-left">
                  <BotIcon
                    v-if="agentIcon(agent)"
                    :shape="agentIcon(agent)!.shape"
                    :fill="agentIcon(agent)!.fill"
                    :size="36"
                    :live="false"
                    eye-color="#ffffff"
                  />
                  <a-avatar v-else :size="40" :src="agent.avatar">
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

        <div v-else class="dash-empty">
          <a-empty description="你还没有智能体应用" />
        </div>

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
import { formatDateTime } from '@/utils/format'
import type { Agent, PageResponse } from '@/types'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { parseBotIcon } from '@/components/bot-icon/types'
import {
  PlusOutlined,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined,
  MessageOutlined,
  SettingOutlined
} from '@ant-design/icons-vue'
import AgentCreateModal from '@/components/AgentCreateModal.vue'
import PageHeader from '../components/PageHeader.vue'
import LoginPrompt from '../components/LoginPrompt.vue'

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const editModalVisible = ref(false)
const editingAgentId = ref<number | undefined>(undefined)
const agentList = ref<Agent[]>([])

function agentIcon(agent: Agent) {
  return parseBotIcon(agent.icon)
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

function handleEditSuccess(): void {
  editModalVisible.value = false
  editingAgentId.value = undefined
  fetchAgents()
}

function goToEdit(agentId: number): void {
  router.push(`/dashboard/agent/${agentId}`)
}

function startChat(agent: Agent): void {
  router.push(`/chat/${agent.id}`)
}

function getStatusName(status: number): string {
  const statuses: Record<number, string> = { 0: '草稿', 1: '已发布' }
  return statuses[status] || '未知'
}

function getStatusColor(status: number): string {
  const colors: Record<number, string> = { 0: 'orange', 1: 'green' }
  return colors[status] || 'default'
}

function showEditModal(agentId: number): void {
  editingAgentId.value = agentId
  editModalVisible.value = true
}

onMounted(() => {
  fetchAgents()
})
</script>

<style lang="scss" scoped>
.agent-card {
  cursor: pointer;

  &:hover .card-footer {
    opacity: 0;
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
    margin-bottom: 12px;
    border-bottom: 1px solid #ece9de;
  }

  .card-footer {
    display: flex;
    align-items: center;
    height: 36px;
    transition: opacity 0.3s;

    .update-time {
      font-size: 12px;
      color: #8c8a82;
    }
  }

  .more-btn {
    flex: 0 0 36px;
    padding: 0;
    font-size: 20px;
    rotate: 90deg;
  }
}
</style>
