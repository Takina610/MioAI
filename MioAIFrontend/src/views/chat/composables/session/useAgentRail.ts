import { computed, ref } from 'vue'
import { queryAgents, pinAgent, reorderAgents } from '@/api/agent'
import { useUserStore } from '@/store/user'
import type { Agent } from '@/types'

/**
 * 左侧智能体轨：我的智能体列表 + 群聊分组 + 置顶/排序。
 * 从 index.vue 抽出（列表态与乐观更新，不含导航）。
 */
export function useAgentRail() {
  const userStore = useUserStore()

  const agentRailExpanded = ref(false)
  const myAgents = ref<Agent[]>([])
  const groupModalVisible = ref(false)

  /** 左轨"我的智能体"：自定义 + 专属 MioBot，群聊独立区块展示 */
  const railSoloAgents = computed(() => myAgents.value.filter(a => a.type !== 2))
  const railGroupAgents = computed(() => myAgents.value.filter(a => a.type === 2))

  /** 群聊候选成员：我的自定义智能体（群聊不可嵌套） */
  const groupCandidates = computed(() => myAgents.value.filter(a => a.type === 1))

  async function loadMyAgents(): Promise<void> {
    if (!userStore.isLoggedIn) {
      myAgents.value = []
      return
    }
    try {
      const res = await queryAgents({ current: 1, pageSize: 100 })
      myAgents.value = res.records ?? []
    } catch (e) {
      console.error('加载我的智能体失败', e)
    }
  }

  /** 左轨图钉：本地乐观切换并移到对应分组顶部，落库失败回读服务端顺序 */
  function handleRailTogglePin(agent: Agent): void {
    const pinned = agent.pinned !== 1
    const next = myAgents.value.map(a => (a.id === agent.id ? { ...a, pinned: pinned ? 1 : 0 } : a))
    myAgents.value = [...next.filter(a => a.pinned === 1), ...next.filter(a => a.pinned !== 1)]
    pinAgent(agent.id, pinned).catch(() => loadMyAgents())
  }

  /** 左轨拖动排序：本地按新顺序重排，落库失败回读 */
  function handleRailReorder(orderedIds: number[]): void {
    if (orderedIds.join() === myAgents.value.map(a => a.id).join()) return
    const byId = new Map(myAgents.value.map(a => [a.id, a]))
    myAgents.value = orderedIds.map(id => byId.get(id)).filter((a): a is Agent => !!a)
    reorderAgents(orderedIds).catch(() => loadMyAgents())
  }

  return {
    agentRailExpanded,
    myAgents,
    groupModalVisible,
    railSoloAgents,
    railGroupAgents,
    groupCandidates,
    loadMyAgents,
    handleRailTogglePin,
    handleRailReorder
  }
}
