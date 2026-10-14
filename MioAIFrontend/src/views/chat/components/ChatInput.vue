<template>
  <div class="chat-center-area" :class="{ 'has-messages': hasMessages, embedded }">
    <!-- 消息列表底边渐隐：贴在输入区上缘，消息滚出列表边界时柔和消隐而非硬切 -->
    <div v-if="hasMessages && !embedded" class="list-fade-overlay" aria-hidden="true"></div>
    <div class="welcome-section" v-if="!hasMessages && !loading">
      <h2 class="welcome-title">{{ welcomeTitle }}</h2>
    </div>
    <!-- 与输入框融合的上区（任务清单等，共享同一容器边框） -->
    <div class="chat-input-wrapper">
      <div
        class="chat-input-container"
        :class="{ 'drag-over': dragOver }"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
      >
        <slot name="above-input" />
        <!-- 待上传附件：一个文件一张卡片，直接作为输入容器的子元素（与输入框一体，无任何包装层）；
             GSAP 进出场由 usePendingCardAnimations 驱动——离场=四周向中间缩小消失+占位塌缩，
             兄弟卡片由文档流连续回流平滑补位 -->
        <AttachmentCard
          v-for="card in renderCards ?? []"
          :key="card.key"
          :ref="refSetter ? refSetter(card.key) : undefined"
          :item="card"
          :leaving="leavingKeys ? leavingKeys.has(card.key) : false"
          variant="card"
          removable
          @remove="emit('remove-pending', $event)"
        />
        <!-- 富文本输入区（contenteditable）：@ / 引用 token 以不可编辑内联节点长在文本流里
             （zcode 同构），不另起容器；modelValue 只承载纯文本，token 由 chatRefs 单独承载 -->
        <div class="input-main">
          <div
            ref="editorRef"
            class="chat-editor"
            contenteditable="true"
            role="textbox"
            aria-multiline="true"
            :data-placeholder="`向 ${agentName || 'MioBot'} 提问，使用@添加上下文，使用/选择命令或能力`"
            @input="onEditorInput"
            @keydown="onEditorKeydown"
            @keyup="onEditorKeyup"
            @click="detectTrigger"
            @focus="onEditorFocus"
            @blur="onEditorBlur"
            @paste="onEditorPaste"
            @copy="onEditorCopy"
            @cut="onEditorCopy($event, true)"
            @compositionstart="composing = true"
            @compositionend="onCompositionEnd"
          ></div>
        </div>
        <!-- @ / 触发的引用菜单：会话/知识库（@）与 命令/技能/MCP（/），键盘上下+回车选择 -->
        <div v-if="menuOpen" class="mention-menu" ref="menuBodyRef">
          <template v-for="section in menuSections" :key="section.title">
            <div class="menu-section-title">{{ section.title }}</div>
            <div
              v-for="row in section.rows"
              :key="row.key"
              class="menu-row"
              :class="[{ active: row.index === activeIndex }, 'tone-' + row.tone]"
              :data-index="row.index"
              @mousedown.prevent="row.pick()"
              @mouseenter="activeIndex = row.index"
            >
              <component :is="row.icon" class="row-icon" />
              <span class="row-main">
                <span class="row-title" :class="'tone-' + row.tone">{{ row.title }}</span>
                <span v-if="row.desc" class="row-desc">{{ row.desc }}</span>
              </span>
              <span v-if="row.suffix" class="row-suffix">{{ row.suffix }}</span>
            </div>
          </template>
          <div v-if="!flatMenuRows.length" class="menu-empty">
            {{ menuTrigger === '@' ? '未找到相关会话或知识库' : '未找到相关命令或能力' }}
          </div>
        </div>
        <!-- zcode 式底部工具栏：左附件+模型切换（多级菜单，+ 右侧）+思考等级、右发送 -->
        <div class="input-toolbar">
          <div class="toolbar-left">
            <input
              ref="fileInputRef"
              type="file"
              multiple
              class="hidden-file-input"
              @change="onFileChange"
            />
            <a-tooltip title="上传附件">
              <button type="button" class="attach-btn" @click="fileInputRef?.click()">
                <PlusOutlined />
              </button>
            </a-tooltip>
            <!-- 模型切换器（zcode 同款多级菜单）：左列供应商分组、右列该组模型 -->
                        <a-dropdown
              v-if="!cancelable"
              :trigger="['click']"
              placement="topLeft"
              :overlay-style="{ userSelect: 'none' }"
            >
              <div class="effort-selector" @click.prevent>
                <BrainIcon :size="14" class="effort-icon" :class="{ dimmed: effort === 'none' }" />
                <span class="effort-label">{{ effortLabel }}</span>
                <DownOutlined class="effort-caret" />
              </div>
              <template #overlay>
                <a-menu :selected-keys="[effort]" @click="onEffortClick">
                  <a-menu-item v-for="opt in effortOptions" :key="opt.value">
                    <BrainIcon :size="13" class="menu-brain" :class="{ dimmed: opt.value === 'none' }" />
                    <span>{{ opt.label }}</span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>

          <div class="toolbar-right">
            <ContextUsageIndicator v-if="!embedded && contextUsage" :data="contextUsage" />
            <button v-if="cancelable" type="button" class="cancel-btn" @mousedown.prevent @click="emit('cancel')">取消</button>
            <button
              v-if="cancelable"
              type="button"
              class="send-pill"
              :disabled="!canSend || loading"
              @click="emit('send', [...chatRefs])"
            >
              {{ loading ? '发送中…' : '发送' }}
            </button>
            <!-- 主输入：生成中变停止按钮（点击中断当前轮次）；空闲为发送 -->
            <a-tooltip v-if="!cancelable" :title="loading ? '停止' : ''">
              <a-button
                type="primary"
                class="send-btn"
                :disabled="!loading && !canSend"
                @click="loading ? emit('stop') : emit('send', [...chatRefs])"
              >
                <span v-if="loading" class="stop-square" />
                <ArrowUpOutlined v-else />
              </a-button>
            </a-tooltip>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h, nextTick, onMounted, ref, render as vueRender, watch } from 'vue'
