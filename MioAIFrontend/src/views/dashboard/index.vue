<template>
  <div class="dashboard-layout">
    <DashboardSidebar @login-required="showAuthModal" />

    <div class="main-container">
      <DashboardHeader />

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
  display: flex;
  min-height: 100vh;
  background: #f5f7fa;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content {
  flex: 1;
  overflow-y: auto;
  background: #ffffff;
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
