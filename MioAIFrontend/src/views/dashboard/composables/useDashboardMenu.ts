import { ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ShopOutlined,
  ToolOutlined,
  GlobalOutlined,
  DatabaseOutlined,
  BarChartOutlined
} from '@ant-design/icons-vue'

export interface MenuItem {
  key: string
  label: string
  icon: Component
  path: string
}

export interface MenuGroup {
  title: string
  items: MenuItem[]
}

/** 侧边栏菜单配置：key/label/图标/路由统一在此维护 */
const menuGroups: MenuGroup[] = [
  {
    title: 'MCP',
    items: [
      { key: 'mcp-market', label: 'MCP 广场', icon: ShopOutlined, path: '/dashboard/mcp-market' },
      { key: 'mcp', label: '我的 MCP', icon: ToolOutlined, path: '/dashboard/mcp' }
    ]
  },
  {
    title: '知识库',
    items: [
      { key: 'public-knowledge', label: '公共知识库', icon: GlobalOutlined, path: '/dashboard/public-knowledge' },
      { key: 'knowledge', label: '我的知识库', icon: DatabaseOutlined, path: '/dashboard/knowledge' }
    ]
  },
  {
    title: '记录',
    items: [
      { key: 'usage', label: '使用记录', icon: BarChartOutlined, path: '/dashboard/usage' }
    ]
  }
]

/** 根据当前路由计算激活的菜单 key（支持子路径前缀匹配） */
function resolveActiveKey(path: string): string {
  for (const group of menuGroups) {
    for (const item of group.items) {
      if (path === item.path || path.startsWith(`${item.path}/`)) {
        return item.key
      }
    }
  }
  return ''
}

export function useDashboardMenu() {
  const route = useRoute()
  const router = useRouter()
  const currentKey = ref('')

  watch(
    () => route.path,
    (path) => {
      currentKey.value = resolveActiveKey(path)
    },
    { immediate: true }
  )

  function navigateTo(item: MenuItem): void {
    currentKey.value = item.key
    router.push(item.path)
  }

  return { menuGroups, currentKey, navigateTo }
}
