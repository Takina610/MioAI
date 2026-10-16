import { computed, onMounted, ref } from 'vue'
import { getPublicAgents } from '@/api/agent'
import { getPublicKnowledgeBases } from '@/api/knowledgeBase'
import { getPublicMcpTools } from '@/api/mcpTool'
import { getPlazaSkills } from '@/api/skill'
import { getFreeCatalog } from '@/api/modelSetting'
import { countMcpTools } from '@/utils/mcpTool'
import type { Agent, KnowledgeBase, McpTool, Skill } from '@/types'

/** 内容池：站内可浏览的四类公开内容 */
export type PoolKey = 'agent' | 'knowledge' | 'mcp' | 'skill'

/** 内容池显示名（筛选按钮与卡片字形共用） */
export const POOL_LABELS: Record<PoolKey, string> = {
  agent: '智能体',
  knowledge: '知识库',
  mcp: 'MCP',
  skill: '技能'
}

/** 内容墙条目：四个池统一成一种视图模型，卡片与筛选共用 */
export interface HomeItem {
  key: string
  pool: PoolKey
  to: string
  title: string
  description: string
  /** 卡片底部读数（作者、用量、文档数等，已拼接） */
  meta: string
  /** 智能体图标配置 JSON（bot-icon/types.ts），缺失时回退 avatar */
  icon?: string
  avatar?: string
}

/** 各池多取一些再排序截断，避免接口的默认顺序把内容埋掉 */
const POOL_SIZE = 24
/** 内容墙最多展示条数 */
const WALL_SIZE = 12
/** 静默请求：后端不可用时首页少内容墙，不弹错误提示 */
const SILENT = { skipErrorMessage: true } as const

/** 时间倒序（接口 createTime 为 ISO 字符串，字典序即时间序） */
function newerFirst(a: { createTime?: string }, b: { createTime?: string }): number {
  return (b.createTime ?? '').localeCompare(a.createTime ?? '')
}

/** 非空读数拼接 */
function metaOf(...parts: Array<string | false | null | undefined>): string {
  return parts.filter((p): p is string => typeof p === 'string' && p.length > 0).join(' · ')
}

/**
 * 同名去重：同一名字在池里出现多次（重复发布/模板复制）时只留一条，
 * 保留排序更靠前的那个。内容墙上一排出现同名卡片看起来像渲染错误。
 */
function dedupeByName<T extends { name: string }>(items: T[]): T[] {
  const seen = new Set<string>()
  return items.filter((item) => {
    const key = item.name.trim()
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function toAgentItem(agent: Agent): HomeItem {
  return {
    key: `agent-${agent.id}`,
    pool: 'agent',
    // 广场模板不能直接对话，定位到广场里该条目（页面自带高亮）
    to: `/dashboard/agent-market?highlight=${agent.id}`,
    title: agent.name,
    description: agent.description || '',
    meta: metaOf(agent.userName || '官方', agent.usageCount > 0 ? `${agent.usageCount} 次使用` : null),
    icon: agent.icon,
    avatar: agent.avatar
  }
}

function toKnowledgeItem(kb: KnowledgeBase): HomeItem {
  return {
    key: `kb-${kb.id}`,
    pool: 'knowledge',
    to: '/dashboard/public-knowledge',
    title: kb.name,
    description: kb.description || '',
    meta: metaOf(
      kb.userName || '匿名',
      kb.documentCount > 0 ? `${kb.documentCount} 篇文档` : null
    )
  }
}

function toMcpItem(tool: McpTool): HomeItem {
  const toolCount = countMcpTools(tool.toolInfo)
  return {
    key: `mcp-${tool.id}`,
    pool: 'mcp',
    to: `/dashboard/mcp-market/${tool.id}`,
    title: tool.name,
    description: tool.description || '',
    meta: metaOf(tool.userName || '匿名', toolCount > 0 ? `${toolCount} 个工具` : null)
  }
}

function toSkillItem(skill: Skill): HomeItem {
  return {
    key: `skill-${skill.id}`,
    pool: 'skill',
    to: '/dashboard/skill-market',
    title: skill.name,
    description: skill.description || '',
    meta: metaOf(skill.userName || '匿名', skill.repoOwner ? `${skill.repoOwner}/${skill.repoName}` : null)
  }
}

export function useHomeContent() {
  const agents = ref<Agent[]>([])
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const mcpTools = ref<McpTool[]>([])
  const skills = ref<Skill[]>([])
  const freeModelCount = ref(0)

  /** 内容墙条目：各池取最有代表性的一批合并（智能体与知识库按热度，MCP 与技能按新旧） */
  const items = computed<HomeItem[]>(() => [
    ...dedupeByName([...agents.value].sort((a, b) => (b.usageCount || 0) - (a.usageCount || 0) || newerFirst(a, b)))
      .slice(0, WALL_SIZE)
      .map(toAgentItem),
    ...dedupeByName(
      [...knowledgeBases.value].sort(
        (a, b) => (b.documentCount || 0) - (a.documentCount || 0) || newerFirst(a, b)
      )
    )
      .slice(0, WALL_SIZE)
      .map(toKnowledgeItem),
    ...dedupeByName([...mcpTools.value].sort(newerFirst)).slice(0, WALL_SIZE).map(toMcpItem),
    ...dedupeByName([...skills.value].sort(newerFirst)).slice(0, WALL_SIZE).map(toSkillItem)
  ])

  /** 顶部一行公告：新上架智能体优先，其次免费模型目录 */
  const announcement = computed<{ text: string; to: string } | null>(() => {
    const latest = [...agents.value].sort(newerFirst)[0]
    if (latest) {
      return {
        text: `「${latest.name}」已上架智能体广场`,
        to: `/dashboard/agent-market?highlight=${latest.id}`
      }
    }
    if (freeModelCount.value > 0) {
      return { text: `免费模型目录已接入 ${freeModelCount.value} 个模型`, to: '/chat' }
    }
    return null
  })

  async function load(): Promise<void> {
    const [agentRes, kbRes, mcpRes, skillRes, catalogRes] = await Promise.allSettled([
      getPublicAgents({ current: 1, size: POOL_SIZE }, SILENT),
      getPublicKnowledgeBases({ current: 1, size: POOL_SIZE }, SILENT),
      getPublicMcpTools({ current: 1, size: POOL_SIZE }, SILENT),
      getPlazaSkills({ current: 1, size: POOL_SIZE }, SILENT),
      getFreeCatalog()
    ])
    if (agentRes.status === 'fulfilled') agents.value = agentRes.value?.records ?? []
    if (kbRes.status === 'fulfilled') knowledgeBases.value = kbRes.value?.records ?? []
    if (mcpRes.status === 'fulfilled') mcpTools.value = mcpRes.value?.records ?? []
    if (skillRes.status === 'fulfilled') skills.value = skillRes.value?.records ?? []
    if (catalogRes.status === 'fulfilled') freeModelCount.value = catalogRes.value.models.length
  }

  onMounted(load)

  return { items, announcement, load }
}
