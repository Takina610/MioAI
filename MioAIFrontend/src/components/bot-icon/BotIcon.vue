<template>
  <svg ref="svgRef" class="bot-icon" role="img" aria-hidden="true" />
</template>

<script setup lang="ts">
/**
 * Grok Bot 风格的动态机器人图标。
 * 引擎为 SVG 弹簧角色（形体形变/多边形眼睛/39 状态姿态/粒子彩带），
 * 本组件只做属性桥接：shape / fill（纯色或渐变）/ state / 尺寸 / 指针跟随。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { GrokCharacter } from './engine/character'
import { GROK_GEO } from './engine/geometry'
import type { BotFill } from './types'
import { safeColor } from './types'

const props = withDefaults(
  defineProps<{
    shape: string
    fill?: BotFill
    /** 39 种状态之一（thinking/searching/writing/celebrate…），见 engine/tables.ts */
    state?: string
    size?: number
    /** 眼睛跟随指针 */
    follow?: boolean
    /** false = 静态渲染一帧（列表/迷你预览用），true = 完整动画循环 */
    live?: boolean
    /** 眼睛镂空处透出的底色（需与所在表面一致） */
    eyeColor?: string
  }>(),
  { state: 'idle', size: 64, follow: false, live: true, eyeColor: '#f3efe6' }
)

const svgRef = ref<SVGSVGElement | null>(null)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let char: any = null
const NS = 'http://www.w3.org/2000/svg'
const gradId = `mio-bot-fill-${Math.random().toString(36).slice(2, 9)}`

onMounted(() => {
  char = new GrokCharacter(svgRef.value!, {
    shape: GROK_GEO.shapes[props.shape] ? props.shape : 'blob',
    color: 'black',
    state: props.state,
    sizePx: props.size,
    followPointer: props.follow,
    live: props.live,
    eyeColor: props.eyeColor,
    loginWrap: true
  })
  applyFill(props.fill)

  watch(
    () => props.shape,
    v => {
      if (v && GROK_GEO.shapes[v] && v !== char.shapeName) char.setShape(v)
    }
  )
  watch(
    () => props.state,
    v => {
      if (v && v !== char.state) char.setState(v)
    }
  )
  watch(
    () => props.size,
    v => {
      if (v) char.setSizePx(v)
    }
  )
  watch(() => props.fill, v => applyFill(v), { deep: true })
  watch(
    () => props.follow,
    v => char.setFollowPointer(!!v)
  )
})

onBeforeUnmount(() => {
  char?.destroy()
  char = null
})

/** 纯色 → --fg 颜色；渐变 → 在 defs 里维护一个渐变节点，--fg 指向它 */
function applyFill(fill?: BotFill): void {
  if (!char) return
  const svg = char.svg as SVGSVGElement
  if (!fill || fill.kind === 'palette') {
    const id = (fill && fill.kind === 'palette' && fill.id) || 'black'
    const pal = GROK_GEO.palette[id] || GROK_GEO.palette.black
    removeGrad(svg)
    svg.style.setProperty('--fg', pal.light)
    return
  }
  if (fill.kind === 'solid') {
    const color = safeColor(fill.color)
    removeGrad(svg)
    svg.style.setProperty('--fg', color || '#000000')
    return
  }
  const from = safeColor(fill.from)
  const to = safeColor(fill.to)
  if (!from || !to) return
  const defs = svg.querySelector('defs')
  if (!defs) return
  const tag = fill.kind === 'linear' ? 'linearGradient' : 'radialGradient'
  let grad = defs.querySelector(`#${gradId}`)
  if (!grad || grad.tagName.toLowerCase() !== tag.toLowerCase()) {
    grad?.remove()
    grad = document.createElementNS(NS, tag)
    grad.setAttribute('id', gradId)
    defs.appendChild(grad)
  }
  if (fill.kind === 'linear') {
    // CSS 渐变角（0deg 向上、90deg 向右）换算到 objectBoundingBox 坐标
    const a = ((fill.angle ?? 135) * Math.PI) / 180
    const dx = Math.sin(a) * 0.5
    const dy = -Math.cos(a) * 0.5
    grad.setAttribute('x1', (0.5 - dx).toFixed(4))
    grad.setAttribute('y1', (0.5 - dy).toFixed(4))
    grad.setAttribute('x2', (0.5 + dx).toFixed(4))
    grad.setAttribute('y2', (0.5 + dy).toFixed(4))
  } else {
    grad.setAttribute('cx', '0.5')
    grad.setAttribute('cy', '0.42')
    grad.setAttribute('r', '0.68')
  }
  for (const gradEl of [grad]) {
    gradEl.innerHTML = ''
    for (const [offset, color] of [
      ['0%', from],
      ['100%', to]
    ] as const) {
      const stop = document.createElementNS(NS, 'stop')
      stop.setAttribute('offset', offset)
      stop.setAttribute('stop-color', color)
      gradEl.appendChild(stop)
    }
  }
  svg.style.setProperty('--fg', `url(#${gradId})`)
}

function removeGrad(svg: SVGSVGElement): void {
  svg.querySelector(`#${gradId}`)?.remove()
}

function spinOnce(turns = 1): void {
  char?.spinOnce(turns)
}

function bounceOnce(): void {
  char?.bounceOnce()
}

function burstOnce(): void {
  char?.burstOnce()
}

defineExpose({ spinOnce, bounceOnce, burstOnce })
</script>

<style lang="scss" scoped>
.bot-icon {
  display: block;
  overflow: visible;
}
</style>
