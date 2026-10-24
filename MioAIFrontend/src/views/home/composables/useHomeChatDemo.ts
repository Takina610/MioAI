import { onUnmounted, ref } from 'vue'
import type { ChatMessage, MessageBlock, PendingAttachment } from '@/types'
import type { DemoEvent } from '../demoScenarios'
import { DEMO_AGENTS } from '../demoAgents'

/** 流式节奏：目标时长内匀速流入（长文本自动加大分片，不拖节奏） */
const THINKING_TICK = 70
const ANSWER_TICK = 45
const THINKING_TARGET = 3600
const ANSWER_TARGET = 5200
/** 工具块 running 态展示时长 */
const TOOL_RUN_MS = 1400
/** 输入框打字节奏 */
const TYPE_TICK = 65
const QUERY_TICK = 110

/** 演示停止哨兵（组件卸载时打断异步链） */
class DemoStopped extends Error {}

/**
 * 首页聊天演示播放器：按剧本推进真实消息块（thinking/工具/引用/回答），
 * 变异语义与对话页 useChatStream.applyEnvelope 同构——流式动画、耗时统计、
 * 过程折叠全部由既有消息组件自动呈现。
 * 用户消息先在真实输入框里演示「打字 → @/菜单选引用 → 发送」全过程：
 * 经 execCommand/合成键盘事件驱动 ChatInput 的原生输入与菜单逻辑。
 */
