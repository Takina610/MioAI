import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 滚动显现:进入视口的 .hm-scroll-reveal 元素加 is-visible
 * 单个根组件内共用一个 IntersectionObserver
 */
export function useScrollReveal(rootSelector: string): void {
  let observer: IntersectionObserver | null = null

  onMounted(() => {
    const root = document.querySelector(rootSelector)
    if (!root) return
    const targets = root.querySelectorAll('.hm-scroll-reveal')
    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible')
            observer?.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.15 }
    )
    targets.forEach((el) => observer?.observe(el))
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
  })
}
