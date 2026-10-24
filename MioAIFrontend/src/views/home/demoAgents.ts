import type { AgentMemberBrief } from '@/types'
import type { DemoEvent, DemoSessionDef } from './demoScenarios'
import { MIOBOT_SESSIONS } from './demoScenarios'

/**
 * 首页演示的智能体注册表：左轨展示与切换的数据源。
 * 每个智能体挂自己的会话（含剧本），点击左轨切换；群聊（type=2）以成员气泡剧本演示。
 * icon 为 bot-icon 配置 JSON（MioBot/1bot 取自真实广场数据）。
 */
export interface DemoAgentDef {
  id: number
  name: string
  description?: string
  /** BotIconConfig JSON */
  icon?: string
  /** 0 官方 / 1 自定义 / 2 群聊 */
  type: number
  /** 群聊成员概要（type=2 时左轨渲染群头像、消息区启用群聊模式） */
  members?: AgentMemberBrief[]
  sessions: DemoSessionDef[]
}

const CYAN_BLOB = JSON.stringify({ shape: 'blob', fill: { kind: 'palette', id: 'cyan' } })
const VIOLET_HEX = JSON.stringify({ shape: 'hex', fill: { kind: 'palette', id: 'violet' } })
const MAGENTA_CAPSULE = JSON.stringify({ shape: 'capsule', fill: { kind: 'palette', id: 'magenta' } })

/** 1bot：日常润色问答 */
const ONEBOT_SCRIPT: DemoEvent[] = [
  {
    type: 'user',
    wait: 200,
    typed: '帮我把这句话润色成英文：这个功能的边界情况比较多，建议分两期做',
    content: '帮我把这句话润色成英文：这个功能的边界情况比较多，建议分两期做'
  },
  { type: 'start', wait: 900 },
  {
    type: 'thinking',
    wait: 300,
    text: '直译会丢失「分两期」的工程语义，用 phased rollout 的表达更贴切。'
  },
  {
    type: 'answer',
    wait: 400,
    text: [
      '**"This feature has quite a few edge cases — I\'d suggest shipping it in two phases."**',
      '',
      '两处取舍：',
      '- `edge cases` 是「边界情况」的行业标准说法，比 corner cases 更通用；',
      '- `two phases` 呼应分期交付的语境，比 milestones 更贴合迭代发布。'
    ].join('\n')
  },
  { type: 'finish', wait: 600 }
]

/** 代码评审官：diff 风险点评审 */
const REVIEWER_SCRIPT: DemoEvent[] = [
  {
    type: 'user',
    wait: 200,
    typed: '帮我看下这次登录重构的 diff，有没有风险点',
    content: '帮我看下这次登录重构的 diff，有没有风险点'
  },
  { type: 'start', wait: 900 },
  {
    type: 'thinking',
    wait: 300,
    text: '先拉 PR 变更，重点看三处：token 刷新竞态、异常分支兜底、旧接口兼容期。'
  },
  {
    type: 'tool',
    wait: 400,
    tool: 'mcp__github__reviewDiff',
    desc: '读取 PR 变更',
    args: '{"repo":"Takina610/MioAI","pr":128}',
    duration: 1900,
    result: '3 files changed: auth.ts / session.ts / login.vue（+186 −94）'
  },
  {
    type: 'answer',
    wait: 400,
    text: [
      '结构没问题，但有三个风险点：',
      '',
      '1. **token 刷新竞态**——`refresh()` 未加锁，并发请求会重复刷新；建议做单飞（in-flight 复用同一个 Promise）。',
      '2. **异常兜底缺失**——`session.ts` 的 401 分支只清了本地态，没有中断后续请求链。',
      '3. **旧接口兼容期**——`login.vue` 仍在调 v1 端点，下线前需要双写灰度。',
      '',
      '处理完这三处即可合入。'
    ].join('\n')
  },
  { type: 'finish', wait: 600 }
]

