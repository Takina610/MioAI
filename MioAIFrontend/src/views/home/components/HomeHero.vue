<template>
  <section class="hero">
    <div class="hero-backdrop" aria-hidden="true"></div>

    <div class="home-container hero-inner">
      <div class="hero-announce-row">
        <router-link v-if="announcement" class="hero-announce hm-reveal" :to="announcement.to">
          <span class="spark" aria-hidden="true">✦</span>
          <span class="announce-text">{{ announcement.text }}</span>
          <svg class="announce-arrow" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path
              d="M2 8h11M9 3.5 13.5 8 9 12.5"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </router-link>
      </div>

      <h1 class="hero-slogan hm-reveal delay-1">会思考，能行动</h1>

      <div class="hero-prompt hm-reveal delay-2">
        <HomePromptCard />
      </div>

      <nav class="hero-entries hm-reveal delay-3" aria-label="内容入口">
        <router-link v-for="entry in entries" :key="entry.to" class="entry-pill" :to="entry.to">
          {{ entry.label }}
        </router-link>
      </nav>
    </div>
  </section>
</template>

<script setup lang="ts">
import HomePromptCard from './HomePromptCard.vue'

defineProps<{
  announcement: { text: string; to: string } | null
}>()

/** 与顶栏导航同源的内容入口：首屏直接进内容，不做能力宣传 */
const entries = [
  { label: '智能体广场', to: '/dashboard/agent-market' },
  { label: 'MCP 广场', to: '/dashboard/mcp-market' },
  { label: '公共知识库', to: '/dashboard/public-knowledge' }
]
</script>

<style lang="scss" scoped>
.hero {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  min-height: min(calc(100svh - 72px), 700px);
  padding: clamp(40px, 7vh, 72px) 0 clamp(28px, 4vh, 48px);
}

/* 顶部品牌色淡染 + 缓慢漂移的柔光：参考 DeepSeek 首屏的通透感，纯 CSS 不加画布。
   渐变补中间色标：两色直接线性插值到透明会在中段显出一条横向 band */
.hero-backdrop {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(
      100% 58% at 50% -12%,
      rgba(42, 161, 169, 0.13) 0%,
      rgba(42, 161, 169, 0.07) 38%,
      rgba(42, 161, 169, 0.02) 68%,
      rgba(42, 161, 169, 0) 100%
    ),
    linear-gradient(
      180deg,
      rgba(42, 161, 169, 0.05) 0%,
      rgba(42, 161, 169, 0.02) 40%,
      rgba(240, 238, 230, 0) 100%
    );
}

.hero-backdrop::after {
  content: '';
  position: absolute;
  top: -18%;
  left: 50%;
  width: min(720px, 82vw);
  height: 420px;
  margin-left: min(-360px, -41vw);
  background: radial-gradient(
    closest-side,
    rgba(42, 161, 169, 0.11) 0%,
    rgba(42, 161, 169, 0.05) 45%,
    rgba(42, 161, 169, 0) 100%
  );
  animation: hm-drift 26s ease-in-out infinite alternate;
}

@keyframes hm-drift {
  from {
    transform: translate3d(-6%, 0, 0) scale(1);
  }
  to {
    transform: translate3d(6%, 4%, 0) scale(1.08);
  }
}

.hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

/* 公告行占位固定高度：数据到达前后不推动首屏 */
.hero-announce-row {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
}

.hero-announce {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: min(100%, 560px);
  font-size: 14px;
  color: var(--hm-text-2);
  opacity: 0.75;
  transition: opacity 0.25s var(--hm-ease), color 0.25s var(--hm-ease);

  .spark {
    color: var(--hm-green);
    font-size: 13px;
    line-height: 1;
  }

  .announce-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .announce-arrow {
    flex-shrink: 0;
    width: 14px;
    height: 14px;
    transition: transform 0.25s var(--hm-ease);
  }

  &:hover {
    opacity: 1;
    color: var(--hm-text);

    .announce-arrow {
      transform: translateX(3px);
    }
  }
}

/* 标语：宽字距衬线大字，字距末位空档用 text-indent 抵消以保持视觉居中 */
.hero-slogan {
  margin-top: clamp(18px, 3vh, 34px);
  font-family: var(--hm-font-display);
  font-size: clamp(34px, 5.4vw, 54px);
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.14em;
  text-indent: 0.14em;
  text-align: center;
  color: var(--hm-green-deep);
}

.hero-prompt {
  width: min(100%, 680px);
  margin-top: clamp(26px, 4vh, 42px);
}

.hero-entries {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: clamp(22px, 3vh, 34px);
}

.entry-pill {
  padding: 10px 22px;
  border-radius: 999px;
  border: 1px solid var(--hm-border);
  font-size: 14px;
  font-weight: 500;
  color: var(--hm-text);
  transition: border-color 0.25s var(--hm-ease), background 0.25s var(--hm-ease);

  &:hover {
    border-color: var(--hm-green);
    background: var(--hm-green-soft);
    color: var(--hm-green-deep);
  }
}

.delay-1 {
  animation-delay: 90ms;
}
.delay-2 {
  animation-delay: 180ms;
}
.delay-3 {
  animation-delay: 270ms;
}
</style>
