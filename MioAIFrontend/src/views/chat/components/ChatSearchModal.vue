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
        <!-- 类别筛选模式：作用域胶囊（退格清空时一并退出） -->
        <button
          v-if="scope"
          class="palette-scope-chip"
          :title="'退出 ' + scopeLabel(scope)"
          @click="exitScope"
        >
          {{ scopeLabel(scope) }}
          <CloseOutlined class="chip-x" />
        </button>
        <input
          ref="inputRef"
          v-model="keyword"
          class="palette-input"
          :placeholder="slashMode ? '选择类别…' : '搜索会话、智能体、页面、设置，输入 / 筛选类别'"
          spellcheck="false"
          @keydown="onInputKeydown"
        />
        <button class="palette-close" aria-label="关闭" @click="close()">
          <CloseOutlined />
        </button>
      </div>

      <div v-if="!slashMode && !scope" class="palette-tabs">
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

      <!-- 固定高度滚动区 + 上下渐隐遮罩：结果增减不改变弹窗高度（避免整体跳动闪烁），
           内容滚出边界时柔和消隐而非硬切 -->
      <div class="palette-body-wrap">
        <div class="palette-body" :class="{ updating: searchLoading }" ref="bodyRef">
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
              <span class="row-main">
                <span class="row-title">
                  <HighlightText :text="row.title" :indices="row.matchIndices" />
                </span>
                <span v-if="row.desc" class="row-desc">{{ row.desc }}</span>
              </span>
              <span v-if="row.category" class="row-category">{{ row.category }}</span>
              <EnterOutlined class="row-enter" />
            </div>
            <!-- 加载骨架屏：每个分组一条占位（扫光），结果到达后替换 -->
            <template v-if="searchLoading && !section.rows.length && section.key !== 'actions'">
              <div class="palette-skel">
                <span class="skel-icon" />
                <span class="skel-bar" />
              </div>
            </template>
            <div v-if="section.empty" class="palette-empty">{{ section.empty }}</div>
          </div>
        </div>
        <div class="body-fade top" aria-hidden="true"></div>
        <div class="body-fade bottom" aria-hidden="true"></div>
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
import { useRouter } from 'vue-router'
import Fuse, { type IFuseOptions } from 'fuse.js'
import { useUserStore } from '@/store/user'
import { getChatIdsPage } from '@/api/chatMemory'
import { queryAgents } from '@/api/agent'
import type { ChatSession, Agent } from '@/types'
import type { Component } from 'vue'
import HighlightText from '@/components/HighlightText.vue'
import {
  SearchOutlined,
  CloseOutlined,
  EnterOutlined,
  MessageOutlined,
  PlusCircleOutlined,
  RobotOutlined,
  FileTextOutlined,
  SettingOutlined,
  HomeOutlined,
  AppstoreOutlined,
  ToolOutlined,
  DatabaseOutlined,
  ReadOutlined,
  ThunderboltOutlined,
  BarChartOutlined,
  UserOutlined,
  HighlightOutlined,
  LogoutOutlined
} from '@ant-design/icons-vue'

type TabKey = 'all' | 'chat' | 'agent'
type ScopeKey = 'chat' | 'agent' | 'page' | 'setting'

/** fuse 索引条目：名称 + 分类双键可搜（页面/设置静态条目共用同一索引） */
interface SearchItem {
  kind: 'session' | 'agent' | 'page' | 'setting'
  id: string
  title: string
  category: string
}

/**
 * fuse 参数（scripts/fuse-tune.mjs 以真实数据调参定版）：
 * threshold 0.4 为甜点——"Dokcer"（转置）可命中 Docker、"Reds"（缺字母）命中 Redis 且居首，
 * 0.3 漏转置词、0.5 出现明显噪音；ignoreLocation 让中英文都不受位置惩罚；
 * 名称 0.7 / 分类 0.3，分类词（"会话"/"页面"）可直达对应类型，纯分类平局按原序（最近使用序）
 */
const FUSE_OPTIONS: IFuseOptions<SearchItem> = {
  includeScore: true,
  includeMatches: true,
  ignoreLocation: true,
  minMatchCharLength: 1,
  threshold: 0.4,
  keys: [
    { name: 'title', weight: 0.7 },
    { name: 'category', weight: 0.3 }
  ]
}

