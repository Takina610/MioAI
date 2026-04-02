<template>
  <div class="home-page">
    <header class="header">
      <div class="header-content">
        <a class="logo-link" @click="refreshPage">
          <img src="/favicon.svg" alt="Logo" class="logo-img" />
          <span class="logo-text">MioAI</span>
        </a>
        <div class="header-right">
          <template v-if="userStore.isLoggedIn">
            <a-dropdown :trigger="['hover']">
              <div class="user-info">
                <a-avatar :size="36" :src="userStore.userAvatar">
                  {{ userStore.userName?.charAt(0)?.toUpperCase() }}
                </a-avatar>
                <span class="user-name">{{ userStore.userName }}</span>
              </div>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="dashboard" @click="goToDashboard">
                    <SettingOutlined /> 控制台
                  </a-menu-item>
                  <a-menu-item key="profile" @click="goToProfile">
                    <UserOutlined /> 个人中心
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="logout" @click="handleLogout">
                    <LogoutOutlined /> 退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
          <template v-else>
            <a-button type="primary" class="start-btn" @click="goToDashboard">
              快速开始
            </a-button>
          </template>
        </div>
      </div>
    </header>

    <main class="main-content">
      <section class="hero-section">
        <div class="hero-content">
          <h1 class="hero-title">
            智能体开发平台
            <span class="highlight">MioAI</span>
          </h1>
          <p class="hero-desc">
            构建你的AI智能体，连接知识库与工具，打造专属AI助手
          </p>
          <div class="hero-actions">
            <a-button type="primary" size="large" @click="handleStart">
              立即开始
              <RightOutlined />
            </a-button>
            <a-button size="large" @click="scrollToFeatures">
              了解更多
            </a-button>
          </div>
        </div>
        <div class="hero-illustration">
          <div class="illustration-card card-1">
            <RobotOutlined class="icon" />
            <span>智能对话</span>
          </div>
          <div class="illustration-card card-2">
            <DatabaseOutlined class="icon" />
            <span>知识库</span>
          </div>
          <div class="illustration-card card-3">
            <ToolOutlined class="icon" />
            <span>MCP工具</span>
          </div>
        </div>
      </section>

      <section class="features-section" ref="featuresRef">
        <h2 class="section-title">核心功能</h2>
        <div class="features-grid">
          <div class="feature-card" v-for="feature in features" :key="feature.title">
            <div class="feature-icon">
              <component :is="feature.icon" />
            </div>
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-desc">{{ feature.desc }}</p>
          </div>
        </div>
      </section>
    </main>

    <footer class="footer">
      <p>&copy; 2026 MioAI. All rights reserved.</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { Component } from 'vue'
