<template>
  <!-- 会话搜索命令面板：a-modal 承载（与创建智能体弹窗同组件），布局对齐命令面板范式、配色沿用站点暖色 -->
  <a-modal
    :open="visible"
    @update:open="handleVisibleChange"
    :footer="null"
    :closable="false"
    centered
    width="640px"
    wrap-class-name="chat-search-modal"
  >
    <div class="palette">
      <div class="palette-input-row">
        <SearchOutlined class="palette-search-icon" />
        <input
          ref="inputRef"
          v-model="keyword"
          class="palette-input"
          placeholder="搜索会话"
          spellcheck="false"
        />
        <button class="palette-close" aria-label="关闭" @click="close()">
          <CloseOutlined />
        </button>
      </div>

      <div class="palette-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="palette-tab"
          :class="{ active: activeTab === tab.key }"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="palette-body" ref="bodyRef">
        <div v-for="section in sections" :key="section.key" class="palette-section">
          <div class="palette-section-title">{{ section.title }}</div>
          <div
            v-for="row in section.rows"
            :key="row.key"
            class="palette-row"
            :class="{ active: row.index === activeIndex }"
            :data-index="row.index"
            @click="row.activate()"
            @mouseenter="activeIndex = row.index"
          >
            <img v-if="row.avatar" :src="row.avatar" alt="" class="row-avatar" />
            <component v-else :is="row.icon" class="row-icon" />
            <span class="row-title">{{ row.title }}</span>
            <EnterOutlined class="row-enter" />
          </div>
          <div v-if="section.empty" class="palette-empty">{{ section.empty }}</div>
        </div>
      </div>

      <div class="palette-footer">
        <span class="hint">关闭 <span class="key-box">Esc</span></span>
        <span class="hint">切换类型 <span class="key-box">←</span> <span class="key-box">→</span></span>
        <span class="hint">打开 <span class="key-box">↵</span></span>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/store/user'
import { getChatIdsPage } from '@/api/chatMemory'
import { queryAgents } from '@/api/agent'
import type { ChatSession, Agent } from '@/types'
import type { Component } from 'vue'
import {
  SearchOutlined,
  CloseOutlined,
  EnterOutlined,
  MessageOutlined,
  PlusCircleOutlined,
  RobotOutlined
} from '@ant-design/icons-vue'

type TabKey = 'all' | 'chat' | 'agent'

interface PaletteRow {
  key: string
  index: number
  icon?: Component
  avatar?: string
  title: string
  activate: () => void
}

interface PaletteSection {
  key: string
  title: string
  rows: PaletteRow[]
  empty?: string
}

const props = defineProps<{
  visible: boolean
  agentId: number
  currentChatId: string
  /** 已加载的最近会话（无关键字时的"最近使用"） */
  recent: ChatSession[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'select-session', conversationId: string): void
  (e: 'select-agent', agentId: number): void
  (e: 'new-chat'): void
  (e: 'open-dashboard'): void
}>()

const userStore = useUserStore()

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'chat', label: '会话' },
  { key: 'agent', label: '智能体' }
]

const inputRef = ref<HTMLInputElement | null>(null)
const bodyRef = ref<HTMLElement | null>(null)
const keyword = ref('')
const activeTab = ref<TabKey>('all')
const activeIndex = ref(0)
const searchLoading = ref(false)
const sessionResults = ref<ChatSession[]>([])
const agentResults = ref<Agent[]>([])

const showSessions = computed(() => activeTab.value !== 'agent')
const showAgents = computed(() => userStore.isLoggedIn && activeTab.value !== 'chat')

// ---------- 数据拉取 ----------
let debounceTimer: number | undefined
let searchSeq = 0

