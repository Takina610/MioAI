<template>
  <div class="dash-page">
    <PageHeader :title="`智能体广场 ${agentList.length}`" />

    <div class="dash-page-content">
      <div v-if="!loading && agentList.length === 0" class="dash-empty">
        <a-empty description="暂无公开智能体" />
      </div>

      <a-row v-else :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.id">
          <div
            class="dash-card agent-card"
            :class="{ actionable: !isOwn(agent), highlighted: agent.id === highlightedId }"
            :data-agent-id="agent.id"
          >
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
import { nextTick, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const forkingId = ref<number | null>(null)
const agentList = ref<AgentWithUser[]>([])
/** 分享页引导定位的智能体：滚动到卡片并高亮 5 秒 */
const highlightedId = ref<number | null>(null)

const PAGE_SIZE = 12

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
    const targetId = highlightTargetId()
    if (targetId != null) {
      await fetchUntil(targetId)
      await focusHighlighted(targetId)
    } else {
      const res: PageResponse<AgentWithUser> = await getPublicAgents()
      agentList.value = res?.records || []
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

/** 路由携带的定位目标（分享页"和 TA 继续聊"跳转广场） */
function highlightTargetId(): number | null {
  const raw = route.query.highlight
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(value) && value > 0 ? value : null
}

/** 目标不一定在第一页：逐页拉取合并（去重），找到或拉完为止 */
async function fetchUntil(targetId: number): Promise<void> {
  const known = new Set<number>()
  for (let page = 1; ; page++) {
    const res: PageResponse<AgentWithUser> = await getPublicAgents({ current: page, size: PAGE_SIZE })
    const records = res?.records ?? []
    for (const record of records) {
      if (!known.has(record.id)) {
        known.add(record.id)
        agentList.value.push(record)
      }
    }
    if (known.has(targetId) || records.length < PAGE_SIZE || page >= 50) return
  }
}

/** 定位目标卡片：滚动到可视区并高亮 5 秒；公开列表里没有则提示未公开 */
async function focusHighlighted(targetId: number): Promise<void> {
  if (!agentList.value.some(agent => agent.id === targetId)) {
    message.info('该智能体未被原作者公开')
    return
  }
  await nextTick()
  const card = document.querySelector(`[data-agent-id="${targetId}"]`)
  card?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  highlightedId.value = targetId
  window.setTimeout(() => {
    if (highlightedId.value === targetId) highlightedId.value = null
  }, 5000)
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

  // 分享页引导定位：主色描边+光晕呼吸动画，持续 5 秒（highlightedId 控制移除）
  &.highlighted {
    animation: agent-card-breathe 1.6s ease-in-out infinite;
  }

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

@keyframes agent-card-breathe {
  0%,
  100% {
    border-color: rgba($primary-color, 0.3);
    box-shadow: 0 0 0 2px rgba($primary-color, 0.07), $shadow-medium;
  }

  50% {
    border-color: rgba($primary-color, 0.7);
    box-shadow: 0 0 0 7px rgba($primary-color, 0.2), $shadow-medium;
  }
}
</style>
