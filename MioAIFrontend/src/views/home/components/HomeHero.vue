<template>
  <section class="hero">
    <HomeCloudFlow class="hero-clouds" />

    <div class="home-container hero-inner">
      <div ref="sloganWrapRef" class="hero-slogan-wrap">
        <HomeSlogan />
      </div>

      <!-- /chat 复刻演示窗：自动播放能力演示（含左轨/会话栏/真实消息组件/输入框） -->
      <div class="hero-demo">
        <HomeChatDemo ref="chatDemoRef" :auto-start="!preloading" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import gsap from 'gsap'
import HomeChatDemo from './HomeChatDemo.vue'
import HomeCloudFlow from './HomeCloudFlow.vue'
import HomeSlogan from './HomeSlogan.vue'

const props = withDefaults(
  defineProps<{
    /** 是否处于首屏加载等待入场态 */
    preloading?: boolean
  }>(),
  {
    preloading: false
  }
)

const sloganWrapRef = ref<HTMLElement | null>(null)
const chatDemoRef = ref<InstanceType<typeof HomeChatDemo> | null>(null)

let entranceTl: gsap.core.Timeline | null = null

function prepareEntrance(): void {
  if (entranceTl) {
    entranceTl.kill()
    entranceTl = null
  }

  // 1. 停止并重置聊天演示剧本
  chatDemoRef.value?.resetDemo()
  if (chatDemoRef.value) {
    chatDemoRef.value.isDocked = false
  }

  // 2. 标语“会思考，能行动”置于初始微缩位（scale: 0.35），确保放大登场清晰可见
  if (sloganWrapRef.value) {
    gsap.set(sloganWrapRef.value, {
      opacity: 0,
      scale: 0.35,
      transformOrigin: '50% 50%'
    })
  }

  // 3. 侧边栏与聊天区置于屏幕两侧入场初始位
  // 注意：左盒宽度(328px)明显小于右盒(792px)，其滑入位移严格小于右盒
  const isDesktop = typeof window !== 'undefined' && window.innerWidth > 980
  const drawerEl = chatDemoRef.value?.panelsDrawerRef
  const mainEl = chatDemoRef.value?.demoMainRef

  if (drawerEl && mainEl) {
    if (isDesktop) {
      const leftDist = Math.round(Math.min(window.innerWidth * 0.24, 330))
      const rightDist = Math.round(Math.min(window.innerWidth * 0.52, 720))
      gsap.set(drawerEl, { x: -leftDist, opacity: 0 })
      gsap.set(mainEl, { x: rightDist, opacity: 0 })
    } else {
      gsap.set(mainEl, { y: 40, opacity: 0 })
    }
  }
}

function playEntrance(): void {
  if (entranceTl) {
    entranceTl.kill()
  }

  const isDesktop = typeof window !== 'undefined' && window.innerWidth > 980
  const sloganEl = sloganWrapRef.value
  const drawerEl = chatDemoRef.value?.panelsDrawerRef
  const mainEl = chatDemoRef.value?.demoMainRef

  const tl = gsap.timeline({
    defaults: { ease: 'power3.out' }
  })
  entranceTl = tl

  // 1. “会思考，能行动” 文字快速显色并在中心显著放大登场（scale: 0.35 -> 1.0）
  if (sloganEl) {
    tl.to(
      sloganEl,
      {
        opacity: 1,
        duration: 0.22,
        ease: 'power1.out'
      },
      0
    )
    tl.to(
      sloganEl,
      {
        scale: 1,
        duration: 0.75,
        ease: 'back.out(1.18)',
        clearProps: 'transform,opacity'
      },
      0
    )
  }

  // 2. 左右两盒按各自尺寸体量非对称滑入（左盒位移较小，右盒位移较大），全程清晰可见对接融合 (0.85s)
  if (drawerEl && mainEl) {
    if (isDesktop) {
      tl.to(
        drawerEl,
        {
          x: 0,
          opacity: 1,
          duration: 0.85,
          ease: 'power3.out'
        },
        0.02
      )
      tl.to(
        mainEl,
        {
          x: 0,
          opacity: 1,
          duration: 0.85,
          ease: 'power3.out'
        },
        0.02
      )

      // 在滑行至 68% 处（0.02 + 0.56 = 0.58s）两盒靠近碰头，内侧圆角平滑收起缝合
      tl.call(
        () => {
          if (chatDemoRef.value) {
            chatDemoRef.value.isDocked = true
          }
        },
        [],
        0.58
      )

      tl.set([drawerEl, mainEl], { clearProps: 'transform' }, 0.88)
    } else {
      tl.to(
        mainEl,
        {
          y: 0,
          opacity: 1,
          duration: 0.70,
          ease: 'power3.out',
          clearProps: 'transform,opacity'
        },
        0.02
      )
      tl.call(
        () => {
          if (chatDemoRef.value) {
            chatDemoRef.value.isDocked = true
          }
        },
        [],
        0.50
      )
    }
  }

  // 3. 并接完成后稍作停顿，随即正式启动剧本演示
  tl.call(
    () => {
      chatDemoRef.value?.playDemo()
    },
    [],
    0.95
  )
}

defineExpose({
  prepareEntrance,
  playEntrance
})
</script>

<style lang="scss" scoped>
.hero {
  position: relative;
  display: flex;
  align-items: center;
  // 上移一个顶栏高度：云层从视口顶端铺起，顶栏未滚动时与背景连成一片（不留横向接缝）
  margin-top: -72px;
  padding: calc(72px + clamp(28px, 6vh, 56px)) 0 clamp(36px, 6vh, 64px);
  overflow-x: clip;
}

/* 云层淡出收尾：底部渐隐到页面底色，与下方区块无缝衔接 */
.hero-clouds {
  mask-image: linear-gradient(180deg, #000 0%, #000 72%, transparent 100%);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, #000 72%, transparent 100%);
}

.hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.hero-slogan-wrap {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  transform-origin: center center;
  will-change: transform, opacity;
}

.hero-demo {
  width: 100%;
  display: flex;
  justify-content: center;
  margin-top: clamp(24px, 4vh, 40px);
}
</style>
