<!-- 任务完成记录：全平台只读视图 -->
<template>
  <el-card shadow="hover">
    <template #header>
      <div class="head">
        <span>任务完成记录（管理员视角：按用户/种植物筛选，查看全平台任务完成历史与生长记录，不修改数据）</span>
      </div>
    </template>

    <!-- 筛选：种植用户 → 该用户的种植物（2026-09-04 改造：管理端不再维护农事任务，仅查询完成记录与生长记录） -->
    <div class="h-filter">
      <span class="h-filter-lbl">筛选：</span>
      <el-radio-group v-model="viewMode" size="small" @change="reload">
        <el-radio-button value="done">完成记录</el-radio-button>
        <el-radio-button value="grow">生长记录</el-radio-button>
      </el-radio-group>
      <el-select v-model="filterUserId" clearable filterable placeholder="全部种植用户" style="width: 210px" @change="onUserChange">
        <el-option v-for="u in users" :key="u.id" :label="(u.realName || u.username) + '（' + u.username + '）'" :value="u.id" />
      </el-select>
      <el-select v-model="historyPlantId" clearable placeholder="全部种植物" style="width: 240px" @change="reload">
        <el-option v-for="p in visiblePlants" :key="p.id" :label="(p.speciesEmoji || '🪴') + ' ' + p.name" :value="p.id" />
        <template #empty>
          <span>{{ filterUserId == null ? '暂无种植物' : '该用户暂无种植物' }}</span>
        </template>
      </el-select>
      <el-button size="small" type="primary" link @click="reload">刷新</el-button>
      <span v-if="viewMode === 'done' ? doneHistory.length : growRecords.length" class="h-count">
        共 {{ viewMode === 'done' ? doneHistory.length : growRecords.length }} 条{{ viewMode === 'done' ? '完成记录' : '生长记录' }}
      </span>
    </div>

    <!-- 完成记录视图：一条条历史填报（时间、类型、农资用量、备注、照片），点「查看」能看执行明细 -->
    <template v-if="viewMode === 'done'">
      <el-empty v-if="!doneHistory.length"
        :description="filterUserId == null ? '暂无完成记录，用户完成浇水/施肥等任务后这里会累积' : '该用户暂无完成记录'" :image-size="60" />
      <DoneRecords v-else :records="doneHistory" show-plant @view="openDone" />
    </template>
    <template v-else>
      <el-empty v-if="!growRecords.length"
        :description="filterUserId == null ? '暂无生长记录，用户登记生长观察后这里会累积' : '该用户暂无生长记录'" :image-size="60" />
      <DoneRecords v-else :records="growRecords" show-plant hide-ops />
    </template>

    <!-- 执行记录弹窗：查看某条任务的执行明细，也可为农事任务补记一条 -->
    <ExecutionDialog v-model="execDialog" :reminder="execReminder" @skip="finishTask" @recorded="finishTask" @deleted="loadDoneHistory" />
  </el-card>
</template>

<script setup lang="ts">
// 任务完成记录（管理端专属页，只读）：管理员按「种植用户 → 该用户的种植物」逐级筛选，
import { computed, onMounted, ref } from 'vue'
import http from '../api'
import ExecutionDialog from '../components/ExecutionDialog.vue'
import DoneRecords from '../components/DoneRecords.vue'

/** 种植用户（roleCode=USER）列表：筛选第 1 级 */
const users = ref<any[]>([])
/** 全平台种植物（管理员可见全部，含 userId 供按用户过滤） */
const allPlants = ref<any[]>([])
/** 筛选：用户（空=全部用户） */
const filterUserId = ref<number | null>(null)
/** 筛选：植物（受用户联动，空=该用户全部植物） */
const historyPlantId = ref<number | null>(null)
/** 视图切换：完成记录 / 生长记录 */
const viewMode = ref<'done' | 'grow'>('done')
/** 完成记录（含系统推送与任务已删除的孤儿执行记录） */
const doneHistory = ref<any[]>([])
/** 生长记录（按筛选范围内每株植物记录合并，时间倒序） */
const growRecords = ref<any[]>([])
/** 「查看」执行记录弹窗 */
const execDialog = ref(false)
// 当前要查看的是哪条任务（弹窗按它去查执行记录），形如 { id, title, status, type }
const execReminder = ref<any>(null)

