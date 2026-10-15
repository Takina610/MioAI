<template>
  <!-- 首页移动端导航抽屉：补位桌面顶栏 nav（≤900px 整体隐藏）。
       链接清单与桌面 nav 同源（navItems），避免两处维护漂移。
       Teleport 到 body：顶栏的 backdrop-filter 会成为 fixed 后代的包含块，
       若留在顶栏内，抽屉会被限制在 72px 高的头部盒内并撑出页面宽度 -->
  <Teleport to="body">
    <div class="m-nav" :class="{ open: visible }">
      <div class="m-nav-scrim" @click="emit('close')"></div>
      <nav class="m-nav-panel">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          class="m-nav-link"
          :to="item.to"
          @click="emit('close')"
        >
          <component :is="item.icon" class="m-nav-icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import {
  MessageOutlined,
  RobotOutlined,
  ShopOutlined,
  BookOutlined
} from '@ant-design/icons-vue'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

/** 与桌面顶栏 nav 一致的入口清单 */
const navItems = [
  { to: '/chat', label: 'MioBot', icon: MessageOutlined },
  { to: '/dashboard/agent-market', label: '智能体广场', icon: RobotOutlined },
  { to: '/dashboard/mcp-market', label: 'MCP 广场', icon: ShopOutlined },
  { to: '/dashboard/public-knowledge', label: '知识库', icon: BookOutlined }
]
</script>

<style lang="scss" scoped>
.m-nav {
  position: fixed;
  inset: 0;
  z-index: 90;
  pointer-events: none;
  // 关闭态整体移出视口时不得撑开页面宽度（否则窄屏出现横向滚动条）
  overflow: hidden;

  &.open {
    pointer-events: auto;
  }
}

.m-nav-scrim {
  position: absolute;
  inset: 0;
  background: rgba(20, 20, 19, 0.28);
  opacity: 0;
  transition: opacity 0.24s ease;
}

.m-nav-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(78vw, 300px);
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 84px 16px 24px;
  box-sizing: border-box;
  background: $bg-ivory;
  box-shadow: -12px 0 32px rgba(20, 20, 19, 0.14);
  transform: translateX(100%);
  transition: transform 0.28s cubic-bezier(0.32, 0.72, 0.35, 1);
}

.m-nav.open {
  .m-nav-scrim {
    opacity: 1;
  }

  .m-nav-panel {
    transform: translateX(0);
  }
}

.m-nav-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 12px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 500;
  color: $text-dark;

  &:active {
    background: rgba(42, 161, 169, 0.1);
  }

  .m-nav-icon {
    color: $primary-color;
    font-size: 17px;
  }
}
</style>
