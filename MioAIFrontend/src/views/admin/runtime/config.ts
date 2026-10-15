import type { AdminRuntimeQueryRequest, AdminRuntimeType } from '@/api/adminRuntime'
import type { ExpandField, LogColumn } from '@/views/admin/components/recordTypes'

export interface RuntimeFilterField {
  key: keyof AdminRuntimeQueryRequest
  placeholder: string
  kind: 'keyword' | 'number' | 'select'
  width?: 'search' | 'item'
  options?: { label: string; value: number | string }[]
}

export interface RuntimeTypeConfig {
  type: AdminRuntimeType
  label: string
  columns: LogColumn[]
  filters: RuntimeFilterField[]
  expand?: ExpandField[]
}

const TIME_COL: LogColumn = {
  key: 'time',
  title: '时间',
  path: ['data', 'createTime'],
  width: 160,
  kind: 'text',
  format: 'datetime'
}

const AGENT_COL: LogColumn = { key: 'agent', title: '智能体', path: ['agentName'], width: 160, kind: 'agent' }
const USER_COL: LogColumn = { key: 'user', title: '用户', path: ['userName'], width: 140, kind: 'user' }

const TASK_STATUS_DOT: LogColumn['dotLabels'] = {
  running: { label: '运行中', tone: 'warn' },
  completed: { label: '已完成', tone: 'success' },
  failed: { label: '失败', tone: 'danger' },
  stopped: { label: '已停止', tone: 'danger' },
  interrupted: { label: '已中断', tone: 'danger' }
}

export const RUNTIME_TYPE_CONFIGS: Record<AdminRuntimeType, RuntimeTypeConfig> = {
  'group-members': {
    type: 'group-members',
    label: '群聊成员',
    columns: [
      { key: 'id', title: 'ID', path: ['data', 'id'], width: 80, kind: 'number' },
      { key: 'group', title: '群聊', path: ['groupName'], width: 180, ellipsis: true, kind: 'text' },
      { key: 'member', title: '成员', path: ['agentName'], width: 180, ellipsis: true, kind: 'agent' },
      TIME_COL
    ],
    filters: [
      { key: 'groupAgentId', placeholder: '群聊智能体 ID', kind: 'number' },
      { key: 'memberAgentId', placeholder: '成员智能体 ID', kind: 'number' }
    ]
  },
  versions: {
    type: 'versions',
    label: '智能体版本',
    columns: [
      { key: 'id', title: 'ID', path: ['data', 'id'], width: 80, kind: 'number' },
      AGENT_COL,
      { key: 'version', title: '版本', path: ['data', 'version'], width: 100, kind: 'text' },
      { key: 'name', title: '名称快照', path: ['data', 'name'], width: 170, ellipsis: true, kind: 'text' },
      { key: 'rollbackFrom', title: '回滚来源', path: ['data', 'rollbackFrom'], width: 100, kind: 'text' },
      USER_COL,
      TIME_COL
    ],
    filters: [
      { key: 'keyword', placeholder: '版本号', kind: 'keyword', width: 'search' },
      { key: 'agentId', placeholder: '智能体 ID', kind: 'number' },
      { key: 'userId', placeholder: '发布人 ID', kind: 'number' }
    ],
    expand: [
      { title: '描述', path: ['data', 'description'] },
      { title: '系统提示词', path: ['data', 'systemPrompt'], mono: true },
      { title: '资源绑定', path: ['data', 'bindingsJson'], mono: true }
    ]
  },
  'user-configs': {
    type: 'user-configs',
    label: '用户配置',
    columns: [
      { key: 'id', title: 'ID', path: ['data', 'id'], width: 80, kind: 'number' },
      USER_COL,
      AGENT_COL,
      {
        key: 'isPublic',
        title: '广场',
        path: ['data', 'isPublic'],
        width: 90,
        kind: 'dot',
        dotLabels: {
          '1': { label: '已公开', tone: 'success' },
          '0': { label: '未公开', tone: 'warn' }
        }
      },
      TIME_COL
    ],
    filters: [
      { key: 'userId', placeholder: '用户 ID', kind: 'number' },
      { key: 'agentId', placeholder: '智能体 ID', kind: 'number' },
      {
        key: 'status',
        placeholder: '广场',
        kind: 'select',
        options: [
          { label: '已公开', value: 1 },
          { label: '未公开', value: 0 }
        ]
      }
    ],
    expand: [{ title: '人设覆盖', path: ['data', 'systemPrompt'], mono: true }]
  },
  tasks: {
    type: 'tasks',
    label: '后台任务',
    columns: [
      { key: 'taskId', title: '任务 ID', path: ['data', 'taskId'], width: 120, kind: 'text' },
      { key: 'type', title: '类型', path: ['data', 'type'], width: 90, kind: 'text' },
      {
        key: 'status',
        title: '状态',
        path: ['data', 'status'],
        width: 96,
        kind: 'dot',
        dotLabels: TASK_STATUS_DOT
      },
      { key: 'description', title: '描述', path: ['data', 'description'], ellipsis: true, kind: 'text' },
      { key: 'chatId', title: '会话', path: ['data', 'chatId'], width: 140, ellipsis: true, kind: 'code' },
      TIME_COL
    ],
    filters: [
      { key: 'keyword', placeholder: '描述或任务 ID', kind: 'keyword', width: 'search' },
      {
        key: 'type',
        placeholder: '类型',
        kind: 'select',
        options: [
          { label: 'bash', value: 'bash' },
          { label: 'agent', value: 'agent' }
        ]
      },
      {
        key: 'taskStatus',
        placeholder: '状态',
        kind: 'select',
        options: Object.keys(TASK_STATUS_DOT).map(status => ({
          label: TASK_STATUS_DOT[status].label,
          value: status
        }))
      }
    ],
    expand: [
      { title: '输出尾部', path: ['data', 'output'], mono: true },
      { title: '退出码', path: ['data', 'exitCode'] },
      { title: '结束时间戳', path: ['data', 'finishedAt'] }
    ]
  }
}

export const RUNTIME_TYPE_ORDER: AdminRuntimeType[] = ['group-members', 'versions', 'user-configs', 'tasks']
