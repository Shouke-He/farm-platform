<!-- 用户端框架：左侧菜单 + 顶部铃铛 + 问答浮窗 -->
<template>
  <el-container class="layout">
    <!-- 左侧：品牌名 + 导航菜单（用户端只保留个人种植相关功能，界面上没有管理入口） -->
    <el-aside width="214px" class="aside">
      <div class="aside-inner neu-card">
        <div class="brand">
          <el-icon :size="24" color="#3f7d4c"><Sunny /></el-icon>
          <span class="brand-name">农事作业智能提醒与记录平台</span>
        </div>
        <el-menu :default-active="route.path" router class="side-menu">
        <el-menu-item v-for="m in personalMenus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
        </el-menu>
      </div>
    </el-aside>
    <!-- 右侧：顶栏（页面标题 / 消息铃铛 / 用户下拉）+ 内容区 -->
    <el-container>
      <el-header class="header">
        <div class="page-title">{{ route.meta.title }}</div>
        <div class="header-right">
          <el-popover placement="bottom" :width="360" trigger="click" @show="unread = 0">
            <template #reference>
              <el-badge :value="unread" :hidden="!unread" :max="99" class="bell">
                <span class="bell-btn">
                  <el-icon :size="18"><Bell /></el-icon>
                </span>
              </el-badge>
            </template>
            <div class="msg-list">
              <el-empty v-if="!messages.length" description="暂无消息" :image-size="60" />
              <div v-for="(m, i) in messages" :key="i" class="msg-item">
                <div class="msg-title">{{ m.title }}</div>
                <div class="msg-content">{{ m.content }}</div>
                <div class="msg-time">{{ m.time }}</div>
              </div>
            </div>
          </el-popover>
          <el-dropdown @command="onCommand">
          <span class="user">
            <el-icon><UserFilled /></el-icon>
            <span class="user-name">{{ userStore.user?.realName }}（{{ userStore.user?.roleName }}）</span>
            <el-icon class="user-arrow"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
    <!-- 智能助手浮窗：独立组件，不干扰布局；farm.agent.enabled=false 时后端不提供接口，前端点了会提示不可用 -->
    <AgentChat />
  </el-container>
</template>

<script setup lang="ts">
// 整体布局外壳：登录之后的每个页面都套在这层壳里显示。
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { connectWs, onWsMessage } from '../utils/ws'
// 智能助手浮窗：右下角常驻，任何页面都能打开
import AgentChat from '../components/AgentChat.vue'

// route 用来取当前路径（高亮菜单）和当前页标题；router 用来做跳转
const route = useRoute()
const router = useRouter()
// 登录态仓库：顶栏右上角的昵称、角色名从这里取
const userStore = useUserStore()

// 实时消息：弹窗通知 + 未读徽标 + 最近消息列表
const messages = ref<any[]>([])
// unread 是未读条数，显示在铃铛角标上；点开铃铛时清零（全部当作已读）
const unread = ref(0)
// 保存「取消订阅」函数，组件销毁时调用，避免离开页面后还在收消息
let offMessage: (() => void) | null = null

// 页面挂载时做两件事：补连 WebSocket（刷新页面后连接会断，这里重新接上）并订阅推送消息
onMounted(() => {
  // 顺序很重要（2026-10-16 调整）：先挂监听、再连 WS。
  // 后端的「上线补推」是你一连上就推，顺序反了那批消息会赶在订阅之前到达而丢掉。
  offMessage = onWsMessage((msg: any) => {   // 【铃铛弹窗】
    // 每收到一条：插到列表最前、超过 20 条就丢掉最旧的、未读数加一，并弹出通知
    messages.value.unshift(msg)
    if (messages.value.length > 20) messages.value.pop()
    unread.value++
    ElNotification({
      title: msg.title,
      message: msg.content,
      // 按消息类型决定通知的配色（deadline 类用橙色告警样式，其余用绿色成功样式）
      type: msg.type === 'deadline' ? 'warning' : 'success',
      // 6000 毫秒 = 6 秒后自动消失
      duration: 6000,
    })
  })
  // 监听挂好之后再连（刷新页面时补连；登录时已经连过一次，这里会被去重判断挡掉）
  connectWs()
})
// 离开布局（退出登录/关闭标签）时取消消息订阅
onUnmounted(() => offMessage?.())