interface PaletteRow {
  key: string
  index: number
  icon?: Component
  avatar?: string
  title: string
  desc?: string
  category?: string
  /** fuse 命中区间（title，闭区间），供高亮渲染 */
  matchIndices?: Array<[number, number]>
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

const router = useRouter()
const userStore = useUserStore()

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'chat', label: '会话' },
  { key: 'agent', label: '智能体' }
]

// ---------- "/" 类别筛选（命令面板范式）：输入 / 选类别，选定后类别内搜索 ----------
const SCOPE_CATEGORIES: Array<{ key: ScopeKey; label: string; desc: string; icon: Component }> = [
  { key: 'chat', label: '会话', desc: '在对话历史中搜索', icon: MessageOutlined },
  { key: 'agent', label: '智能体', desc: '打开并搜索你的智能体', icon: RobotOutlined },
  { key: 'page', label: '页面', desc: '搜索并快速打开页面', icon: FileTextOutlined },
  { key: 'setting', label: '设置', desc: '更改应用设置', icon: SettingOutlined }
]

const scope = ref<ScopeKey | null>(null)
const slashMode = computed(() => !scope.value && keyword.value.startsWith('/'))

function scopeLabel(key: ScopeKey): string {
  return SCOPE_CATEGORIES.find(c => c.key === key)?.label ?? ''
}

function enterScope(key: ScopeKey): void {
  scope.value = key
  keyword.value = ''
  activeIndex.value = 0
}

function exitScope(): void {
  scope.value = null
  keyword.value = ''
}

/** 类别卡（slash 模式）：按 / 后文本即时过滤 */
const slashCategories = computed(() => {
  const text = keyword.value.slice(1).trim()
  if (!text) return SCOPE_CATEGORIES
  return SCOPE_CATEGORIES.filter(c => c.label.includes(text))
})

// ---------- 静态条目：页面与设置（登录态与角色过滤） ----------
interface StaticEntry {
  key: string
  title: string
  desc: string
  icon: Component
  visible: () => boolean
  activate: () => void
}

const STATIC_PAGES: StaticEntry[] = [
  { key: 'home', title: '首页', desc: '回到 MioAI 首页', icon: HomeOutlined, visible: () => true,
    activate: () => { close(); router.push('/') } },
  { key: 'agent-market', title: '智能体广场', desc: '浏览公开智能体模板', icon: AppstoreOutlined, visible: () => true,
    activate: () => { close(); emit('open-dashboard'); router.push('/dashboard/agent-market') } },
  { key: 'agent', title: '我的智能体', desc: '管理与配置你的智能体', icon: RobotOutlined, visible: () => userStore.isLoggedIn,
    activate: () => { close(); router.push('/dashboard/agent') } },
  { key: 'mcp-market', title: 'MCP 广场', desc: '浏览公开 MCP 服务', icon: AppstoreOutlined, visible: () => userStore.isLoggedIn,
    activate: () => { close(); router.push('/dashboard/mcp-market') } },
  { key: 'mcp', title: '我的 MCP', desc: '管理 MCP 服务配置', icon: ToolOutlined, visible: () => userStore.isLoggedIn,
    activate: () => { close(); router.push('/dashboard/mcp') } },
  { key: 'public-knowledge', title: '公共知识库', desc: '全站共享的知识库', icon: ReadOutlined, visible: () => true,
    activate: () => { close(); router.push('/dashboard/public-knowledge') } },
  { key: 'knowledge', title: '我的知识库', desc: '管理你的知识库与文档', icon: DatabaseOutlined, visible: () => userStore.isLoggedIn,
    activate: () => { close(); router.push('/dashboard/knowledge') } },
  { key: 'skill-market', title: '技能广场', desc: '浏览可安装技能', icon: AppstoreOutlined, visible: () => userStore.isLoggedIn,
    activate: () => { close(); router.push('/dashboard/skill-market') } },
  { key: 'skill', title: '我的技能', desc: '管理已安装技能', icon: ThunderboltOutlined, visible: () => userStore.isLoggedIn,
    activate: () => { close(); router.push('/dashboard/skill') } },
  { key: 'usage', title: '使用记录', desc: '用量与调用日志', icon: BarChartOutlined, visible: () => userStore.isLoggedIn,
    activate: () => { close(); router.push('/dashboard/usage') } },
  { key: 'admin-user', title: '用户管理', desc: '管理站点用户', icon: UserOutlined, visible: () => userStore.userInfo?.userRole === 'admin',
    activate: () => { close(); router.push('/admin/user') } }
]