import type { Component } from 'vue'
import { useUserStore } from '@/store/user'
import {
  ApiOutlined,
  ArrowUpOutlined,
  CheckOutlined,
  CompressOutlined,
  DatabaseOutlined,
  DownOutlined,
  MessageOutlined,
  PlusOutlined,
  RightOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import Fuse from 'fuse.js'
import BrainIcon from '@/components/BrainIcon.vue'
import AttachmentCard from './AttachmentCard.vue'
import ContextUsageIndicator from './ContextUsageIndicator.vue'
import { createTokenElement } from '../tokenDom'
import { getAllSessions, type AllSessionItem } from '@/api/chatMemory'
import type { ChatRefItem, ContextUsageData, KnowledgeBase, McpTool, PendingAttachment, Skill } from '@/types'

/** 思考档位全量标签（实际渲染哪些档由后端按模型能力返回） */
const EFFORT_LABELS: Record<string, string> = {
  max: '极限',
  xhigh: '超高',
  high: '最高',
  medium: '中等',
  low: '较低',
  minimal: '极低',
  none: '关闭'
}

/** 探测失败时的兜底档位 */
const FALLBACK_EFFORTS = ['high', 'medium', 'low', 'none']

const props = defineProps<{
  modelValue: string
  agentName?: string
  hasMessages: boolean
  loading: boolean
  /** 思考强度（后端返回的该模型支持的档位之一） */
  effort?: string
  /** 当前模型支持的思考档位（按能力探测，如实渲染） */
  supportedEfforts?: string[]
  /** 待上传卡片渲染列表（含离场动画中的卡，由父级动画组合式维护） */
  renderCards?: PendingAttachment[]
  /** 离场中的键（隐藏 X，防动画期间重复触发） */
  leavingKeys?: Set<string>
  /** v-for 动态 ref 登记器（父级动画组合式需要元素引用驱动 GSAP） */
  refSetter?: (key: string) => (el: unknown) => void
  /** 会话上下文容量估算（null/缺省隐藏指示器） */
  contextUsage?: ContextUsageData | null
  /** 嵌入模式（消息编辑复用）：去外层留白/居中/欢迎区，直接以输入框原貌嵌入 */
  embedded?: boolean
  /** 显示取消按钮（编辑模式用） */
  cancelable?: boolean
  /** 当前智能体绑定的知识库（@ 引用数据源之一） */
  knowledgeBases?: KnowledgeBase[]
  /** 当前智能体绑定的技能（/ 引用数据源） */
  skills?: Skill[]
  /** 当前智能体绑定的 MCP 服务（/ 引用数据源） */
  mcpTools?: McpTool[]
  /** 当前会话 id：切换即清空引用；@ 列表排除当前会话（自身上下文已在内） */
  currentChatId?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:effort', value: string): void
  (e: 'send', refs: ChatRefItem[]): void
  /** 生成中点击停止：中断当前轮次 */
  (e: 'stop'): void
  (e: 'cancel'): void
  (e: 'add-files', files: File[]): void
  (e: 'remove-pending', key: string): void
  (e: 'input-focus'): void
  (e: 'input-blur'): void
}>()

const userStore = useUserStore()

// ---------- 新对话问候语：按时段分短语池随机取一句；有简短画像时混入画像变体 ----------
// [起始小时, 结束小时(含), 短语池]；池内含 {profile} 的变体仅在画像存在且简短时启用
const GREETING_BUCKETS: Array<[number, number, string[]]> = [
  [0, 4, ['还没睡，{name}？', '夜深了，{name}，想聊点什么？', '这么晚还没休息？{name}，我在。', '还没睡，{name}？{profile}的事不急于这一晚。']],
  [5, 8, ['早安，{name}！今天想从什么开始？', '早上好，{name}。新的一天，有什么想做的？', '早安，{name}！今天{profile}的日程还顺利吗？']],
  [9, 11, ['上午好，{name}！有什么想一起解决的？', '嗨，{name}，上午想研究点什么？', '上午好，{name}！{profile}的活儿需要搭把手吗？']],
  [12, 13, ['中午好，{name}！忙碌间隙想聊点什么？', '午安，{name}。有什么我能帮忙的？']],
  [14, 17, ['下午好，{name}！下午想推进点什么？', '嗨，{name}，下午进展如何？', '下午好，{name}！今天{profile}那边今天有什么新进展？']],
  [18, 22, ['晚上好，{name}！今晚想聊点什么？', '晚上好呀，{name}。有什么新鲜事想问？', '晚上好，{name}！今天{profile}的事还顺利吗？']],
  [23, 23, ['还没睡，{name}？', '夜深了，{name}，想聊点什么？', '这么晚还没休息？{name}，我在。', '还没睡，{name}？{profile}的事不急于这一晚。']]
]

const welcomeTitle = computed(() => {
  const name = userStore.userName?.trim() || '朋友'
  const profile = userStore.userInfo?.userProfile?.trim()
  const hour = new Date().getHours()
  const pool = GREETING_BUCKETS.find(([from, to]) => hour >= from && hour <= to)?.[2]
    ?? ['嗨，{name}！有什么想聊的？']
  // 画像缺失或过长时不用画像变体，避免占位符漏出
  const usable = pool.filter(p => !p.includes('{profile}') || (profile && profile.length <= 12))
  const picked = usable[Math.floor(Math.random() * usable.length)]
  return picked.split('{name}').join(name).split('{profile}').join(profile ?? '')
})

const value = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v)
})

