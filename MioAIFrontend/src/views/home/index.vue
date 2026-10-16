<template>
  <div class="home-page">
    <HomeHeader />
    <main>
      <HomeHero :announcement="announcement" />
      <HomeContentWall :items="items" />
    </main>
    <HomeFooter />
  </div>
</template>

<script setup lang="ts">
import HomeHeader from './components/HomeHeader.vue'
import HomeHero from './components/HomeHero.vue'
import HomeContentWall from './components/HomeContentWall.vue'
import HomeFooter from './components/HomeFooter.vue'
import { useHomeContent } from './composables/useHomeContent'

const { items, announcement } = useHomeContent()
</script>

<style lang="scss">
/* 共享 Token 为全局层(供各子组件的 scoped 样式引用 CSS 变量);字体已由 global.scss 全局引入 */
@import './home.scss';
</style>
