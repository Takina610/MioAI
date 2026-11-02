import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useChatShell } from './useChatShell'
import type { SidebarPhase } from './useChatShell'

function mountShell(opts: { loggedIn?: boolean; mobile?: boolean; path?: string } = {}) {
  setActivePinia(createPinia())
  localStorage.clear()
  if (opts.loggedIn) localStorage.setItem('token', 'x')

  const isMobile = ref(!!opts.mobile)
  const path = ref(opts.path ?? '/chat/1')
  let captured!: ReturnType<typeof useChatShell>
  const Comp = defineComponent({
    setup() {
      captured = useChatShell({ isMobile, routeFullPath: () => path.value })
      return () => null
    }
  })
  const wrapper = mount(Comp)
  return { shell: captured, isMobile, path, unmount: () => wrapper.unmount() }
}

describe('useChatShell', () => {
  beforeEach(() => { vi.useRealTimers() })
  afterEach(() => { document.body.style.overflow = '' })

  it('初始为相位 0 且未收栏', () => {
    const { shell } = mountShell({ loggedIn: true })
    expect(shell.sidebarPhase.value).toBe(0)
    expect(shell.sidebarCollapsed.value).toBe(false)
  })

  it('桌面推进相位 0→1→2→3→0', () => {
    const { shell } = mountShell({ loggedIn: true })
    const seen: SidebarPhase[] = [shell.sidebarPhase.value]
    for (let i = 0; i < 4; i++) {
      shell.onToggleClick()
      seen.push(shell.sidebarPhase.value)
    }
    expect(seen).toEqual([0, 1, 2, 3, 0])
  })

  it('游客只有展开↔收起两态', () => {
    const { shell } = mountShell({ loggedIn: false })
    shell.onToggleClick()
    expect(shell.sidebarPhase.value).toBe(2)
    expect(shell.sidebarCollapsed.value).toBe(true)
    shell.onToggleClick()
    expect(shell.sidebarPhase.value).toBe(0)
  })

  it('移动端开关切会话抽屉而非推进相位', () => {
    const { shell, isMobile } = mountShell({ loggedIn: true, mobile: true })
    shell.onToggleClick()
    expect(shell.mobileDrawerVisible.value).toBe(true)
    expect(shell.sidebarPhase.value).toBe(0)
    expect(shell.sidebarCollapsed.value).toBe(false)
    expect(isMobile.value).toBe(true)
  })

  it('移动端两个抽屉互斥', async () => {
    const { shell } = mountShell({ loggedIn: true, mobile: true })
    shell.setMobileDrawerVisible(true)
    await nextTick()
    shell.setMobileAgentsVisible(true)
    await nextTick()
    expect(shell.mobileDrawerVisible.value).toBe(false)
    expect(shell.mobileAgentsVisible.value).toBe(true)
  })

  it('移动端浮层展开锁滚动，关闭后解锁', async () => {
    const { shell } = mountShell({ loggedIn: true, mobile: true })
    shell.setMobileDrawerVisible(true)
    await nextTick()
    expect(document.body.style.overflow).toBe('hidden')
    shell.closeMobileOverlays()
    await nextTick()
    expect(document.body.style.overflow).toBe('')
  })

  it('路由变化自动关闭移动端浮层', async () => {
    const { shell, path } = mountShell({ loggedIn: true, mobile: true })
    shell.setMobileAgentsVisible(true)
    await nextTick()
    path.value = '/chat/2'
    await nextTick()
    expect(shell.mobileAgentsVisible.value).toBe(false)
  })

  it('hover 开关按钮在收起态 peek 出双栏', async () => {
    const { shell } = mountShell({ loggedIn: true })
    // 推进到相位 2（都收起）
    shell.onToggleClick()
    shell.onToggleClick()
    expect(shell.sidebarCollapsed.value).toBe(true)
    shell.onToggleHover()
    expect(shell.sidebarPeeking.value).toBe(true)
    expect(shell.railPeeking.value).toBe(true)
  })

  it('相位 0 时 hover 不触发 peek', () => {
    const { shell } = mountShell({ loggedIn: true })
    shell.onToggleHover()
    expect(shell.sidebarPeeking.value).toBe(false)
  })

  it('离开后延时收回 peek', async () => {
    vi.useFakeTimers()
    const { shell } = mountShell({ loggedIn: true })
    shell.onToggleClick()
    shell.onToggleClick()
    shell.onToggleHover()
    expect(shell.sidebarPeeking.value).toBe(true)
    shell.onToggleLeave()
    vi.advanceTimersByTime(400)
    await nextTick()
    expect(shell.sidebarPeeking.value).toBe(false)
    expect(shell.railPeeking.value).toBe(false)
    vi.useRealTimers()
  })

  it('Esc 关闭移动端浮层', async () => {
    const { shell } = mountShell({ loggedIn: true, mobile: true })
    shell.setMobileAgentsVisible(true)
    await nextTick()
    shell.onMobileKeydown(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(shell.mobileAgentsVisible.value).toBe(false)
  })

  it('从移动端回到桌面自动收起抽屉', async () => {
    const { shell, isMobile } = mountShell({ loggedIn: true, mobile: true })
    shell.setMobileDrawerVisible(true)
    await nextTick()
    isMobile.value = false
    await nextTick()
    expect(shell.mobileDrawerVisible.value).toBe(false)
  })

  it('卸载时清掉滚动锁', async () => {
    const { shell, unmount } = mountShell({ loggedIn: true, mobile: true })
    shell.setMobileDrawerVisible(true)
    await nextTick()
    expect(document.body.style.overflow).toBe('hidden')
    unmount()
    expect(document.body.style.overflow).toBe('')
  })
})
