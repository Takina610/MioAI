<template>
  <div class="dashboard-layout" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <DashboardSidebar
      :collapsed="sidebarCollapsed"
      :peeking="sidebarPeeking"
      :enter-panel="sidebarEnterPanel"
      :leave-panel="sidebarLeavePanel"
      @login-required="showAuthModal"
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

    <AuthModal v-model:visible="authModalVisible" @success="handleAuthSuccess" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AuthModal from '@/components/AuthModal.vue'
import DashboardSidebar from './components/DashboardSidebar.vue'
import DashboardHeader from './components/DashboardHeader.vue'
import { useFloatingSidebar } from '@/composables/useFloatingSidebar'

// 悬浮胶囊侧边栏：Header 上的收缩按钮切换，收起后悬浮按钮唤出（peek）
const {
  collapsed: sidebarCollapsed,
  peeking: sidebarPeeking,
  enterHandle: sidebarEnterHandle,
  leaveHandle: sidebarLeaveHandle,
  enterPanel: sidebarEnterPanel,
  leavePanel: sidebarLeavePanel,
  toggle: sidebarToggle
} = useFloatingSidebar()

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

@media (max-width: 768px) {
  // 窄屏：侧边栏始终以覆盖层出现，内容不再让位
  .main-container,
  .sidebar-collapsed .main-container {
    margin-left: 12px;
  }
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
