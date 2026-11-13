import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 路由表。
 *
 * 【为什么用 hash 模式】
 * history 模式要求服务器把所有未匹配路径回退到 index.html（Nginx try_files），
 * 少配这一行，用户刷新页面就是 404。内部系统部署往往是"丢个 dist 到 Nginx"，
 * 没人会记得改配置。hash 模式在任何静态服务器上都能直接跑。
 *
 * 【meta.manager 的作用】
 * 只是"界面层"的拦截：把不该出现的管理页挡在门口。真正的权限在后端，
 * 前端这套只负责别让用户点了半天才被拒。
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layout/AppLayout.vue'),
      redirect: '/perf',
      children: [
        {
          path: 'perf',
          name: 'perf',
          component: () => import('@/views/PerfView.vue'),
          meta: { title: '我的业绩' },
        },
        {
          path: 'salary',
          name: 'salary',
          component: () => import('@/views/SalaryView.vue'),
          meta: { title: '我的工资' },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/OrderListView.vue'),
          meta: { title: '订单管理' },
        },
        {
          path: 'orders/new',
          name: 'order-create',
          component: () => import('@/views/OrderCreateView.vue'),
          meta: { title: '订单录入', manager: true },
        },
        {
          path: 'team',
          name: 'team',
          component: () => import('@/views/TeamSalaryView.vue'),
          meta: { title: '团队工资', manager: true },
        },
        {
          path: 'receivables',
          name: 'receivables',
          component: () => import('@/views/ReceivablesView.vue'),
          // 客服也要能进：尾款是客服在催，财务只负责到账登记。
          // 用 roles 而不是 manager，因为 manager 只认主管/管理员。
          meta: { title: '收款与催收', roles: ['ADMIN', 'SUPERVISOR', 'FINANCE', 'CS'] },
        },
        {
          path: 'invoices',
          name: 'invoices',
          component: () => import('@/views/InvoiceView.vue'),
          // 客服可以申请开票，但开票/作废按钮由后端鉴权，前端只做可见性
          meta: { title: '开票管理', roles: ['ADMIN', 'SUPERVISOR', 'FINANCE', 'CS'] },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/perf' },
  ],
})

router.beforeEach(async (to) => {
  const store = useUserStore()

  if (to.meta.public) {
    // 已登录还去登录页，直接送回首页，避免"登录成功后又看到登录框"
    return store.token ? { path: '/perf' } : true
  }

  if (!store.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!store.user) {
    try {
      await store.fetchMe()
    } catch {
      store.reset()
      return { path: '/login' }
    }
  }

  if (to.meta.manager && !store.isManager) {
    return { path: '/perf' }
  }

  // 角色白名单：后端仍是最终裁判，这里只避免"点进去才发现 403"
  const allowed = to.meta.roles as string[] | undefined
  if (allowed && !allowed.some((r) => store.roles.includes(r))) {
    return { path: '/perf' }
  }

  return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || ''
  document.title = title ? `${title} · 设计业务管理系统` : '设计业务管理系统'
})

export default router
