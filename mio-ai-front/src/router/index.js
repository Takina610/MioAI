import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/home/index.vue'),
    meta: { title: '首页', requiresAuth: false }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '总览', requiresAuth: true },
    redirect: '/dashboard/agent-market',
    children: [
      {
        path: 'agent-market',
        name: 'AgentMarket',
        component: () => import('@/views/dashboard/AgentMarket.vue'),
        meta: { title: '应用广场', requiresAuth: true }
      },
      {
        path: 'agents',
        name: 'AgentManage',
        component: () => import('@/views/dashboard/AgentManage.vue'),
        meta: { title: '应用管理', requiresAuth: true }
      },
      {
        path: 'mcp-market',
        name: 'McpMarket',
        component: () => import('@/views/dashboard/McpMarket.vue'),
        meta: { title: 'MCP广场', requiresAuth: true }
      },
      {
        path: 'mcp',
        name: 'McpManage',
        component: () => import('@/views/dashboard/McpManage.vue'),
        meta: { title: 'MCP管理', requiresAuth: true }
      },
      {
        path: 'public-knowledge',
        name: 'PublicKnowledge',
        component: () => import('@/views/dashboard/PublicKnowledge.vue'),
        meta: { title: '公共知识库', requiresAuth: true }
      },
      {
        path: 'knowledge',
        name: 'KnowledgeManage',
        component: () => import('@/views/dashboard/KnowledgeManage.vue'),
        meta: { title: '知识库管理', requiresAuth: true }
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

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - MioAI` : 'MioAI'
  
  const token = localStorage.getItem('token')
  
  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.name === 'Login' && token) {
    next({ name: 'Dashboard' })
  } else {
    next()
  }
})

export default router
