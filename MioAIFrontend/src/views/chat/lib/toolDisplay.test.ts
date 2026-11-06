import { describe, it, expect } from 'vitest'
import { toolVerb, toolMeta, toolIcon } from './toolDisplay'
import type { ToolBlock } from './toolDisplay'

/** 构造一个工具块：只需 tool + args，其余字段给最小合法值 */
function block(tool: string, args: Record<string, unknown> = {}): ToolBlock {
  return {
    type: 'tool',
    id: `t-${tool}`,
    tool,
    status: 'done',
    args: JSON.stringify(args),
    content: ''
  } as unknown as ToolBlock
}

describe('对话内资源管理工具的动词', () => {
  it('运行中与完成态都有中文动词，不退化成通用「执行中」', () => {
    for (const tool of ['resource_list', 'agent_save', 'group_create', 'agent_bind']) {
      const running = { ...block(tool), status: 'running' } as ToolBlock
      expect(toolVerb(running)).not.toBe('执行中')
      expect(toolVerb(block(tool))).not.toBe('已执行')
    }
  })

  it('四个工具各有独立动词，便于区分副作用', () => {
    const running = ['resource_list', 'agent_save', 'group_create', 'agent_bind']
      .map(tool => toolVerb({ ...block(tool), status: 'running' } as ToolBlock))
    expect(new Set(running).size).toBe(4)
  })
})

describe('对话内资源管理工具的图标', () => {
  it('每个工具都有专属图标，不落到通用扳手', () => {
    const fallback = toolIcon('__not_registered__')
    for (const tool of ['resource_list', 'agent_save', 'group_create', 'agent_bind']) {
      expect(toolIcon(tool)).not.toBe(fallback)
    }
  })
})

describe('resource_list 摘要', () => {
  it('域 + 查询词都给出时并列展示', () => {
    expect(toolMeta(block('resource_list', { domain: 'agent', query: '助手' }))).toBe('智能体 · “助手”')
  })

  it('只有域时只展示域', () => {
    expect(toolMeta(block('resource_list', { domain: 'knowledge' }))).toBe('知识库')
  })

  it('域未知但给了查询词时回退到查询词', () => {
    expect(toolMeta(block('resource_list', { domain: 'unknown', query: 'x' }))).toBe('“x”')
  })
})

describe('agent_save 摘要', () => {
  it('新建时展示名称', () => {
    expect(toolMeta(block('agent_save', { name: '产品助手' }))).toBe('“产品助手”')
  })

  it('更新时展示名称与 id', () => {
    expect(toolMeta(block('agent_save', { name: '产品助手', agentId: 12 }))).toBe('“产品助手” · #12')
  })

  it('只更新未带名称时展示 id', () => {
    expect(toolMeta(block('agent_save', { agentId: 12 }))).toBe('#12')
  })
})

describe('group_create 摘要', () => {
  it('名称与成员数并列', () => {
    expect(toolMeta(block('group_create', { name: '评审组', memberAgentIds: [1, 2, 3] })))
      .toBe('“评审组” · 3 个成员')
  })

  it('成员列表为空时不显示成员数', () => {
    expect(toolMeta(block('group_create', { name: '评审组' }))).toBe('“评审组”')
  })
})

describe('agent_bind 摘要', () => {
  it('绑定展示域与目标 id', () => {
    expect(toolMeta(block('agent_bind', {
      action: 'bind', domain: 'knowledge', agentId: 5, resourceId: 9
    }))).toBe('绑定知识库 · 智能体 #5 · #9')
  })

  it('解绑动作文案区分', () => {
    expect(toolMeta(block('agent_bind', {
      action: 'unbind', domain: 'mcp', agentId: 5, resourceId: 3
    }))).toBe('解绑MCP · 智能体 #5 · #3')
  })
})
