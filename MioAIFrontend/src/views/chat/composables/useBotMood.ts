import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { ChatMessage, MessageBlock } from '@/types'

/**
 * 会话执行情况 → 机器人情绪（39 态引擎状态机的运行时映射）。
 * 流式期间按最后一个内容块判别（思考/搜索/工具/编写/提问），
 * 回合完成转一圈撒花（fxSignal），持续空闲则犯困入睡。
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
    const la = lastAssistant.value
    if (!la) {
      mood.value = listening.value ? 'listening' : 'idle'
      return
    }
    const created = la.createTime ? new Date(la.createTime).getTime() : 0
    const age = Date.now() - (Number.isFinite(created) && created > 0 ? created : 0)
    if (age > 6 * 60_000) {
      mood.value = 'sleeping'
    } else if (age > 2.5 * 60_000) {
      mood.value = 'drowsy'
    } else {
      mood.value = listening.value ? 'listening' : 'idle'
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

  watch(signature, () => {
    const busy = isLoading.value
    if (busy && !wasLoading) {
      // 刚发出消息：纸飞机送出一小段，再交回流式判别
      hold('sending', 1200)
    } else if (!busy && wasLoading) {
      // 回合结束：成功转圈撒花，中断/失败惊叫
      const la = lastAssistant.value
      if (la?.interrupted) {
        hold('alerting', 3600)
      } else {
        fxSignal.value++
        hold('celebrate', 3200)
      }
    }
    wasLoading = busy
    evaluate()
  })

  // 空闲犯困/入睡的时间推移不依赖签名变化，低频自检即可
  const idleTick = window.setInterval(() => {
    if (!isLoading.value && Date.now() >= holdUntil) evaluate()
  }, 30_000)

  onBeforeUnmount(() => {
    window.clearInterval(idleTick)
    if (holdTimer) window.clearTimeout(holdTimer)
  })

  return { mood, fxSignal }
}
