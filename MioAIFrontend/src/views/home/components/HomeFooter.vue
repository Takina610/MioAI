<template>
  <footer class="footer">
    <div class="home-container">
      <div class="footer-top">
        <div class="footer-brand">
          <div class="brand-row">
            <img src="/logo.png" alt="MioAI" class="brand-logo" />
            <span class="brand-name">MioAI</span>
          </div>
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
      { label: '技能广场', to: '/dashboard/skill-market' },
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
      { label: '控制台', to: '/dashboard' },
      { label: '个人中心', to: '/dashboard/profile' }
    ]
  }
]
</script>

<style lang="scss" scoped>
/* 与首屏同底：页脚不做深色块，直接落在页面背景上，
   只用一条极淡的分隔线与上方内容区分，避免"贴了一整块深色"的割裂感 */
.footer {
  background: transparent;
  padding: clamp(48px, 6vw, 80px) 0 32px;
}

.footer-top {
  display: grid;
  grid-template-columns: 1.6fr repeat(3, 1fr);
  gap: 40px;
  padding-top: clamp(32px, 4vw, 48px);
  border-top: 1px solid var(--hm-border-subtle);
}

.footer-brand {
  .brand-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .brand-logo {
    // 与顶栏同一份原始素材：logo 本身是黑白双色，叠滤镜会把白色部分压成灰、糊成一团
    width: 26px;
    height: 26px;
  }

  .brand-name {
    font-family: var(--hm-font-display);
    font-size: 20px;
    font-weight: 700;
    color: var(--hm-text);
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
    color: var(--hm-text-muted);
    margin-bottom: 4px;
  }

  .col-link {
    font-size: 14px;
    color: var(--hm-text-2);
    transition: color 0.2s var(--hm-ease);

    &:hover {
      color: var(--hm-green-deep);
    }
  }
}

.footer-bottom {
  margin-top: clamp(40px, 6vw, 64px);
  padding-top: 24px;
  border-top: 1px solid var(--hm-border-subtle);
  font-size: 13px;
  color: var(--hm-text-muted);
}
</style>