async function runSearch(): Promise<void> {
  const kw = keyword.value.trim()
  const seq = ++searchSeq
  searchLoading.value = true
  const [sessions, agents] = await Promise.all([
    kw && showSessions.value
      ? getChatIdsPage(props.agentId, 1, 20, kw).then(res => res.records.map(item => ({
          id: item.conversationId,
          title: item.title || '新对话',
          updateTime: item.updateTime ? new Date(item.updateTime) : new Date(),
          hasMessage: true
        }))).catch(() => [])
      : Promise.resolve([]),
    kw && showAgents.value
      ? queryAgents({ name: kw, current: 1, pageSize: 10 }).then(res => res.records).catch(() => [])
      : Promise.resolve([])
  ])
  // 竞态保护：仅采纳最后一次输入的结果
  if (seq !== searchSeq) return
  sessionResults.value = sessions
  agentResults.value = agents
  searchLoading.value = false
}

// ---------- 面板行组装（统一编号供键盘上下移动） ----------
const sections = computed<PaletteSection[]>(() => {
  const result: PaletteSection[] = []
  let counter = 0

  const sessionRow = (chat: ChatSession): PaletteRow => ({
    key: `session_${chat.id}`,
    index: counter++,
    icon: MessageOutlined,
    title: chat.title,
    activate: () => {
      close()
      emit('select-session', chat.id)
    }
  })

  const agentRow = (agent: Agent): PaletteRow => ({
    key: `agent_${agent.id}`,
    index: counter++,
    avatar: agent.avatar,
    title: agent.name,
    activate: () => {
      close()
      emit('select-agent', agent.id)
    }
  })

  if (!keyword.value.trim()) {
    const recent = props.recent.slice(0, 8)
    if (activeTab.value !== 'agent') {
      result.push({
        key: 'recent',
        title: '最近使用',
        rows: recent.map(sessionRow),
        empty: userStore.isLoggedIn ? (recent.length ? undefined : '暂无历史会话') : '登录后可搜索历史会话'
      })
    }
  } else {
    if (activeTab.value !== 'agent') {
      result.push({
        key: 'chats',
        title: '会话',
        rows: sessionResults.value.map(sessionRow),
        empty: searchLoading.value || sessionResults.value.length ? undefined : '未找到相关会话'
      })
    }
    if (activeTab.value !== 'chat') {
      result.push({
        key: 'agents',
        title: '智能体',
        rows: userStore.isLoggedIn ? agentResults.value.map(agentRow) : [],
        empty: userStore.isLoggedIn
          ? (searchLoading.value || agentResults.value.length ? undefined : '未找到相关智能体')
          : '登录后可搜索智能体'
      })
    }
  }

  result.push({
    key: 'actions',
    title: '操作',
    rows: [
      {
        key: 'action_new_chat',
        index: counter++,
        icon: PlusCircleOutlined,
        title: '新建会话',
        activate: () => {
          close()
          emit('new-chat')
        }
      },
      {
        key: 'action_dashboard',
        index: counter++,
        icon: RobotOutlined,
        title: '智能体广场',
        activate: () => {
          close()
          emit('open-dashboard')
        }
      }
    ]
  })

  return result
})

const flatRows = computed<PaletteRow[]>(() => sections.value.flatMap(s => s.rows))

// ---------- 键盘导航 ----------
function moveActive(delta: number): void {
  const total = flatRows.value.length
  if (!total) return
  activeIndex.value = (activeIndex.value + delta + total) % total
  nextTick(() => {
    bodyRef.value
      ?.querySelector(`[data-index="${activeIndex.value}"]`)
      ?.scrollIntoView({ block: 'nearest' })
  })
}

function activateCurrent(): void {
  flatRows.value[activeIndex.value]?.activate()
}

function switchTab(key: TabKey): void {
  activeTab.value = key
}

function onKeydown(e: KeyboardEvent): void {
  if (!props.visible) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    moveActive(1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    moveActive(-1)
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    const idx = tabs.findIndex(t => t.key === activeTab.value)
    switchTab(tabs[(idx + 1) % tabs.length].key)
  } else if (e.key === 'ArrowLeft') {
    e.preventDefault()
    const idx = tabs.findIndex(t => t.key === activeTab.value)
    switchTab(tabs[(idx - 1 + tabs.length) % tabs.length].key)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    activateCurrent()
  }
}

window.addEventListener('keydown', onKeydown)
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

