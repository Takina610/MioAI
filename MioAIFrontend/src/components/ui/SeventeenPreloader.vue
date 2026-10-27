<template>
  <div
    v-if="visible"
    ref="preloaderEl"
    class="seventeen-preloader"
    :class="{ 'is-mobile': isMobile }"
    role="progressbar"
    aria-label="Loading site"
  >
    <!-- 暖调深炭灰石幕布（非纯黑，具有 Anthropic 纸本质感的温润深色，无发光渐变） -->
    <div class="preloader-bg"></div>

    <!-- 铺满全屏的错落蜂巢网格 (Desktop 8列x7行 56格; Mobile 3列x5行 15格) -->
    <div ref="gridEl" class="preloader-grid">
      <div
        v-for="index in TOTAL_CELLS"
        :key="index"
        class="preloader-num"
        :class="{ 'is-desktop': index > MOBILE_CELLS }"
      >
        <span class="preloader-num-text">M</span>
      </div>
    </div>

    <!-- 居中核心舞台：视口几何中心 (50vw, 50vh) 绝对锁定，严禁产生任何下移偏置 -->
    <div class="preloader-center-stage">
      <!-- 
        真实 OTF 矢量提取版 MioAI：
        起始不可见（opacity: 0），待周围小 MioAI 自中心向外字面消散时同步在圆心登场！
        提取自 AnthropicSans-Display-Bold 官方字体的精确三次贝塞尔曲线 SVG Path，
        彻底消除普通 DOM 文本在 GPU 纹理放大时产生的像素锯齿，实现放大数百倍边缘极致丝滑！
      -->
      <div ref="brandWrapEl" class="brand-vector-wrap">
        <svg
          ref="brandSvgEl"
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 5802 1525"
          class="mioai-vector-svg"
          aria-label="MioAI"
        >
          <g transform="translate(0, 1505) scale(1, -1)">
            <!-- M -->
            <path
              transform="translate(0, 0)"
              d="M111 0H407V1122L805 0H1155L1553 1119V0H1866V1500H1379L989 402L598 1500H111Z"
              fill="#ffffff"
            />
            <!-- i -->
            <path
              transform="translate(1978, 0)"
              d="M96 1502V1170H398V1502ZM96 1060V0H398V1060Z"
              fill="#ffffff"
            />
            <!-- o (外轮廓顶点 1080，内轮廓顶点 825，上半圆白色区域中心精准为 952.5) -->
            <path
              transform="translate(2471, 0)"
              d="M612 -20C924 -20 1175 200 1175 530C1175 860 924 1080 612 1080C299 1080 49 860 49 530C49 200 299 -20 612 -20ZM612 234C461 234 367 354 367 530C367 706 461 825 612 825C762 825 855 706 855 530C855 354 762 234 612 234Z"
              fill="#ffffff"
            />
            <!-- A -->
            <path
              transform="translate(3694, 0)"
              d="M346 0 477 344H1074L1205 0H1550L949 1505H620L18 0ZM586 632 776 1130 965 632Z"
              fill="#ffffff"
            />
            <!-- I -->
            <path
              transform="translate(5262, 0)"
              d="M110 1500V0H430V1500Z"
              fill="#ffffff"
            />
          </g>
        </svg>
      </div>

      <!-- 全屏纯白过渡层（在 O 上半圆白色覆盖全屏的中后期无缝融合，确保任意高分屏 100% 变白） -->
      <div ref="whiteScreenEl" class="white-screen-layer" aria-hidden="true"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Seventeen Agency 风格首屏加载动画组件（MioAI 终极高精版）
 *
 * 核心动效节奏：
 * 1. 背景为温润暖深炭灰（#2b2824，非纯死黑，具备 Anthropic 纸本质感，无任何边缘微光渐变）；
 * 2. 一开始不显示中央大 MioAI，由周围小蜂巢网格独占呈现；
 * 3. 网格统一使用官方 AnthropicSans Display 字体，以 M -> Mi -> Mio -> MioA -> MioAI 涟漪递进加载；
 * 4. 计数完成后，周围小 MioAI 从中间向四周呈「单纯字面消散」（纯字形基于真实欧氏距离向外涟漪淡出，无任何遮罩/覆盖层元素，节奏从容舒缓）；
 * 5. 在中心小字消隐的同时，中央大 MioAI 在圆心同步浮现登场；
 * 6. 不做停顿等待，中心 MioAI 浮现差不多后立即无缝衔接放大！
 * 7. 放大采用现代对数级均匀缩放曲线（ExpoScale + SineInOut，时长适中 1.55s），彻底解决多项式缓动带来的生硬与突兀；
 * 8. 镜头平滑锁定在「O 上半圆正中白色区域」，纵向零下移，边缘零锯齿，纯白覆盖全屏后平滑溶入主站。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import gsap from 'gsap'

