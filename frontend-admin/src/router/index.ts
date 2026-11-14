// 管理端路由表 + 全局路由守卫（管理端跑在 5174 端口，只服务管理员/平台运营）。
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  // history 模式：地址栏是 /users 这种干净路径（不带 #），刷新由 Vite/后端做 fallback
  history: createWebHistory(),
  routes: [
    // 登录页：唯一不需要登录就能访问的页面
    { path: '/login', component: () => import('../views/Login.vue') },
    // 后台框架页（Layout.vue）：所有业务页面都嵌在它里面，切换子路由时左侧菜单/顶栏不重刷
    {
      path: '/',
      component: () => import('../views/Layout.vue'),
      // 注意：根路径不设 redirect —— 由守卫统一重定向到登录页（VS Code 链接进入先见登录）
      children: [
        // ---- 运营后台（管理端工程内全部为本端功能，不再区分角色） ----
        { path: 'monitor', component: () => import('../views/Monitor.vue'), meta: { title: '环境监控', icon: 'Monitor' } },
        { path: 'reminders', component: () => import('../views/Reminders.vue'), meta: { title: '任务完成记录', icon: 'Bell' } },
        { path: 'species', component: () => import('../views/Species.vue'), meta: { title: '品种库', icon: 'Cherry' } },
        { path: 'diseases', component: () => import('../views/Disease.vue'), meta: { title: '病虫害防治', icon: 'FirstAidKit' } },
        { path: 'users', component: () => import('../views/Users.vue'), meta: { title: '账号管理', icon: 'User' } },
        { path: 'logs', component: () => import('../views/Logs.vue'), meta: { title: '操作日志', icon: 'Document' } },
        { path: 'stats', component: () => import('../views/Stats.vue'), meta: { title: '统计看板', icon: 'DataAnalysis' } },
        { path: 'profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心' } },
        // ---- 文件+路由保留、菜单不出（与 2026-08-23 功能清单一致） ----
        { path: 'backup', component: () => import('../views/Backup.vue'), meta: { title: '数据备份', icon: 'FolderOpened', disabled: true } },
        { path: 'crops', component: () => import('../views/Crops.vue'), meta: { title: '作物管理', icon: 'Cherry', disabled: true } },
      ],
    },
    // catch-all：未匹配（含 /register、/my-plants 等用户端路径）→ 管理端登录页
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})

// 全局前置守卫：每次跳转前都先跑这里，返回字符串即改跳到该地址
router.beforeEach((to) => {   // 【管理端守卫】
  const token = localStorage.getItem('token')
  // ① 根路径（VS Code 链接/浏览器直开）：无条件先到登录页，已有 token 也先见登录
  if (to.path === '/') return '/login'
  // ② 未登录：仅登录页放行，其余一律到登录页
  if (!token && to.path !== '/login') return '/login'
  return true
})

export default router