/** 植物下拉：选中用户后仅显示该用户的种植物，未选用户=全平台 */
const visiblePlants = computed(() =>
  filterUserId.value == null ? allPlants.value : allPlants.value.filter((p) => p.userId === filterUserId.value))

/** 种植用户（USER 角色）列表：筛选第 1 级（注意 /auth/users 行无 roleCode 联表字段，需按角色表映射） */
async function loadUsers() {
  try {
    const roles = await http.get('/auth/roles')
    const userRoleId = roles.find((r: any) => r.roleCode === 'USER')?.id ?? 2
    users.value = (await http.get('/auth/users')).filter((u: any) => u.roleId === userRoleId)
  } catch { /* 用户列表失败不影响页面 */ }
}

// 拉全平台种植物列表（后面按用户过滤出下拉选项）；失败不影响页面其他部分
async function loadPlants() {
  try {
    allPlants.value = await http.get('/plants')
  } catch { /* 植物列表失败不影响页面 */ }
}

/** 切换用户：清空植物选择（避免跨用户筛选）并重查 */
function onUserChange() {
  historyPlantId.value = null
  reload()
}

/** 按当前视图重载（完成记录 / 生长记录） */
function reload() {
  if (viewMode.value === 'grow') loadGrowRecords()
  else loadDoneHistory()
}

/** 加载生长记录：拉取筛选范围内每株植物的记录合并（GET /plants/{id}/records，无按用户批量接口） */
async function loadGrowRecords() {
  try {
    const list: any[] = []
    // 植物一级筛选叠加：选中具体植物则只查该植物（与完成记录模式的 plantId 语义一致）
    const plants = historyPlantId.value != null
      ? visiblePlants.value.filter((p) => p.id === historyPlantId.value)
      : visiblePlants.value
    for (const p of plants) {
      const rows: any[] = await http.get(`/plants/${p.id}/records`)
      rows.forEach((r) => list.push({ kind: 'grow', time: r.recordTime, plantName: p.name, plantEmoji: p.speciesEmoji, ...r }))
    }
    list.sort((a, b) => String(b.time).localeCompare(String(a.time)))
    growRecords.value = list
  } catch { /* 生长记录加载失败不影响页面 */ }
}

/** 加载完成记录：选中用户 → 按其任务归属过滤（ownerId）；再选植物叠加 plantId； */
// 一次最多取 200 条（limit 参数），够演示和日常查看用
async function loadDoneHistory() {
  try {
    const params: any = { limit: 200 }
    if (filterUserId.value) params.ownerId = filterUserId.value
    if (historyPlantId.value) params.plantId = historyPlantId.value
    doneHistory.value = await http.get('/reminders/history', { params })
  } catch { /* 历史加载失败不影响页面 */ }
}

/** 查看某条完成任务的完整执行记录（任务仍在，可查看/补记执行记录） */
// 触发时机：点某条记录右侧的「查看」按钮（DoneRecords 组件 emit 的 view 事件）
function openDone(h: any) {
  execReminder.value = { id: h.reminderId, title: h.title, status: 'done', type: h.type }
  execDialog.value = true
}

/** 弹窗内补记/跳过执行记录后刷新列表（只读查询，不修改任务状态） */
// 触发时机：ExecutionDialog 完成或跳过时 emit 的 skip/recorded 事件
function finishTask() {
  loadDoneHistory()
}

// 页面打开：并发拉用户下拉、种植物下拉，并按默认条件（全部/全部）先查一遍完成记录
onMounted(() => {
  loadUsers()
  loadPlants()
  loadDoneHistory()
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
/* 完成记录筛选区 */
.h-filter { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.h-filter-lbl { font-size: 13px; color: var(--neu-ink2); }
.h-count { font-size: 12px; color: var(--neu-ink3); }
</style>
