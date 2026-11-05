<template>
  <!-- /chat 复刻演示窗：左轨 + 会话侧栏 + 主区（消息/输入为真实组件），整窗纯展示
       （pointer-events:none，用户不可交互；演示播放器以合成事件驱动输入框） -->
  <div
    class="chat-demo"
    :class="{ 'panels-open': panelsOpen, 'is-docked': isDocked }"
    aria-label="功能演示"
  >
    <!-- 两侧栏：桌面常驻；窄屏转为左滑抽屉（头部按钮唤出，右上角关闭） -->
    <div ref="panelsDrawerRef" class="panels-drawer">
      <aside class="demo-rail">
        <span class="rail-action"><TeamOutlined /></span>
        <span class="rail-divider" />
        <span
          v-for="agent in demoAgents"
          :key="agent.id"
          class="rail-item"
          :class="{ active: agent.id === currentAgentId }"
          role="button"
          tabindex="0"
          :title="agent.name"
          @click="onAgentSelect(agent.id)"
          @keydown.enter="onAgentSelect(agent.id)"
        >
          <GroupAvatar
            v-if="agent.type === 2"
            :members="(agent.members ?? []) as never[]"
            :size="34"
          />
          <BotIcon
            v-else-if="iconOf(agent)"
            :shape="iconOf(agent)!.shape"
            :fill="iconOf(agent)!.fill"
            :size="34"
            :live="false"
            eye-color="#faf9f5"
          />
        </span>
      </aside>

      <DemoSessionSidebar
        :sessions="agentSessions"
        :current-id="currentSessionId"
        :streaming-id="streamingSessionId"
        :agent-name="currentAgent.name"
        :group-members="currentAgent.type === 2 ? currentAgent.members ?? null : null"
        :pet-config="petConfig"
        :pet-mood="petMood"
        :pet-fx="petFxSignal"
        @select="onSessionSelect"
      />

      <button type="button" class="drawer-close" aria-label="收起侧边栏" @click="panelsOpen = false">
        <CloseOutlined />
      </button>
    </div>

    <div ref="demoMainRef" class="demo-main">
      <header class="demo-chat-header">
        <button
          type="button"
          class="sidebar-toggle"
          aria-label="切换侧边栏"
          @click="panelsOpen = !panelsOpen"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <rect x="3.2" y="4.8" width="17.6" height="14.4" rx="3.2" />
            <line x1="9.2" y1="4.8" x2="9.2" y2="19.2" />
          </svg>
        </button>
        <h3 class="chat-title">{{ sessionTitle }}</h3>
      </header>

      <div class="demo-body">
        <ChatMessageList
          ref="listRef"
          :messages="messages"
          :is-loading="isLoading"
          :can-modify="false"
          :group-mode="currentAgent.type === 2"
          :group-members="currentAgent.members"
        />
      </div>

      <div class="demo-input">
        <ChatInput
          v-model="input"
          v-model:effort="effort"
          :agent-name="currentAgent.name"
          :has-messages="true"
          :loading="isLoading"
          :supported-efforts="supportedEfforts"
          :render-cards="pendingFiles"
          :free-models="freeModels"
          :providers="providers"
          :current-model="currentModel"
          :knowledge-bases="demoKbs"
          :skills="demoSkills"
          placeholder="问点什么，剩下的交给我"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { TeamOutlined, CloseOutlined } from '@ant-design/icons-vue'
import ChatMessageList from '@/views/chat/components/messages/ChatMessageList.vue'
import ChatInput from '@/views/chat/components/input/ChatInput.vue'
import BotIcon from '@/components/agent/bot-icon/BotIcon.vue'
import GroupAvatar from '@/components/agent/GroupAvatar.vue'
import { DEFAULT_BOT_ICON, parseBotIcon, type BotIconConfig } from '@/components/agent/bot-icon/types'
import { loadModelCatalog, useModelCatalog } from '@/composables/model/useModelCatalog'
import { useReasoningEfforts } from '@/composables/model/useReasoningEfforts'
import { useBotMood } from '@/views/chat/composables/message/useBotMood'
import DemoSessionSidebar from './DemoSessionSidebar.vue'
import { DEMO_KBS, DEMO_SKILLS } from '../demoScenarios'
import { DEMO_AGENTS, type DemoAgentDef } from '../demoAgents'
import { useHomeChatDemo } from '../composables/useHomeChatDemo'