const effortOptions = computed(() =>
  (props.supportedEfforts?.length ? props.supportedEfforts : FALLBACK_EFFORTS)
    .map(v => ({ value: v, label: EFFORT_LABELS[v] ?? v }))
    .reverse()
)

const effort = computed(() => props.effort || 'high')

const effortLabel = computed(
  () => effortOptions.value.find(o => o.value === effort.value)?.label ?? EFFORT_LABELS[effort.value] ?? effort.value
)

function onEffortClick({ key }: { key: string | number }): void {
  emit('update:effort', String(key))
}

// ---------- 模型切换器：多级菜单（左列分组/右列模型），切换上抛父级联动思考档位 ----------


// ---------- 富文本编辑器（contenteditable）：纯文本编辑 + 内联引用 token ----------
const editorRef = ref<HTMLElement | null>(null)
const chatRefs = ref<ChatRefItem[]>([])
const tokenDestroyers = new Map<HTMLElement, () => void>()

/** 有文本或有引用即可发送（仅引用=让模型基于注入材料继续） */
const canSend = computed(() => !!props.modelValue.trim() || chatRefs.value.length > 0)

let composing = false

/** 编辑器纯文本：text 原文、BR 换行；token 是引用不是文本，不进 modelValue */
function textOfNode(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent ?? ''
  if (node.nodeType !== Node.ELEMENT_NODE) return ''
  const el = node as HTMLElement
  if (el.classList.contains('editor-token')) return ''
  if (el.tagName === 'BR') return '\n'
  return Array.from(el.childNodes).map(textOfNode).join('')
}

function editorText(): string {
  const el = editorRef.value
  return el ? textOfNode(el) : ''
}

/** 空态标记：placeholder 用 CSS ::before 呈现（token 在场时自动消失） */
function syncEmpty(): void {
  const el = editorRef.value
  if (!el) return
  const empty = el.textContent === '' && !el.querySelector('.editor-token')
  if (empty) el.setAttribute('data-empty', '')
  else el.removeAttribute('data-empty')
}

function emitText(): void {
  if (composing) return
  emit('update:modelValue', editorText())
  syncEmpty()
}

/** 编辑器初始渲染 + 外部值同步（草稿恢复/发送清空）：全量重建文本并清掉残留 token 节点
 *  （本地输入走 editorText 相同值短路，不会走到重建） */
function renderEditorText(text: string): void {
  const el = editorRef.value
  if (!el) return
  for (const wrapper of Array.from(el.querySelectorAll<HTMLElement>(':scope .editor-token'))) {
    destroyTokenIcon(wrapper)
    wrapper.remove()
  }
  el.textContent = ''
  if (text) {
    const lines = text.split('\n')
    lines.forEach((line, i) => {
      if (i > 0) el.appendChild(document.createElement('br'))
      if (line) el.appendChild(document.createTextNode(line))
    })
  }
  syncEmpty()
}

onMounted(() => {
  renderEditorText(props.modelValue)
})

watch(value, v => {
  const el = editorRef.value
  if (!el) return
  if (editorText() === v) return
  renderEditorText(v)
})

// 引用随文本清空而清空；不跨会话携带
watch(value, v => {
  if (!v && chatRefs.value.length) chatRefs.value = []
})
watch(
  () => props.currentChatId,
  () => {
    chatRefs.value = []
    closeMenu()
  }
)

// ---------- 光标上下文与触发检测 ----------
interface TriggerRange {
  node: Text
  start: number
  end: number
}

/** 光标所在文本节点（元素边界时回溯前一个文本节点的末尾） */
function caretTextInfo(): { node: Text; offset: number } | null {
  const sel = window.getSelection()
  const el = editorRef.value
  if (!sel || sel.rangeCount === 0 || !el) return null
  const range = sel.getRangeAt(0)
  if (!range.collapsed || !el.contains(range.startContainer)) return null
  let node = range.startContainer
  let offset = range.startOffset
  if (node.nodeType === Node.TEXT_NODE) {
    return { node: node as Text, offset }
  }
  if (node.nodeType === Node.ELEMENT_NODE) {
    const host = node as HTMLElement
    // offset 指向子节点索引：光标在其前——取该位置前最近的文本节点末尾
    let prev: Node | null = offset > 0 ? host.childNodes[offset - 1] : null
    while (prev && prev.nodeType !== Node.TEXT_NODE) {
      const last = prev.childNodes[prev.childNodes.length - 1]
      if (!last) break
      prev = last
    }
    if (prev && prev.nodeType === Node.TEXT_NODE) {
      return { node: prev as Text, offset: (prev as Text).length }
    }
    // 无前文（编辑器最前）：取第一个文本节点的开头
    const first = firstTextNode(host)
    if (first) return { node: first, offset: 0 }
  }
  return null
}

function firstTextNode(root: Node): Text | null {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  return walker.nextNode() as Text | null
}

const menuOpen = ref(false)
const menuTrigger = ref<'@' | '/'>('@')
let triggerRange: TriggerRange | null = null
const menuQuery = ref('')
const activeIndex = ref(0)
const menuBodyRef = ref<HTMLElement | null>(null)

function closeMenu(): void {
  menuOpen.value = false
  triggerRange = null
}

function openMenu(trigger: '@' | '/', range: TriggerRange, query: string): void {
  const wasClosed = !menuOpen.value
  menuOpen.value = true
  menuTrigger.value = trigger
  triggerRange = range
  menuQuery.value = query
  if (wasClosed) {
    activeIndex.value = 0
  }
  // 每次 @ 触发都刷新会话（新会话可能刚产生）；在途去重在 loadMentionSessions 内
  if (trigger === '@') {
    loadMentionSessions()
  }
}

watch([menuQuery, menuTrigger], () => {
  activeIndex.value = 0
})

