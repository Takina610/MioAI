<template>
  <div class="share-page">
    <div class="share-container">
      <div class="conversation-card">
        <div class="card-header">
          <h1 class="conversation-title">{{ conversationTitle || '分享对话' }}</h1>
          <p class="conversation-meta">
            {{ formattedDate }} · 内容由 AI 生成，不能完全保障真实
          </p>
        </div>

        <div class="card-divider"></div>

        <div class="card-content">
          <!-- 与聊天界面同构的消息渲染（块级内容/思考/工具/引用全保留），只读无编辑 -->
          <ChatMessageList
            v-if="messages.length > 0"
            :messages="messages"
            :is-loading="false"
            :can-modify="false"
          />

          <div class="conversation-loading" v-if="loading">
            <a-spin size="large" />
            <p>加载中...</p>
          </div>
        </div>
      </div>

      <div class="action-buttons">
        <button class="text-btn" @click="goToChat">
          <span class="text">{{ ctaText }}</span>
        </button>
      </div>

      <div class="footer-section">
        <div class="agent-identity">
          <AgentFace :agent="agentInfo" :size="22" />
          <span class="agent-name">{{ agentInfo?.name || 'MioBot' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getSharedConversation, type SharedAgentBrief } from '@/api/chatMemory'
import type { ChatMessage, MessageBlock } from '@/types'
import AgentFace from '@/components/AgentFace.vue'
import ChatMessageList from '@/views/chat/components/ChatMessageList.vue'
import { textOfBlocks } from '@/views/chat/composables/useChatMessages'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref<boolean>(true)
const conversationTitle = ref<string>('')
const messages = ref<ChatMessage[]>([])
const agentInfo = ref<SharedAgentBrief | null>(null)
const conversationDate = ref<Date>(new Date())
const agentId = ref<number>(0)

watch(
  () => [route.params.agentId, route.params.conversationId],
  ([agentIdParam, conversationId]) => {
    if (agentIdParam && conversationId && typeof agentIdParam === 'string' && typeof conversationId === 'string') {
      loadShareData(agentIdParam, conversationId)
    }
  },
  { immediate: true }
)

const formattedDate = computed(() => {
  const d = conversationDate.value
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year} 年 ${month} 月 ${day} 日`
})

async function loadShareData(agentIdParam: string, conversationId: string): Promise<void> {
  loading.value = true
  try {
    agentId.value = Number(agentIdParam)

    const shared = await getSharedConversation(agentId.value, conversationId)
    const rows = shared?.messages ?? []

    if (rows.length === 0) {
      router.push('/404')
      return
    }

    // 智能体展示信息随分享数据下发（私有智能体游客读不到 /agents/{id}，不单独请求）
    agentInfo.value = shared.agent ?? null

    if (shared.title) {
      conversationTitle.value = shared.title
      document.title = `${shared.title} - MioAI`
    }
    if (shared.createTime) {
      conversationDate.value = new Date(shared.createTime)
    }

    messages.value = rows.map((row, index) => {
      const blocks = (row.blocks ?? undefined) as MessageBlock[] | undefined
      return {
        id: `${conversationId}_${row.seq ?? index}`,
        role: row.role === 'user' ? 'user' : 'assistant',
        content: textOfBlocks(blocks ?? []),
        createTime: row.createTime ? new Date(row.createTime) : new Date(),
        durationMs: row.durationMs,
        blocks
      } as ChatMessage
    })

    if (!conversationTitle.value) {
      const firstUser = rows.find(r => r.role === 'user')
      const firstUserContent = firstUser ? textOfBlocks((firstUser.blocks ?? []) as MessageBlock[]) : ''
      if (firstUserContent) {
        conversationTitle.value = firstUserContent.slice(0, 30) + (firstUserContent.length > 30 ? '...' : '')
        document.title = `${conversationTitle.value} - MioAI`
      }
    }
  } catch (e) {
    console.error('Failed to load share data:', e)
    message.error('加载分享内容失败')
  } finally {
    loading.value = false
  }
}

/** 按钮文案与去向按登录态/归属分流：
 *  未登录 = 快速开始（与首页同款入口）；别人的智能体 = 广场定位；自己的/内置 = 直接继续聊 */
const ctaText = computed<string>(() =>
  userStore.isLoggedIn ? `和 ${agentInfo.value?.name || 'MioBot'} 继续聊` : '快速开始'
)

function goToChat(): void {
  if (!userStore.isLoggedIn) {
    router.push('/chat')
    return
  }
  const ownerId = agentInfo.value?.userId
  if (ownerId != null && ownerId !== userStore.userInfo?.id) {
    router.push({ path: '/dashboard/agent-market', query: { highlight: String(agentId.value) } })
    return
  }
  router.push(`/chat/${agentId.value || 1}`)
}
</script>

<style lang="scss" scoped>
.share-page {
  // 原背景图 bg.jpg 未随仓库提交，构建时报 ENOENT；先回退为纯色背景
  background: #f0eee6;
  min-height: 100vh;
  background-attachment: fixed;

  display: flex;
  justify-content: center;
  padding: 40px 20px 24px;
  // 不设 overflow：页面滚动走文档流，底部按钮的 sticky 贴底才能锚定视口
}

.share-container {
  width: 100%;
  max-width: 900px;
  display: flex;
  flex-direction: column;
}

.conversation-card {
  background: #fff;
  border-radius: 25px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08), 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.card-header {
  padding: 28px 32px 24px;

  .conversation-title {
    font-size: 30px;
    font-weight: 600;
    color: #141413;
    margin: 0 0 12px 0;
    line-height: 1.4;
  }

  .conversation-meta {
    font-size: 13px;
    color: #a5a29a;
    margin: 0;
  }
}

.card-divider {
  height: 1px;
  background: #e8e6dc;
  margin: 0 32px;
}

.card-content {
  // 消息区由 ChatMessageList 自带内边距，此处只做卡片底部收尾
  padding: 0 0 8px;

  // 分享页无内部滚动：消息区随卡片文档流展开
  :deep(.chat-messages) {
    overflow: visible;
    flex: none;
    scrollbar-gutter: auto;
  }
}

.conversation-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  color: #8c8a82;

  p {
    margin-top: 16px;
    font-size: 14px;
  }
}

.footer-section {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;

  .agent-identity {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  .agent-name {
    font-size: 13px;
    font-weight: 500;
    color: #6e6b62;
  }
}

.action-buttons {
  // 常规状态随内容排在卡片下方；上滑到自然位置离开视口时贴住窗口底部跟随滚动
  position: sticky;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 16px;
  padding: 20px 0 12px;
  // 吸底悬浮时消息从身后滑过，用页面同色渐变过渡
  background: linear-gradient(to bottom, rgba(240, 238, 230, 0), #f0eee6 60%);

  .text-btn {
    display: flex;
    align-items: center;
    height: 40px;
    padding: 0 24px;
    border: none;
    background: $primary-color;
    border-radius: 20px;
    cursor: pointer;
    font-size: 15px;
    font-weight: 500;
    color: #fff;
    box-shadow: 0 4px 16px rgba($primary-color, 0.3);
    transition: all 0.2s;

    &:hover {
      background: darken($primary-color, 8%);
    }
  }
}

@media (max-width: 768px) {
  .share-page {
    padding: 20px 16px 24px;
  }

  .card-header {
    padding: 20px 20px 16px;

    .conversation-title {
      font-size: 18px;
    }

    .conversation-meta {
      font-size: 12px;
    }
  }

  .card-divider {
    margin: 0 20px;
  }

  .card-content {
    padding: 0;
  }

  .action-buttons .text-btn {
    height: 36px;
    padding: 0 18px;
    font-size: 14px;
  }
}
</style>
