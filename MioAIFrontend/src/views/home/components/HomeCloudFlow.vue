<template>
  <div class="cloud-flow" aria-hidden="true">
    <canvas ref="canvasRef"></canvas>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 缓速流动的云层背景（DeepSeek 首屏同款观感）。
 *
 * 每朵云由几个相互叠亮（globalCompositeOperation: lighter）的柔光团组成，
 * 叠出的轮廓是不规则的云朵形，而不是单个圆斑；整体在天空底色上横向缓速漂移，
 * 并随时间缓缓呼吸。每帧只做少量径向渐变填充，不做逐像素运算。
 * 页面不可见时停帧。
 */

interface Blob {
  /** 相对云心的偏移（按云半径归一） */
  dx: number
  dy: number
  /** 半径相对云半径的比例 */
  scale: number
  /** 该团的亮度权重 */
  weight: number
}

/** 云朵轮廓：几个大小不一的团错落叠在一起 */
const CLOUD_BLOBS: Blob[] = [
  { dx: 0, dy: 0, scale: 1, weight: 1 },
  { dx: 0.42, dy: -0.1, scale: 0.66, weight: 0.85 },
  { dx: -0.44, dy: 0.06, scale: 0.6, weight: 0.8 },
  { dx: 0.16, dy: 0.2, scale: 0.52, weight: 0.6 },
  { dx: -0.2, dy: -0.18, scale: 0.48, weight: 0.55 }
]

interface Cloud {
  /** 基准位置（相对宽高的 0~1） */
  x: number
  y: number
  /** 半径（相对宽度） */
  r: number
  /** 横向漂移速度（每秒，相对宽度） */
  vx: number
  /** 呼吸幅度与相位 */
  breath: number
  phase: number
  alpha: number
}

/** 云团配置：多个大小不一的云错落分布，顶部厚密、越往下越淡（下方留白给内容）。
 *  亮度走加法合成（lighter），单团权重会叠加成云心亮度，所以这里的 alpha 很小：
 *  值 = 云心目标提亮量 ÷ 该云几个团的总权重，否则云心会直接烧成纯白 */
const CLOUDS: Cloud[] = [
  { x: 0.1, y: -0.08, r: 0.24, vx: 0.0055, breath: 0.05, phase: 0.0, alpha: 0.05 },
  { x: 0.3, y: -0.04, r: 0.19, vx: -0.0042, breath: 0.06, phase: 1.2, alpha: 0.046 },
  { x: 0.5, y: -0.1, r: 0.22, vx: 0.0036, breath: 0.05, phase: 2.2, alpha: 0.044 },
  { x: 0.7, y: -0.02, r: 0.17, vx: -0.005, breath: 0.07, phase: 3.1, alpha: 0.041 },
  { x: 0.88, y: -0.08, r: 0.2, vx: 0.0044, breath: 0.05, phase: 4.0, alpha: 0.039 },
  { x: 0.2, y: 0.1, r: 0.26, vx: -0.0032, breath: 0.07, phase: 0.6, alpha: 0.029 },
  { x: 0.62, y: 0.14, r: 0.21, vx: 0.0028, breath: 0.06, phase: 2.7, alpha: 0.026 },
  { x: 0.94, y: 0.2, r: 0.18, vx: -0.0036, breath: 0.06, phase: 3.9, alpha: 0.022 },
  { x: 0.38, y: 0.26, r: 0.28, vx: 0.0024, breath: 0.08, phase: 1.5, alpha: 0.019 },
  { x: 0.78, y: 0.32, r: 0.19, vx: -0.002, breath: 0.06, phase: 5.1, alpha: 0.014 },
  { x: 0.06, y: 0.36, r: 0.24, vx: 0.003, breath: 0.07, phase: 4.6, alpha: 0.012 },
  { x: 0.52, y: 0.44, r: 0.3, vx: -0.0016, breath: 0.08, phase: 5.8, alpha: 0.009 }
]

/** 天空底色：顶端带一点品牌青绿，向下回到页面米色（与首屏下方无缝相接） */
const SKY_TOP = '#cfe0d9'
const SKY_BOTTOM = '#f0eee6'
/** 云的颜色：近白微暖，在天空底色上叠亮成形 */
const CLOUD_RGB = '255, 255, 252'

