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
        component: () => import('@/views/dashboard/AgentMarket.vue'),
        meta: { title: '应用广场', requiresAuth: false }
      },
      {
        path: 'agents',
        name: 'AgentManage',
        component: () => import('@/views/dashboard/AgentManage.vue'),
        meta: { title: '应用管理', requiresAuth: false }
      },
      {
        path: 'mcp-market',
        name: 'McpMarket',
        component: () => import('@/views/dashboard/McpMarket.vue'),
        meta: { title: 'MCP广场', requiresAuth: false }
      },
      {
        path: 'mcp',
        name: 'McpManage',
        component: () => import('@/views/dashboard/McpManage.vue'),
        meta: { title: 'MCP管理', requiresAuth: false }
      },
      {
        path: 'public-knowledge',
        name: 'PublicKnowledge',
        component: () => import('@/views/dashboard/PublicKnowledge.vue'),
        meta: { title: '公共知识库', requiresAuth: false }
      },
      {
        path: 'knowledge',
        name: 'KnowledgeManage',
        component: () => import('@/views/dashboard/KnowledgeManage.vue'),
        meta: { title: '知识库管理', requiresAuth: false }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/dashboard/Profile.vue'),
        meta: { title: '个人中心', requiresAuth: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
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
