import type { KbContextGroup, KnowledgeBase, MessageBlock, Skill } from '@/types'
import { EUPH_DURATION_MS, EUPH_REPLY_BLOCKS, EUPH_USER_TEXT } from './demoEuphReply'

/**
 * 首页聊天演示剧本：固定的用户/助手消息，按真实消息块（MessageBlock）语义编排。
 * 用户消息带 typing 脚本：演示播放器会在真实输入框里逐字打出正文，
 * 引用（ref）走真实的 @ / 命令菜单选择流程（过滤 → 回车 → 插入 token 芯片）。
 */
export interface DemoRefSpec {
  /** '@' 打开会话/知识库菜单；'/' 打开命令/技能菜单 */
  trigger: '@' | '/'
  /** 触发后继续键入的过滤词（须能唯一命中目标行） */
  query: string
}

export type DemoEvent =
  | {
      type: 'user'
      wait?: number
      /** 逐字键入输入框的正文（不含引用 token，token 由菜单流程插入） */
      typed: string
      /** 菜单选择流程：先打触发词开菜单，再打过滤词，回车选中第一行 */
      ref?: DemoRefSpec
      /** 随消息上传的附件：先在输入框演示上传卡片（进度环→完成）再发送 */
      attachment?: { name: string; size: number }
      /** 实际入列的完整消息内容（引用标记前缀 + 正文，与真实发送产物一致） */
      content: string
      blocks?: MessageBlock[]
    }
  /** 助手回合开始（最后一条消息进入"已工作"态） */
  | { type: 'start'; wait?: number }
  /** 知识库命中引用行（ResourceRow） */
  | { type: 'kb'; wait?: number; groups: KbContextGroup[] }
  /** 思考块：流式流入后闭合 */
  | { type: 'thinking'; wait?: number; text: string }
  /** 工具调用块：running 保持 duration 毫秒后完成 */
  | {
      type: 'tool'
      wait?: number
      tool: string
      desc?: string
      args?: string
      duration?: number
      result: string
    }
  /** 技能使用行（SKILL.md 摘要） */
  | { type: 'skill'; wait?: number; name: string; content: string }
  /** 群聊成员气泡：输入点动画 → 流式正文 → 完成 */
  | {
      type: 'member'
      wait?: number
      memberId: number
      memberName: string
      memberIcon?: string
      text: string
    }
  /** 回答文本块：流式流入 */
  | { type: 'answer'; wait?: number; text: string }
  /** 动态 UI 块（如引用块）原样追加 */
  | { type: 'ui'; wait?: number; block: MessageBlock }
  /** 真实会话照搬重放：原始块序推进，durationMs 为该轮真实耗时 */
  | { type: 'replay'; wait?: number; blocks: MessageBlock[]; durationMs: number }
  /** 回合收尾：定格耗时、汇总正文（供复制） */
  | { type: 'finish'; wait?: number }

/** 输入框 @ / 命令菜单的演示数据源（ChatInput 的 knowledgeBases / skills 属性） */
export const DEMO_KBS: KnowledgeBase[] = [
  {
    id: 1,
    userId: 10,
    userName: 'Takina',
    name: '前端规范手册',
    description: '布局红线、文案规范与组件用法的项目内约定',
    chunkSize: 512,
    chunkOverlap: 64,
    status: 1,
    isPublic: 1,
    documentCount: 24,
    totalChunks: 860,
    storageSize: 15 * 1024 * 1024,
    createTime: '2026-09-01 10:00:00',
    updateTime: '2026-10-05 10:00:00'
  },
  {
    id: 2,
    userId: 11,
    userName: 'Mio',
    name: '需求评审纪要',
    description: '历次需求评审的结论与待办',
    chunkSize: 512,
    chunkOverlap: 64,
    status: 1,
    isPublic: 1,
    documentCount: 12,
    totalChunks: 340,
    storageSize: 4 * 1024 * 1024,
    createTime: '2026-09-12 10:00:00',
    updateTime: '2026-10-08 10:00:00'
  }
]