const STATIC_SETTINGS: StaticEntry[] = [
  { key: 'profile', title: '个人中心', desc: '账号资料与安全', icon: UserOutlined, visible: () => userStore.isLoggedIn,
    activate: () => { close(); router.push('/dashboard/profile') } },
  { key: 'personalize', title: '个性化设置', desc: '定制你的 MioBot 人设与图标', icon: HighlightOutlined, visible: () => userStore.isLoggedIn,
    activate: () => { close(); router.push('/chat/1?personalize=1') } },
  { key: 'logout', title: '退出登录', desc: '结束当前登录态', icon: LogoutOutlined, visible: () => userStore.isLoggedIn,
    activate: async () => {
      close()
      await userStore.logout()
      // 仅内置 MioBot 页（id=1）退出后可停留游客视角，其余页面直接回首页
      if (props.agentId !== 1) {
        window.location.href = '/'
        return
      }
      window.location.reload()
    } }
]

const staticRows = computed(() => ({
  pages: STATIC_PAGES.filter(p => p.visible()),
  settings: STATIC_SETTINGS.filter(s => s.visible())
}))

const inputRef = ref<HTMLInputElement | null>(null)
const bodyRef = ref<HTMLElement | null>(null)
const keyword = ref('')
const activeTab = ref<TabKey>('all')
const activeIndex = ref(0)

// ---------- 本地索引：打开面板时一次性拉全量，输入即时 fuse 过滤（无防抖无逐键请求） ----------
const searchLoading = ref(false)
const allSessions = ref<ChatSession[]>([])
const allAgents = ref<Agent[]>([])
let fetchSeq = 0

async function fetchIndex(): Promise<void> {
  const seq = ++fetchSeq
  searchLoading.value = true
  const [sessions, agents] = await Promise.all([
    getChatIdsPage(props.agentId, 1, 200)
      .then(res => res.records.map(item => ({
        id: item.conversationId,
        title: item.title || '新对话',
        updateTime: item.updateTime ? new Date(item.updateTime) : new Date(),
        hasMessage: true
      })))
      .catch(() => []),
    userStore.isLoggedIn
      ? queryAgents({ current: 1, pageSize: 100 }).then(res => res.records).catch(() => [])
      : Promise.resolve([])
  ])
  if (seq !== fetchSeq) return
  allSessions.value = sessions
  allAgents.value = agents
  searchLoading.value = false
}

const fuseItems = computed<SearchItem[]>(() => [
  ...allSessions.value.map(s => ({ kind: 'session' as const, id: s.id, title: s.title, category: '会话' })),
  ...allAgents.value.map(a => ({ kind: 'agent' as const, id: String(a.id), title: a.name, category: '智能体' })),
  ...staticRows.value.pages.map(p => ({ kind: 'page' as const, id: p.key, title: p.title, category: '页面' })),
  ...staticRows.value.settings.map(s => ({ kind: 'setting' as const, id: s.key, title: s.title, category: '设置' }))
])

const fuse = computed(() => new Fuse(fuseItems.value, FUSE_OPTIONS))

/** 边输入边出：同步模糊搜索（本地索引，微秒级），结果即相关度排序 */
const matched = computed<Array<{ item: SearchItem; indices: Array<[number, number]> }>>(() => {
  const kw = keyword.value.trim()
  if (!kw) return []
  return fuse.value.search(kw).map(r => {
    const titleMatch = r.matches?.find(m => m.key === 'title')
    return { item: r.item, indices: (titleMatch?.indices ?? []) as Array<[number, number]> }
  })
})