const props = withDefaults(
  defineProps<{
    /** 是否允许跳过 */
    allowSkip?: boolean
    /** 是否只播放一次（写入 sessionStorage 并在同 session 内跳过） */
    sessionOnce?: boolean
    /** session key */
    storageKey?: string
  }>(),
  {
    allowSkip: true,
    sessionOnce: false,
    storageKey: 'mio_preloader_played'
  }
)

const emit = defineEmits<{
  (e: 'reveal'): void
  (e: 'complete'): void
  (e: 'skip'): void
}>()

const TOTAL_CELLS = 56 // 8 列 x 7 行
const MOBILE_CELLS = 15 // 3 列 x 5 行

/** 环绕网格加载步进：M -> Mi -> Mio -> MioA -> MioAI */
const LOADING_STEPS = ['M', 'Mi', 'Mio', 'MioA', 'MioAI']

/**
 * 数学推导：Anthropic 官方字体中「O 上半圆正中白色笔画」在 MioAI 整体矢量图中的精准百分比
 * 字体单位总宽：5802，总高：1525
 * O 字符偏移：2471，O 水平中心相对自身：612 -> 全局 X = 3083 -> 3083 / 5802 = 53.1368%
 * O 上半圆外沿：1080，内沿：825 -> 笔画正中 = 952.5 -> 全局 Y = 1505 - 952.5 = 552.5 -> 552.5 / 1525 = 36.2295%
 */
const FOCAL_X_PERCENT = 53.1368
const FOCAL_Y_PERCENT = 36.2295

const visible = ref(true)
const isMobile = ref(false)

const preloaderEl = ref<HTMLElement | null>(null)
const gridEl = ref<HTMLElement | null>(null)
const brandWrapEl = ref<HTMLElement | null>(null)
const brandSvgEl = ref<SVGSVGElement | null>(null)
const whiteScreenEl = ref<HTMLElement | null>(null)

let tl: gsap.core.Timeline | null = null

function checkReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function handleResize(): void {
  isMobile.value = window.innerWidth < 992
}

function handleKeyDown(e: KeyboardEvent): void {
  if (e.key === 'Escape' && visible.value && props.allowSkip) {
    handleSkip()
  }
}

function handleSkip(): void {
  if (!visible.value) return
  emit('skip')
  emit('reveal')
  if (tl) {
    tl.kill()
    tl = null
  }
  if (preloaderEl.value) {
    gsap.to(preloaderEl.value, {
      opacity: 0,
      duration: 0.25,
      ease: 'power2.out',
      onComplete: () => {
        visible.value = false
        if (props.sessionOnce) {
          sessionStorage.setItem(props.storageKey, '1')
        }
        emit('complete')
      }
    })
  } else {
    visible.value = false
    emit('complete')
  }
}

