<template>
  <Transition :css="false" @enter="onEnter" @after-enter="resetStyle" @leave="onLeave" @after-leave="resetStyle">
    <div v-show="open" class="collapse-transition">
      <slot />
    </div>
  </Transition>
</template>

<script setup lang="ts">
/**
 * 高度折叠过渡（对齐 zcode/Radix Collapsible 的动画不变量，修三类抖动）：
 * 1. wrapper 恒为 BFC（display: flow-root）——子元素 margin 归属在动画期间
 *    （overflow:hidden）与结束后（overflow:visible）完全一致，杜绝 margin
 *    折叠翻转造成的 ±几px 瞬跳（实测思考/工具内容展开结束帧 +4px）；
 * 2. 目标高度用 getBoundingClientRect（分数级）而非 scrollHeight（取整），
 *    动画终点=还原 auto 后的真实高度，结束帧不再有亚像素突增/缩；
 * 3. seamGap：flex gap 布局中的接缝（如工作过程外层），display 切换会让两侧
 *    gap 在动画首帧/末帧瞬移（实测 ±12px）；用同步过渡的负 margin-top 抵消
 *    gap，使后续内容位移全程连续。块级 margin 布局中的内层折叠不传即可。
 * transitionend 只认 wrapper 自身的 height——子元素自己的过渡（hover 背景等）
 * 不再冒泡提前终止动画。
 */
const props = defineProps<{
  open: boolean
  /** 外层 flex gap 像素数（接缝补偿）；块级布局中的折叠不传 */
  seamGap?: number
}>()

const DURATION = 300

interface AnimTarget {
  height: string
  opacity: string
  margin: string
}

function resetStyle(el: Element): void {
  const node = el as HTMLElement
  node.style.transition = ''
  node.style.height = ''
  node.style.overflow = ''
  node.style.opacity = ''
  node.style.marginTop = ''
}

function animate(el: HTMLElement, from: AnimTarget, to: AnimTarget, done: () => void): void {
  let finished = false
  // 先以 transition:none 钉住起始态并强制回流提交，再开启过渡设终态——
  // 若 transition 先设，起始值（如接缝补偿的负 margin）本身会进入过渡，
  // 首帧计算值仍是旧值（0），补偿失效
  el.style.transition = 'none'
  el.style.overflow = 'hidden'
  el.style.height = from.height
  el.style.opacity = from.opacity
  el.style.marginTop = from.margin
  void el.offsetHeight // 强制回流，让起始状态先生效
  el.style.transition = `height ${DURATION}ms ease, opacity ${DURATION}ms ease, margin-top ${DURATION}ms ease`
  el.style.height = to.height
  el.style.opacity = to.opacity
  el.style.marginTop = to.margin
  const finish = (event?: TransitionEvent) => {
    if (finished) return
    // 只认 wrapper 自身 height 的结束事件；其余属性/子元素的事件忽略
    if (event && (event.target !== el || event.propertyName !== 'height')) return
    finished = true
    el.removeEventListener('transitionend', finish)
    window.clearTimeout(fallback)
    done()
  }
  el.addEventListener('transitionend', finish as EventListener)
  // 未触发 transitionend（如隐藏页抑制）时的兜底
  const fallback = window.setTimeout(() => finish(), DURATION + 80)
}

/** 展开帧数级精确测量：此刻 height 仍为 auto，直接量盒子真实（分数）高度 */
function onEnter(el: Element, done: () => void): void {
  const node = el as HTMLElement
  const target = node.getBoundingClientRect().height
  const seam = props.seamGap ? `${-props.seamGap}px` : '0px'
  animate(node, { height: '0px', opacity: '0', margin: seam }, { height: `${target}px`, opacity: '1', margin: '0px' }, done)
}

function onLeave(el: Element, done: () => void): void {
  const node = el as HTMLElement
  const target = node.getBoundingClientRect().height
  const seam = props.seamGap ? `${-props.seamGap}px` : '0px'
  animate(node, { height: `${target}px`, opacity: '1', margin: '0px' }, { height: '0px', opacity: '0', margin: seam }, done)
}
</script>

<style scoped>
/* 恒为 BFC：动画前后子元素 margin 归属一致（zcode 的 overflow-hidden 恒定同理） */
.collapse-transition {
  display: flow-root;
}
</style>
