<template>
  <div class="admin-layout" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <DashboardSidebar
      :collapsed="sidebarCollapsed"
      :peeking="sidebarPeeking"
      :enter-panel="sidebarEnterPanel"
      :leave-panel="sidebarLeavePanel"
      :menu-groups="adminMenuGroups"
    />

    <div class="admin-container">
      <DashboardHeader
        :toggle="sidebarToggle"
        :enter-handle="sidebarEnterHandle"
        :leave-handle="sidebarLeaveHandle"
        hide-admin-entry
      />

      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import DashboardSidebar from '@/views/dashboard/components/DashboardSidebar.vue'
import DashboardHeader from '@/views/dashboard/components/DashboardHeader.vue'
import { useFloatingSidebar } from '@/composables/useFloatingSidebar'
import { adminMenuGroups } from './composables/useAdminMenu'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

// 权限守卫：非管理员跳转 403
if (userStore.userInfo?.userRole !== 'admin') {
  message.error('无权访问管理后台')
  router.replace('/403')
}

// 与控制台同款悬浮胶囊侧边栏：Header 收缩按钮切换，收起后悬浮唤出（peek）
const {
  collapsed: sidebarCollapsed,
  peeking: sidebarPeeking,
  enterHandle: sidebarEnterHandle,
  leaveHandle: sidebarLeaveHandle,
  enterPanel: sidebarEnterPanel,
  leavePanel: sidebarLeavePanel,
  toggle: sidebarToggle
} = useFloatingSidebar()

onBeforeUnmount(() => {
  // 离开管理后台恢复侧边栏展开态，避免控制台继承收起状态
  if (sidebarCollapsed.value) {
    sidebarToggle()
  }
})
</script>

<style lang="scss">
@import '@/styles/admin.scss';

.admin-layout {
  position: relative;
  display: flex;
  height: 100vh;
  background: $bg-cream;
}

.admin-container {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  // 与控制台一致：给悬浮胶囊让位（240 = 12 出血 + 220 胶囊 + 8 间隙），收起时收回出血位
  margin: 12px 12px 12px 240px;
  background: $bg-ivory;
  border: 1px solid $border-warm-subtle;
  border-radius: 18px;
  transition: margin-left 0.28s cubic-bezier(0.32, 0.72, 0.35, 1);
}

.admin-layout.sidebar-collapsed .admin-container {
  margin-left: 12px;
}

@media (max-width: 768px) {
  .admin-container,
  .admin-layout.sidebar-collapsed .admin-container {
    margin-left: 12px;
  }
}

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 40px;
  background: $bg-ivory;
}
</style>
