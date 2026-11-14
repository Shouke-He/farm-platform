<!-- 账号管理：增删改 / 重置密码 / 禁用 -->
<template>
  <el-card shadow="hover">
    <template #header>
      <div class="head">
        <span>账号管理（RBAC 多角色权限）</span>
        <el-button type="primary" @click="openDialog()">新增账号</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading" stripe>
      <!-- 登录名/姓名用 min-width：窗口比表格宽时由这两列按比例吸收多余宽度，右侧不留白；
           所有列的最小宽度之和（100+110+130+120+90+170+260 = 980）必须 ≤ 容器宽，否则会被固定列压住 -->
      <el-table-column prop="username" label="登录名" min-width="100" />
      <el-table-column prop="realName" label="姓名" min-width="110" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="roleTag(row.roleId)">{{ roleName(row.roleId) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.roleId === 2" size="small" type="primary" plain @click="openOverview(row)">种植概览</el-button>
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'primary'" @click="toggle(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑账号' : '新增账号'" width="460px" append-to-body>
      <el-form :model="form" label-width="90px">
        <el-form-item label="登录名" required>
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.roleId" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="form.id ? '重置密码' : '初始密码'" required>
          <el-input v-model="form.password" placeholder="留空表示不修改（新增时必填）" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 用户种植概览抽屉：穿透查看该用户的种植物/设备/提醒（仅管理员） -->
    <el-drawer v-model="drawer" :title="ovUser ? ovUser.realName + '（' + ovUser.username + '）· 种植概览' : '种植概览'" size="720px" append-to-body>
      <div v-loading="ovLoading">
        <template v-if="ovData">
          <div class="ov-cards">
            <div class="ov-card"><div class="ov-num">{{ ovData.plants?.length ?? 0 }}</div><div class="ov-lbl">种植物</div></div>
            <div class="ov-card"><div class="ov-num">{{ ovData.devices?.length ?? 0 }}</div><div class="ov-lbl">设备</div></div>
            <div class="ov-card"><div class="ov-num">{{ ovData.tasks?.total ?? 0 }}</div><div class="ov-lbl">提醒总数</div></div>
            <div class="ov-card"><div class="ov-num">{{ ovData.tasks?.rate ?? 0 }}%</div><div class="ov-lbl">处理率</div></div>
          </div>

          <el-divider content-position="left">种植物（{{ ovData.plants?.length ?? 0 }}）</el-divider>
          <el-table :data="ovData.plants || []" size="small" stripe>
            <el-table-column label="植物" min-width="140">
              <template #default="{ row }">{{ row.speciesEmoji || '🪴' }} {{ row.name }}</template>
            </el-table-column>
            <el-table-column label="品种" min-width="110">
              <template #default="{ row }">{{ row.speciesName || '—' }}</template>
            </el-table-column>
            <el-table-column label="种植时间" width="110">
              <template #default="{ row }">{{ row.plantTime ? row.plantTime.slice(0, 10) : '—' }}</template>
            </el-table-column>
            <el-table-column label="当前阶段" width="110">
              <template #default="{ row }">{{ row.stage?.stageName || '—' }}</template>
            </el-table-column>
            <el-table-column label="生长记录" width="90">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="openGrow(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!ovData.plants?.length" description="该用户暂无种植物" :image-size="50" />

          <el-divider content-position="left">设备（{{ ovData.devices?.length ?? 0 }}）</el-divider>
          <el-table :data="ovData.devices || []" size="small" stripe>
            <el-table-column prop="deviceName" label="设备名称" min-width="140" />
            <el-table-column prop="deviceNo" label="编号" width="120" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'online' ? 'success' : 'info'">{{ row.status === 'online' ? '在线' : '离线' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最后上报" min-width="130">
              <template #default="{ row }">{{ row.lastOnlineTime ? row.lastOnlineTime.slice(0, 16) : '—' }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!ovData.devices?.length" description="该用户暂无绑定设备" :image-size="50" />

          <el-divider content-position="left">提醒统计（共 {{ ovData.tasks?.total ?? 0 }} 条，已处理 {{ ovData.tasks?.done ?? 0 }}）</el-divider>
          <div v-if="Object.keys(ovData.tasks?.byType || {}).length" class="ov-types">
            <div v-for="(v, k) in ovData.tasks.byType" :key="k" class="ov-type">
              <span class="ov-type-name">{{ typeName(String(k)) }}</span>
              <span class="ov-type-num">{{ v.done }}/{{ v.total }}</span>
            </div>
          </div>
          <el-empty v-else description="该用户暂无提醒记录" :image-size="50" />
        </template>
      </div>
    </el-drawer>
  </el-card>

  <!-- 某用户某植物的生长记录（只读，含照片；管理端不修改记录） -->
  <el-dialog v-model="growDialog" :title="(growPlant ? growPlant.name : '') + ' · 生长记录'" width="560px" append-to-body>
    <div v-loading="growLoading">
      <el-empty v-if="!growRecords.length" description="暂无生长记录，用户登记生长观察后这里会展示（株高/叶色/长势/照片）" :image-size="60" />
      <DoneRecords v-else :records="growRecords" hide-ops />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
// 账号管理（管理端专属页）：管理员在这里维护平台上的所有账号 —— 新增账号、改姓名/手机号/角色、
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'
import DoneRecords from '../components/DoneRecords.vue'

// 账号列表（表格数据），每行含 username/realName/phone/roleId/status/createTime
const list = ref<any[]>([])
// 角色列表：从后端 /auth/roles 拉，用于表格里的角色名显示和新增/编辑时的角色下拉
const roles = ref<any[]>([])
// 表格加载态
const loading = ref(false)
// 新增/编辑弹窗的显示开关
const dialog = ref(false)
// 弹窗表单数据（有 id 表示编辑，没有表示新增）
const form = reactive<any>({})
/** 种植概览抽屉（管理端穿透查看用户种植物/设备/提醒） */
const drawer = ref(false)
// 抽屉内容加载态
const ovLoading = ref(false)
// 抽屉里显示的概览数据（种植物/设备/提醒统计三块）
const ovData = ref<any>(null)
// 当前正在看的是哪个用户（抽屉标题上显示姓名和账号）
const ovUser = ref<any>(null)

// 触发时机：点列表操作列的「种植概览」按钮（只对种植用户显示）
async function openOverview(row: any) {
  ovUser.value = row
  ovData.value = null
  drawer.value = true
  ovLoading.value = true
  try {
    ovData.value = await http.get(`/auth/users/${row.id}/overview`)
  } catch {
    drawer.value = false
  } finally {
    ovLoading.value = false
  }
}

/** 生长记录弹窗（只读：时间线 + 照片，无删除按钮） */
const growDialog = ref(false)
// 生长记录弹窗加载态
const growLoading = ref(false)
// 该植物的生长记录列表（交给 DoneRecords 组件按时间线渲染）
const growRecords = ref<any[]>([])
// 当前查看的是哪株植物（弹窗标题用）
const growPlant = ref<any>(null)

/** 查看某用户某植物的生长记录（仅管理员，后端 checkPlant 已放行管理角色） */
// 触发时机：种植概览抽屉里点某株植物那行的「查看」按钮；请求 GET /api/plants/{id}/records
async function openGrow(plant: any) {
  growPlant.value = plant
  growRecords.value = []
  growDialog.value = true
  growLoading.value = true
  try {
    const rows: any[] = await http.get(`/plants/${plant.id}/records`)
    growRecords.value = rows.map((r) => ({ kind: 'grow', time: r.recordTime, ...r }))
  } catch {
    growDialog.value = false
  } finally {
    growLoading.value = false
  }
}

// 提醒类型编码 → 中文名（概览抽屉里按类型分组显示「已处理/总数」时用）；未知编码原样显示
function typeName(t: string) {
  return ({ growth: '生长提醒', solar: '节气', weather: '天气', custom: '农事任务', sensor: '环境告警', plant_advice: '种植建议', plant_pest: '虫害预防' } as any)[t] || t
}

// 角色 id → 角色名（查上面拉到的 roles 列表）；查不到就退回显示 id
function roleName(id: number) {
  return roles.value.find((r) => r.id === id)?.roleName || id
}
// 角色 id → 标签颜色（表格里 el-tag 的 type）；未列出的角色用灰色
function roleTag(id: number) {
  return ({ 1: 'danger', 2: 'warning', 3: 'success', 4: 'info' } as any)[id] || 'info'
}

// 拉取账号列表；触发时机：页面打开时、保存成功后、切换启用状态后
async function load() {
  loading.value = true
  try {
    list.value = await http.get('/auth/users')
  } finally {
    loading.value = false
  }
}

// 打开新增/编辑弹窗：传 row 是编辑（先把表单清空再拷一份该行数据，密码框留空表示不改），
function openDialog(row?: any) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, row ? { ...row, password: '' } : { status: 1 })
  dialog.value = true
}

// 保存账号：触发时机为弹窗里点「保存」
async function save() {
  if (!form.username || !form.realName || !form.roleId) {
    ElMessage.warning('请完整填写账号信息')
    return
  }
  if (!form.id && !form.password) {
    ElMessage.warning('新增账号必须设置初始密码')
    return
  }
  if (form.id) {
    await http.put('/auth/users', form)
  } else {
    await http.post('/auth/users', form)
  }
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

// 启用/禁用账号：触发时机为点某行的「禁用」/「启用」按钮
async function toggle(row: any) {
  await http.put('/auth/users', { ...row, status: row.status === 1 ? 0 : 1 })
  ElMessage.success('已切换状态')
  load()
}

// 页面打开时：拉账号列表 + 拉角色列表（角色列表供表格显示角色名、弹窗下拉选择）
onMounted(async () => {
  load()
  roles.value = await http.get('/auth/roles')
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
/* 种植概览统计卡：白卡柔影（抽屉内数据卡） */
.ov-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.ov-card {
  text-align: center;
  padding: 12px 0;
  background: var(--well);
  border-radius: 12px;
  box-shadow: none;
}
.ov-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--neu-ink);
  font-variant-numeric: tabular-nums;
}
.ov-lbl {
  margin-top: 4px;
  font-size: 12px;
  color: var(--neu-ink2);
}
.ov-types {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
/* 提醒分类条：浅井胶囊（不用边框盒子） */
.ov-type {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--well);
  border-radius: 10px;
  box-shadow: none;
}
.ov-type-name {
  font-size: 13px;
  color: var(--neu-ink);
}
.ov-type-num {
  font-size: 13px;
  font-weight: 700;
  color: #52ae5e;
  font-variant-numeric: tabular-nums;
}
</style>