// ---------- 开关与重置 ----------
function handleVisibleChange(visible: boolean): void {
  if (!visible) close()
}

function close(): void {
  emit('update:visible', false)
}

watch(() => props.visible, (visible) => {
  if (visible) {
    keyword.value = ''
    activeTab.value = 'all'
    sessionResults.value = []
    agentResults.value = []
    activeIndex.value = 0
    nextTick(() => inputRef.value?.focus())
  }
})

watch([keyword, activeTab], () => {
  activeIndex.value = 0
  if (debounceTimer !== undefined) window.clearTimeout(debounceTimer)
  if (!keyword.value.trim()) {
    searchSeq++ // 作废在途请求
    sessionResults.value = []
    agentResults.value = []
    searchLoading.value = false
    return
  }
  debounceTimer = window.setTimeout(runSearch, 250)
})
</script>

<style lang="scss">
.chat-search-modal {
  .ant-modal-content {
    padding: 0;
    background: $bg-ivory;
    border: 1px solid $border-warm-subtle;
    border-radius: 16px;
    overflow: hidden;
    box-shadow: 0 24px 64px rgba(20, 20, 19, 0.18);
  }

  .palette {
    display: flex;
    flex-direction: column;
  }

  .palette-input-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 16px 20px;

    .palette-search-icon {
      font-size: 16px;
      color: #8c8a82;
    }

    .palette-input {
      flex: 1;
      border: none;
      outline: none;
      background: transparent;
      font-size: 15px;
      color: #141413;
      font-family: inherit;

      &::placeholder {
        color: #8c8a82;
      }
    }

    .palette-close {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border: none;
      border-radius: 6px;
      background: transparent;
      color: #8c8a82;
      font-size: 14px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        background: rgba(20, 20, 19, 0.06);
        color: #141413;
      }
    }
  }

  .palette-tabs {
    display: flex;
    gap: 6px;
    padding: 0 20px 12px;

    .palette-tab {
      padding: 5px 14px;
      border: none;
      border-radius: 16px;
      background: transparent;
      color: #6e6b62;
      font-size: 13px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        background: rgba(20, 20, 19, 0.05);
        color: #141413;
      }

      &.active {
        background: #141413;
        color: #faf9f5;
        font-weight: 500;
      }
    }
  }

  .palette-body {
    max-height: 400px;
    min-height: 200px;
    overflow-y: auto;
    padding: 4px 12px 12px;
    scrollbar-width: thin;
    scrollbar-color: #d2cfc5 transparent;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      background: #d2cfc5;
      border-radius: 3px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }
  }

  .palette-section {
    margin-bottom: 8px;
  }

  .palette-section-title {
    font-size: 12px;
    color: #8c8a82;
    padding: 8px 8px 4px;
  }

  .palette-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 10px;
    border-radius: 8px;
    cursor: pointer;
    color: #3d3b35;
    font-size: 14px;

    &:hover,
    &.active {
      background: rgba(42, 161, 169, 0.1);
      color: #141413;
    }

    .row-icon {
      font-size: 15px;
      color: #8c8a82;
      flex-shrink: 0;
    }

    .row-avatar {
      width: 18px;
      height: 18px;
      border-radius: 4px;
      object-fit: cover;
      flex-shrink: 0;
    }

    .row-title {
      flex: 1;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .row-enter {
      font-size: 13px;
      color: #8c8a82;
      opacity: 0;
      transition: opacity 0.15s;
    }

    &:hover .row-enter,
    &.active .row-enter {
      opacity: 1;
    }
  }

  .palette-empty {
    padding: 8px 10px 12px;
    font-size: 13px;
    color: #8c8a82;
  }

  .palette-footer {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 12px 20px;
    border-top: 1px solid $border-warm-subtle;

    .hint {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: #8c8a82;
    }

    .key-box {
      min-width: 20px;
      padding: 1px 6px;
      text-align: center;
      background: #f0eee6;
      border: 1px solid #e0ddd2;
      border-radius: 4px;
      font-size: 11px;
      color: #6e6b62;
      font-weight: 500;
    }
  }
}
</style>
