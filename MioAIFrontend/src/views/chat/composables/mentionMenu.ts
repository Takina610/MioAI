import type { Component } from 'vue'
import Fuse from 'fuse.js'
import {
  ApiOutlined,
  CompressOutlined,
  DatabaseOutlined,
  MessageOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import type { AllSessionItem } from '@/api/chatMemory'
import type { ChatRefItem, KnowledgeBase, McpTool, Skill } from '@/types'

/**
 * 输入框 @ / 菜单的候选项构建（zcode 命令面板同源的模糊检索）。
 * 从 useChatEditor 抽出：只负责「数据源 → 分节候选项」，pick 动作由调用方注入。
 */

export interface MentionRow {
  key: string
  index: number
  icon: Component
  /** 类型色调（与会话/知识库/技能/MCP/Compact 的 token 颜色一致） */
  tone: 'session' | 'kb' | 'skill' | 'mcp' | 'compact'
  title: string
  desc?: string
  suffix?: string
  pick: () => void
}

export interface MentionSection {
  title: string
  rows: MentionRow[]
}

const MENTION_FUSE_OPTIONS = {
  // zcode 命令面板同源的模糊参数：转置/缺字母也能命中（scripts/fuse-tune.mjs 调参定版的同族值）
  threshold: 0.45,
  ignoreLocation: true,
  minMatchCharLength: 1
}

/** 菜单候选项模糊过滤（fuse.js）：query 为空显示全部，非空按相关度返回 */
export function fuzzyFilter<T extends { title: string; desc?: string }>(rows: T[], query: string): T[] {
  if (!query) return rows
  return new Fuse(rows, { ...MENTION_FUSE_OPTIONS, keys: ['title', 'desc'] })
    .search(query)
    .map(r => r.item)
}

/** 统一编号供键盘上下移动 */
export function withIndex(sections: MentionSection[]): MentionSection[] {
  let counter = 0
  return sections.map(section => ({
    ...section,
    rows: section.rows.map(row => ({ ...row, index: counter++ }))
  }))
}

export interface MentionSources {
  sessions: AllSessionItem[]
  knowledgeBases: KnowledgeBase[]
  skills: Skill[]
  mcpTools: McpTool[]
}

export interface MentionPickers {
  pickRef: (item: ChatRefItem) => void
  pickCompact: () => void
}

/** @ 菜单：会话 + 知识库 */
function sessionSections(sources: MentionSources, query: string, pickers: MentionPickers): MentionSection[] {
  const sections: MentionSection[] = [
    {
      title: '会话',
      rows: fuzzyFilter(
        sources.sessions.map(s => ({ ...s, title: s.title || '新对话' })),
        query
      )
        .slice(0, 20)
        .map(s => ({
          key: `session_${s.conversationId}`,
          index: 0,
          icon: MessageOutlined,
          tone: 'session',
          title: s.title || '新对话',
          suffix: s.agentName,
          pick: () =>
            pickers.pickRef({
              type: 'session',
              id: s.conversationId,
              name: s.title || '新对话',
              agentName: s.agentName
            })
        }))
    }
  ]
  const kbRows = fuzzyFilter(
    sources.knowledgeBases.map(kb => ({ ...kb, title: kb.name })),
    query
  ).map(kb => ({
      key: `kb_${kb.id}`,
      index: 0,
      icon: DatabaseOutlined,
      tone: 'kb' as const,
      title: kb.name,
      desc: kb.description,
      pick: () => pickers.pickRef({ type: 'kb', kbId: kb.id, name: kb.name })
    }))
  if (kbRows.length) {
    sections.push({ title: '知识库', rows: kbRows })
  }
  return sections
}

/** / 菜单：命令 + 技能 + MCP */
function commandSections(sources: MentionSources, query: string, pickers: MentionPickers): MentionSection[] {
  const sections: MentionSection[] = [
    {
      title: '命令',
      rows: [
        {
          key: 'cmd_compact',
          index: 0,
          icon: CompressOutlined,
          tone: 'compact' as const,
          title: '/compact',
          desc: '压缩上下文',
          pick: pickers.pickCompact
        }
      ].filter(row => fuzzyFilter([row], query).length > 0)
    }
  ]
  const skillRows = fuzzyFilter(
    sources.skills.map(s => ({ ...s, title: s.name })),
    query
  ).map(s => ({
      key: `skill_${s.id}`,
      index: 0,
      icon: ThunderboltOutlined,
      tone: 'skill' as const,
      title: s.name,
      desc: s.description,
      pick: () => pickers.pickRef({ type: 'skill', name: s.name })
    }))
  if (skillRows.length) {
    sections.push({ title: '技能', rows: skillRows })
  }
  const mcpRows = fuzzyFilter(
    sources.mcpTools.map(m => ({ ...m, title: m.name })),
    query
  ).map(m => ({
      key: `mcp_${m.id}`,
      index: 0,
      icon: ApiOutlined,
      tone: 'mcp' as const,
      title: m.name,
      desc: m.description,
      pick: () => pickers.pickRef({ type: 'mcp', name: m.name })
    }))
  if (mcpRows.length) {
    sections.push({ title: 'MCP', rows: mcpRows })
  }
  return sections
}

/** 按触发符构建分节菜单（含统一编号） */
export function buildMentionSections(
  trigger: '@' | '/',
  sources: MentionSources,
  query: string,
  pickers: MentionPickers
): MentionSection[] {
  const sections = trigger === '@'
    ? sessionSections(sources, query, pickers)
    : commandSections(sources, query, pickers)
  return withIndex(sections)
}
