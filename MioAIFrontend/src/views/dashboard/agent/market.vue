<template>
  <div class="dash-page">
    <PageHeader :title="`智能体广场 ${agentList.length}`" />

    <div class="dash-page-content">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.id">
          <div class="dash-card agent-card">
            <div class="card-header">
              <div class="card-icon-wrapper">
                <img v-if="agent.avatar" :src="agent.avatar" alt="avatar" />
                <RobotOutlined v-else />
              </div>
            </div>
            <h3 class="card-title">{{ agent.name }}</h3>
            <p class="card-desc">{{ agent.description || '暂无描述' }}</p>
            <div class="card-footer">
              <span class="author">
                <UserOutlined /> {{ agent.type === 0 ? '官方' : (agent.userName || '匿名') }}
              </span>
              <span class="time">{{ formatDate(agent.createTime) }}</span>
            </div>
            <div class="card-actions static">
              <a-button class="chat-btn" type="primary" size="small" @click="startChat(agent)">
                开始对话
              </a-button>
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
import { RobotOutlined, UserOutlined } from '@ant-design/icons-vue'
import { getPublicAgents } from '@/api/agent'
import { formatDate } from '@/utils/format'
import type { Agent, PageResponse } from '@/types'
import PageHeader from '../components/PageHeader.vue'

interface AgentWithUser extends Agent {
  userName?: string
}

const router = useRouter()
const loading = ref(false)
const agentList = ref<AgentWithUser[]>([])

async function fetchAgents(): Promise<void> {
  loading.value = true
  try {
    const res: PageResponse<AgentWithUser> = await getPublicAgents()
    agentList.value = res?.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function startChat(agent: AgentWithUser): void {
  router.push(`/chat/${agent.id}`)
}

onMounted(() => {
  fetchAgents()
})
</script>

<style lang="scss" scoped>
.agent-card {
  height: 100%;
  display: flex;
  flex-direction: column;

  &:hover {
    box-shadow: $shadow-medium;
  }

  .card-header {
    margin-bottom: 16px;
  }

  .card-title {
    margin-bottom: 8px;
  }

  .card-desc {
    margin-bottom: 16px;
    flex: 1;
  }

  .card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;
    color: #999;
    margin-bottom: 12px;

    .author {
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }

  .card-actions {
    &.static {
      position: static;
      opacity: 1;
      visibility: visible;
      height: auto;
      margin-top: auto;
    }

    .chat-btn {
      width: 100%;
      height: 35px;
      border-radius: 18px;
      font-size: 14px;
      font-weight: 600;
      color: #fff;
    }
  }
}
</style>