// ---------- 面板行组装（统一编号供键盘上下移动） ----------
const sections = computed<PaletteSection[]>(() => {
  const result: PaletteSection[] = []
  let counter = 0

  const sessionRow = (chat: ChatSession, indices?: Array<[number, number]>): PaletteRow => ({
    key: `session_${chat.id}`,
    index: counter++,
    icon: MessageOutlined,
    title: chat.title,
    category: '会话',
    matchIndices: indices,
    activate: () => {
      close()
      emit('select-session', chat.id)
    }
  })

  const agentRow = (agent: Agent, indices?: Array<[number, number]>): PaletteRow => ({
    key: `agent_${agent.id}`,
    index: counter++,
    avatar: agent.avatar,
    title: agent.name,
    category: '智能体',
    matchIndices: indices,
    activate: () => {
      close()
      emit('select-agent', agent.id)
    }
  })

  const staticRow = (entry: StaticEntry, indices?: Array<[number, number]>): PaletteRow => ({
    key: `static_${entry.key}`,
    index: counter++,
    icon: entry.icon,
    title: entry.title,
    desc: entry.desc,
    matchIndices: indices,
    activate: entry.activate
  })

  const staticById = (kind: 'page' | 'setting', id: string) =>
    (kind === 'page' ? STATIC_PAGES : STATIC_SETTINGS).find(e => e.key === id && e.visible())

  /** "/" 类别筛选：类别卡片（点击/回车进入该类别搜索） */
  if (slashMode.value) {
    result.push({
      key: 'categories',
      title: '选择类别',
      rows: slashCategories.value.map(c => ({
        key: `cat_${c.key}`,
        index: counter++,
        icon: c.icon,
        title: c.label,
        desc: c.desc,
        activate: () => enterScope(c.key)
      })),
      empty: '没有匹配的类别'
    })
    return result
  }

  /** 类别内搜索：只渲染所选类别的结果 */
  if (scope.value) {
    const kw = keyword.value.trim()
    if (scope.value === 'chat') {
      const rows = kw
        ? matched.value.filter(m => m.item.kind === 'session')
            .map(m => sessionRow({ id: m.item.id, title: m.item.title } as ChatSession, m.indices))
        : props.recent.slice(0, 12).map(c => sessionRow(c))
      result.push({
        key: 'chats',
        title: '会话',
        rows,
        empty: userStore.isLoggedIn ? '未找到相关会话' : '登录后可搜索历史会话'
      })
    } else if (scope.value === 'agent') {
      const rows = kw
        ? matched.value.filter(m => m.item.kind === 'agent')
            .map(m => agentRow({ id: Number(m.item.id), name: m.item.title } as Agent, m.indices))
        : allAgents.value.slice(0, 12).map(a => agentRow(a))
      result.push({
        key: 'agents',
        title: '智能体',
        rows,
        empty: !userStore.isLoggedIn ? '登录后可搜索智能体' : '未找到相关智能体'
      })
    } else {
      const kind = scope.value === 'page' ? 'page' : 'setting'
      const label = scope.value === 'page' ? '页面' : '设置'
      const rows = (kw
        ? matched.value.filter(m => m.item.kind === kind)
            .map(m => staticById(kind, m.item.id))
            .filter((e): e is StaticEntry => !!e)
        : staticRows.value[kind === 'page' ? 'pages' : 'settings'])
        .map(e => staticRow(e, kw ? matched.value.find(m => m.item.id === e.key)?.indices : undefined))
      result.push({ key: `${kind}s`, title: label, rows, empty: `未找到相关${label}` })
    }
    return result
  }

  if (!keyword.value.trim()) {
    const recent = props.recent.slice(0, 8)
    if (activeTab.value !== 'agent') {
      result.push({
        key: 'recent',
        title: '最近使用',
        rows: recent.map(c => sessionRow(c)),
        empty: userStore.isLoggedIn ? (recent.length ? undefined : '暂无历史会话') : '登录后可搜索历史会话'
      })
    }
    if (activeTab.value === 'all') {
      result.push({
        key: 'pages',
        title: '页面',
        rows: staticRows.value.pages.slice(0, 4).map(e => staticRow(e))
      })
    }
  } else {
    const chatMatches = matched.value.filter(m => m.item.kind === 'session')
    const agentMatches = matched.value.filter(m => m.item.kind === 'agent')
    if (activeTab.value !== 'agent') {
      const rows = chatMatches
        .map(m => sessionRow({ id: m.item.id, title: m.item.title } as ChatSession, m.indices))
      result.push({
        key: 'chats',
        title: '会话',
        rows,
        empty: searchLoading.value || rows.length ? undefined : '未找到相关会话'
      })
    }
    if (activeTab.value !== 'chat') {
      const rows = agentMatches
        .map(m => agentRow({ id: Number(m.item.id), name: m.item.title } as Agent, m.indices))
      result.push({
        key: 'agents',
        title: '智能体',
        rows,
        empty: !userStore.isLoggedIn
          ? '登录后可搜索智能体'
          : (searchLoading.value || rows.length ? undefined : '未找到相关智能体')
      })
    }
    const pageRows = matched.value
      .filter(m => m.item.kind === 'page')
      .map(m => staticById('page', m.item.id))
      .filter((e): e is StaticEntry => !!e)
      .map(e => staticRow(e))
    if (pageRows.length) {
      result.push({ key: 'pages', title: '页面', rows: pageRows })
    }
    const settingRows = matched.value
      .filter(m => m.item.kind === 'setting')
      .map(m => staticById('setting', m.item.id))
      .filter((e): e is StaticEntry => !!e)
      .map(e => staticRow(e))
    if (settingRows.length) {
      result.push({ key: 'settings', title: '设置', rows: settingRows })
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

/** 输入框内的按键：类别模式下退格清空时退出作用域 */
function onInputKeydown(e: KeyboardEvent): void {
  if (e.key === 'Backspace' && !keyword.value && scope.value) {
    e.preventDefault()
    exitScope()
  }
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
    scope.value = null
    activeTab.value = 'all'
    activeIndex.value = 0
    nextTick(() => inputRef.value?.focus())
    fetchIndex()
  }
})

watch([keyword, activeTab], () => {
  activeIndex.value = 0
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

    // 类别作用域胶囊：当前搜索类别（点击退出）
    .palette-scope-chip {
      display: flex;
      align-items: center;
      gap: 4px;
      flex-shrink: 0;
      padding: 3px 10px;
      border: none;
      border-radius: 999px;
      background: rgba($primary-color, 0.12);
      color: $primary-color;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.2s;

      &:hover {
        background: rgba($primary-color, 0.2);
      }

      .chip-x {
        font-size: 9px;
        opacity: 0.7;
      }
    }

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
    padding: 0 20px 4px;

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

  // 固定高度：结果出现/消失只影响内部滚动，不改弹窗整体高度（高度变化会让居中弹窗整体跳动）
  .palette-body-wrap {
    position: relative;

    .body-fade {
      position: absolute;
      left: 0;
      right: 0;
      height: 22px;
      pointer-events: none;
      z-index: 1;

      &.top {
        top: 0;
        background: linear-gradient(to bottom, #faf9f5 30%, rgba(250, 249, 245, 0));
      }

      &.bottom {
        bottom: 0;
        background: linear-gradient(to top, #faf9f5 30%, rgba(250, 249, 245, 0));
      }
    }
  }

  .palette-body {
    height: 400px;
    overflow-y: auto;
    padding: 4px 12px 12px;
    // 滚动条出现/消失不挤动内容宽度
    scrollbar-gutter: stable;
    scrollbar-width: thin;
    scrollbar-color: #d2cfc5 transparent;
    // 搜索加载中整体压暗：结果替换发生在亮度过渡内，消除"闪一下"的突兀感
    transition: opacity 0.16s ease;

    &.updating {
      opacity: 0.55;
    }

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
    // 新挂载的结果行柔和淡入（key 相同的行复用不重播，替换瞬间不再整块闪变）
    animation: palette-row-in 0.18s ease both;

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

    // 标题+描述双行（页面/设置/类别卡带描述；会话/智能体只有标题）
    .row-main {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 1px;
    }

    .row-title {
      min-width: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;

      // fuse 命中段高亮：主题色淡底
      :deep(mark.hl),
      .hl {
        background: rgba(42, 161, 169, 0.16);
        color: $primary-color;
        font-weight: 600;
        border-radius: 2px;
        padding: 0 1px;
      }
    }

    .row-desc {
      font-size: 12px;
      color: #8c8a82;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .row-category {
      flex-shrink: 0;
      font-size: 11px;
      color: #8c8a82;
      background: #f0eee6;
      border-radius: 8px;
      padding: 1px 8px;
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
    animation: palette-row-in 0.18s ease both;
  }

  @keyframes palette-row-in {
    from {
      opacity: 0;
      transform: translateY(3px);
    }

    to {
      opacity: 1;
      transform: none;
    }
  }

  // 搜索加载骨架：条形占位 + 扫光
  .palette-skel {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 10px;

    .skel-icon {
      width: 16px;
      height: 16px;
      flex-shrink: 0;
      border-radius: 5px;
      background: linear-gradient(90deg, #eceade 25%, #f6f4ec 50%, #eceade 75%);
      background-size: 200% 100%;
      animation: skel-sweep 1.1s linear infinite;
    }

    .skel-bar {
      height: 12px;
      flex: 1;
      border-radius: 6px;
      background: linear-gradient(90deg, #eceade 25%, #f6f4ec 50%, #eceade 75%);
      background-size: 200% 100%;
      animation: skel-sweep 1.1s linear infinite;
    }
  }

  @keyframes skel-sweep {
    from { background-position: 200% 0; }
    to { background-position: -200% 0; }
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