const props = withDefaults(
  defineProps<{
    /** 是否自动开始演示 */
    autoStart?: boolean
  }>(),
  {
    autoStart: true
  }
)

const panelsDrawerRef = ref<HTMLElement | null>(null)
const demoMainRef = ref<HTMLElement | null>(null)
const isDocked = ref(true)

const {
  messages,
  isLoading,
  currentAgentId,
  currentSessionId,
  streamingSessionId,
  pendingFiles,
  start,
  stop,
  resetState,
  jumpToSession,
  switchAgent
} = useHomeChatDemo()

/** 移动端侧栏抽屉开关（≤980px 生效；桌面侧栏常驻无视觉效果） */
const panelsOpen = ref(false)

function onSessionSelect(sessionId: string): void {
  jumpToSession(sessionId)
  panelsOpen.value = false
}

function onAgentSelect(agentId: number): void {
  switchAgent(agentId)
  panelsOpen.value = false
}

const demoAgents: DemoAgentDef[] = DEMO_AGENTS
const demoKbs = DEMO_KBS
const demoSkills = DEMO_SKILLS

const currentAgent = computed<DemoAgentDef>(
  () => DEMO_AGENTS.find(a => a.id === currentAgentId.value) ?? DEMO_AGENTS[0]!
)

const agentSessions = computed(() => currentAgent.value.sessions)

function iconOf(agent: DemoAgentDef): BotIconConfig | null {
  return parseBotIcon(agent.icon)
}

const sessionTitle = computed(
  () => currentAgent.value.sessions.find(s => s.id === currentSessionId.value)?.title ?? '新对话'
)

/** 输入框展示模型目录（真实目录拉取；演示打字经 v-model 同步进输入框） */
const input = ref('')
const { freeModels, providers, selection: currentModel } = useModelCatalog()
const { effort, supported: supportedEfforts, resolve: resolveEfforts } =
  useReasoningEfforts(currentModel, { freeModels, providers })

/** 侧栏头部宠物的实时情绪：与对话页左下角 BotPet 同一驱动（useBotMood） */
const listening = ref(false)
const pendingUploads = computed(() => pendingFiles.value.length)
const { mood: petMood, fxSignal: petFxSignal } = useBotMood(
  messages,
  isLoading,
  listening,
  input,
  pendingUploads,
  undefined,
  currentSessionId
)
/** 宠物形象跟随当前智能体（群聊/无图标智能体回退 MioBot 形象） */
const petConfig = computed<BotIconConfig>(() => iconOf(currentAgent.value) ?? DEFAULT_BOT_ICON)

const listRef = ref<InstanceType<typeof ChatMessageList> | null>(null)

if (props.autoStart) {
  start()
}

function playDemo(): void {
  start()
}

function stopDemo(): void {
  stop()
}

function resetDemo(): void {
  stop()
  resetState()
}

defineExpose({
  panelsDrawerRef,
  demoMainRef,
  isDocked,
  playDemo,
  stopDemo,
  resetDemo
})

/** 滚动跟随：流式期间（isLoading）始终贴底；用户向上滚轮阅读时让位，
    重新滚回底部或切换会话后恢复跟随。以 DOM 观察器驱动（流式字符级变更
    不完全走响应式回调），不用 rAF——被遮挡标签页里 rAF 挂起会卡死跟随。 */
let scrollObserver: MutationObserver | null = null
let userScrolledUp = false

function isNearBottomByEl(): boolean {
  const el = document.querySelector('.chat-demo .chat-messages')
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 120
}

function followToBottom(): void {
  if (!isLoading.value || userScrolledUp) return
  listRef.value?.scrollToBottom()
}

