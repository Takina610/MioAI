/**
 * antd 弹窗关闭收尾兜底（保留原生开关动画）：
 * antdv 的 Dialog mask/content 用 Vue 原生 Transition + ant-fade/ant-zoom
 * keyframes 动画，关闭收尾要等 animationend——标签页后台/窗口被遮挡时渲染冻结，
 * 动画不走、事件不触发，遮罩冻结在关闭中段（半透明黑屏罩页）。
 *
 * 方案：MutationObserver 监听 mask/弹窗本体出现 leave 类（类变更走微任务，
 * 后台页同样触发），宽限期内动画理应播完；超时仍在 leave 态则补发合成
 * animationend/transitionend，让 Vue 走自己的原生收尾路径（摘类 + v-show 隐藏
 * + wrap display:none），不干预正常前台动画。
 */
const GRACE_MS = 1500

const watched = new WeakSet<Element>()

function isModalLeaveTarget(el: Element): boolean {
  const cls = String((el as HTMLElement).className || '')
  const isModalMotionEl = cls.includes('ant-modal-mask') || cls.includes('ant-modal ')
  return isModalMotionEl && cls.includes('-leave')
}

function forceFinish(el: HTMLElement): void {
  if (!el.isConnected || !isModalLeaveTarget(el)) return
  el.dispatchEvent(new AnimationEvent('animationend'))
  el.dispatchEvent(new TransitionEvent('transitionend', { propertyName: 'opacity' }))
}

const observer = new MutationObserver(records => {
  for (const record of records) {
    const el = record.target as HTMLElement
    if (el.nodeType !== 1) continue
    // leave 类被摘（正常播完）→ 解除监视，同一元素下轮关闭可再触发
    if (!isModalLeaveTarget(el)) {
      watched.delete(el)
      continue
    }
    if (!watched.has(el)) {
      watched.add(el)
      window.setTimeout(() => forceFinish(el), GRACE_MS)
    }
  }
})

export function setupModalLeaveGuard(): void {
  observer.observe(document.body, { subtree: true, attributeFilter: ['class'] })
}
