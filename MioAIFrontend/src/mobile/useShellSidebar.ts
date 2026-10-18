import { computed, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { useFloatingSidebar } from '@/composables/layout/useFloatingSidebar'
import { useIsMobile } from './useIsMobile'

/**
 * 外壳侧栏的自适应状态机（对话页 / 控制台 / 管理后台共用）。
 *
 * 桌面沿用既有 `useFloatingSidebar`（常驻展开 + 鼠标悬浮 peek），移动端收敛为
 * 覆盖式抽屉（头部按钮开合、点遮罩/Esc/切路由自动收起）。
 * 两者对外暴露同一组接口，外壳组件无需分支即可工作：
 *
 * - `collapsed`  侧栏是否隐藏（移动端 = 抽屉未展开）→ 传给侧栏组件的既有 prop
 * - `peeking`    桌面悬浮预览态（移动端恒 false，触屏无 hover）
 * - `drawerOpen` 移动端抽屉展开（桌面恒 false）→ 驱动遮罩与布局类名
 *
 * 移动端默认收起：进入即满宽内容，导航按需唤出。
 */
export interface ShellSidebar {
  isMobile: Readonly<Ref<boolean>>
  collapsed: Readonly<Ref<boolean>>
  peeking: Readonly<Ref<boolean>>
  drawerOpen: Readonly<Ref<boolean>>
  enterHandle: () => void
  leaveHandle: () => void
  enterPanel: () => void
  leavePanel: () => void
  toggle: () => void
  closePeek: () => void
  closeDrawer: () => void
}

export function useShellSidebar(): ShellSidebar {
  const { isMobile } = useIsMobile()
  const desktop = useFloatingSidebar()
  const route = useRoute()

  /** 移动端抽屉展开态（桌面不参与） */
  const mobileOpen = ref(false)

  const collapsed = computed(() => (isMobile.value ? !mobileOpen.value : desktop.collapsed.value))
  const peeking = computed(() => (isMobile.value ? false : desktop.peeking.value))
  const drawerOpen = computed(() => isMobile.value && mobileOpen.value)

  function closeDrawer(): void {
    mobileOpen.value = false
  }

  function toggle(): void {
    if (isMobile.value) {
      mobileOpen.value = !mobileOpen.value
      return
    }
    desktop.toggle()
  }

  // 抽屉是即时导航：切页即收，避免挡住刚进入的页面
  watch(() => route.fullPath, closeDrawer)

  // 桌面沿用悬浮 peek；移动端无 hover，直接忽略
  function enterHandle(): void {
    if (isMobile.value) return
    desktop.enterHandle()
  }

  function leaveHandle(): void {
    if (isMobile.value) return
    desktop.leaveHandle()
  }

  function enterPanel(): void {
    if (isMobile.value) return
    desktop.enterPanel()
  }

  function leavePanel(): void {
    if (isMobile.value) return
    desktop.leavePanel()
  }

  function closePeek(): void {
    desktop.closePeek()
    closeDrawer()
  }

  // 抽屉展开时锁定背景滚动：否则触摸滑动会穿透到内容区
  function lockScroll(on: boolean): void {
    if (typeof document === 'undefined') return
    document.body.style.overflow = on ? 'hidden' : ''
  }

  watch(drawerOpen, lockScroll)

  function onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape' && drawerOpen.value) closeDrawer()
  }

  onMounted(() => window.addEventListener('keydown', onKeydown))

  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onKeydown)
    lockScroll(false)
  })

  return {
    isMobile,
    collapsed,
    peeking,
    drawerOpen,
    enterHandle,
    leaveHandle,
    enterPanel,
    leavePanel,
    toggle,
    closePeek,
    closeDrawer
  }
}