const MENTION_FUSE_OPTIONS = {
  // zcode 命令面板同源的模糊参数：转置/缺字母也能命中（scripts/fuse-tune.mjs 调参定版的同族值）
  threshold: 0.45,
  ignoreLocation: true,
  minMatchCharLength: 1
}

/** 菜单候选项模糊过滤（fuse.js）：query 为空显示全部，非空按相关度返回 */
function fuzzyFilter<T extends { title: string; desc?: string }>(rows: T[], query: string): T[] {
  if (!query) return rows
  return new Fuse(rows, { ...MENTION_FUSE_OPTIONS, keys: ['title', 'desc'] })
    .search(query)
    .map(r => r.item)
}

const mentionSessions = ref<AllSessionItem[]>([])
let sessionsReqSeq = 0
let sessionsInflight = false

/** @ 菜单打开时刷新跨智能体会话（排除当前会话：自身上下文已在模型内） */
async function loadMentionSessions(): Promise<void> {
  if (sessionsInflight) return
  sessionsInflight = true
  const seq = ++sessionsReqSeq
  try {
    const rows = await getAllSessions(200)
    if (seq === sessionsReqSeq) {
      mentionSessions.value = rows.filter(s => s.conversationId !== props.currentChatId)
    }
  } catch {
    // 拉取失败保持空态行，不打断输入
  } finally {
    sessionsInflight = false
  }
}

interface MentionRow {
  key: string
  index: number
  icon: Component
  /** 类型色调（与会话/知识库/技能/MCP/Compact 的 token 颜色一致） */
  tone: 'session' | 'kb' | 'skill' | 'mcp' | 'compact'
  title: string
  desc?: string
  suffix?: string
  pick: () => void
}

interface MentionSection {
  title: string
  rows: MentionRow[]
}

const menuSections = computed<MentionSection[]>(() => {
  const query = menuQuery.value.trim().toLowerCase()
  if (menuTrigger.value === '@') {
    const sections: MentionSection[] = [
      {
        title: '会话',
        rows: fuzzyFilter(
          mentionSessions.value.map(s => ({ ...s, title: s.title || '新对话' })),
          query
        )
          .slice(0, 20)
          .map(s => ({
            key: `session_${s.conversationId}`,
            index: 0,
            icon: MessageOutlined,
            tone: 'session',
            title: s.title || '新对话',
            suffix: s.agentName,
            pick: () =>
              pickRef({
                type: 'session',
                id: s.conversationId,
                name: s.title || '新对话',
                agentName: s.agentName
              })
          }))
      }
    ]
    const kbRows = fuzzyFilter(
      (props.knowledgeBases ?? []).map(kb => ({ ...kb, title: kb.name })),
      query
    ).map(kb => ({
        key: `kb_${kb.id}`,
        index: 0,
        icon: DatabaseOutlined,
        tone: 'kb' as const,
        title: kb.name,
        desc: kb.description,
        pick: () => pickRef({ type: 'kb', kbId: kb.id, name: kb.name })
      }))
    if (kbRows.length) {
      sections.push({ title: '知识库', rows: kbRows })
    }
    return withIndex(sections)
  }
  const sections: MentionSection[] = [
    {
      title: '命令',
      rows: [
        {
          key: 'cmd_compact',
          index: 0,
          icon: CompressOutlined,
          tone: 'compact' as const,
          title: '/compact',
          desc: '压缩上下文',
          pick: pickCompact
        }
      ].filter(row => fuzzyFilter([row], query).length > 0)
    }
  ]
  const skillRows = fuzzyFilter(
    (props.skills ?? []).map(s => ({ ...s, title: s.name })),
    query
  ).map(s => ({
      key: `skill_${s.id}`,
      index: 0,
      icon: ThunderboltOutlined,
      tone: 'skill' as const,
      title: s.name,
      desc: s.description,
      pick: () => pickRef({ type: 'skill', name: s.name })
    }))
  if (skillRows.length) {
    sections.push({ title: '技能', rows: skillRows })
  }
  const mcpRows = fuzzyFilter(
    (props.mcpTools ?? []).map(m => ({ ...m, title: m.name })),
    query
  ).map(m => ({
      key: `mcp_${m.id}`,
      index: 0,
      icon: ApiOutlined,
      tone: 'mcp' as const,
      title: m.name,
      desc: m.description,
      pick: () => pickRef({ type: 'mcp', name: m.name })
    }))
  if (mcpRows.length) {
    sections.push({ title: 'MCP', rows: mcpRows })
  }
  return withIndex(sections)
})

/** 统一编号供键盘上下移动 */
function withIndex(sections: MentionSection[]): MentionSection[] {
  let counter = 0
  return sections.map(section => ({
    ...section,
    rows: section.rows.map(row => ({ ...row, index: counter++ }))
  }))
}

const flatMenuRows = computed<MentionRow[]>(() => menuSections.value.flatMap(s => s.rows))

/** 光标前的触发词探测：从光标回扫到空白边界，'@'/'/' 开头即开菜单（嵌入模式不启用） */
function detectTrigger(): void {
  if (props.embedded || composing) {
    closeMenu()
    return
  }
  const info = caretTextInfo()
  if (!info) {
    closeMenu()
    return
  }
  const before = (info.node.textContent ?? '').slice(0, info.offset)
  let i = before.length - 1
  while (i >= 0 && !/\s/.test(before[i]!)) i--
  const token = before.slice(i + 1)
  if (token.startsWith('@') && token.length <= 64) {
    openMenu('@', { node: info.node, start: i + 1, end: info.offset }, token.slice(1))
  } else if (token.startsWith('/') && token.length <= 64) {
    openMenu('/', { node: info.node, start: i + 1, end: info.offset }, token.slice(1))
  } else {
    closeMenu()
  }
}

