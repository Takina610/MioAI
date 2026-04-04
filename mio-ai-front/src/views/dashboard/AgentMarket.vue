<template>
  <div class="agent-market">
    <div class="page-header">
      <div class="header-content">
        <h2>智能体广场</h2>
        <p class="desc">探索公开的智能体</p>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content">
      <div class="agent-list">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.id">
            <div class="agent-card">
              <div class="card-header">
                <div class="icon-wrapper">
                  <img v-if="agent.avatar" :src="agent.avatar" alt="avatar" class="agent-avatar" />
                  <RobotOutlined v-else />
                </div>
              </div>
              <h3 class="card-title">{{ agent.name }}</h3>
              <p class="card-desc">{{ agent.description || '暂无描述' }}</p>
              <div class="card-footer">
                <span class="author">
                  <UserOutlined /> {{ agent.type === 0 ? '官方' : (agent.userName || '匿名') }}
                </span>
                <span class="time">{{ formatTime(agent.createTime) }}</span>
              </div>
              <div class="card-actions">
                <a-button class="chat-btn" type="primary" size="small" @click="startChat(agent)">
                  开始对话
                </a-button>
              </div>
            </div>
          </a-col>
        </a-row>
      </div>
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

function startChat(agent: AgentWithUser): void {
  router.push(`/chat/${agent.id}`)
}

function formatTime(time: string): string {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleDateString()
}
</script>

<style lang="scss" scoped>
.agent-market {
  height: 100%;
  .page-header {
    .header-content {
      padding: 10px 24px;
      h2 {
        font-size: 24px;
        font-weight: 600;
        color: #202124;
        margin-bottom: 8px;
      }

      .desc {
        color: #5f6368;
        font-size: 14px;
        margin-bottom: 0px;
      }
    }
    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .page-content {
    height: calc(100% - 136px);
    display: flex;
    .agent-list {
      margin: 24px;
    }
  }

  .agent-card {
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
      margin-bottom: 16px;

      .icon-wrapper {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        background: rgba($primary-color, 0.1);
        display: flex;
        align-items: center;
        justify-content: center;
        overflow: hidden;

        .anticon {
          font-size: 24px;
          color: $primary-color;
        }

        .agent-avatar {
          width: 100%;
          height: 100%;
          object-fit: cover;
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
      margin-bottom: 12px;

      .author {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }

    .card-actions {
      .chat-btn {
        width: 100%;
        height: 35px;
        border-radius: 18px;
        font-size: 14px;
        font-weight: 600;
        color: #fff;
      }

      :deep(.chat-btn) {
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