const canvasRef = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let raf = 0
let elapsed = 0
let lastTs = 0
let resizeObserver: ResizeObserver | null = null
let reduceMotion = false

/** 设备像素比（上限 2：再高对柔和渐变没有增益，白费填充率） */
function dpr(): number {
  return Math.min(window.devicePixelRatio || 1, 2)
}

function resize(): void {
  const canvas = canvasRef.value
  if (!canvas || !ctx) return
  const ratio = dpr()
  const { clientWidth: w, clientHeight: h } = canvas
  canvas.width = Math.max(1, Math.round(w * ratio))
  canvas.height = Math.max(1, Math.round(h * ratio))
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
  draw()
}

/** 绘制一帧：天空底色 + 各云朵叠亮 */
function draw(): void {
  const canvas = canvasRef.value
  if (!canvas || !ctx) return
  const w = canvas.clientWidth
  const h = canvas.clientHeight
  if (!w || !h) return

  ctx.globalCompositeOperation = 'source-over'
  const sky = ctx.createLinearGradient(0, 0, 0, h)
  sky.addColorStop(0, SKY_TOP)
  sky.addColorStop(0.55, SKY_BOTTOM)
  sky.addColorStop(1, SKY_BOTTOM)
  ctx.fillStyle = sky
  ctx.fillRect(0, 0, w, h)

  // 云团相互叠亮，叠出不规则云朵轮廓
  ctx.globalCompositeOperation = 'lighter'

  CLOUDS.forEach((cloud) => {
    // 横向漂移做环绕：出屏后从另一侧回来，避免长期单向后留空
    const drift = cloud.x + cloud.vx * elapsed
    const cx = ((((drift % 1.6) + 1.6) % 1.6) - 0.3) * w
    const cy = cloud.y * h
    const radius = cloud.r * w * (1 + Math.sin(elapsed * 0.12 + cloud.phase) * cloud.breath)
    if (radius <= 0) return

    CLOUD_BLOBS.forEach((blob) => {
      const bx = cx + blob.dx * radius
      const by = cy + blob.dy * radius
      const br = radius * blob.scale
      const alpha = cloud.alpha * blob.weight
      if (br <= 0 || alpha <= 0) return
      const grad = ctx!.createRadialGradient(bx, by, 0, bx, by, br)
      // 停靠点收得比线性更紧：云心保亮、边缘快速化开，云朵才有轮廓而不是一团雾
      grad.addColorStop(0, `rgba(${CLOUD_RGB}, ${alpha})`)
      grad.addColorStop(0.42, `rgba(${CLOUD_RGB}, ${alpha * 0.72})`)
      grad.addColorStop(0.72, `rgba(${CLOUD_RGB}, ${alpha * 0.22})`)
      grad.addColorStop(1, `rgba(${CLOUD_RGB}, 0)`)
      ctx!.fillStyle = grad
      ctx!.fillRect(bx - br, by - br, br * 2, br * 2)
    })
  })
}

function tick(now: number): void {
  // 用真实时间差推进：切回前台/掉帧后云的位置仍然连续
  if (!lastTs) lastTs = now
  elapsed += Math.min((now - lastTs) / 1000, 0.25)
  lastTs = now
  draw()
  raf = window.requestAnimationFrame(tick)
}

function start(): void {
  if (raf) return
  lastTs = 0
  raf = window.requestAnimationFrame(tick)
}

function stop(): void {
  if (!raf) return
  window.cancelAnimationFrame(raf)
  raf = 0
}

/** 页面不可见时停帧（省电），回来继续 */
function onVisibilityChange(): void {
  if (document.hidden) stop()
  else start()
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  ctx = canvas.getContext('2d')
  if (!ctx) return

  reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  resize()

  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvas)
  document.addEventListener('visibilitychange', onVisibilityChange)

  // 降低动态效果时只画一帧静态云层，不做持续漂移
  if (reduceMotion) draw()
  else start()
})

onBeforeUnmount(() => {
  stop()
  resizeObserver?.disconnect()
  resizeObserver = null
  document.removeEventListener('visibilitychange', onVisibilityChange)
  ctx = null
})
</script>

<style scoped>
.cloud-flow {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.cloud-flow canvas {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
