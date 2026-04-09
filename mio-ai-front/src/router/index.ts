import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
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
        meta: { title: '应用管理', requiresAuth: false }
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
        meta: { title: 'MCP管理', requiresAuth: false }
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
        component: () => import('@/views/dashboard/knowledge/publicSimilaritySearch.vue'),
        meta: { title: '命中测试', requiresAuth: false }
      },
      {
        path: 'knowledge',
        name: 'KnowledgeManage',
        component: () => import('@/views/dashboard/knowledge/manage.vue'),
        meta: { title: '知识库管理', requiresAuth: false }
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
        component: () => import('@/views/dashboard/knowledge/similaritySearch.vue'),
        meta: { title: '命中测试', requiresAuth: false }
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

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - MioAI` : 'MioAI'
})

export default router
