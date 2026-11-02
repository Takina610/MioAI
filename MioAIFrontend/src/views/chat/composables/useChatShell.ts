import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { useUserStore } from '@/store/user'

/**
 * 聊天页侧栏相位 + 移动端抽屉协调。
 * 从 index.vue 抽出：桌面四相位（都展示→仅会话栏→都收起→仅会话栏→循环）与
 * 移动端两抽屉（会话/智能体）的互斥、滚动锁、Esc 关闭。
 */

/** 都展示(0) → 仅会话栏(1) → 都收起(2) → 仅会话栏(3) → 循环 */
export type SidebarPhase = 0 | 1 | 2 | 3

export function useChatShell(options: {
  isMobile: Readonly<Ref<boolean>>
  /** 路由切换时收起移动端浮层（覆盖层不跟随路由过渡） */
  routeFullPath: () => string
}) {
  const userStore = useUserStore()
  const { isMobile } = options

  const sidebarPhase = ref<SidebarPhase>(0)
  const sidebarPeeking = ref(false)
  const railPeeking = ref(false)
  let sidebarRetractTimer: number | undefined

  /** 移动端抽屉状态（会话栏 / 智能体切换，互斥） */
  const mobileDrawerVisible = ref(false)
  const mobileAgentsVisible = ref(false)

  /** 任一移动端浮层展开：驱动遮罩、滚动锁与布局类名 */
  const mobileDrawerOpen = computed(() => isMobile.value && mobileDrawerVisible.value)

  function clearSidebarRetract(): void {
    if (sidebarRetractTimer !== undefined) {
      window.clearTimeout(sidebarRetractTimer)
      sidebarRetractTimer = undefined
    }
  }

  function scheduleSidebarRetract(): void {
    clearSidebarRetract()
    sidebarRetractTimer = window.setTimeout(() => {
      sidebarPeeking.value = false
      railPeeking.value = false
      sidebarRetractTimer = undefined
    }, 400)
  }

  /** 会话胶囊是否收起：桌面按相位 2，移动端按抽屉是否展开 */
  const sidebarCollapsed = computed(() =>
    isMobile.value ? !mobileDrawerVisible.value : sidebarPhase.value === 2
  )

  /** 开关按钮点击：移动端开合会话抽屉；桌面推进相位（游客只有 展开↔收起 两态） */
  function onToggleClick(): void {
    if (isMobile.value) {
      mobileAgentsVisible.value = false
      mobileDrawerVisible.value = !mobileDrawerVisible.value
      return
    }
    advanceSidebarPhase()
  }

  /** 开关按钮点击（桌面）：推进相位 */
  function advanceSidebarPhase(): void {
    clearSidebarRetract()
    sidebarPeeking.value = false
    railPeeking.value = false
    if (!userStore.isLoggedIn) {
      sidebarPhase.value = sidebarPhase.value === 2 ? 0 : 2
      return
    }
    sidebarPhase.value = ((sidebarPhase.value + 1) % 4) as SidebarPhase
  }

  /** 悬浮开关按钮：peek 出当前被收起的部分（都收起→双栏 peek；仅左轨收起→左轨 peek） */
  function onToggleHover(): void {
    if (isMobile.value) return
    if (sidebarPhase.value === 0) return
    clearSidebarRetract()
    if (sidebarCollapsed.value) {
      sidebarPeeking.value = true
    }
    if (userStore.isLoggedIn) {
      railPeeking.value = true
    }
  }

  function onToggleLeave(): void {
    if (isMobile.value) return
    scheduleSidebarRetract()
  }

  /** 悬浮区域（左轨/会话胶囊）：进入保持，离开整体后延时收回 */
  function panelEnter(): void {
    if (isMobile.value) return
    clearSidebarRetract()
  }

  function panelLeave(): void {
    if (isMobile.value) return
    scheduleSidebarRetract()
  }

  /** 选中会话等动作后主动收回 peek（移动端同时收起抽屉） */
  function sidebarClosePeek(): void {
    clearSidebarRetract()
    sidebarPeeking.value = false
    railPeeking.value = false
    mobileDrawerVisible.value = false
  }

  /** 关闭移动端全部浮层 */
  function closeMobileOverlays(): void {
    mobileDrawerVisible.value = false
    mobileAgentsVisible.value = false
  }

  /** 打开/关闭移动端智能体抽屉（与会话抽屉互斥由 watch 保证） */
  function setMobileAgentsVisible(visible: boolean): void {
    mobileAgentsVisible.value = visible
  }

  function setMobileDrawerVisible(visible: boolean): void {
    mobileDrawerVisible.value = visible
  }

  // 两个抽屉互斥：智能体抽屉打开时会话抽屉让位（它们宽高一致，重叠会互相遮挡）
  watch(mobileAgentsVisible, open => {
    if (open) mobileDrawerVisible.value = false
  })

  // 视口从移动形态变回桌面：抽屉是移动专属覆盖层，留在桌面会浮在内容区上
  watch(isMobile, mobile => {
    if (!mobile) closeMobileOverlays()
  })

  // 切页即收：智能体抽屉点选后会跳转，抽屉留在原位会挡住新会话
  watch(() => options.routeFullPath(), closeMobileOverlays)

  // 移动端浮层展开时锁定背景滚动（触摸滑动不穿透到消息区）
  watch(() => mobileDrawerOpen.value || mobileAgentsVisible.value, open => {
    document.body.style.overflow = open && isMobile.value ? 'hidden' : ''
  })

  // Esc 关闭移动端浮层
  function onMobileKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') closeMobileOverlays()
  }

  onBeforeUnmount(() => {
    clearSidebarRetract()
    document.body.style.overflow = ''
  })

  return {
    sidebarPhase,
    sidebarPeeking,
    railPeeking,
    mobileDrawerVisible,
    mobileAgentsVisible,
    mobileDrawerOpen,
    sidebarCollapsed,
    onToggleClick,
    onToggleHover,
    onToggleLeave,
    panelEnter,
    panelLeave,
    sidebarClosePeek,
    closeMobileOverlays,
    setMobileAgentsVisible,
    setMobileDrawerVisible,
    onMobileKeydown
  }
}