function runAnimation(): void {
  if (checkReducedMotion()) {
    visible.value = false
    emit('reveal')
    emit('complete')
    return
  }

  if (props.sessionOnce && sessionStorage.getItem(props.storageKey) === '1') {
    visible.value = false
    emit('reveal')
    emit('complete')
    return
  }

  const preLoader = preloaderEl.value
  const grid = gridEl.value
  const brandWrap = brandWrapEl.value
  const brandSvg = brandSvgEl.value
  const whiteScreen = whiteScreenEl.value
  if (!preLoader || !grid || !brandWrap || !brandSvg) return

  const isMob = window.innerWidth < 992
  const activeCells = Array.from(
    isMob
      ? grid.querySelectorAll<HTMLElement>('.preloader-num:not(.is-desktop)')
      : grid.querySelectorAll<HTMLElement>('.preloader-num')
  )
  const activeTexts = isMob
    ? grid.querySelectorAll<HTMLElement>('.preloader-num:not(.is-desktop) .preloader-num-text')
    : grid.querySelectorAll<HTMLElement>('.preloader-num-text')

  // 计算屏幕几何中心与最大欧式距离（中心到视口四角）
  const screenCenterX = window.innerWidth / 2
  const screenCenterY = window.innerHeight / 2
  const maxRadius = Math.hypot(screenCenterX, screenCenterY) || 1

  // 为每个单元格计算相对于屏幕中心的真实欧几里得几何距离
  // 确保「向四周字面消隐」纯粹基于实际同心圆距离原地褪变，不产生任何位移与形变
  const cellDisperseData = activeCells.map((cell) => {
    const r = cell.getBoundingClientRect()
    const cellX = r.left + r.width / 2
    const cellY = r.top + r.height / 2
    const dx = cellX - screenCenterX
    const dy = cellY - screenCenterY
    const dist = Math.hypot(dx, dy)
    const normDist = Math.min(1, dist / maxRadius)
    return {
      cell,
      normDist
    }
  })

  // 测量基础尺寸（未变换状态），严格计算将「O 上半圆白色区域」绝对锚定在屏幕几何中心所需的微量偏移
  const baseW = brandWrap.offsetWidth || 180
  const baseH = brandWrap.offsetHeight || 47
  const shiftX = baseW * 0.5 - baseW * (FOCAL_X_PERCENT / 100)
  const shiftY = baseH * 0.5 - baseH * (FOCAL_Y_PERCENT / 100)

  // 1. 初始状态：中央大 MioAI 绝对隐藏，无任何遮罩层
  gsap.set(brandWrap, {
    transformOrigin: `${FOCAL_X_PERCENT}% ${FOCAL_Y_PERCENT}%`,
    scale: 0.85,
    opacity: 0,
    x: 0,
    y: 0
  })

  if (whiteScreen) {
    gsap.set(whiteScreen, {
      opacity: 0
    })
  }

  // 网格步进波浪配置（以中心向四周 2D 网格形式涟漪扩散）
  const gridRows = isMob ? 5 : 7
  const gridCols = isMob ? 3 : 8
  const gridStagger: gsap.StaggerVars = {
    each: isMob ? 0.05 : 0.06,
    from: 'center',
    grid: [gridRows, gridCols]
  }

  const timeline = gsap.timeline({
    defaults: { ease: 'power2' },
    onComplete: () => {
      visible.value = false
      if (props.sessionOnce) {
        sessionStorage.setItem(props.storageKey, '1')
      }
      emit('complete')
    }
  })
  tl = timeline

  // 2. 起始阶段：周围小网格以中心向四周波浪式淡入，起始全为「M」
  timeline.add('show').to(
    activeCells,
    {
      opacity: 1,
      duration: 0.35,
      ease: 'power1.out',
      stagger: { ...gridStagger, amount: 0.45 }
    },
    'show'
  )

  // 3. 周围小网格涟漪式更新：M -> Mi -> Mio -> MioA -> MioAI
  const steps = LOADING_STEPS.slice(1) // Mi, Mio, MioA, MioAI
  for (let i = 0; i < steps.length; i++) {
    const textVal = steps[i]
    timeline.to(
      activeTexts,
      {
        duration: 0.01,
        ease: 'none',
        stagger: {
          ...gridStagger,
          onComplete: function (this: any) {
            const el = this.targets?.()[0] as HTMLElement | undefined
            if (el) el.textContent = textVal
          }
        }
      },
      `show+=${0.35 + i * 0.26}`
    )
  }

  // 4. 周围小 MioAI 全部加载完毕，微留 0.15s 让视觉定格，随后开始向四周字面单纯消隐：
  const gridDisperseTime = 0.35 + steps.length * 0.26 + 0.15
  timeline.add('disperse', `show+=${gridDisperseTime}`)

  // 4.1 每一个小 MioAI 单元格纯粹进行原地透明度褪变消隐，无任何位移
  // 延迟跨度 0.70s，单字淡出 0.35s，总计 1.05s 全屏小字 100% 彻底消散
  const STAGGER_SPAN = 0.70
  const CELL_FADE_DURATION = 0.35
  cellDisperseData.forEach(({ cell, normDist }) => {
    const cellTriggerDelay = normDist * STAGGER_SPAN
    timeline.to(
      cell,
      {
        opacity: 0,
        duration: CELL_FADE_DURATION,
        ease: 'power1.out'
      },
      `disperse+=${cellTriggerDelay}`
    )
  })

  // 4.2 在中心小字开始消隐的瞬间，中央大 MioAI 直接显现登场（无渐显过渡，直接出现）！
  // 登场后保持绝对静止（完全不动），静静等待周围小 MioAI 全部彻底消隐！
  timeline.set(
    brandWrap,
    {
      opacity: 1,
      scale: 1
    },
    'disperse+=0.08'
  )

  // 5. 核心放大阶段：
  // 严格等待周围所有小 MioAI 全部彻底消失完毕（1.05s）并微留 0.1s 纯净定格后，再启动放大！
  const allDispersedTime = STAGGER_SPAN + CELL_FADE_DURATION + 0.10 // 0.70 + 0.35 + 0.10 = 1.15s
  timeline.add('zoomStart', `disperse+=${allDispersedTime}`)

  // 放大时长 1.20s，由静止持续加速冲入 O 白色笔画
  const ZOOM_DURATION = 1.20

  // 纯矢量路径冲入 O 笔画内：速度越来越快（power2.in），冲击感十足，绝不在终点减速停留
  timeline.to(
    brandWrap,
    {
      scale: 360,
      duration: ZOOM_DURATION,
      ease: 'power2.in'
    },
    'zoomStart'
  )

  // 中心偏置同步以同一缓动补偿，严格锁定视口中心
  timeline.to(
    brandWrap,
    {
      x: shiftX,
      y: shiftY,
      duration: ZOOM_DURATION,
      ease: 'power2.in'
    },
    'zoomStart'
  )

  // 在放大后段（O 白色笔画充满绝大部分视口时），衔接纯白过渡层
  if (whiteScreen) {
    timeline.to(
      whiteScreen,
      {
        opacity: 1,
        duration: 0.22,
        ease: 'power1.in'
      },
      `zoomStart+=${ZOOM_DURATION - 0.22}`
    )
  }

  // 放大冲入达成白幕后，白幕迅速溶褪（0.28s）
  timeline.to(
    preLoader,
    {
      opacity: 0,
      duration: 0.28,
      ease: 'power2.out'
    },
    `zoomStart+=${ZOOM_DURATION}`
  )

  // 关键：在白幕溶褪半程（0.12s后，主站已清晰进入用户视野时），正式触发 reveal 启动首页各组件入场动画！
  timeline.call(() => emit('reveal'), [], `zoomStart+=${ZOOM_DURATION + 0.12}`)
}

