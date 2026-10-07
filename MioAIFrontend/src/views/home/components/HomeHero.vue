<template>
  <section class="hero">
    <div class="home-container">
      <h1 class="hero-title">
        <span class="line hm-reveal">会思考、能行动的</span>
        <span class="line hm-reveal delay-1">
          AI 助手<span class="brand">MioBot</span>
        </span>
      </h1>

      <p class="hero-sub hm-reveal delay-2">
        自主规划任务、联网搜索、抓取网页、生成文件<br class="sub-break" />
        像真人助理一样把事情做完
      </p>

      <div class="hero-actions hm-reveal delay-3">
        <button type="button" class="hm-btn" @click="router.push('/chat')">
          立即开始
          <svg class="arrow" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M2 8h11M9 3.5 13.5 8 9 12.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        <button type="button" class="hm-btn hm-btn--outline" @click="scrollToFeatures">了解更多</button>
      </div>

      <button type="button" class="hero-banner hm-reveal delay-4" @click="router.push('/dashboard/agent-market')">
        <span class="banner-mark">MioBot</span>
        <span class="banner-text">绑定 MCP 工具与知识库，打造你的专属智能体</span>
        <span class="banner-link">
          去看看
          <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M2 8h11M9 3.5 13.5 8 9 12.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function scrollToFeatures(): void {
  document.getElementById('capabilities')?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<style lang="scss" scoped>
.hero {
  padding: clamp(56px, 9vh, 104px) 0 clamp(48px, 8vh, 88px);
}

.hero-title {
  font-family: var(--hm-font-display);
  font-size: clamp(42px, 6.6vw, 84px);
  font-weight: 700;
  line-height: 1.18;
  letter-spacing: -0.015em;
  color: var(--hm-text);
  display: flex;
  flex-direction: column;

  .line {
    display: block;
  }

  .brand {
    color: var(--hm-green);
    margin-left: 0.18em;
    font-style: normal;
  }
}

.hero-sub {
  margin-top: 28px;
  max-width: 640px;
  font-size: 17px;
  line-height: 1.75;
  color: var(--hm-text-2);

  .sub-break {
    display: inline;

    @media (max-width: 640px) {
      display: none;
    }
  }
}

.hero-actions {
  margin-top: 40px;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;

  .arrow {
    width: 15px;
    height: 15px;
  }
}

/* 公告横幅卡(仿官网 hero 下的产品横幅) */
.hero-banner {
  margin-top: clamp(48px, 8vh, 72px);
  width: 100%;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 18px 24px;
  border-radius: 16px;
  background: var(--hm-bg-raised);
  border: 1px solid var(--hm-border);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.25s var(--hm-ease), box-shadow 0.25s var(--hm-ease);

  &:hover {
    border-color: var(--hm-border-strong, #9b9890);
    box-shadow: 0 4px 20px rgba(20, 20, 19, 0.06);

    .banner-link {
      gap: 10px;
    }
  }

  .banner-mark {
    flex-shrink: 0;
    font-family: var(--hm-font-display);
    font-weight: 700;
    font-size: 16px;
    color: var(--hm-text);
    padding-right: 20px;
    border-right: 1px solid var(--hm-border);
  }

  .banner-text {
    flex: 1;
    font-size: 15px;
    color: var(--hm-text-2);
    min-width: 0;
  }

  .banner-link {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: var(--hm-green);
    transition: gap 0.25s var(--hm-ease);

    svg {
      width: 14px;
      height: 14px;
    }
  }

  @media (max-width: 640px) {
    flex-wrap: wrap;

    .banner-text {
      flex-basis: 100%;
      order: 3;
    }
  }
}

/* 载入错落 */
.delay-1 {
  animation-delay: 80ms;
}
.delay-2 {
  animation-delay: 160ms;
}
.delay-3 {
  animation-delay: 240ms;
}
.delay-4 {
  animation-delay: 320ms;
}
</style>
