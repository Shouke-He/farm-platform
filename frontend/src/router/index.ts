// 路由表 + 登录守卫：决定「什么地址显示哪个页面」以及「没登录能不能进」。
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/Login.vue') },
    { path: '/register', component: () => import('../views/Register.vue') },
    {
      path: '/',
      component: () => import('../views/Layout.vue'),
      // 注意：根路径不设 redirect —— 由守卫统一重定向到登录页（VS Code 链接进入先见登录）
      children: [
        // ---- 个人种植/养护（本端全部功能） ----
        { path: 'my-plants', component: () => import('../views/MyPlants.vue'), meta: { title: '我的种植', icon: 'Sunny' } },
        { path: 'species', component: () => import('../views/Species.vue'), meta: { title: '品种库', icon: 'Cherry' } },
        { path: 'diseases', component: () => import('../views/Disease.vue'), meta: { title: '病虫害防治', icon: 'FirstAidKit' } },
        { path: 'monitor', component: () => import('../views/Monitor.vue'), meta: { title: '环境监控', icon: 'Monitor' } },
        { path: 'profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心' } },
        { path: 'reminders', component: () => import('../views/Reminders.vue'), meta: { title: '农事任务', icon: 'Bell' } },
        // ---- 停用入口（文件保留，菜单隐藏，演示可恢复） ----
        { path: 'solar-terms', component: () => import('../views/SolarTerms.vue'), meta: { title: '节气知识库', icon: 'Sunny', disabled: true } },
      ],
    },
    // catch-all：未匹配（含 /admin-login、/users 等管理端路径）→ 用户端登录页
    { path: '/:pathMatch(.*)*', redirect: '/login' },   // 【错地址送登录】
  ],
})

// 全局前置守卫：每次跳转前都会先跑一遍这个函数。
router.beforeEach((to) => {   // 【没登录送回】
  const token = localStorage.getItem('token')
  // ① 根路径（VS Code 链接/浏览器直开）：无条件先到登录页，已有 token 也先见登录
  if (to.path === '/') return '/login'
  // ② 未登录：登录/注册页直接放行，其余一律到登录页
  if (!token) {
    if (to.path === '/login' || to.path === '/register') return true
    return '/login'
  }
  // ③ 已登录访问注册页：送回我的种植
  if (to.path === '/register') return '/my-plants'
  return true
})

export default router