// ---------- 引用 token 插入与移除（都在文本流内，无外置容器） ----------
function removeToken(wrapper: HTMLElement, item: ChatRefItem): void {
  tokenDestroyers.get(wrapper)?.()
  tokenDestroyers.delete(wrapper)
  wrapper.remove()
  const idx = chatRefs.value.indexOf(item)
  if (idx >= 0) chatRefs.value.splice(idx, 1)
  syncEmpty()
  emitText()
  refocusEditor()
}

/** 清理 token 节点上的 Vue 图标渲染（卸载防泄漏） */
function destroyTokenIcon(wrapper: HTMLElement): void {
  for (const icon of Array.from(wrapper.querySelectorAll<HTMLElement>(':scope .token-icon'))) {
    vueRender(null, icon)
  }
}

/** 删除光标处记录的触发词（菜单起始位 → 当前光标） */
function removeTriggerRange(): void {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return
  if (triggerRange && triggerRange.node.isConnected) {
    const tr = document.createRange()
    tr.setStart(triggerRange.node, triggerRange.start)
    tr.setEnd(triggerRange.node, triggerRange.end)
    tr.deleteContents()
    tr.collapse(true)
    sel.removeAllRanges()
    sel.addRange(tr)
  }
}

/** 移除编辑器里的 Compact token（压缩与其他引用互斥） */
function removeCompactToken(): void {
  const el = editorRef.value
  const wrapper = el?.querySelector<HTMLElement>(':scope .editor-token:has(.is-compact)')
  if (wrapper) {
    destroyTokenIcon(wrapper)
    wrapper.remove()
  }
  chatRefs.value = chatRefs.value.filter(r => r.type !== 'compact')
}

/**
 * 选区 → 标记文本（zcode PromptClipboardPlugin 对位）：token 输出 canonical 标记，
 * 其余保留原文本；copy/cut 时写入剪贴板——选中的是文字，复制到的是标记。
 */
function selectionToMarkdown(selection: Selection): string {
  const range = selection.getRangeAt(0)
  const walk = (node: Node): string => {
    if (node.nodeType === Node.TEXT_NODE) return node.textContent ?? ''
    if (node.nodeType === Node.ELEMENT_NODE) {
      const el = node as HTMLElement
      if (el.classList.contains('editor-token')) {
        return el.dataset.refCanonical ?? el.dataset.refName ?? ''
      }
    }
    // DocumentFragment/容器节点：递归拼接子节点
    return Array.from(node.childNodes).map(walk).join('')
  }
  return walk(range.cloneContents())
}

function onEditorCopy(e: ClipboardEvent, cut = false): void {
  const sel = window.getSelection()
  const el = editorRef.value
  if (!sel || sel.isCollapsed || sel.rangeCount === 0 || !el) return
  const range = sel.getRangeAt(0)
  if (!el.contains(range.commonAncestorContainer)) return
  const markdown = selectionToMarkdown(sel)
  if (!markdown) return
  e.clipboardData?.setData('text/plain', markdown)
  e.preventDefault()
  if (cut) {
    range.deleteContents()
    sel.removeAllRanges()
    for (const [wrapper, destroy] of [...tokenDestroyers]) {
      if (!el.contains(wrapper)) {
        destroy()
        tokenDestroyers.delete(wrapper)
        const item = refItemOf(wrapper)
        if (item) {
          const idx = chatRefs.value.indexOf(item)
          if (idx >= 0) chatRefs.value.splice(idx, 1)
        }
      }
    }
    syncEmpty()
    emitText()
  }
}

/** 光标处删除触发词并插入 token 节点 + 空格，光标落空格后 */
function pickRef(item: ChatRefItem): void {
  // Compact 是命令型引用，编辑器内最多一个
  if (item.type === 'compact' && chatRefs.value.some(r => r.type === 'compact')) {
    removeTriggerRange()
    closeMenu()
    emitText()
    refocusEditor()
    return
  }
  // 压缩命令只能单独出现：引入其他引用前先移除 Compact
  if (item.type !== 'compact') {
    removeCompactToken()
  }
  const el = editorRef.value
  const sel = window.getSelection()
  if (!el || !sel || sel.rangeCount === 0) return
  // 同一引用（同类型同目标）只保留一个：重复选择只剔除触发词
  const dup = chatRefs.value.find(r =>
    r.type === item.type && (r.id ?? r.kbId ?? r.name) === (item.id ?? item.kbId ?? item.name))
  if (dup && item.type !== 'compact') {
    removeTriggerRange()
    closeMenu()
    emitText()
    refocusEditor()
    return
  }
  removeTriggerRange()
  const range = sel.getRangeAt(0)
  const { wrapper, destroy } = createTokenElement(item)
  tokenDestroyers.set(wrapper, destroy)
  range.insertNode(wrapper)
  // 普通空格（pre-wrap 下保留）：Backspace 可先删空格再回退删 token，不被 nbsp 挡住
  const space = document.createTextNode(' ')
  wrapper.after(space)
  range.setStart(space, 1)
  range.collapse(true)
  sel.removeAllRanges()
  sel.addRange(range)
  chatRefs.value.push(item)
  closeMenu()
  syncEmpty()
  emitText()
}

/**
 * /compact：压缩命令单独出现——清空输入框内其他引用与文字，只留 Compact token。
 * 发送时由父级编排为压缩指令。
 */
function pickCompact(): void {
  const el = editorRef.value
  if (!el) return
  // 清空已有 token 与全部文本（Compact 不可与任何内容同现）
  for (const wrapper of Array.from(el.querySelectorAll<HTMLElement>(':scope .editor-token'))) {
    destroyTokenIcon(wrapper)
    wrapper.remove()
  }
  el.textContent = ''
  chatRefs.value = []
  const sel = window.getSelection()
  const range = document.createRange()
  range.selectNodeContents(el)
  range.collapse(false)
  if (sel) {
    sel.removeAllRanges()
    sel.addRange(range)
  }
  pickRef({ type: 'compact' })
}