onMounted(async () => {
  handleResize()
  window.addEventListener('resize', handleResize, { passive: true })
  window.addEventListener('keydown', handleKeyDown)

  // 确保 Anthropic 官方字体在首帧即已就绪，避免因异步加载闪现系统字体
  if ('fonts' in document) {
    try {
      await Promise.race([
        Promise.all([
          document.fonts.load('600 16px "AnthropicSans Display"'),
          document.fonts.load('700 16px "AnthropicSans Display"'),
          document.fonts.load('500 16px "AnthropicSans Text"')
        ]),
        new Promise((resolve) => setTimeout(resolve, 250))
      ])
    } catch {
      // 容错继续
    }
  }

  runAnimation()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', handleKeyDown)
  if (tl) {
    tl.kill()
    tl = null
  }
})

defineExpose({
  replay: () => {
    visible.value = true
    if (tl) {
      tl.kill()
      tl = null
    }
    requestAnimationFrame(() => runAnimation())
  },
  skip: handleSkip
})
</script>

<style lang="scss" scoped>
/* ==========================================================================
   Seventeen Preloader (MioAI 终极高精版)
   暖深炭灰石背景（非纯黑），统一 Anthropic 官方字体栈，严格中心锁定 O 上半圆白区放大
   ========================================================================== */

