import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useWelcomeGreeting } from './useWelcomeGreeting'
import { useUserStore } from '@/store/user'

// 固定时间以便按时段断言（问候语池按小时取）
function atHour(hour: number): void {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 10, 2, hour, 0, 0))
}

describe('useWelcomeGreeting', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.useRealTimers()
  })

  it('未登录时用 store 的默认名“用户”', () => {
    atHour(10)
    const { welcomeTitle } = useWelcomeGreeting()
    expect(welcomeTitle.value).toContain('用户')
    vi.useRealTimers()
  })

  it('登录用户问候语含用户名', () => {
    atHour(10)
    const store = useUserStore()
    store.userInfo = { userName: '阿测' } as never
    const { welcomeTitle } = useWelcomeGreeting()
    expect(welcomeTitle.value).toContain('阿测')
    vi.useRealTimers()
  })

  it('画像过久不参与，占位符不漏出', () => {
    atHour(20)
    const store = useUserStore()
    store.userInfo = {
      userName: '阿测',
      userProfile: '一个非常非常长的个人画像描述超过十二个字了'
    } as never
    const { welcomeTitle } = useWelcomeGreeting()
    expect(welcomeTitle.value).not.toContain('{profile}')
    expect(welcomeTitle.value).not.toContain('{name}')
    vi.useRealTimers()
  })

  it('任意时段都不残留占位符', () => {
    for (let h = 0; h < 24; h++) {
      atHour(h)
      const store = useUserStore()
      store.userInfo = { userName: '阿测', userProfile: '' } as never
      const { welcomeTitle } = useWelcomeGreeting()
      expect(welcomeTitle.value).not.toContain('{name}')
      expect(welcomeTitle.value).not.toContain('{profile}')
    }
    vi.useRealTimers()
  })
})
