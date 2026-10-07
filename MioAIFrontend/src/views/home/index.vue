<template>
  <div class="home-page">
    <HomeHeader />
    <main>
      <HomeHero />
      <HomeFeatures />
      <HomeStatement />
    </main>
    <HomeFooter />
  </div>
</template>

<script setup lang="ts">
import HomeHeader from './components/HomeHeader.vue'
import HomeHero from './components/HomeHero.vue'
import HomeFeatures from './components/HomeFeatures.vue'
import HomeStatement from './components/HomeStatement.vue'
import HomeFooter from './components/HomeFooter.vue'
import { useScrollReveal } from './composables/useScrollReveal'

useScrollReveal('.home-page')
</script>

<style lang="scss">
/* 字体与共享 Token 为全局层(供各子组件的 scoped 样式引用 CSS 变量) */
@import '@/styles/home-fonts.scss';
@import './home.scss';
</style>