export const DEMO_SKILLS: Skill[] = [
  {
    id: 41,
    userId: 10,
    userName: 'Takina',
    name: 'docx',
    description: '把 Markdown 章节结构渲染成 Word 文档',
    installed: 0,
    isPublic: 1,
    owned: false,
    status: 1,
    createTime: '2026-09-01 10:00:00',
    updateTime: '2026-10-01 10:00:00'
  }
]

/** 剧本一：@ 引用知识库提问，命中片段 + 规范要点回答 */
const KB_SCENARIO: DemoEvent[] = [
  {
    type: 'user',
    wait: 200,
    typed: '里对列表卡片的操作按钮是怎么约定的？',
    ref: { trigger: '@', query: '前端规范' },
    content: '[#前端规范手册](#kb_1) 里对列表卡片的操作按钮是怎么约定的？'
  },
  { type: 'start', wait: 900 },
  {
    type: 'kb',
    wait: 500,
    groups: [
      {
        kbId: 1,
        kbName: '前端规范手册',
        hits: [
          {
            fileName: '交互约定.md',
            snippet: '列表卡片操作用 hover 浮现：card-actions 绝对定位随 hover 显现，footer 同时淡出，避免常显按钮遮挡内容。',
            score: 0.912
          },
          {
            fileName: '组件结构.md',
            snippet: 'DOM 顺序必须 card-footer 在前、card-actions 最后，否则浮动按钮的命中会被 footer 拦截。',
            score: 0.871
          }
        ]
      }
    ]
  },
  {
    type: 'thinking',
    wait: 400,
    text: '命中两段规范：一段讲 hover 浮现与 footer 淡出，一段讲 DOM 顺序约束。归纳成两条要点，并说明原因。'
  },
  {
    type: 'answer',
    wait: 500,
    text: [
      '规范里是两条硬约定：',
      '',
      '1. **悬浮浮现，不要常显**——操作按钮绝对定位在卡片右下，hover 时浮现，同时 footer 淡出，避免按钮常年遮挡内容。',
      '2. **footer 在前，actions 最后**——DOM 顺序为 `card-footer` 在前、`card-actions` 收尾，反过来浮动按钮的点击会被 footer 拦截。',
      '',
      '> 两条都出自《前端规范手册 · 交互约定》。'
    ].join('\n')
  },
  { type: 'finish', wait: 600 }
]

/** 剧本二：MCP 工具调用，running→done 动画 + 数据汇总回答 */
const MCP_SCENARIO: DemoEvent[] = [
  {
    type: 'user',
    wait: 200,
    typed: 'MioAI 仓库最近一周的提交活跃度怎么样？',
    content: 'MioAI 仓库最近一周的提交活跃度怎么样？'
  },
  { type: 'start', wait: 900 },
  {
    type: 'thinking',
    wait: 300,
    text: '本地没有仓库数据，需要走 MCP 的 github 服务拉最近一周提交，再按作者聚合出活跃度。'
  },
  {
    type: 'tool',
    wait: 400,
    tool: 'mcp__github__listCommits',
    desc: '拉取仓库提交',
    args: '{"repo":"Takina610/MioAI","days":7}',
    duration: 2100,
    result: '23 commits / 3 authors；最新提交：使用记录与分析：ECharts 深度分析区'
  },
  {
    type: 'answer',
    wait: 400,
    text: [
      '这一周 **23 次提交**、3 位作者，节奏集中在工作日晚间：',
      '',
      '| 作者 | 提交 | 主要内容 |',
      '| --- | --- | --- |',
      '| Takina | 17 | 使用记录分析、目录重构 |',
      '| Mio | 4 | 知识库向量化修复 |',
      '| Bot | 2 | README 与文档 |',
      '',
      '高峰在周三（6 次），最近一次提交是「使用记录与分析：ECharts 深度分析区」。'
    ].join('\n')
  },
  {
    type: 'ui',
    wait: 300,
    block: {
      type: 'ui',
      id: 'demo-commit-chart',
      kind: 'chart',
      title: '本周提交分布',
      chartType: 'bar',
      categories: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
      series: [{ name: '提交数', data: [2, 4, 6, 3, 1, 5, 2] }]
    }
  },
  { type: 'finish', wait: 600 }
]

