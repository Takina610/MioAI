import type { MessageBlock } from '@/types'
import type { ToolBlock } from './toolDisplay'

/**
 * 子代理只读对话视图（zcode 子代理面板对位）：
 * 从父消息的内容块流重建单个子代理的完整过程——任务 prompt 来自 Agent 块的 args，
 * 过程工具行来自带 `agent_<id>-` 前缀 id 的镜像块（流式与持久化同构），
 * 最终报告来自 Agent 块的 result（工具结果尾部带 agentId 与 usage 页脚）。
 * 后端无需额外协议：面板与主消息流消费同一份块数据。
 */

/** zagent 镜像行 id 形如 agent_<12位hex>-toolu_xxx（Subagents.MirroringEvents 的 idPrefix） */
const MIRRORED_ID_RE = /^agent_[a-f0-9]{12}-(.+)$/

/** 是否子代理镜像工具行（不属主消息流，渲染于子代理面板） */
export function isMirroredToolBlock(block: MessageBlock): boolean {
  return block.type === 'tool' && !!block.id && MIRRORED_ID_RE.test(block.id)
}

/** 镜像行所属子代理 id（agent_<12hex>）；非镜像行返回 null */
export function mirroredOwner(block: MessageBlock): string | null {
  if (block.type !== 'tool' || !block.id) return null
  const m = block.id.match(/^agent_[a-f0-9]{12}-/)
  return m ? m[0].slice(0, -1) : null
}

export interface SubagentUsage {
  tokens?: number
  toolUses?: number
  durationMs?: number
}

export interface SubagentTranscript {
  /** 子代理类型（general-purpose / Explore…），取自 Agent 块参数 */
  agentType: string
  /** 任务简述（Agent 块 description 参数） */
  description: string
  /** 派发给子代理的完整任务 prompt */
  prompt: string
  /** args 仍在流式中（JSON 未闭合，prompt 可能不完整） */
  promptPartial: boolean
  /** run_in_background：结果由主任务经 TaskOutput 取回，本块不含最终报告 */
  background: boolean
  running: boolean
  /** 子代理过程工具行（时间序） */
  entries: ToolBlock[]
  /** 最终报告文本 */
  finalText: string
  usage: SubagentUsage | null
}

/** 从 Agent 块 result 提取子代理 agentId（前台页脚 `agentId: agent_x`，后台首行 `Agent agent_x (…)`） */
function resultAgentId(result: string): string | null {
  const patterns = [/agentId:\s*(agent_[a-f0-9]{12})/, /Agent\s+(agent_[a-f0-9]{12})\s+\(/]
  for (const re of patterns) {
    const m = result.match(re)
    if (m) return m[1]
  }
  return null
}

/** 前台 result 剥离 agentId/usage 页脚后的最终报告 */
function finalTextOf(result: string): string {
  const marker = result.indexOf('\n\nagentId: ')
  const text = marker >= 0 ? result.slice(0, marker) : result.replace(/<usage>[\s\S]*?<\/usage>\s*$/, '')
  return text.trim()
}

function usageOf(result: string): SubagentUsage | null {
  const m = result.match(/<usage>\s*subagent_tokens:\s*(\d+)\s*tool_uses:\s*(\d+)\s*duration_ms:\s*(\d+)\s*<\/usage>/)
  if (!m) return null
  return { tokens: Number(m[1]), toolUses: Number(m[2]), durationMs: Number(m[3]) }
}

/** 宽松抓取流式残缺 JSON 里某字段的字符串值（引号未闭合也算） */
function lenientString(raw: string, field: string): string {
  const m = raw.match(new RegExp(`"${field}"\\s*:\\s*"((?:[^"\\\\]|\\\\.)*)`))
  return m ? unescapeJson(m[1]) : ''
}

function unescapeJson(s: string): string {
  return s.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, g: string) => {
    switch (g[0]) {
      case 'n': return '\n'
      case 't': return '\t'
      case 'r': return '\r'
      case '"': return '"'
      case '\\': return '\\'
      case '/': return '/'
      case 'u': return String.fromCharCode(parseInt(g.slice(1), 16))
      default: return g
    }
  })
}

/**
 * 重建子代理对话。entries 归属：已知 agentId 时按 id 前缀精确配对
 * （后台子代理与父级行为交错也不串）；运行中尚无 agentId 时，因前台子代理
 * 独占执行（同一时刻只有一个运行中的 Agent 块），未认领的镜像行必属当前块，
 * 扫到下一个 Agent 块即停以防误收。
 */
export function buildSubagentTranscript(blocks: MessageBlock[], blockIndex: number): SubagentTranscript | null {
  const block = blocks[blockIndex]
  if (!block || block.type !== 'tool' || block.tool !== 'Agent') return null

  const argsRaw = block.args || ''
  let parsed: Record<string, any> | null = null
  try {
    parsed = JSON.parse(argsRaw)
  } catch {
    parsed = null
  }
  const prompt = parsed && typeof parsed.prompt === 'string'
    ? parsed.prompt
    : lenientString(argsRaw, 'prompt')
  const description = parsed && typeof parsed.description === 'string'
    ? parsed.description.trim()
    : lenientString(argsRaw, 'description').trim()
  const agentType = parsed && typeof parsed.subagent_type === 'string' && parsed.subagent_type
    ? parsed.subagent_type
    : (lenientString(argsRaw, 'subagent_type') || 'subagent')

  const result = block.result || ''
  const agentId = resultAgentId(result)
  const background = /launched in background/.test(result)

  // 本消息里已被其他 Agent 块认领（result 带 agentId）的子代理：其镜像行不收
  const claimed = new Set<string>()
  for (const b of blocks) {
    if (b.type === 'tool' && b.tool === 'Agent' && b.result) {
      const id = resultAgentId(b.result)
      if (id) claimed.add(id)
    }
  }

  const entries: ToolBlock[] = []
  for (let i = blockIndex + 1; i < blocks.length; i++) {
    const b = blocks[i]
    if (b.type !== 'tool') continue
    if (b.tool === 'Agent') {
      if (!agentId) break
      continue
    }
    const owner = mirroredOwner(b)
    if (!owner) continue
    if (agentId ? owner === agentId : !claimed.has(owner)) {
      entries.push(b)
    }
  }

  return {
    agentType,
    description,
    prompt,
    promptPartial: !parsed,
    background,
    running: block.status === 'running',
    entries,
    finalText: background || !result ? '' : finalTextOf(result),
    usage: usageOf(result)
  }
}
