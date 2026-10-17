<template>
  <div class="dashboard-layout" :class="{ 'sidebar-collapsed': sidebarCollapsed, 'drawer-open': drawerOpen }">
    <DashboardSidebar
      :collapsed="sidebarCollapsed"
      :peeking="sidebarPeeking"
      :enter-panel="sidebarEnterPanel"
      :leave-panel="sidebarLeavePanel"
      @login-required="showAuthModal"
      @close="closeDrawer"
    />

    <div class="main-container">
      <DashboardHeader
        :toggle="sidebarToggle"
        :enter-handle="sidebarEnterHandle"
        :leave-handle="sidebarLeaveHandle"
      />

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" @login-required="showAuthModal" />
          </transition>
        </router-view>
      </main>
    </div>

    <MobileScrim v-if="drawerOpen" @close="closeDrawer" />
    <AuthModal v-model:visible="authModalVisible" @success="handleAuthSuccess" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AuthModal from '@/components/AuthModal.vue'
import MobileScrim from '@/mobile/MobileScrim.vue'
import DashboardSidebar from './components/DashboardSidebar.vue'
import DashboardHeader from './components/DashboardHeader.vue'
import { useShellSidebar } from '@/mobile/useShellSidebar'

// 外壳侧栏：桌面为悬浮胶囊（收缩按钮切换 + 悬浮 peek），移动端为覆盖式抽屉
const {
  collapsed: sidebarCollapsed,
  peeking: sidebarPeeking,
  drawerOpen,
  enterHandle: sidebarEnterHandle,
  leaveHandle: sidebarLeaveHandle,
  enterPanel: sidebarEnterPanel,
  leavePanel: sidebarLeavePanel,
  toggle: sidebarToggle,
  closeDrawer
} = useShellSidebar()

const authModalVisible = ref(false)

function showAuthModal(): void {
  authModalVisible.value = true
}

function handleAuthSuccess(): void {
  window.location.reload()
}
</script>

<style lang="scss" scoped>
.dashboard-layout {
  position: relative;
  display: flex;
  height: 100vh;
  background: #f0eee6;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  // 侧边栏展开时给悬浮胶囊让位（240 = 12 出血 + 220 胶囊 + 8 间隙），收起时收回出血位
  margin: 12px 12px 12px 240px;
  background: $bg-ivory;
  border: 1px solid $border-warm-subtle;
  border-radius: 18px;
  transition: margin-left 0.28s cubic-bezier(0.32, 0.72, 0.35, 1);
}

.sidebar-collapsed .main-container {
  margin-left: 12px;
}

.content {
  flex: 1;
  overflow-y: auto;
  background: $bg-ivory;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
