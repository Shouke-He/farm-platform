<!-- 管理员个人中心：改资料与密码 -->
<template>
  <div>
    <el-row :gutter="16">
      <!-- 个人信息 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>👤 个人信息</template>
          <el-form :model="form" label-width="90px">
            <el-form-item label="用户名">
              <el-input :model-value="userStore.user?.username" disabled />
              <div class="tip">用户名是登录凭证，不可修改</div>
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="form.realName" placeholder="如：爱种花的阿黄" maxlength="20" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="选填" maxlength="11" />
            </el-form-item>
            <el-form-item label="所在区域">
              <el-select v-model="form.city" placeholder="选择区域" style="width: 100%">
                <el-option v-for="c in cities" :key="c.id" :value="c.id" :label="c.name" />
              </el-select>
              <div class="tip">🌦️ 天气提醒按所在区域推送（南宁/柳州/桂林）</div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 修改密码 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>🔒 修改密码</template>
          <el-form :model="pwd" label-width="90px">
            <el-form-item label="原密码" required>
              <el-input v-model="pwd.oldPassword" type="password" show-password placeholder="输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码" required>
              <el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
            <el-form-item label="确认新密码" required>
              <el-input v-model="pwd.confirm" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" :loading="changing" @click="changePwd">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
// 个人中心（管理端）：当前登录的管理员改自己的资料和密码。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()

// 所在区域（和风城市 ID，与注册页/后端 farm.weather.cities 一致）
const cities = [
  { id: '101300101', name: '南宁市（默认）' },
  { id: '101300301', name: '柳州市' },
  { id: '101300501', name: '桂林市' },
]

// 资料表单：昵称 realName、手机号 phone、所在区域 city（用户名不在这里改）
const form = reactive<any>({})
// 改密码表单：原密码 / 新密码 / 确认新密码（提交后清空）
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
// 「保存资料」按钮的加载态
const saving = ref(false)
// 「修改密码」按钮的加载态
const changing = ref(false)

// 页面打开时先拉一次最新资料
onMounted(async () => {
  // 从服务端拉最新资料（含 city），避免用本地旧数据
  const me = await http.get('/auth/me')
  form.realName = me.realName || userStore.user?.realName || ''
  form.phone = me.phone || ''
  form.city = me.city || '101300101'
})

// 保存资料：触发时机为点「保存资料」按钮
async function saveProfile() {
  if (!form.realName) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    const me = await http.put('/auth/profile', { realName: form.realName, phone: form.phone, city: form.city })
    userStore.updateUser({ realName: me.realName, phone: me.phone, city: me.city })
    ElMessage.success('资料已保存')
  } finally {
    saving.value = false
  }
}

// 修改密码：触发时机为点「修改密码」按钮
async function changePwd() {
  if (!pwd.oldPassword || !pwd.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (pwd.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (pwd.newPassword !== pwd.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changing.value = true
  try {
    await http.put('/auth/password', { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码已修改，下次登录请用新密码')
    pwd.oldPassword = ''
    pwd.newPassword = ''
    pwd.confirm = ''
  } finally {
    changing.value = false
  }
}
</script>

<style scoped>
.tip {
  font-size: 12px;
  color: var(--neu-ink2);
  margin-top: 4px;
  width: 100%;
  line-height: 1.5;
}
</style>
