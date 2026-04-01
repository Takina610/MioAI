<template>
  <div class="agent-market">
    <div class="page-header">
      <h2>应用广场</h2>
      <p class="desc">探索公开的智能体应用</p>
    </div>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.id">
        <a-card hoverable class="agent-card" @click="viewAgent(agent)">
          <template #cover>
            <div class="card-cover">
              <RobotOutlined class="cover-icon" />
            </div>
          </template>
          <a-card-meta :title="agent.name" :description="agent.description">
            <template #avatar>
              <a-avatar :style="{ backgroundColor: '#2aa1a9' }">
                {{ agent.name?.charAt(0)?.toUpperCase() }}
              </a-avatar>
            </template>
          </a-card-meta>
          <div class="card-footer">
            <span class="author">
              <UserOutlined /> {{ agent.userName || '匿名' }}
            </span>
            <span class="time">
              {{ formatTime(agent.createTime) }}
            </span>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-empty v-if="!loading && agentList.length === 0" description="暂无公开应用" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { RobotOutlined, UserOutlined } from '@ant-design/icons-vue'
import { getPublicAgents } from '@/api/agent'

const router = useRouter()
const loading = ref(false)
const agentList = ref([])

onMounted(() => {
  fetchAgents()
})

async function fetchAgents() {
  loading.value = true
  try {
    const res = await getPublicAgents()
    agentList.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function viewAgent(agent) {
  router.push(`/dashboard/agents/${agent.id}`)
}

function formatTime(time) {
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
    }
  }

  .agent-card {
    border-radius: 12px;
    transition: all 0.3s;

    &:hover {
      transform: translateY(-4px);
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
    }

    .card-cover {
      height: 120px;
      background: linear-gradient(135deg, #2aa1a9 0%, #263749 100%);
      display: flex;
      align-items: center;
      justify-content: center;

      .cover-icon {
        font-size: 48px;
        color: #fff;
      }
    }

    .card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid #f0f0f0;
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