function onUserScrollIntent(e: Event): void {
  const wheel = e as WheelEvent
  if (wheel.deltaY < 0) {
    userScrolledUp = true
  } else if (isNearBottomByEl()) {
    userScrolledUp = false
  }
}

onMounted(() => {
  scrollObserver = new MutationObserver(followToBottom)
  scrollObserver.observe(document.querySelector('.chat-demo') ?? document.body, {
    childList: true,
    characterData: true,
    subtree: true
  })
  document.querySelector('.chat-demo')?.addEventListener('wheel', onUserScrollIntent, { passive: true })
  void loadModelCatalog().then(() => {
    if (currentModel.value.model) resolveEfforts(currentModel.value)
  })
})

/** 新回合开始/切换会话后恢复跟随 */
watch(isLoading, loading => {
  if (loading) userScrolledUp = false
})
watch(currentSessionId, () => {
  userScrolledUp = false
})

onUnmounted(() => {
  scrollObserver?.disconnect()
  scrollObserver = null
  document.querySelector('.chat-demo')?.removeEventListener('wheel', onUserScrollIntent)
})

watch(
  messages,
  async () => {
    await nextTick()
    followToBottom()
  },
  { deep: true }
)
</script>

<style lang="scss" scoped>
/* 固定宽度、高度随视口自适应（上下留白恒定，不压屏底）；
   窄屏收起两侧栏，与 /chat 移动端同策略 */
.chat-demo {
  position: relative;
  width: 1120px;
  max-width: 100%;
  height: clamp(440px, calc(100svh - 320px), 760px);
  display: flex;
  overflow: visible;
}

/* 智能体左轨：可点击切换智能体（创建入口为装饰） */
.demo-rail {
  width: 64px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 14px 0;
  border-right: 1px solid rgba(216, 213, 204, 0.45);

  .rail-action {
    pointer-events: none; // 装饰性创建入口，不可点击
    width: 38px;
    height: 38px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    border: 1px dashed var(--hm-border);
    color: var(--hm-text-muted);
    font-size: 15px;
  }

  .rail-divider {
    width: 26px;
    height: 1px;
    background: var(--hm-border-subtle);
  }

  .rail-item {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    cursor: pointer;
    transition: background 0.2s ease;

    &:hover {
      background: rgba(20, 20, 19, 0.05);
    }

    &.active {
      background: var(--hm-green-soft);
      box-shadow: inset 0 0 0 1.5px rgba(42, 161, 169, 0.45);
    }
  }
}

/* 主区 */
.demo-main {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(250, 249, 245, 0.72);
  backdrop-filter: blur(24px) saturate(1.5);
  -webkit-backdrop-filter: blur(24px) saturate(1.5);
  border: 1px solid rgba(232, 230, 220, 0.85);
  box-shadow: 0 14px 44px rgba(20, 20, 19, 0.08);
  border-radius: 16px;
  overflow: hidden;
  transition: border-radius 0.40s var(--hm-ease);
  will-change: transform, opacity;
}

.demo-chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(216, 213, 204, 0.45);
  flex-shrink: 0;

  .sidebar-toggle {
    display: flex;
    align-items: center;
    padding: 4px;
    border: none;
    background: transparent;
    color: var(--hm-text-2);
    cursor: pointer;
  }

  .chat-title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--hm-text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.demo-body {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.chat-messages) {
    padding: 18px 20px;
  }

  /* 底边渐隐：消息贴近输入区时柔和没入（颜色对齐输入区底色，不出现白条断层） */
  &::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 30px;
    background: linear-gradient(180deg, rgba(250, 249, 245, 0), rgba(250, 249, 245, 0.82));
    pointer-events: none;
  }
}

.demo-input {
  position: relative;
  flex-shrink: 0;
  pointer-events: none; // 输入框纯展示：不可聚焦/键入/切换（演示以合成事件驱动）

  /* 输入容器融入玻璃窗：半透明底 + 轻度再模糊；禁用为 /chat 设计的白色渐隐带 */
  :deep(.chat-center-area .list-fade-overlay) {
    display: none;
  }

  :deep(.chat-center-area .chat-input-wrapper .chat-input-container) {
    background: rgba(250, 249, 245, 0.82);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
  }
}