function refocusEditor(): void {
  nextTick(() => {
    const el = editorRef.value
    el?.focus()
    const sel = window.getSelection()
    if (el && sel) {
      const range = document.createRange()
      range.selectNodeContents(el)
      range.collapse(false)
      sel.removeAllRanges()
      sel.addRange(range)
    }
  })
}

/** 光标紧邻的前一个 token 节点（Backspace 整体删除目标）：token 后的占位空格一并接管 */
function tokenBeforeCaret(): HTMLElement | null {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return null
  const range = sel.getRangeAt(0)
  if (!range.collapsed) return null
  const node = range.startContainer
  const offset = range.startOffset
  let prev: Node | null = null
  if (node.nodeType === Node.TEXT_NODE) {
    const text = node.textContent ?? ''
    // 光标前有真实字符：正常删字符，不接管（除非前缀全是空白=占位空格）
    if (text.slice(0, offset).trim() !== '') return null
    prev = node.previousSibling
  } else if (node.nodeType === Node.ELEMENT_NODE) {
    prev = (node as HTMLElement).childNodes[offset - 1] ?? null
  } else {
    return null
  }
  while (prev && prev.nodeType === Node.TEXT_NODE && (prev.textContent ?? '').trim() === '') {
    prev = prev.previousSibling
  }
  return prev && prev.nodeType === Node.ELEMENT_NODE && (prev as HTMLElement).classList.contains('editor-token')
    ? prev as HTMLElement
    : null
}

function moveActive(delta: number): void {
  const total = flatMenuRows.value.length
  if (!total) return
  activeIndex.value = (activeIndex.value + delta + total) % total
  nextTick(() => {
    menuBodyRef.value
      ?.querySelector(`[data-index="${activeIndex.value}"]`)
      ?.scrollIntoView({ block: 'nearest' })
  })
}

function onEditorKeydown(e: KeyboardEvent): void {
  if (menuOpen.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      moveActive(1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      moveActive(-1)
      return
    }
    if (e.key === 'Enter' || e.key === 'Tab') {
      e.preventDefault()
      flatMenuRows.value[activeIndex.value]?.pick()
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      closeMenu()
      return
    }
  }
  // Backspace 在 token 紧邻处：整体删除 token（含 Vue 卸载与 refs 同步）
  if (e.key === 'Backspace') {
    const wrapper = tokenBeforeCaret()
    if (wrapper) {
      e.preventDefault()
      const item = refItemOf(wrapper)
      if (item) {
        removeToken(wrapper, item)
      } else {
        wrapper.remove()
        syncEmpty()
        emitText()
      }
      return
    }
  }
  // 换行强制走 <br>（Chrome 默认插块级 div，会让纯文本提取出现双换行）
  if (e.key === 'Enter' && e.shiftKey && !e.isComposing) {
    e.preventDefault()
    document.execCommand('insertLineBreak')
    return
  }
  // 回车=发送（Shift+Enter 换行走上方分支；IME 组合中不触发）
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    emit('send', [...chatRefs.value])
  }
}

/** wrapper ↔ 引用项对应：按插入顺序与 DOM 顺序一致推断（token 不重排） */
function refItemOf(wrapper: HTMLElement): ChatRefItem | undefined {
  const el = editorRef.value
  if (!el) return undefined
  const wrappers = Array.from(el.querySelectorAll(':scope .editor-token'))
  const idx = wrappers.indexOf(wrapper)
  return idx >= 0 ? chatRefs.value[idx] : undefined
}

function onEditorInput(): void {
  emitText()
  detectTrigger()
}

function onEditorKeyup(e: KeyboardEvent): void {
  // 方向键移动光标后触发词上下文可能变化
  if (e.key.startsWith('Arrow')) detectTrigger()
}

function onEditorFocus(): void {
  emit('input-focus')
  detectTrigger()
}

function onEditorBlur(): void {
  emit('input-blur')
  // 菜单行用 mousedown.prevent 接管选择，不会走到这里；点外部即收起
  closeMenu()
}

function onCompositionEnd(): void {
  composing = false
  emitText()
  detectTrigger()
}

function onEditorPaste(e: ClipboardEvent): void {
  const files = Array.from(e.clipboardData?.files ?? [])
  if (files.length) {
    e.preventDefault()
    emit('add-files', files)
    return
  }
  const text = e.clipboardData?.getData('text/plain') ?? ''
  if (text) {
    e.preventDefault()
    document.execCommand('insertText', false, text)
  }
}

const fileInputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)

/** 拖拽上传：文件拖入输入框容器 */
function onDrop(event: DragEvent): void {
  dragOver.value = false
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (files.length) {
    emit('add-files', files)
  }
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length) {
    emit('add-files', files)
  }
  // 允许再次选择同一个文件
  input.value = ''
}
</script>

