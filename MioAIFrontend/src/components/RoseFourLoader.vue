<template>
  <svg
    class="rose-four"
    :style="{ width: `${size}px`, height: `${size}px` }"
    viewBox="0 0 100 100"
    fill="none"
    aria-hidden="true"
  >
    <g ref="groupRef">
      <path ref="pathRef" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" opacity="0.14" />
    </g>
  </svg>
</template>

<script setup lang="ts">
/**
 * Rose Four 数学曲线加载动画（r = a·cos(4θ) 玫瑰线）。
 * 呼吸脉动 + 慢旋转 + 粒子尾迹；prefers-reduced-motion 时渲染静态曲线。
 * 小尺寸自动加粗描边，保证曲线在 12~14px 下仍清晰。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 渲染边长 px */
    size?: number
  }>(),
  { size: 18 }
)

const NS = 'http://www.w3.org/2000/svg'
const groupRef = ref<SVGGElement | null>(null)
const pathRef = ref<SVGPathElement | null>(null)

const DURATION_MS = 5400
const ROTATION_MS = 28000
const PULSE_MS = 4500
const TRAIL_SPAN = 0.32
const PARTICLE_COUNT = 78
const ROSE_A = 9.2
const ROSE_A_BOOST = 0.6
const BREATH_BASE = 0.72
const BREATH_BOOST = 0.28
const ROSE_SCALE = 3.25

function point(progress: number, detailScale: number): { x: number; y: number } {
  const t = progress * Math.PI * 2
  const a = ROSE_A + detailScale * ROSE_A_BOOST
  const r = a * (BREATH_BASE + detailScale * BREATH_BOOST) * Math.cos(4 * t)
  return {
    x: 50 + Math.cos(t) * r * ROSE_SCALE,
    y: 50 + Math.sin(t) * r * ROSE_SCALE
  }
}

function detailScaleOf(time: number): number {
  const pulseAngle = ((time % PULSE_MS) / PULSE_MS) * Math.PI * 2
  return 0.52 + ((Math.sin(pulseAngle + 0.55) + 1) / 2) * 0.48
}

function buildPath(detailScale: number, steps = 360): string {
  let d = ''
  for (let i = 0; i <= steps; i++) {
    const p = point(i / steps, detailScale)
    d += `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`
  }
  return d
}

let raf = 0
let particles: SVGCircleElement[] = []
let lastPathScale = -1

onMounted(() => {
  const group = groupRef.value
  const path = pathRef.value
  if (!group || !path) return

  // 小尺寸加粗：20px 时 4.6（≈0.9px），14px 时 ≈6.6（≈0.92px），40px 时 4.6（≈1.8px）
  path.setAttribute('stroke-width', String(Math.max(4.6, 92 / props.size)))
  path.setAttribute('d', buildPath(detailScaleOf(0)))

  const reduce = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduce) return

  for (let i = 0; i < PARTICLE_COUNT; i++) {
    const c = document.createElementNS(NS, 'circle')
    c.setAttribute('fill', 'currentColor')
    group.appendChild(c)
    particles.push(c)
  }

  const startedAt = performance.now()
  const render = (now: number): void => {
    const time = now - startedAt
    // 路径只在呼吸档位变化时重建（48 档），粒子每帧跟手
    const scale = Math.round(detailScaleOf(time) * 47) / 47
    if (scale !== lastPathScale) {
      path.setAttribute('d', buildPath(scale))
      lastPathScale = scale
    }
    group.setAttribute('transform', `rotate(${(-((time % ROTATION_MS) / ROTATION_MS) * 360).toFixed(2)} 50 50)`)
    const progress = (time % DURATION_MS) / DURATION_MS
    for (let i = 0; i < PARTICLE_COUNT; i++) {
      const tail = i / (PARTICLE_COUNT - 1)
      const p = point(((progress - tail * TRAIL_SPAN) % 1 + 1) % 1, scale)
      const fade = Math.pow(1 - tail, 0.56)
      const node = particles[i]
      node.setAttribute('cx', p.x.toFixed(2))
      node.setAttribute('cy', p.y.toFixed(2))
      node.setAttribute('r', (0.9 + fade * 2.7).toFixed(2))
      node.setAttribute('opacity', (0.04 + fade * 0.96).toFixed(3))
    }
    raf = requestAnimationFrame(render)
  }
  raf = requestAnimationFrame(render)
})

onBeforeUnmount(() => cancelAnimationFrame(raf))
</script>

<style lang="scss" scoped>
.rose-four {
  display: inline-block;
  overflow: visible;
  color: $primary-color;
  vertical-align: middle;
}
</style>
