import { ref, onBeforeUnmount, type Ref } from 'vue'

/**
 * 悬浮胶囊侧边栏的状态机（聊天页与控制台共用）：
 * - collapsed=false：侧边栏常驻展开（占据布局，内容区随之让位）
 * - collapsed=true：完全收起（滑出屏幕外）；悬浮收缩按钮临时滑出（peek 覆盖层，不挤动内容），
 *   鼠标进入按钮/侧边栏任一区域保持展开，两者都离开后延时收回
 * - 点击按钮：展开 ↔ 收起（收起态点击即固定展开）
 */
export function useFloatingSidebar(options: { /** 离开后延时收回的毫秒数 */ retractDelay?: number } = {}) {
  const retractDelay = options.retractDelay ?? 400

  const collapsed = ref(false)
  const peeking = ref(false)
  let retractTimer: number | undefined

  function clearRetractTimer(): void {
    if (retractTimer !== undefined) {
      window.clearTimeout(retractTimer)
      retractTimer = undefined
    }
  }

  /** 鼠标进入收缩按钮（收起态）：取消待收回并滑出 */
  function enterHandle(): void {
    if (!collapsed.value) return
    clearRetractTimer()
    peeking.value = true
  }

  /** 鼠标离开收缩按钮：离开侧边栏区域后延时收回 */
  function leaveHandle(): void {
    if (!peeking.value) return
    clearRetractTimer()
    retractTimer = window.setTimeout(() => {
      peeking.value = false
      retractTimer = undefined
    }, retractDelay)
  }

  /** 鼠标进入侧边栏：peek 期间保持展开 */
  function enterPanel(): void {
    if (collapsed.value) clearRetractTimer()
  }

  /** 鼠标离开侧边栏：延时收回（与按钮共用一个延时器，先离开按钮再进入面板不清零重计） */
  function leavePanel(): void {
    if (!peeking.value) return
    clearRetractTimer()
    retractTimer = window.setTimeout(() => {
      peeking.value = false
      retractTimer = undefined
    }, retractDelay)
  }

  /** 点击收缩按钮：展开 ↔ 收起 */
  function toggle(): void {
    clearRetractTimer()
    if (collapsed.value) {
      collapsed.value = false
      peeking.value = false
    } else {
      collapsed.value = true
      peeking.value = false
    }
  }

  /** 选择会话等动作后主动收回 peek（面板滑走后鼠标自然落在内容区） */
  function closePeek(): void {
    clearRetractTimer()
    peeking.value = false
  }

  onBeforeUnmount(clearRetractTimer)

  return {
    collapsed: collapsed as Ref<boolean>,
    peeking: peeking as Ref<boolean>,
    enterHandle,
    leaveHandle,
    enterPanel,
    leavePanel,
    toggle,
    closePeek
  }
}
