import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { ChatMessage, MessageBlock, PlanStep } from '@/types'

/**
 * 会话执行情况 → 机器人情绪（39 态引擎状态机的运行时映射）。
 *
 * 完整映射（按优先级）：
 * - 附件上传中 ……………………………… uploading（入坞）
 * - 发送瞬间 ………………………………… sending（纸飞机）；用户消息带致谢 → happy
 * - sending 持续到回复首字出现（避免首字前出现"思考中"的空档）
 * - 提问待回答 ……………………………… curious（凑近等你说）
 * - 瞬态重试中（retryNotice）……………… confused（疑惑挠头）
 * - 搜索类工具运行 ………………………… radar（雷达扫描）
 * - 其他工具运行 …………………………… working（忙碌晃动）
 * - 任务清单有进行中步骤 …………………… progress（进度环）
 * - 思考块流式 ……………………………… thinking（思考点）
 * - 回答文本流式 …………………………… writing（铅笔书写）
 * - 回合结束：
 *   - 连接中断 ……………………………… angry（生气，3.6s）
 *   - 任务清单全部完成 ……………………… proud（自豪）
 *   - 回复带笑点（哈哈/233/😂）…………… laughing（大笑）
 *   - 输出超 3000 tokens ……………………… surprised（惊讶）
 *   - 回复为空 ………………………………… sad（失落）
 *   - 其余成功 ………………………………… celebrate（转一圈撒花）
 * - 闲置递进：1.5 分钟 bored（无聊）→ 3 分钟 drowsy（犯困）→ 6 分钟 sleeping（入睡）
 * - 切换会话 ………………………………… waking（伸懒腰醒来）
 * - 输入框聚焦 ……………………………… listening（在听）；聚焦且有字 → dictating（记录中）
 *
 * 所有输入（输入内容/上传数/重试/清单）都进 signature，任一变化立即重估情绪。
 */
export function useBotMood(
  messages: Ref<ChatMessage[]>,
  isLoading: Ref<boolean>,
  listening: Ref<boolean>,
  inputText?: Ref<string>,
  pendingUploads?: Ref<number>,
  plan?: Ref<PlanStep[] | null>
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

  const lastMessage = computed<ChatMessage | undefined>(() => messages.value[messages.value.length - 1])
  const lastAssistant = computed<ChatMessage | undefined>(() => {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === 'assistant') return messages.value[i]
    }
    return undefined
  })
  const lastUser = computed<ChatMessage | undefined>(() => {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === 'user') return messages.value[i]
    }
    return undefined
  })

  const uploading = computed(() => (pendingUploads?.value ?? 0) > 0)
  const inputHasText = computed(() => !!inputText?.value?.trim())
  const planInProgress = computed(() => {
    const steps = plan?.value
    return !!steps?.some(s => s.status === 'in_progress')
  })
  const planAllDone = computed(() => {
    const steps = plan?.value
    return !!steps?.length && steps.every(s => s.status === 'done')
  })

  /** 回复是否已出现首字（content 或任一内容块存在） */
  function hasFirstToken(la: ChatMessage | undefined): boolean {
    return !!(la && (la.content?.trim() || (la.blocks?.length ?? 0) > 0))
  }

  /** 流式签名：任一输入变化都会触发情绪重估 */
  const signature = computed(() => {
    const la = lastAssistant.value
    const blocks = la?.blocks ?? []
    const last = blocks[blocks.length - 1]
    return [
      messages.value.length,
      la?.id ?? '',
      blocks.length,
      last ? `${last.type}:${'status' in last ? last.status : ''}` : '',
      isLoading.value ? 1 : 0,
      listening.value ? 1 : 0,
      inputHasText.value ? 1 : 0,
      uploading.value ? 1 : 0,
      la?.interrupted ? 1 : 0,
      la?.retryNotice ? 1 : 0,
      planInProgress.value ? 1 : 0
    ].join('|')
  })

  function toolMood(tool: string): string {
    if (/search|web|fetch|browse|http|news/i.test(tool)) return 'radar'
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
    if (uploading.value) {
      mood.value = 'uploading'
      return
    }
    if (isLoading.value) {
      mood.value = deriveBusyMood()
      return
    }
    if (listening.value) {
      lastActiveAt = Date.now()
      mood.value = inputHasText.value ? 'dictating' : 'listening'
      return
    }
    // 闲置递进只看本页闲置时长：打开旧会话不会立刻"睡着"
    const idleFor = Date.now() - lastActiveAt
    if (idleFor > 6 * 60_000) {
      mood.value = 'sleeping'
    } else if (idleFor > 3 * 60_000) {
      mood.value = 'drowsy'
    } else if (idleFor > 1.5 * 60_000) {
      mood.value = 'bored'
    } else {
      mood.value = 'idle'
    }
  }

  function deriveBusyMood(): string {
    const la = lastAssistant.value
    const blocks = la?.blocks ?? []
    const last = blocks[blocks.length - 1]
    if (la?.retryNotice) return 'confused'
    if (last?.type === 'question') return last.status === 'pending' ? 'curious' : 'working'
    if (last?.type === 'tool') return last.status === 'running' ? toolMood(last.tool) : 'thinking'
    if (planInProgress.value) return 'progress'
    // 首字出现前恒为发送中
    if (!hasFirstToken(la)) return 'sending'
    if (last?.type === 'thinking') return 'thinking'
    if (last?.type === 'attachments') return 'loading'
    return 'writing'
  }

  /** 回复文本（content 优先，块兜底） */
  function replyTextOf(la: ChatMessage | undefined): string {
    if (!la) return ''
    if (la.content?.trim()) return la.content
    return (la.blocks ?? [])
      .filter((b): b is Extract<MessageBlock, { type: 'text' }> => b.type === 'text')
      .map(b => b.text)
      .join('')
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
      lastActiveAt = Date.now()
      // 用户致谢 → 开心一下；其余进入发送中（持续到首字出现）
      const said = lastUser.value?.content ?? ''
      if (/谢谢|辛苦|厉害|太棒|好棒|真棒|666/.test(said)) {
        hold('happy', 2400)
      } else {
        hold('sending', 600)
      }
    } else if (!busy && wasLoading) {
      lastActiveAt = Date.now()
      // 回合结束：按结果分派情绪
      const la = lastAssistant.value
      if (la?.interrupted) {
        hold('angry', 3600)
      } else if (la?.retryNotice) {
        hold('confused', 2600)
      } else if (planAllDone.value) {
        hold('proud', 4200)
      } else if (/哈哈|233|😂|🤣|乐死|笑死/.test(replyTextOf(la))) {
        hold('laughing', 3000)
      } else if ((la?.usage?.outputTokens ?? 0) > 3000) {
        hold('surprised', 3000)
      } else if (!replyTextOf(la).trim() && !(la?.blocks?.length)) {
        hold('sad', 3000)
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