<style lang="scss" scoped>
.chat-center-area {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 24px;
  width: 100%;
  box-sizing: border-box;

  // 嵌入模式（消息编辑复用）：去外层留白与居中，输入框原貌嵌入；间距与光晕对齐主输入框
  &.embedded {
    padding: 0;
    flex: none;
    align-items: stretch;

    .welcome-section {
      display: none;
    }

    .chat-input-wrapper {
      max-width: none;
    }

    .chat-input-wrapper .chat-input-container {
      box-shadow: 0 4px 18px rgba(42, 161, 169, 0.1);

      &:focus-within {
        box-shadow: 0 4px 22px rgba(42, 161, 169, 0.22);
      }
    }

    .chat-input-wrapper .chat-input-container .input-main {
      padding: 16px 16px 8px;
    }

    .chat-input-wrapper .chat-input-container .input-toolbar {
      padding: 8px 14px 12px 14px;
    }
  }

  // 有消息时组合器固定在底部（常规流布局，消息区不再被遮挡）
  &.has-messages {
    flex: none;
    padding: 20px 24px 20px;

    .welcome-section {
      display: none;
    }
  }

  // 消息列表底边渐隐：悬在输入区上缘（负 top 压在列表底边之上），越靠近输入框越实
  .list-fade-overlay {
    position: absolute;
    top: -34px;
    left: 0;
    right: 0;
    height: 34px;
    z-index: 2;
    pointer-events: none;
    background: linear-gradient(to bottom, rgba(250, 249, 245, 0) 0%, #faf9f5 82%);
  }

  .welcome-section {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 16px;
    margin-bottom: 32px;

    .welcome-title {
      font-size: 24px;
      font-weight: 600;
      color: #141413;
      margin: 0;
      white-space: nowrap;
    }
  }

  .chat-input-wrapper {
    width: 100%;
    max-width: 800px;

    // 融合容器：待传卡片/任务清单（插槽）与输入框共处一个边框内
    .chat-input-container {
      position: relative;
      width: 100%;
      background: #fff;
      border: 1px solid #e8e6dc;
      border-radius: 16px;
      box-shadow: 0 4px 14px rgba(20, 20, 19, 0.05);
      // 只过渡视觉属性：高度由卡片 GSAP 逐帧驱动，transition:all 会与之打架造成收回卡顿
      transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;

      // 文件拖入悬停提示
      &.drag-over {
        border-color: $primary-color;
        background: rgba(42, 161, 169, 0.04);
      }

      &:focus-within {
        border-color: $primary-color;
        box-shadow: 0 4px 16px rgba(42, 161, 169, 0.2);
      }

      .input-main {
        padding: 14px 16px 6px;

        // 富文本输入区：与原 textarea 同排版；引用 token 内联在文本流中
        .chat-editor {
          width: 100%;
          min-height: 24px;
          max-height: 180px;
          overflow-y: auto;
          font-size: 15px;
          line-height: 1.5;
          color: #141413;
          white-space: pre-wrap;
          word-break: break-word;
          outline: none;
          caret-color: $primary-color;
          cursor: text;

          // placeholder 绝对定位浮在下层：不占行盒，空态光标停在行首而非占位文字之后
          position: relative;

          &[data-empty]::before {
            content: attr(data-placeholder);
            position: absolute;
            left: 0;
            top: 0;
            color: #8c8a82;
            pointer-events: none;
          }

          // token 内是真实文本节点（可选中、复制经拦截转标记）；图标为装饰不参与选区
          :deep(.editor-token) {
            display: inline-flex;
            align-items: center;
            gap: 5px;
            vertical-align: baseline;
            margin: 0 2px;
            cursor: default;
            font-weight: 500;

            .token-icon {
              font-size: 13px;
              flex-shrink: 0;
              user-select: none;
              pointer-events: none;
            }

            &.is-session { color: $token-session; }
            &.is-kb { color: $token-kb; }
            &.is-skill { color: $token-skill; }
            &.is-mcp { color: $token-mcp; }
            &.is-compact { color: $token-compact; }
          }
        }
      }

      // @ / 触发的引用菜单：悬浮于输入容器上方（覆盖消息区）
      .mention-menu {
        position: absolute;
        left: 0;
        right: 0;
        bottom: calc(100% + 8px);
        z-index: 40;
        max-height: 320px;
        overflow-y: auto;
        padding: 8px;
        background: #fff;
        border: 1px solid #e8e6dc;
        border-radius: 14px;
        box-shadow: 0 12px 40px rgba(20, 20, 19, 0.14);

        .menu-section-title {
          padding: 8px 10px 4px;
          font-size: 12px;
          color: #8c8a82;
          user-select: none;
        }

        .menu-row {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 8px 10px;
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
            flex-shrink: 0;
            font-size: 14px;
            color: #8c8a82;
          }

          &.tone-session .row-icon { color: $token-session; }
          &.tone-kb .row-icon { color: $token-kb; }
          &.tone-skill .row-icon { color: $token-skill; }
          &.tone-mcp .row-icon { color: $token-mcp; }
          &.tone-compact .row-icon { color: $token-compact; }

          .row-main {
            flex: 1;
            min-width: 0;
            display: flex;
            align-items: baseline;
            gap: 8px;
          }

          // 名称优先且完整显示（不截断）；描述在剩余空间内让位截断
          .row-title {
            flex-shrink: 0;
            white-space: nowrap;
            font-weight: 500;

            &.tone-session { color: $token-session; }
            &.tone-kb { color: $token-kb; }
            &.tone-skill { color: $token-skill; }
            &.tone-mcp { color: $token-mcp; }
            &.tone-compact { color: $token-compact; }
          }

          .row-desc {
            flex: 1;
            min-width: 0;
            font-size: 12px;
            color: #8c8a82;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }

          .row-suffix {
            flex-shrink: 0;
            font-size: 11px;
            color: #8c8a82;
            max-width: 80px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
        }

        .menu-empty {
          padding: 12px 10px;
          font-size: 13px;
          color: #8c8a82;
          user-select: none;
        }
      }

      // zcode 式工具栏：左附件+思考等级 / 右上下文容量+发送（圆角方形）
      .input-toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 6px 10px 10px 12px;
        user-select: none;

        .toolbar-left {
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .toolbar-right {
          display: flex;
          align-items: center;
          gap: 4px;
        }
      }

      .hidden-file-input {
        display: none;
      }

      .attach-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        height: 30px;
        padding: 0;
        border: none;
        border-radius: 10px;
        background: transparent;
        color: #5f5d55;
        font-size: 15px;
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
          background: #f0ede4;
          color: $primary-color;
        }
      }

      .effort-selector {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 5px 10px;
        border-radius: 10px;
        font-size: 13px;
        color: #5f5d55;
        cursor: pointer;
        user-select: none;
        transition: background 0.2s;

        &:hover {
          background: #f0ede4;
        }

        .effort-icon {
          color: $primary-color;

          &.dimmed {
            color: #b0aea5;
          }
        }

        .effort-caret {
          font-size: 10px;
          color: #8c8a82;
        }
      }

      // 取消按钮（编辑模式）：与发送成组靠右
      .cancel-btn {
        margin-right: 8px;
        height: 30px;
        padding: 0 14px;
        border: 1px solid #d8d5cc;
        border-radius: 8px;
        background: #fff;
        color: #5f5d55;
        font-size: 13px;
        cursor: pointer;
        transition: all 0.2s;

        &:hover {
          border-color: $primary-color;
          color: $primary-color;
        }
      }

      // 发送 pill（编辑模式，原编辑发送按钮样式）
      .send-pill {
        height: 30px;
        padding: 0 16px;
        border: none;
        border-radius: 8px;
        background: $primary-color;
        color: #fff;
        font-size: 13px;
        cursor: pointer;
        transition: all 0.2s;

        &:hover:not(:disabled) {
          background: darken($primary-color, 8%);
        }

        &:disabled {
          background: #b0aea5;
          cursor: not-allowed;
        }
      }

      .send-btn {
        width: 34px;
        height: 34px;
        min-width: 34px;
        padding: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 10px;
        background: $primary-color;
        border-color: $primary-color;
        flex-shrink: 0;

        &:hover:not(:disabled) {
          background: darken($primary-color, 10%);
          border-color: darken($primary-color, 10%);
        }

        &:disabled {
          background: #e8e6dc;
          border-color: #e8e6dc;
          color: #b0aea5;
        }

        :deep(.anticon) {
          font-size: 15px;
          display: flex;
        }

        // 生成中的停止态：背景保持主题色，白色小方块即停止（zcode 同款）
        .stop-square {
          width: 11px;
          height: 11px;
          border-radius: 2px;
          background: #fff;
          display: inline-block;
        }
      }
    }
  }
}

