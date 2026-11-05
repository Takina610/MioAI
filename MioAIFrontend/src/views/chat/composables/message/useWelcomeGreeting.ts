import { computed } from 'vue'
import { useUserStore } from '@/store/user'

/**
 * 新对话问候语：按时段分短语池随机取一句；有简短画像时混入画像变体。
 * 从 ChatInput 抽出（纯展示逻辑，无 DOM 依赖）。
 */

// [起始小时, 结束小时(含), 短语池]；池内含 {profile} 的变体仅在画像存在且简短时启用
const GREETING_BUCKETS: Array<[number, number, string[]]> = [
  [0, 4, ['还没睡，{name}？', '夜深了，{name}，想聊点什么？', '这么晚还没休息？{name}，我在。', '还没睡，{name}？{profile}的事不急于这一晚。']],
  [5, 8, ['早安，{name}！今天想从什么开始？', '早上好，{name}。新的一天，有什么想做的？', '早安，{name}！今天{profile}的日程还顺利吗？']],
  [9, 11, ['上午好，{name}！有什么想一起解决的？', '嗨，{name}，上午想研究点什么？', '上午好，{name}！{profile}的活儿需要搭把手吗？']],
  [12, 13, ['中午好，{name}！忙碌间隙想聊点什么？', '午安，{name}。有什么我能帮忙的？']],
  [14, 17, ['下午好，{name}！下午想推进点什么？', '嗨，{name}，下午进展如何？', '下午好，{name}！今天{profile}那边今天有什么新进展？']],
  [18, 22, ['晚上好，{name}！今晚想聊点什么？', '晚上好呀，{name}。有什么新鲜事想问？', '晚上好，{name}！今天{profile}的事还顺利吗？']],
  [23, 23, ['还没睡，{name}？', '夜深了，{name}，想聊点什么？', '这么晚还没休息？{name}，我在。', '还没睡，{name}？{profile}的事不急于这一晚。']]
]

const FALLBACK_GREETING = ['嗨，{name}！有什么想聊的？']

export function useWelcomeGreeting() {
  const userStore = useUserStore()

  const welcomeTitle = computed(() => {
    const name = userStore.userName?.trim() || '朋友'
    const profile = userStore.userInfo?.userProfile?.trim()
    const hour = new Date().getHours()
    const pool = GREETING_BUCKETS.find(([from, to]) => hour >= from && hour <= to)?.[2]
      ?? FALLBACK_GREETING
    // 画像缺失或过长时不用画像变体，避免占位符漏出
    const usable = pool.filter(p => !p.includes('{profile}') || (profile && profile.length <= 12))
    const picked = usable[Math.floor(Math.random() * usable.length)]
    return picked.split('{name}').join(name).split('{profile}').join(profile ?? '')
  })

  return { welcomeTitle }
}
