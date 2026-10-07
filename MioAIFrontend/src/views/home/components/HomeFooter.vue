<template>
  <footer class="footer">
    <div class="home-container">
      <div class="footer-top">
        <div class="footer-brand">
          <div class="brand-row">
            <img src="/logo.png" alt="MioAI" class="brand-logo" />
            <span class="brand-name">MioAI</span>
          </div>
          <p class="brand-desc">会思考、能行动的 AI 助手</p>
        </div>

        <nav v-for="group in groups" :key="group.title" class="footer-col" :aria-label="group.title">
          <h3 class="col-title">{{ group.title }}</h3>
          <router-link v-for="item in group.items" :key="item.label" :to="item.to" class="col-link">
            {{ item.label }}
          </router-link>
        </nav>
      </div>

      <div class="footer-bottom">
        <span>&copy; 2026 MioAI</span>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
interface FooterGroup {
  title: string
  items: { label: string; to: string }[]
}

const groups: FooterGroup[] = [
  {
    title: '产品',
    items: [
      { label: 'MioBot 对话', to: '/chat' },
      { label: '智能体广场', to: '/dashboard/agent-market' },
      { label: 'MCP 广场', to: '/dashboard/mcp-market' },
      { label: '公共知识库', to: '/dashboard/public-knowledge' }
    ]
  },
  {
    title: '控制台',
    items: [
      { label: '我的智能体', to: '/dashboard/agent' },
      { label: '我的 MCP', to: '/dashboard/mcp' },
      { label: '我的知识库', to: '/dashboard/knowledge' },
      { label: '使用记录', to: '/dashboard/usage' }
    ]
  },
  {
    title: '账号',
    items: [
      { label: '个人中心', to: '/dashboard/profile' },
      { label: '快速开始', to: '/chat' }
    ]
  }
]
</script>

<style lang="scss" scoped>
.footer {
  background: var(--hm-bg-dark);
  color: var(--hm-text-invert);
  padding: clamp(48px, 6vw, 80px) 0 32px;
}

.footer-top {
  display: grid;
  grid-template-columns: 1.6fr repeat(3, 1fr);
  gap: 40px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr 1fr;

    .footer-brand {
      grid-column: 1 / -1;
    }
  }
}

.footer-brand {
  .brand-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .brand-logo {
    width: 26px;
    height: 26px;
    filter: brightness(0) invert(1);
  }

  .brand-name {
    font-family: var(--hm-font-display);
    font-size: 20px;
    font-weight: 700;
  }

  .brand-desc {
    margin-top: 14px;
    font-size: 14px;
    color: rgba(250, 249, 245, 0.55);
  }
}

.footer-col {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .col-title {
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.08em;
    color: rgba(250, 249, 245, 0.45);
    margin-bottom: 4px;
  }

  .col-link {
    font-size: 14px;
    color: rgba(250, 249, 245, 0.82);
    transition: color 0.2s var(--hm-ease);

    &:hover {
      color: #7fd0d5;
    }
  }
}

.footer-bottom {
  margin-top: clamp(40px, 6vw, 64px);
  padding-top: 24px;
  border-top: 1px solid rgba(250, 249, 245, 0.12);
  font-size: 13px;
  color: rgba(250, 249, 245, 0.45);
}
</style>
