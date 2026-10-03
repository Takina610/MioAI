<template>
  <Transition :css="false" @enter="onEnter" @after-enter="resetStyle" @leave="onLeave" @after-leave="resetStyle">
    <div v-show="open" class="collapse-transition">
      <slot />
    </div>
  </Transition>
</template>

<script setup lang="ts">
/**
 * 高度折叠过渡：展开/收起（以及关闭消失）时高度平滑变化而非瞬间出现消失。
 * 动画结束后还原为 height:auto，流式增长的内容不受固定高度限制。
 */
const props = defineProps<{
  open: boolean
}>()
void props

const DURATION = 220

function resetStyle(el: Element): void {
  const node = el as HTMLElement
  node.style.transition = ''
  node.style.height = ''
  node.style.overflow = ''
  node.style.opacity = ''
}

function animate(el: HTMLElement, from: string, to: number | 'auto', done: () => void): void {
  let finished = false
  el.style.transition = `height ${DURATION}ms ease, opacity ${DURATION}ms ease`
  el.style.overflow = 'hidden'
  el.style.height = from
  void el.offsetHeight // 强制回流，让起始高度先生效
  el.style.height = typeof to === 'number' ? `${to}px` : 'auto'
  const finish = () => {
    if (finished) return
    finished = true
    el.removeEventListener('transitionend', finish)
    window.clearTimeout(fallback)
    done()
  }
  el.addEventListener('transitionend', finish)
  // height:auto / 未触发 transitionend 时的兜底
  const fallback = window.setTimeout(finish, DURATION + 60)
}

function onEnter(el: Element, done: () => void): void {
  const node = el as HTMLElement
  node.style.opacity = '0'
  animate(node, '0px', node.scrollHeight, () => {
    node.style.opacity = ''
    done()
  })
}

function onLeave(el: Element, done: () => void): void {
  const node = el as HTMLElement
  node.style.opacity = '1'
  animate(node, `${node.scrollHeight}px`, 0, done)
}
</script>

<style scoped>
.collapse-transition {
  display: block;
}
</style>
