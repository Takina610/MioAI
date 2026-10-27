<template>
  <div class="home-page" :class="{ 'is-preloading': isPreloading }">
    <!-- Seventeen Agency 风格首屏加载动画 -->
    <SeventeenPreloader
      v-if="showPreloader"
      ref="preloaderRef"
      @reveal="onReveal"
      @complete="onComplete"
    />

    <HomeHeader ref="headerRef" />

    <main class="home-main-wrap">
      <HomeHero ref="heroRef" :preloading="isPreloading" />
    </main>

    <HomeFooter @replay-animation="replayPreloader" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import SeventeenPreloader from '@/components/ui/SeventeenPreloader.vue'
import HomeHeader from './components/HomeHeader.vue'
import HomeHero from './components/HomeHero.vue'
import HomeFooter from './components/HomeFooter.vue'

const route = useRoute()

/**
 * 会话态首访标记：
 * 浏览器硬刷新（F5 / 首次进入）时模块重新加载，loadedInSession 初始为 false，首屏正常播放；
 * SPA 内部路由切回时（如从对话页回首页）不重复打扰，若 URL 带 ?preloader=1 或点击页脚重播则强制播放。
 */
let loadedInSession = false

const isPreloading = ref(false)
const showPreloader = ref(false)
const preloaderRef = ref<InstanceType<typeof SeventeenPreloader> | null>(null)
const headerRef = ref<InstanceType<typeof HomeHeader> | null>(null)
const heroRef = ref<InstanceType<typeof HomeHero> | null>(null)

function shouldPlayPreloader(): boolean {
  if (route.query.preloader === '1' || route.query.replay === '1') return true
  if (!loadedInSession) return true
  return false
}

onMounted(() => {
  if (shouldPlayPreloader()) {
    loadedInSession = true
    isPreloading.value = true
    showPreloader.value = true

    nextTick(() => {
      headerRef.value?.prepareEntrance()
      heroRef.value?.prepareEntrance()
    })
  }
})

function onReveal(): void {
  headerRef.value?.playEntrance()
  heroRef.value?.playEntrance()
}

function onComplete(): void {
  isPreloading.value = false
  showPreloader.value = false
}

function replayPreloader(): void {
  isPreloading.value = true
  showPreloader.value = true

  nextTick(() => {
    headerRef.value?.prepareEntrance()
    heroRef.value?.prepareEntrance()
    preloaderRef.value?.replay()
  })
}
</script>

<style lang="scss">
/* 共享 Token 为全局层(供各子组件的 scoped 样式引用 CSS 变量);字体已由 global.scss 全局引入 */
@import './home.scss';

.home-page.is-preloading {
  height: 100vh;
  overflow: hidden;
}

.home-main-wrap {
  width: 100%;
}
</style>
