import type { AdminLogQueryRequest, AdminLogType } from '@/api/admin/log'
import type { ExpandField, LogColumn } from '@/views/admin/components/recordTypes'

export interface LogFilterField {
  key: keyof AdminLogQueryRequest
  placeholder: string
  kind: 'keyword' | 'number' | 'select' | 'date'
  width?: 'search' | 'item'
  options?: { label: string; value: number | string }[]
}

export interface LogTypeConfig {
  type: AdminLogType
  label: string
  columns: LogColumn[]
  filters: LogFilterField[]
  /** 展开行字段：输入参数/输出结果等大字段 */
  expand?: ExpandField[]
}

const STATUS_DOT = {
  '1': { label: '成功', tone: 'success' },
  '0': { label: '失败', tone: 'danger' }
} satisfies LogColumn['dotLabels']

const USER_COL: LogColumn = { key: 'user', title: '用户', path: ['userName'], width: 150, kind: 'user' }
const AGENT_COL: LogColumn = { key: 'agent', title: '智能体', path: ['agentName'], width: 150, kind: 'agent' }
const TIME_COL: LogColumn = {
  key: 'time',
  title: '时间',
  path: ['data', 'createTime'],
  width: 160,
  kind: 'text',
  format: 'datetime'
}

export const LOG_TYPE_CONFIGS: Record<AdminLogType, LogTypeConfig> = {
  usage: {
    type: 'usage',
    label: '对话用量',
    columns: [
      { key: 'id', title: 'ID', path: ['data', 'id'], width: 80, kind: 'number' },
      USER_COL,
      AGENT_COL,
      { key: 'model', title: '模型', path: ['data', 'model'], width: 150, ellipsis: true, kind: 'text' },
      { key: 'input', title: '输入', path: ['data', 'inputTokens'], width: 90, kind: 'number' },
      { key: 'output', title: '输出', path: ['data', 'outputTokens'], width: 90, kind: 'number' },
      {
        key: 'responseTime',
        title: '耗时',
        path: ['data', 'responseTime'],
        width: 90,
        kind: 'text',
        format: 'duration-s'
      },
      {
        key: 'status',
        title: '状态',
        path: ['data', 'status'],
        width: 84,
        kind: 'dot',
        dotLabels: STATUS_DOT
      },
      TIME_COL
    ],
    filters: [
      { key: 'keyword', placeholder: '错误信息', kind: 'keyword', width: 'search' },
      { key: 'userId', placeholder: '用户 ID', kind: 'number' },
      { key: 'agentId', placeholder: '智能体 ID', kind: 'number' },
      { key: 'model', placeholder: '模型', kind: 'keyword' },
      {
        key: 'status',
        placeholder: '状态',
        kind: 'select',
        options: [
          { label: '成功', value: 1 },
          { label: '失败', value: 0 }
        ]
      }
    ],
    expand: [
      { title: '会话 ID', path: ['data', 'conversationId'], mono: true },
      { title: '缓存 Token', path: ['data', 'cachedTokens'] },
      { title: '错误信息', path: ['data', 'errorMsg'] }
    ]
  },
  skill: {
    type: 'skill',
    label: '技能调用',
    columns: [
      { key: 'id', title: 'ID', path: ['data', 'id'], width: 80, kind: 'number' },
      USER_COL,
      AGENT_COL,
      { key: 'skillName', title: '技能', path: ['data', 'skillName'], width: 170, ellipsis: true, kind: 'text' },
      { key: 'skillId', title: '技能 ID', path: ['data', 'skillId'], width: 90, kind: 'number' },
      TIME_COL
    ],
    filters: [
      { key: 'keyword', placeholder: '技能名称', kind: 'keyword', width: 'search' },
      { key: 'userId', placeholder: '用户 ID', kind: 'number' },
      { key: 'agentId', placeholder: '智能体 ID', kind: 'number' },
      { key: 'skillId', placeholder: '技能 ID', kind: 'number' }
    ],
    expand: [{ title: '会话 ID', path: ['data', 'conversationId'], mono: true }]
  },
  tool: {
    type: 'tool',
    label: '工具调用',
    columns: [
      { key: 'id', title: 'ID', path: ['data', 'id'], width: 80, kind: 'number' },
      USER_COL,
      AGENT_COL,
      { key: 'name', title: '工具', path: ['data', 'name'], width: 170, ellipsis: true, kind: 'text' },
      {
        key: 'status',
        title: '状态',
        path: ['data', 'status'],
        width: 84,
        kind: 'dot',
        dotLabels: { ...STATUS_DOT, '2': { label: '超时', tone: 'warn' } }
      },
      {
        key: 'executionTime',
        title: '耗时',
        path: ['data', 'executionTime'],
        width: 90,
        kind: 'text',
        format: 'duration-s'
      },
      TIME_COL
    ],
    filters: [
      { key: 'keyword', placeholder: '工具名称', kind: 'keyword', width: 'search' },
      { key: 'userId', placeholder: '用户 ID', kind: 'number' },
      { key: 'agentId', placeholder: '智能体 ID', kind: 'number' },
      {
        key: 'status',
        placeholder: '状态',
        kind: 'select',
        options: [
          { label: '成功', value: 1 },
          { label: '失败', value: 0 },
          { label: '超时', value: 2 }
        ]
      }
    ],
    expand: [
      { title: '会话 ID', path: ['data', 'conversationId'], mono: true },
      { title: '输入参数', path: ['data', 'inputParams'], mono: true },
      { title: '输出结果', path: ['data', 'outputResult'], mono: true },
      { title: '错误信息', path: ['data', 'errorMsg'] }
    ]
  },
  rag: {
    type: 'rag',
    label: 'RAG 检索',
    columns: [
      { key: 'id', title: 'ID', path: ['data', 'id'], width: 80, kind: 'number' },
      USER_COL,
      AGENT_COL,
      { key: 'query', title: '查询内容', path: ['data', 'query'], ellipsis: true, kind: 'text' },
      { key: 'kbId', title: '知识库 ID', path: ['data', 'kbId'], width: 100, kind: 'number' },
      { key: 'topK', title: 'TopK', path: ['data', 'topK'], width: 70, kind: 'number' },
      {
        key: 'responseTime',
        title: '耗时',
        path: ['data', 'responseTime'],
        width: 90,
        kind: 'text',
        format: 'duration-s'
      },
      TIME_COL
    ],
    filters: [
      { key: 'keyword', placeholder: '查询内容', kind: 'keyword', width: 'search' },
      { key: 'userId', placeholder: '用户 ID', kind: 'number' },
      { key: 'agentId', placeholder: '智能体 ID', kind: 'number' },
      { key: 'kbId', placeholder: '知识库 ID', kind: 'number' }
    ],
    expand: [{ title: '命中分块', path: ['data', 'retrievedChunks'], mono: true }]
  },
  activity: {
    type: 'activity',
    label: '活动日志',
    columns: [
      { key: 'id', title: 'ID', path: ['data', 'id'], width: 80, kind: 'number' },
      USER_COL,
      { key: 'module', title: '模块', path: ['data', 'module'], width: 100, kind: 'text' },
      { key: 'action', title: '动作', path: ['data', 'action'], width: 170, ellipsis: true, kind: 'text' },
      {
        key: 'path',
        title: '请求',
        path: ['data', 'path'],
        ellipsis: true,
        kind: 'text'
      },
      {
        key: 'success',
        title: '结果',
        path: ['data', 'success'],
        width: 84,
        kind: 'dot',
        dotLabels: STATUS_DOT
      },
      { key: 'costMs', title: '耗时', path: ['data', 'costMs'], width: 90, kind: 'text', format: 'duration-ms' },
      TIME_COL
    ],
    filters: [
      { key: 'keyword', placeholder: '动作或路径', kind: 'keyword', width: 'search' },
      { key: 'userId', placeholder: '用户 ID', kind: 'number' },
      {
        key: 'module',
        placeholder: '模块',
        kind: 'select',
        options: ['agent', 'knowledge', 'mcp', 'skill', 'model', 'chat', 'auth'].map(m => ({
          label: m,
          value: m
        }))
      },
      {
        key: 'status',
        placeholder: '结果',
        kind: 'select',
        options: [
          { label: '成功', value: 1 },
          { label: '失败', value: 0 }
        ]
      }
    ],
    expand: [
      { title: '入参摘要', path: ['data', 'summary'], mono: true },
      { title: '失败摘要', path: ['data', 'errorMsg'], mono: true },
      { title: 'IP', path: ['data', 'ip'], mono: true }
    ]
  },
  daily: {
    type: 'daily',
    label: '每日用量',
    columns: [
      { key: 'id', title: 'ID', path: ['data', 'id'], width: 80, kind: 'number' },
      USER_COL,
      { key: 'statDate', title: '日期', path: ['data', 'statDate'], width: 110, kind: 'text' },
      { key: 'input', title: '输入', path: ['data', 'inputTokens'], width: 100, kind: 'number' },
      { key: 'output', title: '输出', path: ['data', 'outputTokens'], width: 100, kind: 'number' },
      { key: 'callCount', title: '轮次', path: ['data', 'callCount'], width: 80, kind: 'number' },
      {
        key: 'chatDurationMs',
        title: '聊天时长',
        path: ['data', 'chatDurationMs'],
        width: 100,
        kind: 'text',
        format: 'duration-ms'
      }
    ],
    filters: [
      { key: 'userId', placeholder: '用户 ID', kind: 'number' },
      { key: 'startDate', placeholder: '开始日期', kind: 'date' },
      { key: 'endDate', placeholder: '结束日期', kind: 'date' }
    ]
  }
}

export const LOG_TYPE_ORDER: AdminLogType[] = ['usage', 'skill', 'tool', 'rag', 'activity', 'daily']
