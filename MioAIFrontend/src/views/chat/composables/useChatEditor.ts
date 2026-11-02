import { computed, nextTick, onMounted, ref, watch, render as vueRender } from 'vue'
import { createTokenElement } from '../tokenDom'
import { getAllSessions, type AllSessionItem } from '@/api/chatMemory'
import {
  buildMentionSections,
  type MentionRow,
  type MentionSection
} from './mentionMenu'
import type { ChatRefItem, KnowledgeBase, McpTool, Skill } from '@/types'

/**
 * 富文本输入编辑器（contenteditable）+ @ / 引用 token + 触发菜单。
 * 从 ChatInput 抽出：token 内联在文本流中（zcode 同构），modelValue 只承载纯文本，
 * token 由 chatRefs 单独承载。依赖 DOM 与 props，故以组合式形式复用。
 */

export type { MentionRow, MentionSection }

export interface UseChatEditorOptions {
  /** 当前纯文本（v-model） */
  modelValue: () => string
  /** 文本变更上抛 */
  onUpdateModelValue: (value: string) => void
  /** 发送（回车或发送按钮） */
  onSend: () => void
  /** 文件拖入/粘贴/选择 */
  onAddFiles: (files: File[]) => void
  onFocus: () => void
  onBlur: () => void
  /** 嵌入模式未启用菜单时不做 trigger 检测 */
  menuEnabled: () => boolean
  /** @ 列表排除的当前会话 */
  currentChatId: () => string | undefined
  knowledgeBases: () => KnowledgeBase[] | undefined
  skills: () => Skill[] | undefined
  mcpTools: () => McpTool[] | undefined
}

interface TriggerRange {
  node: Text
  start: number
  end: number
}

export function useChatEditor(options: UseChatEditorOptions) {
  const editorRef = ref<HTMLElement | null>(null)
  const chatRefs = ref<ChatRefItem[]>([])
  const tokenDestroyers = new Map<HTMLElement, () => void>()

  /** 有文本或有引用即可发送（仅引用=让模型基于注入材料继续） */
  const canSend = computed(() => !!options.modelValue().trim() || chatRefs.value.length > 0)

  let composing = false

  // ---------- 纯文本提取 ----------
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
    options.onUpdateModelValue(editorText())
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
    renderEditorText(options.modelValue())
  })

  watch(options.modelValue, v => {
    const el = editorRef.value
    if (!el) return
    if (editorText() === v) return
    renderEditorText(v)
  })

  // 引用随文本清空而清空；不跨会话携带
  watch(options.modelValue, v => {
    if (!v && chatRefs.value.length) chatRefs.value = []
  })
  watch(options.currentChatId, () => {
    chatRefs.value = []
    closeMenu()
  })

  // ---------- 光标上下文与触发检测 ----------
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
      let prev: Node | null = offset > 0 ? host.childNodes[offset - 1] ?? null : null
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
        mentionSessions.value = rows.filter(s => s.conversationId !== options.currentChatId())
      }
    } catch {
      // 拉取失败保持空态行，不打断输入
    } finally {
      sessionsInflight = false
    }
  }

  const menuSections = computed<MentionSection[]>(() =>
    buildMentionSections(
      menuTrigger.value,
      {
        sessions: mentionSessions.value,
        knowledgeBases: options.knowledgeBases() ?? [],
        skills: options.skills() ?? [],
        mcpTools: options.mcpTools() ?? []
      },
      menuQuery.value.trim().toLowerCase(),
      { pickRef, pickCompact }
    )
  )

  const flatMenuRows = computed<MentionRow[]>(() => menuSections.value.flatMap(s => s.rows))

  /** 光标前的触发词探测：从光标回扫到空白边界，'@'/'/' 开头即开菜单（嵌入模式不启用） */
  function detectTrigger(): void {
    if (!options.menuEnabled() || composing) {
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
      options.onSend()
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
    options.onFocus()
    detectTrigger()
  }

  function onEditorBlur(): void {
    options.onBlur()
    // 菜单行用 mousedown.prevent 接管选择，不会走到这里；点外部即收起
    closeMenu()
  }

  function onCompositionStart(): void {
    composing = true
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
      options.onAddFiles(files)
      return
    }
    const text = e.clipboardData?.getData('text/plain') ?? ''
    if (text) {
      e.preventDefault()
      document.execCommand('insertText', false, text)
    }
  }

  /** 发送时清空引用并重建空态（父级发送后调用） */
  function clearRefs(): void {
    chatRefs.value = []
  }

  return {
    editorRef,
    chatRefs,
    canSend,
    menuOpen,
    menuTrigger,
    menuQuery,
    activeIndex,
    menuBodyRef,
    menuSections,
    flatMenuRows,
    onEditorInput,
    onEditorKeydown,
    onEditorKeyup,
    onEditorFocus,
    onEditorBlur,
    onEditorPaste,
    onEditorCopy,
    onCompositionStart,
    onCompositionEnd,
    detectTrigger,
    clearRefs
  }
}
