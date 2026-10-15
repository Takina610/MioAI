/** 管理后台配置驱动表格的列/行类型（日志审计与运行数据共用） */

/** 表格列渲染方式 */
export type ColumnKind = 'user' | 'agent' | 'dot' | 'text' | 'code' | 'number'

export interface LogColumn {
  key: string
  title: string
  /** 取值路径：['data', 'inputTokens'] 或 ['userName'] */
  path?: string[]
  width?: number
  ellipsis?: boolean
  kind?: ColumnKind
  /** dot 渲染：值 → 文案与色调 */
  dotLabels?: Record<string, { label: string; tone: 'success' | 'danger' | 'warn' }>
  format?: 'datetime' | 'duration-ms' | 'duration-s' | 'number'
}

export interface ExpandField {
  title: string
  path: string[]
  mono?: boolean
}

export interface RecordRow {
  data: unknown
  userName?: string | null
  userAvatar?: string | null
  agentName?: string | null
  agentAvatar?: string | null
  groupName?: string | null
}
