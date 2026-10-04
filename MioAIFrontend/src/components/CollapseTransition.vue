<template>
  <Transition :css="false" @enter="onEnter" @after-enter="resetStyle" @leave="onLeave" @after-leave="resetStyle">
    <div v-show="open" class="collapse-transition">
      <slot />
    </div>
  </Transition>
</template>

<script setup lang="ts">
/**
 * 高度折叠过渡：展开/收起时高度与透明度同步变化（内容随展开渐显、随收起渐隐），
 * 不再出现"先撑开一块空白、内容最后才出现"的观感。
 * 动画结束后还原为 height:auto，流式增长的内容不受固定高度限制。
 */
defineProps<{
  open: boolean
}>()

const DURATION = 300

interface AnimTarget {
  height: string
  opacity: string
}

function resetStyle(el: Element): void {
  const node = el as HTMLElement
  node.style.transition = ''
  node.style.height = ''
  node.style.overflow = ''
  node.style.opacity = ''
}

function animate(el: HTMLElement, from: AnimTarget, to: AnimTarget, done: () => void): void {
  let finished = false
  el.style.transition = `height ${DURATION}ms ease, opacity ${DURATION}ms ease`
  el.style.overflow = 'hidden'
  el.style.height = from.height
  el.style.opacity = from.opacity
  void el.offsetHeight // 强制回流，让起始状态先生效
  el.style.height = to.height
  el.style.opacity = to.opacity
  const finish = () => {
    if (finished) return
    finished = true
    el.removeEventListener('transitionend', finish)
    window.clearTimeout(fallback)
    done()
  }
  el.addEventListener('transitionend', finish)
  // 未触发 transitionend（如隐藏页抑制）时的兜底
  const fallback = window.setTimeout(finish, DURATION + 80)
}

function onEnter(el: Element, done: () => void): void {
  const node = el as HTMLElement
  animate(node, { height: '0px', opacity: '0' }, { height: `${node.scrollHeight}px`, opacity: '1' }, done)
}

function onLeave(el: Element, done: () => void): void {
  const node = el as HTMLElement
  animate(node, { height: `${node.scrollHeight}px`, opacity: '1' }, { height: '0px', opacity: '0' }, done)
}
</script>

<style scoped>
.collapse-transition {
  display: block;
}
</style>
