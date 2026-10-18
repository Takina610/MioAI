<template>
  <section class="hero">
    <HomeCloudFlow class="hero-clouds" />

    <div class="home-container hero-inner">
      <h1 class="hero-slogan hm-reveal">会思考，能行动</h1>

      <!-- 复用对话页输入框：嵌入模式只保留输入框本体（无外层留白与欢迎区）。
           loading 恒为 false：本页不发消息，点发送只是"备好内容并跳转"，
           跳转准备期的重复点击由 useHomeChat 内部的 sending 拦掉 -->
      <div class="hero-input hm-reveal delay-1">
        <ChatInput
          v-model="input"
          v-model:effort="effort"
          :has-messages="false"
          :loading="false"
          :supported-efforts="supportedEfforts"
          :render-cards="renderCards"
          :leaving-keys="leavingKeys"
          :ref-setter="setCardRef"
          :free-models="freeModels"
          :providers="providers"
          :current-model="currentModel"
          embedded
          placeholder="问点什么，剩下的交给我"
          @send="send"
          @add-files="addFiles"
          @remove-pending="removePending"
          @select-model="handleSelectModel"
          @manage-models="manageModelsVisible = true"
        />
      </div>

      <nav class="hero-entries hm-reveal delay-2" aria-label="站内入口">
        <router-link v-for="entry in entries" :key="entry.to" class="hero-entry" :to="entry.to">
          <component :is="entry.icon" class="entry-icon" />
          <span>{{ entry.label }}</span>
        </router-link>
      </nav>
    </div>

    <!-- 管理模型：与对话页同款弹窗（内嵌个人中心的模型设置） -->
    <a-modal
      v-model:open="manageModelsVisible"
      title="管理模型"
      :footer="null"
      :width="720"
      wrap-class-name="manage-models-modal"
    >
      <ModelSettings />
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  BookOutlined,
  RobotOutlined,
  ShopOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import ChatInput from '@/views/chat/components/ChatInput.vue'
import ModelSettings from '@/views/dashboard/profile/components/ModelSettings.vue'
import HomeCloudFlow from './HomeCloudFlow.vue'
import { useHomeChat } from '../composables/useHomeChat'
import { loadModelCatalog, useModelCatalog, writeLocalSelection } from '@/composables/model/useModelCatalog'
import { useReasoningEfforts } from '@/composables/model/useReasoningEfforts'
import type { ModelSelection } from '@/types'

/** 输入框下方的内容入口 */
const entries = [
  { label: '智能体广场', to: '/dashboard/agent-market', icon: RobotOutlined },
  { label: 'MCP 广场', to: '/dashboard/mcp-market', icon: ShopOutlined },
  { label: '技能广场', to: '/dashboard/skill-market', icon: ThunderboltOutlined },
  { label: '公共知识库', to: '/dashboard/public-knowledge', icon: BookOutlined }
]

const { input, renderCards, leavingKeys, setCardRef, addFiles, removePending, send } = useHomeChat()

const { freeModels, providers, selection: currentModel } = useModelCatalog()
const { effort, supported: supportedEfforts, resolve: resolveEfforts } =
  useReasoningEfforts(currentModel, { freeModels, providers })

const manageModelsVisible = ref(false)

/** 模型切换：与对话页同口径——共享选择 + 本地镜像，档位按新模型重新解析 */
async function handleSelectModel(next: ModelSelection): Promise<void> {
  const prev = currentModel.value
  if (next.source === prev.source && next.providerId === prev.providerId && next.model === prev.model) {
    return
  }
  currentModel.value = next
  writeLocalSelection(next)
  await resolveEfforts(next)
}

onMounted(async () => {
  await loadModelCatalog()
  if (currentModel.value.model) resolveEfforts(currentModel.value)
})
</script>

<style lang="scss" scoped>
.hero {
  position: relative;
  display: flex;
  align-items: center;
  // 上移一个顶栏高度：云层从视口顶端铺起，顶栏未滚动时与背景连成一片（不留横向接缝）
  margin-top: -72px;
  min-height: 100svh;
  padding: calc(72px + clamp(24px, 4vh, 48px)) 0 clamp(40px, 8vh, 80px);
}

/* 云层淡出收尾：底部渐隐到页面底色，与下方区块无缝衔接 */
.hero-clouds {
  mask-image: linear-gradient(180deg, #000 0%, #000 72%, transparent 100%);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, #000 72%, transparent 100%);
}

.hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.hero-slogan {
  font-family: var(--hm-font-display);
  font-size: clamp(32px, 5vw, 52px);
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.14em;
  // 宽字距会在末字后留出空档，用等量缩进抵消以保持视觉居中
  text-indent: 0.14em;
  text-align: center;
  color: var(--hm-green-deep);
}

.hero-input {
  width: min(100%, 720px);
  margin-top: clamp(28px, 5vh, 48px);
}

.hero-entries {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 22px;
}

.hero-entry {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 999px;
  border: 1px solid rgba(20, 20, 19, 0.12);
  background: rgba(250, 249, 245, 0.55);
  font-size: 14px;
  font-weight: 500;
  color: var(--hm-text);
  transition: border-color 0.25s var(--hm-ease), color 0.25s var(--hm-ease),
    background 0.25s var(--hm-ease);

  .entry-icon {
    font-size: 15px;
    color: var(--hm-text-muted);
    transition: color 0.25s var(--hm-ease);
  }

  &:hover {
    border-color: var(--hm-green);
    background: rgba(250, 249, 245, 0.9);
    color: var(--hm-green-deep);

    .entry-icon {
      color: var(--hm-green-deep);
    }
  }
}

.delay-1 {
  animation-delay: 90ms;
}
.delay-2 {
  animation-delay: 180ms;
}
</style>
