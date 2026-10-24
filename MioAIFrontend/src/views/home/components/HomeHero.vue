<template>
  <section class="hero">
    <HomeCloudFlow class="hero-clouds" />

    <div class="home-container hero-inner">
      <HomeSlogan />

      <!-- /chat 复刻演示窗：自动播放能力演示（含左轨/会话栏/真实消息组件/输入框） -->
      <div class="hero-demo hm-reveal delay-1">
        <HomeChatDemo />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import HomeChatDemo from './HomeChatDemo.vue'
import HomeCloudFlow from './HomeCloudFlow.vue'
import HomeSlogan from './HomeSlogan.vue'
</script>

<style lang="scss" scoped>
.hero {
  position: relative;
  display: flex;
  align-items: center;
  // 上移一个顶栏高度：云层从视口顶端铺起，顶栏未滚动时与背景连成一片（不留横向接缝）
  margin-top: -72px;
  padding: calc(72px + clamp(28px, 6vh, 56px)) 0 clamp(36px, 6vh, 64px);
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

.hero-demo {
  width: 100%;
  display: flex;
  justify-content: center;
  margin-top: clamp(24px, 4vh, 40px);
}

.delay-1 {
  animation-delay: 90ms;
}
</style>
