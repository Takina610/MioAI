import { computed, nextTick, ref, watch, type Ref } from 'vue'
import gsap from 'gsap'
import type { PendingAttachment } from '@/types'

/**
 * 待上传卡片的 GSAP 进出场（无 TransitionGroup——卡片是输入容器的直接子元素，
 * 不允许任何包装层，DOM 与 DeepSeek 一致）：
 * - 新增：缩放浮现；
 * - 移除：钉宽转 border-box 后四周向中间缩小消失（scale→0）+ 占位塌缩
 *   （width/margin→0），兄弟卡片由文档流连续回流平滑补位；
 * - 最后一张离场时垂直空间随之收回（输入框平滑上移，无需容器动画——
 *   卡片自身 margin/width 塌缩即收回，连续回流无跳变）。
 */
export function usePendingCardAnimations(pending: Ref<PendingAttachment[] | undefined>) {
  /** 离场动画中的卡片（仍渲染在原位，动画完成后真正移除） */
  const leavingCards = ref<PendingAttachment[]>([])

  /** 渲染列表 = 待传卡片 + 离场卡片 */
  const renderCards = computed<PendingAttachment[]>(() => [...(pending.value ?? []), ...leavingCards.value])

  const cardEls = new Map<string, HTMLElement>()
  const setCardRef = (key: string) => (el: unknown) => {
    const node = el as HTMLElement | null
    node ? cardEls.set(key, node) : cardEls.delete(key)
  }

  let prevList: PendingAttachment[] = []
  let hydrated = false

  watch(
    () => (pending.value ?? []).map(p => p.key).join('|'),
    async () => {
      const nowList = pending.value ?? []
      if (!hydrated) {
        // 首次挂载（会话恢复等）只记录基线，不重放进场动画
        hydrated = true
        prevList = [...nowList]
        return
      }
      const added = nowList.filter(p => !prevList.some(b => b.key === p.key))
      const removed = prevList.filter(b => !nowList.some(n => n.key === b.key))
      prevList = [...nowList]

      if (added.length) {
        await nextTick()
        for (const p of added) {
          const el = cardEls.get(p.key)
          el && gsap.from(el, { opacity: 0, scale: 0.85, duration: 0.2, ease: 'power2.out' })
        }
      }

      for (const r of removed) {
        // 离场卡片保留渲染（watch 默认 pre-flush：先入列再渲染，元素不会被立即卸载）
        leavingCards.value = [...leavingCards.value, r]
        await nextTick()
        const el = cardEls.get(r.key)
        if (!el) {
          leavingCards.value = leavingCards.value.filter(c => c.key !== r.key)
          continue
        }
        // 钉宽转 border-box：GSAP 的 width 数值插值才有正确起点；溢出裁剪防内容外溢
        el.style.boxSizing = 'border-box'
        el.style.width = `${el.offsetWidth}px`
        el.style.overflow = 'hidden'
        const finish = () => {
          leavingCards.value = leavingCards.value.filter(c => c.key !== r.key)
        }
        gsap.to(el, {
          // 四周向中间缩小消失 + 占位塌缩（兄弟卡片连续回流平滑补位；
          // 末卡离场时垂直空间一并收回，输入框平滑上移）
          scale: 0,
          opacity: 0,
          width: 0,
          paddingLeft: 0,
          paddingRight: 0,
          borderLeftWidth: 0,
          borderRightWidth: 0,
          marginLeft: 0,
          marginTop: 0,
          duration: 0.25,
          ease: 'power2.in',
          onComplete: finish,
        })
        // 兜底：GSAP ticker 异常停摆时离场仍会完成（真实前台用户走动画，无感）
        window.setTimeout(finish, 400)
      }
    },
  )

  return { renderCards, setCardRef }
}
