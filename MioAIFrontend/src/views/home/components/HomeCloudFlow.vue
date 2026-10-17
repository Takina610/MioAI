<template>
  <div class="cloud-flow" aria-hidden="true">
    <canvas ref="canvasRef"></canvas>
    <div class="cloud-grid"></div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 流体云背景：浅色天空上铺大团青绿雾霭（柔焦色块观感，颜色取站内主题绿）。
 *
 * 每朵云由若干半透明柔焦团以 source-over 叠色而成；各团的偏移随自身环流速率
 * 缓慢绕云心转动，相对位置持续变化 → 云的轮廓像流体一样徐徐变形，而不是整块
 * 刚性平移。整朵云同时横向环绕漂移。每帧只做少量径向渐变填充，不做逐像素
 * 运算；页面不可见时停帧。
 */

interface Blob {
  /** 相对云心的偏移（按云半径归一） */
  dx: number
  dy: number
  /** 半径相对云半径的比例 */
  scale: number
  /** 该团的亮度权重 */
  weight: number
  /** 绕云心的环流速率（弧度/秒）与初始相位：各团速率不同才有流体变形 */
  orbit: number
  phase: number
}

/** 云朵轮廓：7 个大小不一的团错落叠成，形成有内部分支的积云剪影 */
const CLOUD_BLOBS: Blob[] = [
  { dx: 0, dy: 0, scale: 0.72, weight: 0.9, orbit: 0.032, phase: 0.0 },
  { dx: 0.5, dy: -0.14, scale: 0.5, weight: 0.85, orbit: -0.042, phase: 1.1 },
  { dx: -0.52, dy: 0.08, scale: 0.46, weight: 0.8, orbit: 0.05, phase: 2.3 },
  { dx: 0.2, dy: 0.3, scale: 0.42, weight: 0.7, orbit: -0.028, phase: 3.4 },
  { dx: -0.26, dy: -0.3, scale: 0.4, weight: 0.65, orbit: 0.038, phase: 4.6 },
  { dx: 0.66, dy: 0.2, scale: 0.32, weight: 0.5, orbit: 0.022, phase: 5.7 },
  { dx: -0.68, dy: -0.16, scale: 0.3, weight: 0.45, orbit: -0.034, phase: 0.8 }
]

interface Cloud {
  /** 基准位置（相对宽高的 0~1） */
  x: number
  y: number
  /** 半径（相对宽度） */
  r: number
  /** 横向漂移速度（每秒，相对宽度）：刻意慢但可感知，十几分钟横穿一屏 */
  vx: number
  /** 呼吸幅度与相位 */
  breath: number
  phase: number
  alpha: number
}

/**
 * 云团配置：少而大的雾团铺在天空上——朵与朵之间留出空隙才能看出"云"的形状，
 * 半径控制在 1/3 屏宽量级：再大就彼此重叠糊成一片均匀渐变。
 * alpha 是单团不透明度（source-over 直接覆盖），云心浓度由几个团叠出来：
 * 顶部主云浓、越往下越淡，与云层向下收尾的节奏一致。
 */
const CLOUDS: Cloud[] = [
  { x: 0.1, y: 0.02, r: 0.34, vx: 0.0022, breath: 0.06, phase: 0.2, alpha: 0.34 },
  { x: 0.62, y: 0.12, r: 0.38, vx: -0.0016, breath: 0.07, phase: 1.4, alpha: 0.36 },
  { x: 1.02, y: 0.0, r: 0.3, vx: 0.0018, breath: 0.06, phase: 2.6, alpha: 0.29 },
  { x: 0.3, y: 0.42, r: 0.32, vx: -0.0012, breath: 0.08, phase: 3.8, alpha: 0.24 },
  { x: 0.85, y: 0.55, r: 0.3, vx: 0.0014, breath: 0.07, phase: 5.1, alpha: 0.19 },
  { x: 0.05, y: 0.68, r: 0.3, vx: 0.001, breath: 0.08, phase: 5.6, alpha: 0.15 },
  { x: 0.55, y: 0.86, r: 0.32, vx: -0.0008, breath: 0.07, phase: 2.1, alpha: 0.1 }
]

/**
 * 天空底色：近白、微微偏绿的冷调（雾团是彩色时底色必须退得很浅才不脏），
 * 末端接近页面米色以便与下方无缝衔接。
 */
const SKY_TOP = '#f1f5f3'
const SKY_MID = '#eef1ee'
const SKY_BOTTOM = '#edebe2'
/** 雾团颜色：主题青绿（同 variables.scss 的 --green） */
const CLOUD_RGB = '42, 161, 169'

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
  sky.addColorStop(0.45, SKY_MID)
  sky.addColorStop(1, SKY_BOTTOM)
  ctx.fillStyle = sky
  ctx.fillRect(0, 0, w, h)

  CLOUDS.forEach((cloud) => {
    // 横向漂移做环绕：出屏后从另一侧回来，避免长期单向后留空
    const drift = cloud.x + cloud.vx * elapsed
    const cx = ((((drift % 1.8) + 1.8) % 1.8) - 0.4) * w
    const cy = cloud.y * h
    const radius = cloud.r * w * (1 + Math.sin(elapsed * 0.1 + cloud.phase) * cloud.breath)
    if (radius <= 0) return

    CLOUD_BLOBS.forEach((blob) => {
      // 各团按自身速率绕云心环流：相对位置缓慢变化 → 轮廓像流体一样变形
      const angle = blob.phase + blob.orbit * elapsed
      const cosA = Math.cos(angle)
      const sinA = Math.sin(angle)
      const ox = blob.dx * cosA - blob.dy * sinA
      const oy = blob.dx * sinA + blob.dy * cosA
      const bx = cx + ox * radius
      const by = cy + oy * radius
      const br = radius * blob.scale
      const alpha = cloud.alpha * blob.weight
      if (br <= 0 || alpha <= 0) return
      const grad = ctx!.createRadialGradient(bx, by, 0, bx, by, br)
      // 停靠点收得紧：团心保浓度、边缘快速化开，才能叠出有轮廓的柔焦色块
      grad.addColorStop(0, `rgba(${CLOUD_RGB}, ${alpha})`)
      grad.addColorStop(0.42, `rgba(${CLOUD_RGB}, ${alpha * 0.72})`)
      grad.addColorStop(0.7, `rgba(${CLOUD_RGB}, ${alpha * 0.22})`)
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

/* 细网格：给流体云一点结构感，极淡的中性暖灰、越往下越淡（与云层同一收尾节奏） */
.cloud-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(20, 20, 19, 0.04) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(20, 20, 19, 0.04) 1px, transparent 1px);
  background-size: 76px 76px;
  mask-image: linear-gradient(180deg, #000 0%, #000 55%, transparent 100%);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, #000 55%, transparent 100%);
}
</style>
