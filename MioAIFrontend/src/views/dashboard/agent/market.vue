<template>
  <div class="dash-page">
    <PageHeader :title="`智能体广场 ${agentList.length}`" />

    <div class="dash-page-content">
      <div v-if="!loading && agentList.length === 0" class="dash-empty">
        <a-empty description="暂无公开智能体" />
      </div>

      <a-row v-else :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.id">
          <div class="dash-card agent-card" :class="{ actionable: !isOwn(agent) }">
            <div class="card-header">
              <div class="card-icon-wrapper plain">
                <BotIcon
                  v-if="agentIcon(agent)"
                  :shape="agentIcon(agent)!.shape"
                  :fill="agentIcon(agent)!.fill"
                  :size="48"
                  :live="false"
                  eye-color="#ffffff"
                />
                <img v-else-if="agent.avatar" :src="agent.avatar" alt="avatar" />
                <RobotOutlined v-else />
              </div>
            </div>
            <h3 class="card-title">{{ agent.name }}</h3>
            <p class="card-desc">{{ agent.description || '暂无描述' }}</p>
            <div class="card-footer">
              <span class="author">
                <UserOutlined /> {{ agent.userName || (agent.type === 0 ? '官方' : '匿名') }}
              </span>
              <span class="time">{{ formatDate(agent.createTime) }}</span>
            </div>
            <!-- 广场模板不可直接对话：先复制为我的智能体，再从我的智能体发起对话 -->
            <div v-if="!isOwn(agent)" class="card-actions" @click.stop>
              <a-button
                class="template-btn"
                type="primary"
                size="small"
                :loading="forkingId === agent.id"
                @click="useTemplate(agent)"
              >
                使用该模板
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
import { message } from 'ant-design-vue'
import { RobotOutlined, UserOutlined } from '@ant-design/icons-vue'
import { getPublicAgents, forkAgent } from '@/api/agent'
import { formatDate } from '@/utils/format'
import type { Agent, PageResponse } from '@/types'
import { useUserStore } from '@/store/user'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { parseBotIcon } from '@/components/bot-icon/types'
import PageHeader from '../components/PageHeader.vue'

interface AgentWithUser extends Agent {
  userName?: string
}

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const forkingId = ref<number | null>(null)
const agentList = ref<AgentWithUser[]>([])

function agentIcon(agent: Agent) {
  return parseBotIcon(agent.icon)
}

/** 自己发布的智能体（含自己发布的 MioBot）不提供模板复制 */
function isOwn(agent: Agent): boolean {
  return userStore.userInfo?.id != null && agent.userId === userStore.userInfo.id
}

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

/** 使用该模板：复制一份为我的草稿智能体，跳转编辑页微调后即可对话 */
async function useTemplate(agent: AgentWithUser): Promise<void> {  if (!userStore.isLoggedIn) {
    emit('login-required')
    return
  }
  if (forkingId.value) return
  try {
    forkingId.value = agent.id
    const newId = await forkAgent(agent.id)
    message.success('已创建副本，可微调后发布')
    router.push(`/dashboard/agent/${newId}`)
  } catch (e) {
    console.error(e)
    message.error('复制模板失败')
  } finally {
    forkingId.value = null
  }
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

  // 有模板操作（非本人）才可交互：悬浮按钮浮现、footer 淡出
  &.actionable {
    cursor: pointer;

    &:hover {
      box-shadow: $shadow-medium;

      .card-footer {
        opacity: 0;
      }
    }
  }

  // 只显示 bot-icon：去掉公共卡头的底色与圆角
  .card-icon-wrapper.plain {
    background: none;
    border-radius: 0;
    overflow: visible;

    img {
      border-radius: 0;
    }
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
    color: #8c8a82;
    margin-bottom: 12px;
    height: 36px;
    transition: opacity 0.3s;

    .author {
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }

  .card-actions {
    .template-btn {
      width: 100%;
      height: 36px;
      border-radius: 10px;
      font-size: 14px;
      font-weight: 600;
    }
  }
}
</style>
