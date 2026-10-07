<template>
  <section id="capabilities" class="features">
    <div class="home-container">
      <div class="section-head hm-scroll-reveal">
        <h2 class="section-title">核心能力</h2>
        <p class="section-lead">从对话到执行，MioAI 把「回答问题」延伸到「完成任务」</p>
      </div>

      <div class="grid">
        <router-link
          v-for="(feature, i) in features"
          :key="feature.title"
          :to="feature.to"
          class="card hm-scroll-reveal"
          :class="{ 'delay-more': i >= 2 }"
        >
          <span class="hm-tag">{{ feature.tag }}</span>
          <h3 class="card-title">{{ feature.title }}</h3>
          <p class="card-desc">{{ feature.desc }}</p>
          <span class="card-arrow" aria-hidden="true">
            <svg viewBox="0 0 16 16" fill="none">
              <path d="M2 8h11M9 3.5 13.5 8 9 12.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </span>
        </router-link>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
interface Feature {
  tag: string
  title: string
  desc: string
  to: string
}

const features: Feature[] = [
  {
    tag: 'Agent',
    title: '完整 Agent 能力',
    desc: '自主规划任务清单，逐步调用工具执行，失败自动重试，直到完成你的需求',
    to: '/chat'
  },
  {
    tag: '智能体',
    title: '定制智能体',
    desc: '创建专属智能体，绑定你的 MCP 工具与知识库，同样具备完整 Agent 能力',
    to: '/dashboard/agent'
  },
  {
    tag: '知识库',
    title: '知识库',
    desc: '上传文档构建知识库，让 AI 拥有专业知识背景，回答有据可依',
    to: '/dashboard/public-knowledge'
  },
  {
    tag: '工具',
    title: 'MCP 工具',
    desc: '接入 MCP 工具生态，联网搜索、文件操作、系统联动，扩展能力边界',
    to: '/dashboard/mcp-market'
  },
  {
    tag: '体验',
    title: '全流程流式输出',
    desc: '思考过程、工具调用与回答全程实时可见，每一步都看得见',
    to: '/chat'
  },
  {
    tag: '安全',
    title: '安全与开放接口',
    desc: '数据加密存储、多层级权限管理，并提供完整 API 方便集成到你的应用',
    to: '/dashboard'
  }
]
</script>

<style lang="scss" scoped>
.features {
  background: var(--hm-bg-raised);
  border-block: 1px solid var(--hm-border-subtle);
  padding: clamp(64px, 9vw, 112px) 0;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: clamp(40px, 5vw, 64px);

  .section-title {
    font-family: var(--hm-font-display);
    font-size: clamp(30px, 4vw, 48px);
    font-weight: 700;
    letter-spacing: -0.01em;
    line-height: 1.2;
    color: var(--hm-text);
  }

  .section-lead {
    font-size: 15px;
    line-height: 1.75;
    color: var(--hm-text-2);
    max-width: 320px;
    text-align: right;

    @media (max-width: 768px) {
      text-align: left;
    }
  }

  @media (max-width: 768px) {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: clamp(24px, 3vw, 36px);
  border-radius: 16px;
  background: var(--hm-bg);
  border: 1px solid var(--hm-border-subtle);
  transition: transform 0.3s var(--hm-ease), border-color 0.3s var(--hm-ease),
    box-shadow 0.3s var(--hm-ease);

  &:hover {
    transform: translateY(-3px);
    border-color: var(--hm-border);
    box-shadow: 0 8px 28px rgba(20, 20, 19, 0.07);

    .card-arrow {
      opacity: 1;
      transform: translateX(0);
    }
  }

  .card-title {
    margin-top: 14px;
    font-family: var(--hm-font-display);
    font-size: 22px;
    font-weight: 700;
    color: var(--hm-text);
    letter-spacing: -0.01em;
  }

  .card-desc {
    margin-top: 10px;
    font-size: 14.5px;
    line-height: 1.75;
    color: var(--hm-text-2);
    max-width: 46ch;
  }

  .card-arrow {
    margin-top: 18px;
    width: 16px;
    height: 16px;
    color: var(--hm-green);
    opacity: 0;
    transform: translateX(-6px);
    transition: opacity 0.3s var(--hm-ease), transform 0.3s var(--hm-ease);

    svg {
      width: 100%;
      height: 100%;
    }
  }
}

/* 同排卡片错落出现 */
.delay-more {
  transition-delay: 80ms;
}
</style>