// 个人用户菜单（种植物养护）
const personalMenus = [
  { path: '/my-plants', title: '我的种植', icon: 'Sunny' },
  { path: '/reminders', title: '农事任务', icon: 'Bell' },
  { path: '/species', title: '品种库', icon: 'Cherry' },
  { path: '/diseases', title: '病虫害防治', icon: 'FirstAidKit' },
  { path: '/monitor', title: '环境监控', icon: 'Monitor' },
]

// 右上角用户下拉菜单被点击时触发：profile 跳个人中心；
function onCommand(cmd: string) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}

/* 左侧导航：透明区 + 菜单白胶囊（Soft UI：激活项白底浮起） */
.aside {
  background: transparent;
  padding: 14px 0 14px 14px;
  overflow: visible;
}
.aside-inner {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.brand {
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(60, 84, 66, 0.08);
}
.brand-name {
  flex: 1;
  min-width: 0;
  white-space: normal;
  line-height: 1.35;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--neu-ink);
}

/* 菜单：透明底 + 白胶囊激活（Soft UI：hover 浅井填充，当前项白底浮起 + 主绿） */
.aside-inner :deep(.el-menu) {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #5c6a60;
  --el-menu-active-color: #3f7d4c;
  --el-menu-hover-text-color: #2f3a33;
  --el-menu-item-height: 44px;
  border-right: none;
  flex: 1;
  padding: 12px 10px;
  overflow-y: auto;
}
.aside-inner :deep(.el-menu-item) {
  border-radius: 14px;
  margin-bottom: 5px;
  transition: box-shadow 0.3s ease-in-out, background-color 0.3s ease-in-out, color 0.3s ease-in-out;
}
.aside-inner :deep(.el-menu-item .el-icon) {
  font-size: 17px;
}
/* hover：浅井填充（无硬影） */
.aside-inner :deep(.el-menu-item:hover) {
  background-color: #eef2ec;
  color: #2f3a33;
}
/* 激活：白胶囊 + 柔和绿调影（浮起 = 当前位置） */
.aside-inner :deep(.el-menu-item.is-active) {
  background-color: #ffffff;
  color: #3f7d4c;
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}
.aside-inner :deep(.el-menu-item.is-active:hover) {
  background-color: #ffffff;
}

/* 顶栏：与页面同底，不设白条（无边框无纯白） */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: transparent;
  padding: 4px 26px 0 28px;
  height: 62px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--neu-ink);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.bell {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: var(--neu-ink2);
}
/* 铃铛：白胶囊，hover 上浮扩散（Cloud Float） */
.bell-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 14px;
  background: #ffffff;
  color: var(--neu-ink2);
  box-shadow: var(--shadow-sm);
  transition: all 0.3s ease-in-out;
}
.bell-btn:hover {
  color: #3f7d4c;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px -4px rgba(63, 125, 76, 0.3);
}
.bell-btn:active {
  transform: scale(0.95);
  box-shadow: var(--press);
  color: #3f7d4c;
}
.user {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  padding: 7px 14px;
  border-radius: 14px;
  color: var(--neu-ink);
  background: #ffffff;
  box-shadow: var(--shadow-sm);
  transition: all 0.3s ease-in-out;
}
.user:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px -4px rgba(63, 125, 76, 0.26);
  color: #3f7d4c;
}
.user:active {
  transform: scale(0.97);
  box-shadow: var(--press);
}
.user-name {
  font-size: 13px;
  font-weight: 500;
  max-width: 180px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.user-arrow {
  font-size: 12px;
}
.msg-list {
  max-height: 380px;
  overflow-y: auto;
}
.msg-item {
  padding: 9px 6px;
  border-bottom: 1px dashed rgba(60, 84, 66, 0.08);
}
.msg-item:last-child {
  border-bottom: none;
}
.msg-title {
  font-weight: 600;
  font-size: 13px;
  color: var(--neu-ink);
}
.msg-content {
  font-size: 12px;
  color: var(--neu-ink2);
  margin-top: 2px;
}
.msg-time {
  font-size: 11px;
  color: var(--neu-ink3);
  margin-top: 2px;
}
.main {
  padding: 12px 24px 22px 16px;
  overflow-y: auto;
  background: transparent;
}
</style>
