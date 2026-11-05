import { describe, it, expect } from 'vitest'
import { buildMentionSections, fuzzyFilter, withIndex } from './mentionMenu'
import type { MentionSources, MentionPickers } from './mentionMenu'
import type { ChatRefItem } from '@/types'

function pickers(): { pickers: MentionPickers; picked: ChatRefItem[]; compacted: number } {
  const picked: ChatRefItem[] = []
  let compacted = 0
  return {
    pickers: { pickRef: i => picked.push(i), pickCompact: () => compacted++ },
    picked,
    get compacted() { return compacted }
  }
}

const emptySources: MentionSources = { sessions: [], knowledgeBases: [], skills: [], mcpTools: [] }

describe('fuzzyFilter', () => {
  it('空 query 原样返回', () => {
    const rows = [{ title: 'abc' }]
    expect(fuzzyFilter(rows, '')).toBe(rows)
  })

  it('模糊命中（允许少量字符差异）', () => {
    const rows = [{ title: 'knowledge base' }, { title: 'session transcript' }]
    expect(fuzzyFilter(rows, 'knowldge').map(r => r.title)).toContain('knowledge base')
  })
})

describe('withIndex', () => {
  it('跨分节连续编号', () => {
    const out = withIndex([
      { title: 'A', rows: [{ key: 'a', index: 0, icon: null as never, tone: 'kb', title: 'a', pick: () => {} }] },
      { title: 'B', rows: [
        { key: 'b1', index: 0, icon: null as never, tone: 'kb', title: 'b1', pick: () => {} },
        { key: 'b2', index: 0, icon: null as never, tone: 'kb', title: 'b2', pick: () => {} }
      ] }
    ])
    expect(out.flatMap(s => s.rows.map(r => r.index))).toEqual([0, 1, 2])
  })
})

describe('buildMentionSections', () => {
  it('@ 触发：会话 + 知识库两节', () => {
    const sources: MentionSources = {
      sessions: [{ conversationId: 'c1', title: '会话一', agentName: 'MioBot' } as never],
      knowledgeBases: [{ id: 1, name: '库一', description: 'd' } as never],
      skills: [],
      mcpTools: []
    }
    const { pickers: p } = pickers()
    const sections = buildMentionSections('@', sources, '', p)
    expect(sections.map(s => s.title)).toEqual(['会话', '知识库'])
    expect(sections[0]!.rows[0]!.title).toBe('会话一')
    expect(sections[1]!.rows[0]!.title).toBe('库一')
  })

  it('@ 触发：无知识库时只剩会话节', () => {
    const sources: MentionSources = {
      ...emptySources,
      sessions: [{ conversationId: 'c1', title: '', agentName: 'A' } as never]
    }
    const { pickers: p } = pickers()
    const sections = buildMentionSections('@', sources, '', p)
    expect(sections.map(s => s.title)).toEqual(['会话'])
    // 空标题回退默认名
    expect(sections[0]!.rows[0]!.title).toBe('新对话')
  })

  it('/ 触发：命令 + 技能 + MCP', () => {
    const sources: MentionSources = {
      ...emptySources,
      skills: [{ id: 2, name: '搜索', description: 'sd' } as never],
      mcpTools: [{ id: 3, name: 'github', description: 'md' } as never]
    }
    const { pickers: p } = pickers()
    const sections = buildMentionSections('/', sources, '', p)
    expect(sections.map(s => s.title)).toEqual(['命令', '技能', 'MCP'])
  })

  it('/ 触发：查询过滤掉命令时该节为空但技能节仍在', () => {
    const sources: MentionSources = {
      ...emptySources,
      skills: [{ id: 2, name: 'search', description: '' } as never]
    }
    const { pickers: p } = pickers()
    const sections = buildMentionSections('/', sources, 'search', p)
    const commandSection = sections.find(s => s.title === '命令')
    expect(commandSection!.rows).toEqual([])
    expect(sections.find(s => s.title === '技能')!.rows.length).toBe(1)
  })

  it('pick 动作透传正确载荷', () => {
    const sources: MentionSources = {
      ...emptySources,
      knowledgeBases: [{ id: 9, name: '库', description: '' } as never]
    }
    const { pickers: p, picked } = pickers()
    const sections = buildMentionSections('@', sources, '', p)
    sections[1]!.rows[0]!.pick()
    expect(picked).toEqual([{ type: 'kb', kbId: 9, name: '库' }])
  })

  it('命令节 pick 触发 pickCompact', () => {
    let compacted = 0
    const p = { pickRef: () => {}, pickCompact: () => { compacted++ } }
    const sections = buildMentionSections('/', emptySources, '', p)
    sections[0]!.rows[0]!.pick()
    expect(compacted).toBe(1)
  })

  it('会话行携带 agentName 后缀', () => {
    const sources: MentionSources = {
      ...emptySources,
      sessions: [{ conversationId: 'c1', title: 'T', agentName: 'AgentX' } as never]
    }
    const { pickers: p } = pickers()
    const row = buildMentionSections('@', sources, '', p)[0]!.rows[0]!
    expect(row.suffix).toBe('AgentX')
  })
})
