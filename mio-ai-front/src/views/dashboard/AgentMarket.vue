<template>
  <div class="agent-market">
    <div class="page-header">
      <h2>应用广场</h2>
      <p class="desc">探索公开的智能体应用</p>
      <div class="header-line"></div>
    </div>

    <div class="agent-list">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.id">
          <div class="agent-card" @click="viewAgent(agent)">
            <div class="card-header">
              <div class="icon-wrapper">
                <RobotOutlined />
              </div>
            </div>
            <h3 class="card-title">{{ agent.name }}</h3>
            <p class="card-desc">{{ agent.description || '暂无描述' }}</p>
            <div class="card-footer">
              <span class="author">
                <UserOutlined /> {{ agent.userName || '匿名' }}
              </span>
              <span class="time">{{ formatTime(agent.createTime) }}</span>
            </div>
          </div>
        </a-col>
      </a-row>

      <a-empty v-if="!loading && agentList.length === 0" description="暂无公开应用" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { RobotOutlined, UserOutlined } from '@ant-design/icons-vue'
import { getPublicAgents } from '@/api/agent'
import type { Agent, PageResponse } from '@/types'

interface AgentWithUser extends Agent {
  userName?: string
}

const router = useRouter()
const loading = ref<boolean>(false)
const agentList = ref<AgentWithUser[]>([])

onMounted(() => {
  fetchAgents()
})

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

function viewAgent(agent: AgentWithUser): void {
  router.push(`/dashboard/agents/${agent.id}`)
}

function formatTime(time: string): string {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleDateString()
}
</script>

<style lang="scss" scoped>
.agent-market {
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
      margin-bottom: 16px;
    }

    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .agent-list {
    margin-top: 24px;
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
      justify-content: space-between;
      align-items: center;
      font-size: 12px;
      color: #999;

      .author {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }
  }
}
</style>