/** 剧本三：/ 命令菜单引用技能，整理成可交付文档 */
const SKILL_SCENARIO: DemoEvent[] = [
  {
    type: 'user',
    wait: 200,
    typed: '把这份调研笔记整理成可交付的文档',
    ref: { trigger: '/', query: 'docx' },
    attachment: { name: '调研笔记.md', size: 48213 },
    content: '[$docx](#skill_docx) 把这份调研笔记整理成可交付的文档',
    blocks: [
      {
        type: 'attachments',
        side: 'input',
        items: [{ path: 'uploads/demo/调研笔记.md', name: '调研笔记.md', size: 48213 }]
      }
    ]
  },
  { type: 'start', wait: 900 },
  {
    type: 'skill',
    wait: 350,
    name: 'docx',
    content: [
      '---',
      'name: docx',
      'description: 把 Markdown 章节结构渲染成 Word 文档',
      '---',
      '',
      '# SKILL.md',
      '',
      '- Markdown 标题层级 → Word 样式映射',
      '- 表格 / 代码块 / 引用块转换规则',
      '- 输出 .docx 到 outputs/ 目录'
    ].join('\n')
  },
  {
    type: 'thinking',
    wait: 300,
    text: '先读 docx 技能的用法约定，再按笔记的章节结构组织成文档大纲，正文原样保留。'
  },
  {
    type: 'answer',
    wait: 400,
    text: [
      '已按 docx 技能整理完成，结构与原笔记一致：',
      '',
      '- **调研背景 / 目标**（第 1-2 节）→ 前言',
      '- **现状梳理**（第 3 节）→ 表格化对比',
      '- **结论与待办**（第 4 节）→ 分条列示，待办加复选框',
      '',
      '生成物为 Word 文档，样式映射遵循技能约定。'
    ].join('\n')
  },
  { type: 'finish', wait: 600 }
]

/** 剧本四：照搬真实会话「介绍一下《吹响吧上低音号》系列」的完整回复（思考/工具/动态 UI 组件原样重放） */
const EUPH_SCENARIO: DemoEvent[] = [
  {
    type: 'user',
    wait: 200,
    typed: EUPH_USER_TEXT,
    content: EUPH_USER_TEXT
  },
  { type: 'start', wait: 900 },
  { type: 'replay', wait: 400, blocks: EUPH_REPLY_BLOCKS, durationMs: EUPH_DURATION_MS }
]

/** 演示会话：标题 + 时间 + 剧本事件（挂在所属智能体下） */
export interface DemoSessionDef {
  id: string
  title: string
  updateTime: Date
  pinned?: number
  events: DemoEvent[]
}

/** MioBot（默认智能体）的四个演示会话 */
export const MIOBOT_SESSIONS: DemoSessionDef[] = [
  { id: 'demo-s1', title: '列表卡片操作约定', updateTime: new Date('2026-10-24 11:20:00'), events: KB_SCENARIO },
  { id: 'demo-s2', title: '仓库提交活跃度', updateTime: new Date('2026-10-23 21:04:00'), events: MCP_SCENARIO },
  { id: 'demo-s3', title: '调研笔记整理', updateTime: new Date('2026-10-22 15:42:00'), events: SKILL_SCENARIO },
  { id: 'demo-s4', title: '吹响吧！上低音号', updateTime: new Date('2026-10-21 09:30:00'), pinned: 1, events: EUPH_SCENARIO }
]