export function useHomeChatDemo() {
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  /** 左轨当前智能体 + 会话侧栏联动（流式期间该会话显示执行动画） */
  const currentAgentId = ref(1)
  const currentSessionId = ref('')
  const streamingSessionId = ref<string | null>(null)
  /** 输入框内的待上传附件卡片（随剧本演示上传过程） */
  const pendingFiles = ref<PendingAttachment[]>([])

  let token = 0
  let timer: number | null = null
  const reducedMotion =
    typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

  function later(ms: number): Promise<void> {
    const my = token
    return new Promise((resolve, reject) => {
      const arm = (): void => {
        timer = window.setTimeout(() => {
          if (my !== token) {
            reject(new DemoStopped())
            return
          }
          // 后台标签页挂起播放（回来后从等待点继续）
          if (document.hidden) {
            arm()
            return
          }
          resolve()
        }, reducedMotion ? Math.min(ms, 60) : ms)
      }
      arm()
    })
  }

  /** 往最后一条消息追加块，返回响应式代理（直接变异可触发视图更新） */
  function pushBlock(block: MessageBlock): MessageBlock {
    const msg = messages.value[messages.value.length - 1]!
    if (!msg.blocks) msg.blocks = []
    msg.blocks.push(block)
    return msg.blocks[msg.blocks.length - 1]!
  }

  function pushMessage(msg: ChatMessage): void {
    messages.value.push(msg)
  }

  /** 流式流入：按目标时长自适应分片（长文本加大步长，节奏恒定不拖沓） */
  async function streamText(
    block: { text: string },
    full: string,
    tick: number,
    targetMs: number
  ): Promise<void> {
    if (reducedMotion) {
      block.text = full
      return
    }
    const chunk = Math.max(2, Math.ceil((full.length * tick) / targetMs))
    while (block.text.length < full.length) {
      block.text = full.slice(0, Math.min(block.text.length + chunk, full.length))
      await later(tick)
    }
  }

  // ---------- 真实输入框驱动（打字 / 引用菜单 / 发送） ----------

  function getEditor(): HTMLElement | null {
    return document.querySelector<HTMLElement>('.demo-input .chat-editor')
  }

  function pressEditorKey(key: string): void {
    getEditor()?.dispatchEvent(
      new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
    )
  }

  async function typeChars(text: string, tick: number): Promise<void> {
    for (const ch of text) {
      const el = getEditor()
      // 焦点被用户点走：中止打字动画（残留内容由后续剧本的占用检查自愈）
      if (!el || document.activeElement !== el) return
      document.execCommand('insertText', false, ch)
      await later(tick + Math.floor(Math.random() * 40))
    }
  }

  /** 输入框内的附件上传演示：进度环走满后转完成态（变异响应式代理以驱动视图） */
  async function performUpload(name: string, size: number): Promise<void> {
    pendingFiles.value = [{ key: `demo-file-${Date.now()}`, name, size, status: 'uploading', progress: 0 }]
    const card = pendingFiles.value[pendingFiles.value.length - 1]!
    if (reducedMotion) {
      card.status = 'done'
      await later(400)
      return
    }
    for (let p = 8; p <= 100; p += 9) {
      card.progress = Math.min(p, 100)
      await later(90)
    }
    card.progress = 100
    card.status = 'done'
    await later(300)
  }

  /**
   * 用户消息的输入演示：聚焦 →（可选）附件上传 →（可选）触发词开菜单 → 过滤 →
   * 回车选中 → 打正文 → 清空输入（视觉即"发送"）。输入框被真实用户占用时返回 false。
   */
  async function performUserInput(
    typed: string,
    refSpec?: { trigger: '@' | '/'; query: string },
    attachment?: { name: string; size: number }
  ): Promise<boolean> {
    const el = getEditor()
    if (!el || reducedMotion) return false
    if ((el.textContent ?? '').trim() || el.querySelector('.editor-token') || pendingFiles.value.length) return false

    el.focus({ preventScroll: true })
    const sel = window.getSelection()
    const range = document.createRange()
    range.selectNodeContents(el)
    range.collapse(false)
    sel?.removeAllRanges()
    sel?.addRange(range)
    await later(320)

    if (attachment) {
      await performUpload(attachment.name, attachment.size)
    }

    if (refSpec) {
      await typeChars(refSpec.trigger, QUERY_TICK)
      await later(700) // 菜单亮相
      await typeChars(refSpec.query, QUERY_TICK)
      await later(450)
      pressEditorKey('ArrowDown') // 确认高亮在第一行（默认即是，防御性归位）
      await later(120)
      pressEditorKey('Enter') // 选中行 → 原生 pickRef 插入 token 芯片
      await later(300)
    }

    await typeChars(typed, TYPE_TICK)
    await later(600)

    // 发送：输入与附件卡清空 + 消息上屏（不触发真实 send 事件）
    el.focus({ preventScroll: true })
    document.execCommand('selectAll')
    document.execCommand('delete')
    pendingFiles.value = []
    await later(150)
    return true
  }

  async function playEvents(events: DemoEvent[]): Promise<void> {
    for (const ev of events) {
      await later(ev.wait ?? 300)
      switch (ev.type) {
        case 'user':
          await performUserInput(ev.typed, ev.ref, ev.attachment)
          pushMessage({
            id: `demo-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
            role: 'user',
            content: ev.content,
            blocks: ev.blocks?.map(b => ({ ...b })),
            createTime: new Date()
          })
          break
        case 'start':
          isLoading.value = true
          streamingSessionId.value = currentSessionId.value
          pushMessage({
            id: `demo-${Date.now()}-a`,
            role: 'assistant',
            content: '',
            blocks: [],
            createTime: new Date()
          })
          break
        case 'kb':
          pushBlock({ type: 'kb', groups: ev.groups })
          break
        case 'thinking': {
          const b = pushBlock({ type: 'thinking', text: '', startedAt: Date.now() }) as Extract<
            MessageBlock,
            { type: 'thinking' }
          >
          await streamText(b, ev.text, THINKING_TICK, THINKING_TARGET)
          b.durationMs = Math.max(600, Date.now() - (b.startedAt ?? Date.now()))
          break
        }
        case 'tool': {
          const b = pushBlock({
            type: 'tool',
            tool: ev.tool,
            desc: ev.desc,
            args: ev.args,
            status: 'running'
          }) as Extract<MessageBlock, { type: 'tool' }>
          await later(ev.duration ?? 1800)
          b.status = 'done'
          b.result = ev.result
          break
        }
        case 'skill':
          pushBlock({ type: 'skill_use', name: ev.name, content: ev.content })
          break
        case 'member': {
          const b = pushBlock({
            type: 'member',
            id: `member-${ev.memberId}`,
            memberId: ev.memberId,
            memberName: ev.memberName,
            memberIcon: ev.memberIcon,
            status: 'running',
            text: ''
          }) as Extract<MessageBlock, { type: 'member' }>
          await later(650) // 输入点动画亮相
          await streamText(b, ev.text, ANSWER_TICK, 3000)
          b.status = 'done'
          break
        }
        case 'answer': {
          const b = pushBlock({ type: 'text', text: '' }) as Extract<MessageBlock, { type: 'text' }>
          await streamText(b, ev.text, ANSWER_TICK, ANSWER_TARGET)
          break
        }
        case 'ui':
          pushBlock({ ...ev.block })
          break
        case 'replay': {
          // 真实会话照搬重放：按原始块序推进（思考/文本流式、工具走 running→done、其余整块上屏）
          for (const raw of ev.blocks) {
            const block = JSON.parse(JSON.stringify(raw)) as MessageBlock
            if (block.type === 'thinking') {
              const b = pushBlock({ ...block, text: '', startedAt: Date.now() }) as Extract<
                MessageBlock,
                { type: 'thinking' }
              >
              await later(300)
              await streamText(b, block.text, THINKING_TICK, THINKING_TARGET)
              b.durationMs = Math.max(600, Date.now() - (b.startedAt ?? Date.now()))
            } else if (block.type === 'text') {
              const b = pushBlock({ type: 'text', text: '' }) as Extract<MessageBlock, { type: 'text' }>
              await streamText(b, block.text, ANSWER_TICK, ANSWER_TARGET)
            } else if (block.type === 'tool') {
              const b = pushBlock({ ...block, status: 'running' }) as Extract<MessageBlock, { type: 'tool' }>
              await later(TOOL_RUN_MS)
              b.status = 'done'
            } else {
              pushBlock(block)
            }
          }
          const msg = messages.value[messages.value.length - 1]!
          msg.content = (msg.blocks ?? [])
            .filter((b): b is Extract<MessageBlock, { type: 'text' }> => b.type === 'text')
            .map(b => b.text)
            .join('\n\n')
          msg.durationMs = ev.durationMs
          isLoading.value = false
          streamingSessionId.value = null
          break
        }
        case 'finish': {
          const msg = messages.value[messages.value.length - 1]!
          msg.content = (msg.blocks ?? [])
            .filter((b): b is Extract<MessageBlock, { type: 'text' }> => b.type === 'text')
            .map(b => b.text)
            .join('\n\n')
          msg.durationMs = Date.now() - msg.createTime.getTime()
          isLoading.value = false
          streamingSessionId.value = null
          break
        }
      }
    }
  }

  /** 播放单个会话剧本：播完保持完成态，不自动轮换（切换由用户点击左轨/会话触发） */
  async function run(sessionId: string): Promise<void> {
    try {
      let agent = DEMO_AGENTS.find(a => a.sessions.some(s => s.id === sessionId))
      let session = agent?.sessions.find(s => s.id === sessionId)
      if (!agent || !session) {
        agent = DEMO_AGENTS[0]!
        session = agent.sessions[0]!
      }
      currentAgentId.value = agent.id
      currentSessionId.value = session.id
      await later(600)
      await playEvents(session.events)
    } catch (e) {
      // DemoStopped=正常打断（卸载/用户切会话）；其余为剧本执行错误，上抛诊断不静默吞掉
      if (!(e instanceof DemoStopped)) console.error('[home-demo] 播放中断', e)
    }
  }

  function resetState(): void {
    messages.value = []
    isLoading.value = false
    streamingSessionId.value = null
    pendingFiles.value = []
    // 清掉编辑器残留（跳转可能发生在打字阶段中），同步通知 ChatInput 收敛 modelValue
    const el = getEditor()
    if (el) {
      el.textContent = ''
      el.dispatchEvent(new Event('input', { bubbles: true }))
    }
  }

  function start(): void {
    stop()
    token++
    void run(DEMO_AGENTS[0]!.sessions[0]!.id)
  }

  /** 用户点击左轨智能体：切到该智能体的第一个会话剧本 */
  function switchAgent(agentId: number): void {
    const agent = DEMO_AGENTS.find(a => a.id === agentId)
    if (!agent || agentId === currentAgentId.value) return
    stop()
    token++
    resetState()
    void run(agent.sessions[0]!.id)
  }

  /** 用户点击会话：立即切到该会话对应的剧本重播 */
  function jumpToSession(sessionId: string): void {
    if (sessionId === currentSessionId.value) return
    stop()
    token++
    resetState()
    void run(sessionId)
  }

  function stop(): void {
    token++
    if (timer != null) {
      window.clearTimeout(timer)
      timer = null
    }
  }

  onUnmounted(stop)

  return {
    messages,
    isLoading,
    currentAgentId,
    currentSessionId,
    streamingSessionId,
    pendingFiles,
    start,
    jumpToSession,
    switchAgent
  }
}
