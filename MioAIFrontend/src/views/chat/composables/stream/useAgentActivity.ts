import { computed, reactive, ref } from 'vue'

/**
 * 智能体活动仓（模块级单例）：把左轨图标情绪与各智能体在会话里的实时执行状态绑定。
 *
 * 数据流：
 * - 流式事件（useChatStream.applyEnvelope 处的 observe）→ 按事件类型映射引擎状态
 *   （思考→thinking / 工具→searching|working / 回答→writing / 重试→confused / 提问→curious / 清单→progress）
 * - 后端轮询（/bot/active-chats，10s）→ 补挂本页没有流式连接的执行中会话（刷新/接管场景），
 *   并摘除已不在后端执行的轮次（后端 active 列表是执行与否的权威）
 * - done/stopped/error → endTurn
 *
 * 状态规则：
 * - 会话执行中：实时绑定该会话状态；同一智能体多会话并发以 startedAt 最新者为准（computed 取最大），
 *   较新的会话结束后回退到较旧会话的实时状态
 * - 无会话执行：happy → shy → idle 三态循环（按 id 相位错开，不同智能体不同步）——
 *   不用随机情绪、也不显示收尾结果情绪，左轨不出现与实际执行无关的状态
 *
 * 状态值为 BotIcon 引擎的合法状态名（与 useBotMood 的忙碌态同一套词汇）。
 */

export interface ActiveTurn {
  agentId: number
  /** 轮次开始时间（epoch ms）：后端 active-chats 提供，本地发起用 Date.now() */
  startedAt: number
  state: string
}

/** 后端 /bot/active-chats 行（缺 agentId 的行无法绑定智能体，仅作执行中占位） */
export interface BackendTurn {
  chatId: string
  agentId?: number
  startedAt?: number
}

/** chatId → 轮次（reactive Map，组件外模块级：切会话/切智能体不重置） */
const activeTurns = reactive(new Map<string, ActiveTurn>())

/** 搜索类工具 → searching（与 useBotMood.toolMood 同一启发式） */
function toolState(tool: string): string {
  return /search|web|fetch|browse|http|news/i.test(tool) ? 'searching' : 'working'
}

/** 本地发起一轮：发送瞬间为 sending（首字前恒发送中，与宠物映射一致） */
export function beginTurn(chatId: string, agentId: number, startedAt = Date.now()): void {
  if (!chatId || !agentId) return
  activeTurns.set(chatId, { agentId, startedAt, state: 'sending' })
}

/** 结束一轮（done/stopped/error/后端消失）：摘除执行态。幂等 */
export function endTurn(chatId: string): void {
  activeTurns.delete(chatId)
}

/** 空闲三态循环（无会话执行时左轨的唯一空闲表现）：happy → shy → idle 循环，
 *  相位按智能体 id 错开避免全员同拍。tick 驱动使用方 computed 周期重估。 */
const IDLE_CYCLE = ['happy', 'shy', 'idle']
const IDLE_PHASE_MS = 4000
const idleCycleTick = ref(0)
window.setInterval(() => { idleCycleTick.value++ }, IDLE_PHASE_MS)

export function idleCycleMood(id: number): string {
  void idleCycleTick.value
  const phase = Math.floor(Date.now() / IDLE_PHASE_MS) + id
  return IDLE_CYCLE[phase % IDLE_CYCLE.length]
}

/** agentId → 执行中状态（仅活跃轮次，最新会话优先）：群组磁贴（成员拼贴头像）执行中绑定用 */
export const agentExecutingStates = computed(() => {
  const best = new Map<number, ActiveTurn>()
  for (const turn of activeTurns.values()) {
    const current = best.get(turn.agentId)
    if (!current || turn.startedAt >= current.startedAt) {
      best.set(turn.agentId, turn)
    }
  }
  const states = new Map<number, string>()
  for (const [agentId, turn] of best) {
    states.set(agentId, turn.state)
  }
  return states
})

/**
 * 左轨最终情绪（agentId → 状态）：执行中绑定实时状态（多会话最新优先）。
 * 无执行中会话的智能体不在表内，由调用方（RailAgentItem）回退 idleCycleMood 三态循环。
 */
export const railMoodStates = computed(() => {
  const states = new Map<number, string>()
  const best = new Map<number, ActiveTurn>()
  for (const turn of activeTurns.values()) {
    const current = best.get(turn.agentId)
    if (!current || turn.startedAt >= current.startedAt) {
      best.set(turn.agentId, turn)
    }
  }
  for (const [agentId, turn] of best) {
    states.set(agentId, turn.state)
  }
  return states
})

/**
 * 流式事件观察：把信封事件映射为引擎状态。仅更新该轮次自身的状态——
 * "同智能体多会话取最新"由 agentStates computed 统一裁决，旧会话的更新
 * 不会覆盖新会话的绑定。
 */
export function observeTurn(chatId: string, event: Record<string, any>): void {
  const turn = activeTurns.get(chatId)
  if (!turn) return
  switch (String(event?.type ?? '')) {
    case 'thinking':
      turn.state = 'thinking'
      break
    case 'answer':
    case 'ui_block':
      turn.state = 'writing'
      break
    case 'tool_use':
      turn.state = toolState(String(event.tool ?? ''))
      break
    case 'tool_args':
      if (turn.state === 'sending') turn.state = 'working'
      break
    case 'tool_result':
      // 工具收尾：引擎继续下一步（与宠物 busy 映射的"工具完成→思考"一致）
      if (turn.state === 'searching' || turn.state === 'working') turn.state = 'thinking'
      break
    case 'member_start':
    case 'member_delta':
      // 群聊成员执行中：群主在协调
      turn.state = 'working'
      break
    case 'plan':
      turn.state = 'progress'
      break
    case 'question':
      turn.state = String(event.status ?? '') === 'answered' ? 'working' : 'curious'
      break
    case 'retry':
      turn.state = 'confused'
      break
    case 'done':
    case 'stopped':
    case 'error':
      endTurn(chatId)
      break
    default:
      break
  }
}

/**
 * 后端执行中轮次同步（10s 轮询）：
 * - 补挂本页没有流连接的会话（刷新/另一端发起），状态统一 working
 * - 摘除后端已结束的轮次——后端 active 列表是"是否仍在执行"的权威；
 *   本地流连接可能 wedged（收尾事件丢失），10s 内兜底清除，左轨不卡死态。
 *   刚本地发起（<20s）的轮次豁免一次：后端登记与本轮首个事件间有短暂竞态窗口。
 */
export function syncBackendTurns(turns: BackendTurn[]): void {
  const backendIds = new Set<string>()
  for (const turn of turns) {
    backendIds.add(turn.chatId)
    if (!activeTurns.has(turn.chatId)) {
      if (!turn.agentId) continue
      activeTurns.set(turn.chatId, {
        agentId: turn.agentId,
        startedAt: turn.startedAt ?? Date.now(),
        state: 'working'
      })
    }
  }
  for (const [chatId, turn] of [...activeTurns.entries()]) {
    if (!backendIds.has(chatId) && Date.now() - turn.startedAt > 20_000) {
      endTurn(chatId)
    }
  }
}