/* 两侧栏容器：横排（桌面常驻；窄屏整体转为左滑抽屉） */
.panels-drawer {
  display: flex;
  flex-shrink: 0;
  height: 100%;
  background: rgba(250, 249, 245, 0.72);
  backdrop-filter: blur(24px) saturate(1.5);
  -webkit-backdrop-filter: blur(24px) saturate(1.5);
  border: 1px solid rgba(232, 230, 220, 0.85);
  box-shadow: 0 14px 44px rgba(20, 20, 19, 0.08);
  border-radius: 16px;
  overflow: hidden;
  transition: border-radius 0.40s var(--hm-ease);
  will-change: transform, opacity;
}

/* 拼接状态：左右两盒无缝并接为一体工作台 */
.chat-demo.is-docked {
  .panels-drawer {
    border-top-right-radius: 0;
    border-bottom-right-radius: 0;
    border-right: none;
  }

  .demo-main {
    border-top-left-radius: 0;
    border-bottom-left-radius: 0;
    border-left: none; // 由 DemoSessionSidebar 的右边框提供 1px 细线
  }
}

/* 抽屉右上角关闭钮：仅窄屏显示 */
.drawer-close {
  display: none;
}

@media (max-width: 980px) {
  .chat-demo {
    height: clamp(420px, calc(100svh - 300px), 720px);
  }

  /* 两侧栏整体转为左滑抽屉：头部按钮唤出，右上角关闭钮收起 */
  .panels-drawer {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 32;
    background: rgba(250, 249, 245, 0.96);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-right: 1px solid var(--hm-border);
    box-shadow: 8px 0 28px rgba(20, 20, 19, 0.14);
    transform: translateX(-110%);
    transition: transform 0.3s var(--hm-ease);
    border-radius: 0 16px 16px 0 !important;
  }

  .chat-demo.panels-open .panels-drawer {
    transform: translateX(0);
  }

  .demo-main {
    border-radius: 16px !important;
  }

  :deep(.dsb) {
    max-width: calc(100vw - 96px);
  }

  .drawer-close {
    display: flex;
    position: absolute;
    top: 12px;
    right: 12px;
    z-index: 3;
    width: 32px;
    height: 32px;
    align-items: center;
    justify-content: center;
    border: 1px solid var(--hm-border);
    border-radius: 50%;
    background: rgba(250, 249, 245, 0.9);
    color: var(--hm-text-2);
    cursor: pointer;
    transition: color 0.2s ease, border-color 0.2s ease;

    &:hover {
      color: var(--hm-green-deep);
      border-color: var(--hm-green);
    }
  }
}

/* 手机：收紧内边距，输入框工具行（模型名/档位/发送）整行塞进窄屏 */
@media (max-width: 560px) {
  .chat-demo {
    height: clamp(400px, calc(100svh - 290px), 640px);
    border-radius: 14px;
  }

  .demo-chat-header {
    padding: 0 12px;
  }

  .demo-body :deep(.chat-messages) {
    padding: 12px;
  }

  .demo-body :deep(.chat-messages .messages-wrapper .message) {
    margin-bottom: 16px;

    /* 用户气泡占更宽，减去左右内边距后的可用宽度更小 */
    .user-msg-body {
      max-width: 88%;
    }
  }

  .demo-input {
    :deep(.chat-center-area) {
      padding: 10px 10px 12px;

      .chat-input-container .input-main {
        padding: 12px 12px 6px;
      }

      .chat-input-container .input-toolbar {
        padding: 6px 8px 10px;
      }

      /* 模型名可收缩截断；档位只留图标+箭头；@ 菜单不超出屏幕 */
      .chat-input-container .model-selector {
        min-width: 0;
        max-width: 150px;
      }

      .chat-input-container .effort-selector .effort-label {
        display: none;
      }

      .mention-menu {
        max-width: calc(100vw - 28px);
      }
    }
  }
}
</style>
