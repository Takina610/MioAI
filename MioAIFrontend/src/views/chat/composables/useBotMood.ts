import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { ChatMessage, MessageBlock } from '@/types'

/**
 * 会话执行情况 → 机器人情绪（39 态引擎状态机的运行时映射）。
 * 流式期间按最后一个内容块判别（思考/搜索/工具/编写/提问），
 * 回合完成转一圈撒花（fxSignal），切换会话伸个懒腰，
 * 在本页持续闲置才犯困入睡（与消息本身的年龄无关）。
 */
export function useBotMood(
  messages: Ref<ChatMessage[]>,
  isLoading: Ref<boolean>,
  listening: Ref<boolean>
) {
  const mood = ref('idle')
  /** 里程碑特效信号：+1 = 转一圈撒花 */
  const fxSignal = ref(0)

  let holdUntil = 0
  let holdMood = ''
  let holdTimer: number | undefined
  let wasLoading = false
  /** 本页活跃时钟：会话切换/新消息/流式都会刷新；犯困按它计时 */
  let lastActiveAt = Date.now()

  const lastAssistant = computed<ChatMessage | undefined>(() => {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === 'assistant') return messages.value[i]
    }
    return undefined
  })

  /** 流式签名：块数量/最后块类型/状态变化都会变化（深层 push 由 setChatMessages/块数组变更触发） */
  const signature = computed(() => {
    const la = lastAssistant.value
    const blocks = la?.blocks ?? []
    const last = blocks[blocks.length - 1]
    return [
      messages.value.length,
      la?.id ?? '',
      blocks.length,
      last ? `${last.type}:${'status' in last ? last.status : ''}` : '',
      isLoading.value,
      listening.value,
      la?.interrupted ? 1 : 0
    ].join('|')
  })

  function toolMood(tool: string): string {
    if (/search|web|fetch|browse|http|news/i.test(tool)) return 'searching'
    if (/write|edit|read|file|doc|skill|todo/i.test(tool)) return 'writing'
    return 'working'
  }

  function hold(name: string, ms: number): void {
    holdMood = name
    holdUntil = Date.now() + ms
    mood.value = name
    if (holdTimer) window.clearTimeout(holdTimer)
    holdTimer = window.setTimeout(() => {
      holdUntil = 0
      evaluate()
    }, ms)
  }

  function evaluate(): void {
    if (Date.now() < holdUntil) return
    if (isLoading.value) {
      const la = lastAssistant.value
      const blocks = la?.blocks ?? []
      const last = blocks[blocks.length - 1]
      mood.value = deriveBusyMood(la, last)
      return
    }
    if (listening.value) {
      lastActiveAt = Date.now()
      mood.value = 'listening'
      return
    }
    // 闲置犯困只看本页闲置时长：打开旧会话不会立刻"睡着"
    const idleFor = Date.now() - lastActiveAt
    if (idleFor > 6 * 60_000) {
      mood.value = 'sleeping'
    } else if (idleFor > 2.5 * 60_000) {
      mood.value = 'drowsy'
    } else {
      mood.value = 'idle'
    }
  }

  function deriveBusyMood(la: ChatMessage | undefined, last: MessageBlock | undefined): string {
    if (!la || !last) return 'thinking'
    if (last.type === 'question') return last.status === 'pending' ? 'curious' : 'working'
    if (last.type === 'tool') return last.status === 'running' ? toolMood(last.tool) : 'thinking'
    if (last.type === 'thinking') return 'thinking'
    if (last.type === 'attachments') return 'loading'
    return 'writing'
  }

  // 会话切换/历史加载 = messages 整表替换：伸个懒腰醒来，闲置时钟归零。
  // 流式追加新消息行（首条 id 不变）与版本切换不算切换，避免回合中途被打断
  watch(messages, (next, prev) => {
    if ((next?.[0]?.id ?? '') === (prev?.[0]?.id ?? '')) return
    lastActiveAt = Date.now()
    hold('waking', 1400)
  })

  watch(signature, () => {
    const busy = isLoading.value
    if (busy && !wasLoading) {
      // 刚发出消息：纸飞机送出一小段，再交回流式判别
      lastActiveAt = Date.now()
      hold('sending', 1200)
    } else if (!busy && wasLoading) {
      // 回合结束：成功转圈撒花，中断/失败惊叫
      lastActiveAt = Date.now()
      const la = lastAssistant.value
      if (la?.interrupted) {
        hold('alerting', 3600)
      } else {
        fxSignal.value++
        hold('celebrate', 3200)
      }
    } else if (busy) {
      lastActiveAt = Date.now()
    }
    wasLoading = busy
    evaluate()
  })

  // 闲置犯困/入睡的时间推移不依赖签名变化，低频自检即可
  const idleTick = window.setInterval(() => {
    if (!isLoading.value && Date.now() >= holdUntil) evaluate()
  }, 30_000)

  onBeforeUnmount(() => {
    window.clearInterval(idleTick)
    if (holdTimer) window.clearTimeout(holdTimer)
  })

  return { mood, fxSignal }
}
