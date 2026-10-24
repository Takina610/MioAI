<template>
  <!-- 标语聚光灯：比单字稍大的深色圆形负片遮罩，缓动阻尼跟随鼠标；
       从光标处放大出现，离开时缩小至光标处消失。悬停判定=文字区域本体，
       遮罩绘制区外扩，圆越出文字框不会被裁/被盖 -->
  <div
    class="slogan-wrap"
    @mouseenter="onEnter"
    @mousemove="onMove"
    @mouseleave="onLeave"
  >
    <h1 class="hero-slogan">会思考，能行动</h1>
    <div ref="layerRef" class="spot-layer" :style="layerStyle" aria-hidden="true">
      <h1 class="hero-slogan alt">会思考，能行动</h1>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

/** 缓动系数：位置跟手偏紧，缩放稍缓（放大出现/缩小消失的呼吸感） */
const POS_EASE = 0.16
const SCALE_EASE = 0.13

const active = ref(false)
/** 鼠标目标位与圆形当前位（相对容器左上角） */
const tx = ref(0)
const ty = ref(0)
const sx = ref(0)
const sy = ref(0)
/** 半径缩放：0=收成点，1=全尺寸（出现/消失的放大缩小动画） */
const sc = ref(0)

let raf = 0

const layerRef = ref<HTMLElement | null>(null)

const layerStyle = computed(() => ({
  '--sx': `${sx.value}px`,
  '--sy': `${sy.value}px`,
  '--sc': sc.value.toFixed(3)
}))

/** 阻尼循环：位置向鼠标逼近，缩放向目标（悬停=1/离开=0）逼近 */
function loop(): void {
  const snap = reducedMotion() ? 1 : null
  const posEase = snap ?? POS_EASE
  const scaleEase = snap ?? SCALE_EASE
  sx.value += (tx.value - sx.value) * posEase
  sy.value += (ty.value - sy.value) * posEase
  sc.value += ((active.value ? 1 : 0) - sc.value) * scaleEase
  raf = requestAnimationFrame(loop)
}

function reducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function locate(e: MouseEvent): void {
  // 坐标以遮罩绘制层（外扩后）为基准：遮罩圆心与光标始终同一坐标系
  const el = layerRef.value ?? (e.currentTarget as HTMLElement)
  const r = el.getBoundingClientRect()
  tx.value = e.clientX - r.left
  ty.value = e.clientY - r.top
}

function onEnter(e: MouseEvent): void {
  locate(e)
  // 从进入点起追：圆在此处从 0 放大出现
  sx.value = tx.value
  sy.value = ty.value
  active.value = true
}

function onMove(e: MouseEvent): void {
  locate(e)
}

function onLeave(e: MouseEvent): void {
  locate(e)
  // 圆向离开点的光标位收缩消失
  active.value = false
}

onMounted(() => {
  raf = requestAnimationFrame(loop)
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
})
</script>

<style lang="scss" scoped>
.slogan-wrap {
  --spot-d: calc(clamp(28px, 4vw, 44px) * 1.6);
  position: relative;
  // 盖过下方演示窗（backdrop-filter 合成层在 DOM 后方，不抬 z 会被其覆盖）
  z-index: 2;
  display: inline-block;
  cursor: default;
}

.hero-slogan {
  font-family: var(--hm-font-display);
  font-size: clamp(28px, 4vw, 44px);
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.14em;
  // 宽字距会在末字后留出空档，用等量缩进抵消以保持视觉居中
  text-indent: 0.14em;
  text-align: center;
  color: var(--hm-green-deep);
}

/* 负片层：整层铺反色底（#f0eee6 取反 → #0f1119），文字用反色（#238b92 取反 → #dc746d）。
   绘制区四向外扩 80px（圆越出文字框不被裁），等量内边距让反色文字与底层逐字对齐。
   遮罩半径 = 圆径一半 × 缩放系数：出现时从光标点放大，离开时收成点消失 */
.spot-layer {
  position: absolute;
  inset: -80px;
  padding: 80px;
  background: #0f1119;
  pointer-events: none;
  -webkit-mask-image: radial-gradient(
    circle calc(var(--spot-d) * var(--sc) / 2) at var(--sx) var(--sy),
    #000 98%,
    transparent 100%
  );
  mask-image: radial-gradient(
    circle calc(var(--spot-d) * var(--sc) / 2) at var(--sx) var(--sy),
    #000 98%,
    transparent 100%
  );

  .hero-slogan.alt {
    margin: 0;
    color: #dc746d;
  }
}
</style>