.seventeen-preloader {
  position: fixed;
  inset: 0;
  z-index: 99999;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background-color: #2b2824; /* 优雅温润的暖深炭灰石色（非纯黑，具备 Anthropic 纸本质感） */
  color: #faf9f5;
  user-select: none;
  pointer-events: all;
  font-family: 'AnthropicSans Text', -apple-system, sans-serif;
}

/* 底层背景幕布：优雅温润的暖深炭灰（非死黑，具备纸实质感，无边缘发光渐变） */
.preloader-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background-color: #2b2824;
}

/* 铺满全屏的交错蜂巢网格：无任何遮罩裁切，纯粹字形显隐 */
.preloader-grid {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: grid;
  width: 100%;
  height: 100%;
  pointer-events: none;
  grid-column-gap: 8px;
  grid-row-gap: 8px;
  grid-template-columns: repeat(8, 1fr);
  grid-template-rows: repeat(7, 1fr);
  padding: 16px;
  box-sizing: border-box;
}

/* 网格单元格：统一 Anthropic Display 官方字体栈，与中心 MioAI 保持极致字体纯度 */
.preloader-num {
  display: flex;
  justify-content: center;
  align-items: center;
  opacity: 0;
  font-family: 'AnthropicSans Display', 'AnthropicSans Text', sans-serif;
  font-size: clamp(13px, 1.1vw, 16px);
  font-weight: 600;
  letter-spacing: 0.05em;
  color: rgba(250, 249, 245, 0.72);
  will-change: opacity, transform;
}

.preloader-num-text {
  display: inline-block;
  font-family: inherit;
  font-weight: inherit;
  font-variant-numeric: tabular-nums;
  transition: color 0.15s ease;
}

/* 偶数行 50% 偏移，形成经典交错砖砌结构 */
.preloader-num:nth-child(n + 9):nth-child(-n + 16),
.preloader-num:nth-child(n + 25):nth-child(-n + 32),
.preloader-num:nth-child(n + 41):nth-child(-n + 48),
.preloader-num:nth-child(n + 57):nth-child(-n + 64) {
  transform: translateX(50%);
}

/* 移动端响应式网格 (3列 x 5行) */
@media (max-width: 991px) {
  .preloader-grid {
    grid-template-columns: repeat(3, 1fr);
    grid-template-rows: repeat(5, 1fr);
    grid-column-gap: 8px;
    grid-row-gap: 16px;
  }

  .preloader-num {
    font-size: 14px;
  }

  .preloader-num.is-desktop {
    display: none !important;
  }

  .preloader-num:nth-child(n + 4):nth-child(-n + 6),
  .preloader-num:nth-child(n + 10):nth-child(-n + 12) {
    transform: translateX(50%);
  }
}

/* 居中核心舞台：完全由视口绝对居中锁定 (50vw, 50vh)，严禁产生纵向向下偏移 */
.preloader-center-stage {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 5;
  display: flex;
  justify-content: center;
  align-items: center;
  pointer-events: none;
}

/* 中央矢量容器：适度收敛精致的初始尺寸（约 140px ~ 195px 宽度），无任何多余发光 */
.brand-vector-wrap {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  width: clamp(140px, 13vw, 195px);
  height: auto;
  user-select: none;
  opacity: 0;
  z-index: 3;
  will-change: transform, opacity;
}

.mioai-vector-svg {
  width: 100%;
  height: auto;
  display: block;
  overflow: visible;
  /* 确保矢量贝塞尔曲线在放大时以最高平滑度光栅化，绝无锯齿 */
  shape-rendering: geometricPrecision;
}

/* 全屏纯白过渡层：由 O 上半圆白色区域极速覆盖后平滑过渡至全白 */
.white-screen-layer {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  background-color: #ffffff;
  pointer-events: none;
  z-index: 7;
  opacity: 0;
  will-change: opacity;
}

</style>