.menu-brain {
  color: $primary-color;
  margin-right: 8px;
  vertical-align: -2px;

  &.dimmed {
    color: #b0aea5;
  }
}

// 模型切换菜单（overlay 挂 body，但节点属于本组件模板、scoped 属性可命中）
.model-menu-name {
  display: inline-block;
  max-width: 240px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}

.model-check {
  color: $primary-color;
  margin-left: 8px;
  font-size: 12px;
}

// zcode 同款多级模型菜单：左列分组（当前组高亮+勾选）、右列该组模型
.model-cascade {
  display: flex;
  width: 380px;
  min-height: 200px;
  max-height: 360px;
  padding: 6px;
  background: #fff;
  border: 1px solid #e8e6dc;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(20, 20, 19, 0.14);
  overflow: hidden;

  .cascade-left {
    display: flex;
    flex-direction: column;
    gap: 2px;
    width: 150px;
    flex-shrink: 0;
    padding: 4px;
    border-right: 1px solid #f0ede4;
    overflow-y: auto;

    .cascade-group {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 6px;
      padding: 7px 9px;
      border: none;
      border-radius: 8px;
      background: transparent;
      color: #3d3b35;
      font-size: 13px;
      cursor: pointer;
      text-align: left;
      transition: background 0.15s;

      .group-label {
        min-width: 0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .group-meta {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        flex-shrink: 0;

        .group-check {
          color: $primary-color;
          font-size: 11px;
        }

        .group-arrow {
          color: #b0aea5;
          font-size: 10px;
        }
      }

      &:hover {
        background: #f0ede4;
      }

      &.active {
        background: rgba(42, 161, 169, 0.1);
        color: #141413;
      }
    }

    .cascade-manage {
      margin-top: auto;
      padding: 7px 9px;
      border: none;
      border-top: 1px solid #f0ede4;
      border-radius: 0;
      background: transparent;
      color: #6e6b62;
      font-size: 13px;
      text-align: left;
      cursor: pointer;

      &:hover {
        color: $primary-color;
      }
    }
  }

  .cascade-right {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 4px;
    overflow-y: auto;

    .cascade-model {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 7px 9px;
      border: none;
      border-radius: 8px;
      background: transparent;
      color: #3d3b35;
      font-size: 13px;
      cursor: pointer;
      text-align: left;
      transition: background 0.15s;

      &:hover {
        background: #f0ede4;
      }

      &.active {
        background: rgba(42, 161, 169, 0.1);
        color: #141413;
      }

      .vision-badge {
        flex-shrink: 0;
        padding: 1px 7px;
        border-radius: 999px;
        background: rgba(42, 161, 169, 0.12);
        color: $primary-color;
        font-size: 11px;
      }

      .model-check {
        margin-left: auto;
      }
    }

    .cascade-empty {
      padding: 12px 9px;
      font-size: 12px;
      color: #8c8a82;
      user-select: none;
    }
  }
}

@media (max-width: 768px) {
  .chat-center-area {
    padding: 16px;

    .welcome-section {
      gap: 12px;

      .welcome-title {
        font-size: 20px;
      }
    }

    .chat-input-wrapper {
      .chat-input-container {
        .input-main {
          padding: 12px 14px 4px;

          .chat-editor {
            font-size: 14px;
          }
        }

        .send-btn {
          width: 32px;
          height: 32px;
          min-width: 32px;

          :deep(.anticon) {
            font-size: 14px;
          }
        }
      }
    }
  }
}

@media (max-width: 480px) {
  .chat-center-area {
    .welcome-section {
      .welcome-title {
        font-size: 20px;
      }
    }
  }
}
</style>
