// 登录态仓库（Pinia）：管理端「当前登录的是谁」全存在这里，顶栏显示姓名/角色、按钮控权限都靠它。
import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '../api'
import { connectWs, closeWs } from '../utils/ws'

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
  // 当前登录用户信息（响应式）：初始值从 localStorage 的 user 副本还原，所以刷新页面不会掉登录态
  const user = ref<UserInfo | null>(JSON.parse(localStorage.getItem('user') || 'null'))

  /** 登录；role=登录入口（ADMIN/USER，可选）：undefined 时不携带（注册自动登录沿用） */
  // 触发时机：Login.vue 点「登 录」或点演示账号时调用；接口 POST /api/auth/login
  async function login(username: string, password: string, role?: string) {
    const data = await http.post('/auth/login', { username, password, role })
    applyLogin(data)
  }

  /** 登录/注册成功后统一写入本地并建立实时通道 */
  // 把后端返回的 token 和用户信息写进内存 + localStorage，并顺手连上 WebSocket（登录后才有 token 可用）
  function applyLogin(data: any) {
    localStorage.setItem('token', data.token)
    user.value = { id: data.userId, username: data.username, realName: data.realName, roleCode: data.roleCode, roleName: data.roleName }
    localStorage.setItem('user', JSON.stringify(user.value))
    connectWs()
  }

  // 退出登录：触发时机为顶栏右上角下拉菜单点「退出登录」（Layout.vue 的 onCommand）
  function logout() {
    closeWs()
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  /** 个人中心修改资料后同步内存 + localStorage（保留 roleCode/roleName 等登录态字段） */
  // 触发时机：Profile.vue 保存资料成功后调用；用展开合并的方式只覆盖传进来的字段（如 realName/phone/city）
  function updateUser(data: any) {
    user.value = { ...user.value, ...data }
    localStorage.setItem('user', JSON.stringify(user.value))
  }

  // 2026-10-04 瘦身：原来导出一个 isAdmin()，但全项目没有任何页面用它

  // 暴露给组件使用（模板里写 userStore.user?.realName 这种）
  return { user, login, applyLogin, logout, updateUser }
})
