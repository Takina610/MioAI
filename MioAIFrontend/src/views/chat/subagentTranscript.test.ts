import { describe, it, expect } from 'vitest'
import {
  isMirroredToolBlock,
  mirroredOwner,
  buildSubagentTranscript
} from './subagentTranscript'
import type { MessageBlock } from '@/types'

const AGENT_ID = 'agent_0123456789ab'

function agentBlock(overrides: Record<string, unknown> = {}): MessageBlock {
  return {
    type: 'tool',
    tool: 'Agent',
    args: JSON.stringify({ prompt: 'do the work', description: 'work', subagent_type: 'general-purpose' }),
    status: 'running',
    ...overrides
  } as MessageBlock
}

describe('isMirroredToolBlock / mirroredOwner', () => {
  it('识别 agent_<12hex>- 前缀的镜像工具行', () => {
    const block = { type: 'tool', id: `${AGENT_ID}-toolu_1`, tool: 'Read' } as MessageBlock
    expect(isMirroredToolBlock(block)).toBe(true)
    expect(mirroredOwner(block)).toBe(AGENT_ID)
  })

  it('普通工具行与错误前缀不算镜像', () => {
    expect(isMirroredToolBlock({ type: 'tool', id: 'toolu_x', tool: 'Read' } as MessageBlock)).toBe(false)
    expect(mirroredOwner({ type: 'tool', id: 'agent_XYZ-toolu_1', tool: 'Read' } as MessageBlock)).toBeNull()
    expect(mirroredOwner({ type: 'text', text: 'x' } as MessageBlock)).toBeNull()
  })
})

describe('buildSubagentTranscript', () => {
  it('非 Agent 块返回 null', () => {
    expect(buildSubagentTranscript([{ type: 'text', text: 'x' } as MessageBlock], 0)).toBeNull()
    expect(buildSubagentTranscript([], 0)).toBeNull()
  })

  it('解析 args 中的 prompt/description/agentType', () => {
    const blocks = [agentBlock()]
    const t = buildSubagentTranscript(blocks, 0)!
    expect(t.prompt).toBe('do the work')
    expect(t.description).toBe('work')
    expect(t.agentType).toBe('general-purpose')
    expect(t.promptPartial).toBe(false)
  })

  it('args 残缺时宽松解析并标记 partial', () => {
    const blocks = [agentBlock({ args: '{"prompt":"half' })]
    const t = buildSubagentTranscript(blocks, 0)!
    expect(t.prompt).toBe('half')
    expect(t.promptPartial).toBe(true)
  })

  it('按 agentId 精确收集镜像行，并剥离最终报告页脚', () => {
    const result = 'report body\n\nagentId: agent_0123456789ab'
      + '\n\n<usage>\nsubagent_tokens: 100\ntool_uses: 3\nduration_ms: 5000\n</usage>'
    const blocks: MessageBlock[] = [
      agentBlock({ status: 'success', result }),
      { type: 'tool', id: `${AGENT_ID}-toolu_1`, tool: 'Read' } as MessageBlock,
      { type: 'tool', id: 'toolu_parent', tool: 'Grep' } as MessageBlock,
      { type: 'tool', id: 'agent_ffffffffffff-toolu_9', tool: 'Read' } as MessageBlock
    ]
    const t = buildSubagentTranscript(blocks, 0)!
    expect(t.entries.map(e => e.id)).toEqual([`${AGENT_ID}-toolu_1`])
    expect(t.finalText).toBe('report body')
    expect(t.usage).toEqual({ tokens: 100, toolUses: 3, durationMs: 5000 })
    expect(t.running).toBe(false)
  })

  it('后台子代理不输出最终报告', () => {
    const blocks = [agentBlock({ result: 'launched in background' })]
    const t = buildSubagentTranscript(blocks, 0)!
    expect(t.background).toBe(true)
    expect(t.finalText).toBe('')
  })
})
