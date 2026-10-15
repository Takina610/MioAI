import type { MenuGroup } from '@/views/dashboard/composables/useDashboardMenu'
import {
  AppstoreOutlined,
  BookOutlined,
  DashboardOutlined,
  FileTextOutlined,
  GlobalOutlined,
  MessageOutlined,
  RobotOutlined,
  BarChartOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  ApiOutlined
} from '@ant-design/icons-vue'

/** 管理后台侧边栏菜单：复用控制台悬浮胶囊侧边栏的菜单结构 */
export const adminMenuGroups: MenuGroup[] = [
  {
    title: '总览',
    items: [
      { key: 'overview', label: '总览', icon: DashboardOutlined, path: '/admin/overview' }
    ]
  },
  {
    title: '内容管理',
    items: [
      { key: 'user', label: '用户', icon: TeamOutlined, path: '/admin/user' },
      { key: 'agent', label: '智能体', icon: RobotOutlined, path: '/admin/agent' },
      { key: 'knowledge', label: '知识库', icon: BookOutlined, path: '/admin/knowledge' },
      { key: 'mcp', label: 'MCP', icon: ApiOutlined, path: '/admin/mcp' },
      { key: 'skill', label: '技能', icon: ThunderboltOutlined, path: '/admin/skill' }
    ]
  },
  {
    title: '会话数据',
    items: [
      { key: 'conversation', label: '会话', icon: MessageOutlined, path: '/admin/conversation' }
    ]
  },
  {
    title: '统计与日志',
    items: [
      { key: 'usage', label: '使用统计', icon: BarChartOutlined, path: '/admin/usage' },
      { key: 'logs', label: '日志审计', icon: FileTextOutlined, path: '/admin/logs' }
    ]
  },
  {
    title: '运行数据',
    items: [
      { key: 'runtime', label: '运行任务', icon: ToolOutlined, path: '/admin/runtime' },
      { key: 'back-to-dashboard', label: '返回控制台', icon: AppstoreOutlined, path: '/dashboard/agent-market' }
    ]
  }
]
