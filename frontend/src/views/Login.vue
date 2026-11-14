<!-- 用户端登录页：账号密码 + 演示账号快捷登录 -->
<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="logo">
        <span class="logo-icon">
          <el-icon :size="26" color="#3f7d4c"><Sunny /></el-icon>
        </span>
        <h2>农事作业智能提醒与记录平台</h2>
        <p>用户端 · 个人种植养护登录</p>
      </div>
      <el-form :model="form" @keyup.enter="doLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="doLogin">
          登 录
        </el-button>
      </el-form>
      <div class="reg-line">
        还没有账号？
        <el-link type="primary" @click="router.push('/register')">立即注册，上传你的种植物 🌾</el-link>
      </div>
      <div class="tips neu-panel">
        <p class="tips-title">演示账号（密码均为 123456，点击直接登录）：</p>
        <div class="demo-accounts">
          <button v-for="a in demoAccounts" :key="a.username" class="demo-tag" :class="'is-' + a.type"
            type="button" @click="quickLogin(a.username)">
            {{ a.label }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 登录页：用户端的大门。未登录时访问任何页面，路由守卫都会把人送到这里。
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
// 登录态仓库：登录成功后由它负责保存登录信息、建立 WebSocket 连接
const userStore = useUserStore()
// 登录表单：预填了演示账号，方便直接点「登录」体验
const form = reactive({ username: 'worker1', password: '123456' })
// 登录按钮的加载状态，请求期间按钮转圈并禁止重复点击
const loading = ref(false)

// 用户端演示账号一键登录（密码统一 123456）
const demoAccounts = [
  { username: 'worker1', label: 'worker1 用户（茉莉花）', type: 'success' as const },
]

// 点「登录」按钮或在输入框里按回车触发：
async function doLogin() {   // 【前端校验】
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return   // 【两个入口】
  }
  loading.value = true
  try {
    // 用户端登录：后端按入口角色校验（管理员账号会被拒绝并提示去管理端）
    await userStore.login(form.username, form.password, 'USER')   // 【交给管家】   // 【两个入口】
    ElMessage.success('登录成功')
    router.push('/my-plants')   // 【存令牌连ws】
  } finally {
    loading.value = false
  }
}

/** 点击演示账号直接登录 */
function quickLogin(username: string) {
  form.username = username
  form.password = '123456'
  doLogin()
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: var(--app-bg);
  /* 环境光：纯同色系柔和色块（非渐变、无高对比），点缀浅底 */
}
.login-wrap::before,
.login-wrap::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: #f1f5ef;
  pointer-events: none;
}
.login-wrap::before {
  width: 560px;
  height: 560px;
  top: -220px;
  left: -160px;
  background: #e6eee1;
}
.login-wrap::after {
  width: 640px;
  height: 640px;
  bottom: -260px;
  right: -200px;
  background: #f1f6ec;
}

/* 登录面板：纯白卡 + 柔和大影（Soft UI 白卡于浅底） */
.login-card {
  position: relative;
  z-index: 1;
  width: 430px;
  background: #ffffff;
  border-radius: 24px;
  padding: 38px 40px 26px;
  box-shadow: var(--shadow-pop);
}
.logo {
  text-align: center;
  margin-bottom: 26px;
}
.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  border-radius: 18px;
  background: var(--tint);
  box-shadow: none;
  margin-bottom: 12px;
}
.logo h2 {
  margin: 2px 0 6px;
  color: var(--neu-ink);
  font-size: 19px;
  letter-spacing: 0.5px;
}
.logo p {
  color: var(--neu-ink2);
  font-size: 13px;
}
.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 6px;
}
.reg-line {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--neu-ink2);
}
/* 演示账号提示：凹陷信息槽 */
.tips {
  margin-top: 18px;
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 12px;
  color: var(--neu-ink2);
  line-height: 1.8;
}
.tips-title {
  color: var(--neu-ink3);
  font-size: 12px;
}
.demo-accounts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
/* 演示账号按钮：浅染胶囊，hover 上浮，active 轻按缩放（Pillow Press） */
.demo-tag {
  cursor: pointer;
  border: none;
  font-size: 12.5px;
  font-weight: 500;
  padding: 5px 13px;
  border-radius: 999px;
  font-family: inherit;
  transition: all 0.3s ease-in-out;
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-9);
  box-shadow: var(--shadow-xs);
}
.demo-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px -4px rgba(63, 125, 76, 0.28);
  color: var(--el-color-primary);
}
.demo-tag:active {
  transform: scale(0.96);
  box-shadow: none;
}
.demo-tag.is-success {
  color: var(--el-color-success-dark-2);
  background: var(--el-color-success-light-9);
}
.demo-tag.is-success:hover {
  color: var(--el-color-success);
}
</style>