import {
  RobotOutlined,
  DatabaseOutlined,
  ToolOutlined,
  RightOutlined,
  SettingOutlined,
  UserOutlined,
  LogoutOutlined,
  ApiOutlined,
  SafetyOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import { useUserStore } from '@/store/user'
import { message } from 'ant-design-vue'

interface Feature {
  icon: Component
  title: string
  desc: string
}

const router = useRouter()
const userStore = useUserStore()
const featuresRef = ref<HTMLElement | null>(null)

function refreshPage(): void {
  window.location.reload()
}

function goToDashboard(): void {
  router.push('/dashboard')
}

function goToProfile(): void {
  router.push('/dashboard/profile')
}

function handleStart(): void {
  router.push('/dashboard')
}

function scrollToFeatures(): void {
  featuresRef.value?.scrollIntoView({ behavior: 'smooth' })
}

async function handleLogout(): Promise<void> {
  await userStore.logout()
  message.success('已退出登录')
  router.push('/')
}

const features: Feature[] = [
  {
    icon: RobotOutlined,
    title: '智能体管理',
    desc: '创建和管理你的 AI 智能体，配置系统提示词和行为'
  },
  {
    icon: DatabaseOutlined,
    title: '知识库',
    desc: '上传文档构建知识库，让 AI 拥有专业知识背景'
  },
  {
    icon: ToolOutlined,
    title: 'MCP 工具',
    desc: '集成 MCP 工具，扩展 AI 的能力边界'
  },
  {
    icon: ApiOutlined,
    title: 'API 接口',
    desc: '提供完整的 API 接口，方便集成到你的应用中'
  },
  {
    icon: SafetyOutlined,
    title: '安全可靠',
    desc: '数据安全加密，多层级权限管理'
  },
  {
    icon: ThunderboltOutlined,
    title: '高效响应',
    desc: '流式响应技术，实时获取 AI 回复'
  }
]
</script>

<style lang="scss" scoped>
.home-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #f0f0f0;
  z-index: 100;

  .header-content {
    max-width: 1200px;
    margin: 0 auto;
    height: 100%;
    padding: 0 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .logo-link {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    text-decoration: none;

    .logo-img {
      width: 32px;
      height: 32px;
    }

    .logo-text {
      font-size: 24px;
      font-weight: 700;
      color: $primary-color;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    padding: 4px 8px;
    border-radius: 8px;
    transition: background 0.3s;

    &:hover {
      background: #f5f5f5;
    }

    .user-name {
      font-size: 14px;
      color: $text-dark;
    }
  }

  .start-btn {
    background: $primary-color;
    border-color: $primary-color;

    &:hover {
      background: #238b92;
      border-color: #238b92;
    }
  }
}

.main-content {
  flex: 1;
  padding-top: 64px;
}

.hero-section {
  min-height: calc(100vh - 64px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
  max-width: 1200px;
  margin: 0 auto;
  gap: 80px;

  .hero-content {
    flex: 1;

    .hero-title {
      font-size: 48px;
      font-weight: 700;
      color: $text-dark;
      line-height: 1.2;
      margin-bottom: 24px;

      .highlight {
        color: $primary-color;
        display: block;
      }
    }

    .hero-desc {
      font-size: 18px;
      color: #666;
      margin-bottom: 40px;
      max-width: 480px;
    }

    .hero-actions {
      display: flex;
      gap: 16px;
    }
  }

  .hero-illustration {
    position: relative;
    width: 400px;
    height: 300px;

    .illustration-card {
      position: absolute;
      background: #fff;
      border-radius: 12px;
      padding: 20px 24px;
      box-shadow: $shadow-medium;
      display: flex;
      align-items: center;
      gap: 12px;
      animation: float 3s ease-in-out infinite;

      .icon {
        font-size: 24px;
        color: $primary-color;
      }

      span {
        font-size: 14px;
        font-weight: 500;
        color: $text-dark;
      }

      &.card-1 {
        top: 20px;
        left: 40px;
        animation-delay: 0s;
      }

      &.card-2 {
        top: 100px;
        right: 20px;
        animation-delay: 0.5s;
      }

      &.card-3 {
        bottom: 40px;
        left: 80px;
        animation-delay: 1s;
      }
    }
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.features-section {
  padding: 80px 24px;
  background: #f9fafb;

  .section-title {
    text-align: center;
    font-size: 32px;
    font-weight: 600;
    color: $text-dark;
    margin-bottom: 48px;
  }

  .features-grid {
    max-width: 1200px;
    margin: 0 auto;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 24px;

    @media (max-width: 768px) {
      grid-template-columns: repeat(2, 1fr);
    }

    @media (max-width: 480px) {
      grid-template-columns: 1fr;
    }
  }

  .feature-card {
    background: #fff;
    border-radius: 12px;
    padding: 32px;
    transition: all 0.3s;

    &:hover {
      transform: translateY(-4px);
      box-shadow: $shadow-medium;
    }

    .feature-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: rgba($primary-color, 0.1);
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;

      :deep(.anticon) {
        font-size: 24px;
        color: $primary-color;
      }
    }

    .feature-title {
      font-size: 18px;
      font-weight: 600;
      color: $text-dark;
      margin-bottom: 8px;
    }

    .feature-desc {
      font-size: 14px;
      color: #666;
      line-height: 1.6;
    }
  }
}

.footer {
  padding: 24px;
  text-align: center;
  color: #999;
  font-size: 14px;
  border-top: 1px solid #f0f0f0;
}
</style>
