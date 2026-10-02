import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { message } from 'ant-design-vue'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    /** 仅管理员可访问（需要在 requiresAuth 基础上使用） */
    requiresAdmin?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/home/index.vue'),
    meta: { title: '首页', requiresAuth: false }
  },
  {
    path: '/chat',
    redirect: '/chat/1'
  },
  {
    path: '/chat/:id',
    name: 'Chat',
    component: () => import('@/views/chat/index.vue'),
    meta: { title: '智能体对话', requiresAuth: false }
  },
  {
    path: '/chat/:id/:conversationId',
    name: 'ChatConversation',
    component: () => import('@/views/chat/index.vue'),
    meta: { title: '智能体对话', requiresAuth: false }
  },
  {
    path: '/share/:agentId/:conversationId',
    name: 'ShareConversation',
    component: () => import('@/views/share/index.vue'),
    meta: { title: '分享对话', requiresAuth: false }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '总览', requiresAuth: false },
    redirect: '/dashboard/agent-market',
    children: [
      {
        path: 'agent-market',
        name: 'AgentMarket',
        component: () => import('@/views/dashboard/agent/market.vue'),
        meta: { title: '应用广场', requiresAuth: false }
      },
      {
        path: 'agent',
        name: 'AgentManage',
        component: () => import('@/views/dashboard/agent/manage.vue'),
        meta: { title: '我的应用', requiresAuth: false }
      },
      {
        path: 'agent/:id',
        name: 'AgentEdit',
        component: () => import('@/views/dashboard/agent/edit.vue'),
        meta: { title: '智能体编辑', requiresAuth: false }
      },
      {
        path: 'mcp-market',
        name: 'McpMarket',
        component: () => import('@/views/dashboard/mcp/market.vue'),
        meta: { title: 'MCP广场', requiresAuth: false }
      },
      {
        path: 'mcp',
        name: 'McpManage',
        component: () => import('@/views/dashboard/mcp/manage.vue'),
        meta: { title: '我的MCP', requiresAuth: false }
      },
      {
        path: 'mcp/:id',
        name: 'McpEdit',
        component: () => import('@/views/dashboard/mcp/edit.vue'),
        meta: { title: 'MCP编辑', requiresAuth: false }
      },
      {
        path: 'mcp-market/:id',
        name: 'McpMarketDetail',
        component: () => import('@/views/dashboard/mcp/detail.vue'),
        meta: { title: 'MCP广场详情', requiresAuth: false }
      },
      {
        path: 'public-knowledge',
        name: 'PublicKnowledge',
        component: () => import('@/views/dashboard/knowledge/public.vue'),
        meta: { title: '公共知识库', requiresAuth: false }
      },
      {
        path: 'public-knowledge/:id',
        name: 'PublicKnowledgeDetail',
        component: () => import('@/views/dashboard/knowledge/detail.vue'),
        meta: { title: '公共知识库详情', requiresAuth: false }
      },
      {
        path: 'public-knowledge/similaritySearch/:id',
        name: 'PublicSimilaritySearch',
        component: () => import('@/views/dashboard/knowledge/similarity-search.vue'),
        meta: { title: '命中测试', requiresAuth: false }
      },
      {
        path: 'knowledge',
        name: 'KnowledgeManage',
        component: () => import('@/views/dashboard/knowledge/manage.vue'),
        meta: { title: '我的知识库', requiresAuth: false }
      },
      {
        path: 'knowledge/:id',
        name: 'KnowledgeEdit',
        component: () => import('@/views/dashboard/knowledge/edit.vue'),
        meta: { title: '知识库编辑', requiresAuth: false }
      },
      {
        path: 'knowledge/similaritySearch/:id',
        name: 'SimilaritySearch',
        component: () => import('@/views/dashboard/knowledge/similarity-search.vue'),
        meta: { title: '命中测试', requiresAuth: false }
      },
      {
        path: 'usage',
        name: 'Usage',
        component: () => import('@/views/dashboard/usage/index.vue'),
        meta: { title: '使用记录', requiresAuth: false }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/dashboard/profile/index.vue'),
        meta: { title: '个人中心', requiresAuth: true }
      }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/layout.vue'),
    meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true },
    redirect: '/admin/user',
    children: [
      {
        path: 'user',
        name: 'AdminUser',
        component: () => import('@/views/admin/user/index.vue'),
        meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'agent',
        name: 'AdminAgent',
        component: () => import('@/views/admin/agent/index.vue'),
        meta: { title: '智能体管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'mcp',
        name: 'AdminMcp',
        component: () => import('@/views/admin/mcp/index.vue'),
        meta: { title: 'MCP 管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'knowledge',
        name: 'AdminKnowledge',
        component: () => import('@/views/admin/knowledge/index.vue'),
        meta: { title: '知识库管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'usage',
        name: 'AdminUsage',
        component: () => import('@/views/admin/usage/index.vue'),
        meta: { title: '使用记录管理', requiresAuth: true, requiresAdmin: true }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权访问' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFoundCatch',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局路由守卫：
 * 1. 设置页面标题；
 * 2. requiresAuth 路由校验登录态（本地 token），未登录跳转首页；
 * 3. requiresAdmin 路由校验管理员角色，非管理员跳转 403。
 * 说明：前端守卫只做体验层的拦截，真正的数据安全由后端接口鉴权保证。
 */
router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - MioAI` : 'MioAI'

  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    message.warning('请先登录')
    return { path: '/', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin) {
    let userInfo: { userRole?: string } | null = null
    try {
      userInfo = JSON.parse(localStorage.getItem('userInfo') || 'null')
    } catch {
      userInfo = null
    }
    if (userInfo?.userRole !== 'admin') {
      message.error('没有权限访问管理后台')
      return { path: '/403' }
    }
  }
  return true
})

export default router