/** 周报整理助手：拉提交生成周报 */
const WEEKLY_SCRIPT: DemoEvent[] = [
  {
    type: 'user',
    wait: 200,
    typed: '生成本周周报',
    content: '生成本周周报'
  },
  { type: 'start', wait: 900 },
  {
    type: 'thinking',
    wait: 300,
    text: '拉本周提交与已关闭的事项，按「进展 / 风险 / 下周计划」三段组织。'
  },
  {
    type: 'tool',
    wait: 400,
    tool: 'mcp__github__listCommits',
    desc: '拉取本周提交',
    args: '{"repo":"Takina610/MioAI","days":7}',
    duration: 1700,
    result: '19 commits / 2 authors；登录重构、使用记录分析页为主'
  },
  {
    type: 'answer',
    wait: 400,
    text: [
      '**本周进展**',
      '- 登录重构完成 80%（auth / session 已过评审）',
      '- 使用记录与分析页上线',
      '',
      '**风险**',
      '- v1 接口下线时间未定，影响双写灰度窗口',
      '',
      '**下周计划**',
      '- 登录重构灰度放量',
      '- 周报模板沉淀为可复用技能'
    ].join('\n')
  },
  { type: 'finish', wait: 600 }
]

/** 需求评审群（群聊）：成员逐个冒泡同步 */
const GROUP_SCRIPT: DemoEvent[] = [
  {
    type: 'user',
    wait: 200,
    typed: '这周的重点对一下，各位同步下手里的情况',
    content: '这周的重点对一下，各位同步下手里的情况'
  },
  { type: 'start', wait: 900 },
  {
    type: 'member',
    wait: 500,
    memberId: 11,
    memberName: '代码评审官',
    memberIcon: CYAN_BLOB,
    text: 'CR 积压已清完；登录重构的三个风险点都修掉了，明天可以灰度。'
  },
  {
    type: 'member',
    wait: 400,
    memberId: 12,
    memberName: '周报整理助手',
    memberIcon: VIOLET_HEX,
    text: '周报模板已沉淀成技能，运营数据还差最后一天。'
  },
  {
    type: 'answer',
    wait: 400,
    text: '好，本周重点就是**登录重构灰度 + 周报技能上线**。剩下的风险只有 v1 接口下线时间，我去和后端对齐。'
  },
  { type: 'finish', wait: 600 }
]

export const DEMO_AGENTS: DemoAgentDef[] = [
  {
    id: 1,
    name: 'MioBot',
    description: 'MioAI 智能助手：自主规划、调用工具、迭代执行',
    icon: JSON.stringify({ shape: 'blob', fill: { kind: 'palette', id: 'black' } }),
    type: 0,
    sessions: MIOBOT_SESSIONS
  },
  {
    id: 2,
    name: '1bot',
    description: '日常问答与文案润色',
    icon: MAGENTA_CAPSULE,
    type: 1,
    sessions: [
      { id: 'demo-1b1', title: '英文润色', updateTime: new Date('2026-10-23 16:20:00'), events: ONEBOT_SCRIPT }
    ]
  },
  {
    id: 11,
    name: '代码评审官',
    description: '读 diff，出结构化评审意见',
    icon: CYAN_BLOB,
    type: 1,
    sessions: [
      { id: 'demo-rv1', title: 'CR：登录重构', updateTime: new Date('2026-10-24 09:12:00'), events: REVIEWER_SCRIPT }
    ]
  },
  {
    id: 12,
    name: '周报整理助手',
    description: '提交记录与日报汇总成周报',
    icon: VIOLET_HEX,
    type: 1,
    sessions: [
      { id: 'demo-wk1', title: '本周周报', updateTime: new Date('2026-10-24 18:40:00'), events: WEEKLY_SCRIPT }
    ]
  },
  {
    id: 101,
    name: '需求评审群',
    description: '代码评审官、周报整理助手与 MioBot',
    type: 2,
    members: [
      { id: 1, name: 'MioBot', icon: JSON.stringify({ shape: 'blob', fill: { kind: 'palette', id: 'black' } }) },
      { id: 11, name: '代码评审官', icon: CYAN_BLOB },
      { id: 12, name: '周报整理助手', icon: VIOLET_HEX }
    ],
    sessions: [
      { id: 'demo-g1', title: '本周评审对齐', updateTime: new Date('2026-10-24 20:05:00'), events: GROUP_SCRIPT }
    ]
  }
]
