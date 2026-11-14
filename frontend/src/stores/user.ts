// 登录态仓库（Pinia）：整个前端「当前登录的是谁、有没有登录」只从这里读。
import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '../api'
import { connectWs, closeWs } from '../utils/ws'

// 登录用户的信息结构：后端 /auth/login 返回的字段（不含密码）
interface UserInfo {
  id: number
  username: string
  realName: string
  roleCode: string
  roleName: string
  phone?: string
  city?: string
}

export const useUserStore = defineStore('user', () => {
  // 当前登录用户（响应式）。初始化时直接从 localStorage 里读上次存的，
  const user = ref<UserInfo | null>(JSON.parse(localStorage.getItem('user') || 'null'))

  /** 登录；role=登录入口（ADMIN/USER，可选）：undefined 时不携带（注册自动登录沿用） */
  // 前台页面（Login.vue）点「登录」时调用：POST /api/auth/login，
  async function login(username: string, password: string, role?: string) {   // 【交给管家】
    const data = await http.post('/auth/login', { username, password, role })   // 【统一出口】
    applyLogin(data)
  }

  /** 登录/注册成功后统一写入本地并建立实时通道 */
  // 两个入口共用：① 登录成功后；② 注册成功后（Register.vue 直接拿注册返回的登录信息进来，免二次登录）
  function applyLogin(data: any) {   // 【存令牌连ws】
    // token 是后续所有请求的通行证，api/index.ts 的请求拦截器会把它放进请求头
    localStorage.setItem('token', data.token)
    // 后端返回的是 userId 字段，这里映射成前端统一用的 id
    user.value = { id: data.userId, username: data.username, realName: data.realName, roleCode: data.roleCode, roleName: data.roleName }
    localStorage.setItem('user', JSON.stringify(user.value))
    // 登录成功即建立 WebSocket，开始接收后端主动推送的提醒消息
    connectWs()
  }

  // 退出登录：先断开实时连接，再清空内存和本地存储（路由守卫随后就会把人挡回登录页）
  function logout() {
    closeWs()
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  /** 个人中心修改资料后同步内存 + localStorage（保留 roleCode/roleName 等登录态字段） */
  // Profile.vue 保存资料成功后调用，让左上角/顶栏的昵称立刻跟着变
  function updateUser(data: any) {
    user.value = { ...user.value, ...data }
    localStorage.setItem('user', JSON.stringify(user.value))
  }

  // 2026-10-04 瘦身：原来导出一个 isAdmin()，但全项目没有任何页面用它
  return { user, login, applyLogin, logout, updateUser }
})
