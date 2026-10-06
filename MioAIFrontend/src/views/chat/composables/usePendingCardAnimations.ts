import { computed, nextTick, ref, watch, type Ref } from 'vue'
import gsap from 'gsap'
import type { PendingAttachment } from '@/types'

/**
 * 待上传卡片的 GSAP 进出场（无 TransitionGroup——卡片是输入容器的直接子元素，
 * 不允许任何包装层，DOM 与 DeepSeek 一致）：
 * - 新增：缩放浮现；
 * - 移除：两阶段有重叠的连续动画——①卡片原位四周向中心缩小消失（transformOrigin
 *   正中，汇集点恒在几何中心）②宽度/高度/占位塌缩，右侧卡片被文档流连续回流
 *   平滑推向左侧。阶段二在阶段一结束前就开始（overlap），全程无停顿无急动。
 * - 会话切换/新建等整表替换：beginSilentSwap() 后的下一次差异直接静默落位，
 *   不播任何离场动画（卡片是为用户保存起来的，不是被删除）。
 * order 数组维护展示顺序：离场卡保持在原位渲染（甩到列表尾会让右卡瞬跳）。
 */
export function usePendingCardAnimations(pending: Ref<PendingAttachment[] | undefined>) {
  /** 离场动画中的卡片（保持在原位渲染，动画完成后真正移除） */
  const leavingCards = ref<PendingAttachment[]>([])
  /** 展示顺序（含离场中的键） */
  const order = ref<string[]>([])
  /** 见过的全部卡片（离场卡的渲染数据源：卡片已从 pending 移除，但动画期间还要显示） */
  const knownCards = new Map<string, PendingAttachment>()
  let hydrated = false
  let silentNext = false

  const renderCards = computed<PendingAttachment[]>(() => {
    const all = new Map<string, PendingAttachment>()
    for (const p of pending.value ?? []) all.set(p.key, p)
    for (const l of leavingCards.value) all.set(l.key, l)
    return order.value.map(k => all.get(k)).filter((c): c is PendingAttachment => !!c)
  })

  /** 离场中的键（隐藏 X，防动画期间重复触发） */
  const leavingKeys = computed(() => new Set(leavingCards.value.map(c => c.key)))

  /** 下一次 pending 差异静默落位（会话切换/新建：整表替换，不播离场/进场动画）。
   * 0ms 定时器自动过期：若本次交换没有产生差异，标志不会污染后续的删除动画。 */
  function beginSilentSwap(): void {
    silentNext = true
    window.setTimeout(() => {
      silentNext = false
    }, 0)
  }

  const cardEls = new Map<string, HTMLElement>()
  const setCardRef = (key: string) => (el: unknown) => {
    // 组件 ref 拿到的是组件实例，单根组件取 $el 才是卡片根 DOM
    const inst = el as { $el?: HTMLElement } | null
    const node = inst && inst.$el ? inst.$el : (el as HTMLElement | null)
    node ? cardEls.set(key, node) : cardEls.delete(key)
  }

  function finishLeave(key: string): void {
    leavingCards.value = leavingCards.value.filter(c => c.key !== key)
    order.value = order.value.filter(k => k !== key)
    cardEls.delete(key)
  }

  watch(
    () => (pending.value ?? []).map(p => p.key).join('|'),
    async () => {
      const nowList = pending.value ?? []
      const nowKeys = nowList.map(p => p.key)
      for (const p of nowList) knownCards.set(p.key, p)
      if (!hydrated) {
        // 首次挂载（会话恢复等）只记录基线，不重放进场动画
        hydrated = true
        order.value = nowKeys
        return
      }

      // 静默交换（会话切换/新建）：整表直接落位，无任何动画
      if (silentNext) {
        silentNext = false
        leavingCards.value = []
        order.value = nowKeys
        return
      }

      const added = nowList.filter(p => !order.value.includes(p.key))
      const removed: PendingAttachment[] = []
      const removedKeys: string[] = []
      for (const k of order.value) {
        if (!nowKeys.includes(k) && !leavingKeys.value.has(k)) {
          const card = knownCards.get(k)
          if (card) removed.push(card)
          removedKeys.push(k)
        }
      }

      // 展示顺序：存活键保持相对位置；离场键插回原相对位置（动画期间不换位）
      const prevOrder = order.value
      const next: string[] = prevOrder.filter(k => !removedKeys.includes(k))
      for (const r of removed) {
        // 插回原位置：找原前驱中仍存活且已在 next 的最近键，插到它后面
        const idx = prevOrder.indexOf(r.key)
        let pos = 0
        for (let i = idx - 1; i >= 0; i--) {
          const at = next.indexOf(prevOrder[i])
          if (at >= 0) {
            pos = at + 1
            break
          }
        }
        next.splice(pos, 0, r.key)
      }
      for (const k of nowKeys) {
        if (!next.includes(k)) {
          next.push(k)
        }
      }
      order.value = next
      leavingCards.value = [...leavingCards.value, ...removed]

      // 进场：缩放浮现
      if (added.length) {
        await nextTick()
        for (const p of added) {
          const el = cardEls.get(p.key)
          el && gsap.from(el, { opacity: 0, scale: 0.85, duration: 0.2, ease: 'power2.out' })
        }
      }

      // 离场两阶段（有重叠的连续动画，无"停顿→急收"）：
      // ①原位四周向中心缩小消失（无布局位移，汇集点=几何中心）
      // ②未等①结束就开始宽度/高度/占位塌缩——右侧卡片被平滑推向左侧，
      //   末卡移除时垂直空间同步收回（输入框丝滑上移）
      for (const r of removed) {
        await nextTick()
        const el = cardEls.get(r.key)
        if (!el) {
          finishLeave(r.key)
          continue
        }
        // 钉宽高转 border-box：数值插值才有正确起点；溢出裁剪防内容外溢
        el.style.boxSizing = 'border-box'
        el.style.width = `${el.offsetWidth}px`
        el.style.height = `${el.offsetHeight}px`
        el.style.overflow = 'hidden'
        el.style.willChange = 'transform, opacity, width, height'
        let finished = false
        const finish = () => {
          if (finished) return
          finished = true
          finishLeave(r.key)
        }
        gsap
          .timeline({
            onComplete: finish,
            // GSAP ticker 异常停摆时的兜底：离场仍会完成（真实前台用户走动画）
            onStart: () => window.setTimeout(finish, 900),
          })
          .to(el, {
            scale: 0,
            opacity: 0,
            transformOrigin: '50% 50%',
            duration: 0.24,
            ease: 'power2.in',
          })
          .to(
            el,
            {
              width: 0,
              height: 0,
              minHeight: 0,
              paddingLeft: 0,
              paddingRight: 0,
              borderLeftWidth: 0,
              borderRightWidth: 0,
              borderTopWidth: 0,
              borderBottomWidth: 0,
              marginLeft: 0,
              marginTop: 0,
              duration: 0.28,
              ease: 'power1.inOut',
            },
            '-=0.1',
          )
        // 兜底：GSAP ticker 异常停摆时离场仍会完成（真实前台用户走动画，无感）
        window.setTimeout(finish, 700)
      }
    },
  )

  return { renderCards, leavingKeys, setCardRef, beginSilentSwap }
}
