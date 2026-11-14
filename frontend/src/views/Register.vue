<!-- 注册页：用户名/密码/所在城市，注册即登录 -->
<template>
  <div class="reg-wrap">
    <div class="reg-card">
      <div class="logo">
        <span class="logo-icon">
          <el-icon :size="26" color="#3f7d4c"><Sunny /></el-icon>
        </span>
        <h2>注册盆栽养护账号</h2>
        <p>注册即成为个人用户，开始智能养护你的植物</p>
      </div>
      <el-form :model="form" @keyup.enter="doRegister">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名（登录用）" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.realName" placeholder="昵称（如：爱种花的阿黄）" size="large" :prefix-icon="Avatar" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码（至少 6 位）" size="large" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.confirm" type="password" placeholder="确认密码" size="large" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.phone" placeholder="手机号（选填）" size="large" :prefix-icon="Iphone" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="form.city" placeholder="所在区域（天气提醒按区域推送）" size="large" style="width: 100%">
            <el-option v-for="c in cities" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
          <div class="city-tip">🌦️ 天气预警当前覆盖南宁、柳州、桂林三市，其他城市暂以南宁数据展示</div>
        </el-form-item>
        <el-button type="primary" size="large" class="reg-btn" :loading="loading" @click="doRegister">
          注 册 并 登 录
        </el-button>
      </el-form>
      <div class="back">
        <el-link type="primary" @click="router.push('/login')">已有账号？返回登录</el-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 注册页：新农户自助开通账号。
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar, Iphone } from '@element-plus/icons-vue'
import http from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
// 登录态仓库：注册成功后调它的 applyLogin() 写入登录信息
const userStore = useUserStore()
// 所在区域（和风城市 ID）：注册时选择，天气提醒按区域过滤
const cities = [
  { id: '101300101', name: '南宁市（默认）' },
  { id: '101300301', name: '柳州市' },
  { id: '101300501', name: '桂林市' },
]
// 注册表单：city 默认南宁（和风天气城市 ID）；
const form = reactive({ username: '', realName: '', password: '', confirm: '', phone: '', city: '101300101' })
// 注册按钮的加载状态
const loading = ref(false)

// 点「注册并登录」：先做三条前端校验（必填项、密码至少 6 位、两次输入一致），
async function doRegister() {
  if (!form.username || !form.realName || !form.password) {   // 【注册前校验】
    ElMessage.warning('请填写用户名、昵称和密码')
    return
  }
  if (form.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  if (form.password !== form.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    // 注册成功返回登录信息，直接复用登录态
    const data = await http.post('/auth/register', {   // 【连城市提交】
      username: form.username, realName: form.realName, password: form.password, phone: form.phone, city: form.city,
    })
    userStore.applyLogin(data)
    ElMessage.success('注册成功，欢迎使用 🪴')
    router.push('/my-plants')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.reg-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: var(--app-bg);
}
.reg-wrap::before,
.reg-wrap::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.reg-wrap::before {
  width: 560px;
  height: 560px;
  top: -220px;
  left: -160px;
  background: #e6eee1;
}
.reg-wrap::after {
  width: 640px;
  height: 640px;
  bottom: -260px;
  right: -200px;
  background: #f1f6ec;
}
.reg-card {
  position: relative;
  z-index: 1;
  width: 440px;
  background: #ffffff;
  border-radius: 24px;
  padding: 34px 40px 24px;
  box-shadow: var(--shadow-pop);
}
.logo {
  text-align: center;
  margin-bottom: 20px;
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
  margin-bottom: 10px;
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
.back {
  text-align: center;
  margin-top: 16px;
}
.reg-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
}
.city-tip {
  font-size: 12px;
  color: var(--neu-ink3);
  margin-top: 4px;
  width: 100%;
  line-height: 1.6;
}
</style>
