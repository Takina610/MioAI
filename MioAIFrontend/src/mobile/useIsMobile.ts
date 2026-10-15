import { readonly, ref, type Ref } from 'vue'
import {
  COMPACT_MAX_WIDTH,
  MOBILE_MAX_WIDTH,
  NARROW_MAX_WIDTH,
  TABLET_MAX_WIDTH,
  TIGHT_MAX_WIDTH,
  TOUCH_QUERY
} from './breakpoints'

/**
 * 视口/触屏能力检测（模块级单例，全应用共享同一组 matchMedia 监听）。
 *
 * 每个能力对应一个 <html> 标记类，样式侧以 `html.<标记>` 前缀消费：
 * 标记类既替代散落的媒体查询字面量（断点只在 breakpoints.ts 定义一次），
 * 又比组件 scoped 选择器（`.x[data-v-hash]`）多一档特异性，
 * 移动端覆盖因此无需 !important 竞争。
 *
 * - is-compact   ≤480   小屏手机：进一步压缩
 * - is-mobile    ≤768   手机：外壳抽屉化、页面纵向单列、表格横向滚动
 * - is-tablet    ≤960   窄屏：首页导航折叠为菜单按钮
 * - is-narrow    ≤1200  中小屏：图表/指标栅格收窄
 * - is-touch     触屏：hover 才显形的控件常显化
 */
export interface ViewportFlags {
  isCompact: Readonly<Ref<boolean>>
  isMobile: Readonly<Ref<boolean>>
  isTablet: Readonly<Ref<boolean>>
  isNarrow: Readonly<Ref<boolean>>
  isTight: Readonly<Ref<boolean>>
  isTouch: Readonly<Ref<boolean>>
}

interface FlagSpec {
  /** 挂到 <html> 的标记类名 */
  marker: string
  query: string
}

const FLAGS: Record<keyof ViewportFlags, FlagSpec> = {
  isCompact: { marker: 'is-compact', query: `(max-width: ${COMPACT_MAX_WIDTH}px)` },
  isMobile: { marker: 'is-mobile', query: `(max-width: ${MOBILE_MAX_WIDTH}px)` },
  isTablet: { marker: 'is-tablet', query: `(max-width: ${TABLET_MAX_WIDTH}px)` },
  isNarrow: { marker: 'is-narrow', query: `(max-width: ${NARROW_MAX_WIDTH}px)` },
  isTight: { marker: 'is-tight', query: `(max-width: ${TIGHT_MAX_WIDTH}px)` },
  isTouch: { marker: 'is-touch', query: TOUCH_QUERY }
}

const state = {
  isCompact: ref(false),
  isMobile: ref(false),
  isTablet: ref(false),
  isNarrow: ref(false),
  isTight: ref(false),
  isTouch: ref(false)
}

let installed = false
const disposers: Array<() => void> = []

function apply(key: keyof ViewportFlags, matches: boolean): void {
  state[key].value = matches
  if (typeof document !== 'undefined') {
    document.documentElement.classList.toggle(FLAGS[key].marker, matches)
  }
}

function setupFlag(key: keyof ViewportFlags): void {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
  const { query } = FLAGS[key]
  const mql = window.matchMedia(query)
  apply(key, mql.matches)
  const handler = (e: MediaQueryListEvent) => apply(key, e.matches)
  mql.addEventListener('change', handler)
  disposers.push(() => {
    mql.removeEventListener('change', handler)
    document.documentElement.classList.remove(FLAGS[key].marker)
  })
}

/**
 * 幂等安装：main.ts 在挂载前调用一次，保证首帧布局即为正确形态（无桌面→移动闪跳）。
 * 组件内调用 useIsMobile() 亦会确保已安装，单独挂载组件时同样安全。
 */
export function installViewport(): void {
  if (installed || typeof window === 'undefined') return
  installed = true
  ;(Object.keys(FLAGS) as Array<keyof ViewportFlags>).forEach(setupFlag)
}

/** 卸载监听并清掉标记类（仅测试/微前端场景需要） */
export function disposeViewport(): void {
  disposers.forEach(fn => fn())
  disposers.length = 0
  installed = false
}

export function useIsMobile(): ViewportFlags {
  installViewport()
  return {
    isCompact: readonly(state.isCompact),
    isMobile: readonly(state.isMobile),
    isTablet: readonly(state.isTablet),
    isNarrow: readonly(state.isNarrow),
    isTight: readonly(state.isTight),
    isTouch: readonly(state.isTouch)
  }
}
