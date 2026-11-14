<!-- 农事任务：任务 / 完成记录 / 生长记录 三视图 -->
<template>
  <el-card shadow="hover">
    <template #header>
      <div class="head">
        <span>农事任务（自主设置浇水/施肥等，将显示在首页「今日农事清单」）</span>
        <el-button v-if="viewMode === 'tasks'" type="primary" @click="openDialog()">新建任务</el-button>
      </div>
    </template>

    <!-- ===== 视图切换：农事任务 / 完成记录 / 生长记录 ===== -->
    <div class="view-bar">
      <el-radio-group v-model="viewMode" size="small" @change="onViewMode">
        <el-radio-button value="tasks">📋 农事任务</el-radio-button>
        <el-radio-button value="done">✅ 完成记录</el-radio-button>
        <el-radio-button value="grow">🌱 生长记录</el-radio-button>
      </el-radio-group>
      <template v-if="viewMode !== 'tasks'">
        <el-select v-model="donePlantId" clearable filterable placeholder="按种植物筛选" style="width: 220px" @change="onFilterChange">
          <el-option v-for="p in plants" :key="p.id" :label="(p.speciesEmoji || '🪴') + ' ' + p.name" :value="p.id" />
          <template #empty><span>暂未添加种植物，可先到「我的种植」添加</span></template>
        </el-select>
        <el-button size="small" type="primary" link @click="loadDoneHistory">刷新</el-button>
        <span v-if="viewMode === 'done' ? doneHistory.length : growRecords.length" class="h-count">共 {{ (viewMode === 'done' ? doneHistory : growRecords).length }} 条</span>
      </template>
    </div>

    <!-- ===== 任务管理 ===== -->
    <template v-if="viewMode === 'tasks'">
    <!-- 快捷任务：点击即预填标题，再选时间保存 -->
    <div class="quick">
      <span class="quick-lbl">快捷任务：</span>
      <el-button v-for="t in quickTasks" :key="t" size="small" plain @click="openDialog(undefined, t)">{{ t }}</el-button>
    </div>

    <!-- 提醒处理闭环统计 -->
    <div class="stats" v-if="stats">
      <div class="stat-item">
        <span class="num">{{ stats.total }}</span><span class="lbl">提醒总数</span>
      </div>
      <div class="stat-item">
        <span class="num warn">{{ stats.pending }}</span><span class="lbl">待处理</span>
      </div>
      <div class="stat-item">
        <span class="num ok">{{ stats.done }}</span><span class="lbl">已处理</span>
      </div>
      <div class="stat-item">
        <span class="num rate">{{ stats.rate }}%</span><span class="lbl">处理率</span>
      </div>
      <div class="stat-item type" v-for="(v, k) in stats.byType" :key="k">
        <span class="num">{{ v.done }}/{{ v.total }}</span>
        <span class="lbl">{{ typeName(String(k)) }}</span>
      </div>
      <div class="stat-item" v-if="execStats">
        <span class="num">{{ execStats.total }}</span><span class="lbl">已执行</span>
      </div>
      <div class="stat-item type" v-for="(b, i) in execStats?.byType || []" :key="'e' + i">
        <span class="num">{{ b.count }}</span><span class="lbl">{{ b.type }}</span>
      </div>
    </div>

    <!-- 任务表格：标题/种植物/类型/内容/时间/重复/优先级/状态/操作 -->
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="title" label="提醒标题" min-width="180" />
      <el-table-column label="种植物" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.plantId" size="small" type="success">{{ row.plantEmoji }} {{ row.plantName }}</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.type)">{{ typeName(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="remindTime" label="提醒时间" width="160" />
      <el-table-column label="重复" width="95">
        <template #default="{ row }">{{ repeatName(row) }}</template>
      </el-table-column>
      <el-table-column label="优先级" width="90">
        <template #default="{ row }">
          <el-tag :type="priorityTag(row.priority)" size="small">{{ priorityName(row.priority) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'done' ? 'success' : 'info'" size="small">
            {{ row.status === 'pending' ? '待办' : '已完成' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="270" fixed="right" class-name="op-col">
        <template #default="{ row }">
          <el-button v-if="row.status === 'pending'" size="small" type="primary" @click="mark(row, 'done')">完成</el-button>
          <el-button v-if="row.status === 'done'" size="small" type="primary" plain @click="openExec(row)">记录</el-button>
          <el-button size="small" type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px" layout="total, prev, pager, next" :total="total"
      :page-size="size" v-model:current-page="page" @current-change="load" />
    </template>

    <!-- ===== 完成记录（本人全类型完成痕迹 + 孤儿执行记录，按种植物可筛） ===== -->
    <template v-else-if="viewMode === 'done'">
      <el-empty v-if="!doneHistory.length"
        description="暂无完成记录，完成任务或处理系统提醒后这里会累计" :image-size="60" />
      <DoneRecords v-else :records="doneHistory" show-plant @view="openDone" />
    </template>

    <!-- ===== 生长记录（本人种植物的生长观察，按种植物可筛，可删除） ===== -->
    <template v-else>
      <el-empty v-if="!growRecords.length"
        description="暂无生长记录，登记生长观察后这里会累计" :image-size="60" />
      <DoneRecords v-else :records="growRecords" show-plant @delete="delGrow" />
    </template>

    <!-- 新建 / 编辑提醒弹窗 -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑提醒' : '新建提醒'" width="520px" append-to-body>
      <el-form :model="form" label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="种植物">
          <el-select v-model="form.plantId" clearable placeholder="选择关联的种植物（可选）" style="width: 100%">
            <el-option v-for="p in plants" :key="p.id" :label="(p.speciesEmoji || '🪴') + ' ' + p.name" :value="p.id" />
            <template #empty>
              <el-empty description="暂未添加种植物，可先到「我的种植」添加" :image-size="40" />
            </template>
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="提醒时间" required>
          <el-date-picker v-model="form.remindTime" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="重复">
          <el-select v-model="form.repeatType" style="width: 100%">
            <el-option label="不重复" value="none" />
            <el-option label="每天" value="daily" />
            <el-option label="每周" value="weekly" />
            <el-option label="每月" value="monthly" />
            <el-option label="每年" value="yearly" />
            <el-option label="每隔 N 天" value="interval" />
          </el-select>
        </el-form-item>
        <!-- 每隔 N 天（2026-10-04 新增）：从「提醒时间」那天起算，第 0、N、2N… 天出现在今日清单 -->
        <el-form-item v-if="form.repeatType === 'interval'" label="间隔天数" required>
          <el-input-number v-model="form.intervalDays" :min="2" :max="365" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">从「提醒时间」那天算起，每 N 天一次</span>
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio value="normal">普通</el-radio>
            <el-radio value="important">重要</el-radio>
            <el-radio value="urgent">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <ExecutionDialog v-model="execDialog" :reminder="execReminder"
      @skip="finishFromDialog(false)"
      @recorded="finishFromDialog(true)"
      @deleted="viewMode === 'done' ? loadDoneHistory() : loadExecStats()" />
  </el-card>
</template>

<script setup lang="ts">
// 农事任务：农户自己安排农活的地方，也是回看「干了哪些活」的地方。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'
import ExecutionDialog from '../components/ExecutionDialog.vue'
import DoneRecords from '../components/DoneRecords.vue'

// 当前页的任务列表（只含自己创建的农事任务）
const list = ref<any[]>([])
// 表格加载状态
const loading = ref(false)
// 提醒处理闭环统计：总数 / 待处理 / 已处理 / 处理率 / 各类型明细
const stats = ref<any>(null)
// 任务列表当前页码（与分页组件双向绑定）
const page = ref(1)
// 每页固定显示 10 条
const size = 10
// 任务总条数，分页组件按它算总页数
const total = ref(0)
// 新建 / 编辑提醒弹窗的显隐
const dialog = ref(false)
// 执行记录弹窗的显隐（子组件 ExecutionDialog）
const execDialog = ref(false)
// 当前要办的那条任务，传给执行记录弹窗用
const execReminder = ref<any>(null)
// 执行记录统计：一共执行了多少次、按类型分布
const execStats = ref<any>(null)
// 提醒表单：带 id 表示编辑已有任务，没有 id 表示新建
const form = reactive<any>({})
// 我的植物列表，供「种植物」下拉选择（任务可以挂在某株植物名下）
const plants = ref<any[]>([])
/** 视图切换：农事任务 / 完成记录 / 生长记录 */
const viewMode = ref<'tasks' | 'done' | 'grow'>('tasks')
/** 完成/生长记录筛选：种植物（空=全部） */
const donePlantId = ref<number | null>(null)
/** 完成记录列表：本人全类型完成痕迹 + 孤儿执行记录（时间倒序） */
const doneHistory = ref<any[]>([])
/** 生长记录列表：本人各植物记录合并（时间倒序） */
const growRecords = ref<any[]>([])

/** 我的植物列表（任务关联下拉用，登录即可见） */
async function loadPlants() {
  try {
    plants.value = await http.get('/plants')
  } catch { /* 植物列表失败不影响任务功能 */ }
}

// 任务类型的中文名（后端 type 字段共 7 种取值）
function typeName(t: string) {
  return ({ growth: '生长提醒', solar: '节气', weather: '天气', custom: '农事任务', sensor: '环境告警', plant_advice: '种植建议', plant_pest: '虫害预防' } as any)[t] || t
}
// 重复方式的显示名（interval = 2026-10-04 新增的「每隔 N 天」，天数在 row.intervalDays 里）
function repeatName(r: any) {
  if (r.repeatType === 'interval') return `每 ${r.intervalDays || '?'} 天`
  return ({ none: '不重复', daily: '每天', weekly: '每周', monthly: '每月', yearly: '每年' } as any)[r.repeatType] || r.repeatType || '—'
}
// 任务类型对应的标签颜色
function typeTag(t: string) {
  return ({ growth: 'warning', solar: 'success', weather: 'danger', custom: 'info', sensor: 'danger', plant_advice: 'primary', plant_pest: 'warning' } as any)[t] || 'info'
}
// 优先级的中文名
function priorityName(p: string) {
  return ({ normal: '普通', important: '重要', urgent: '紧急' } as any)[p] || p
}
// 优先级对应的标签颜色
function priorityTag(p: string) {
  return ({ normal: 'info', important: 'warning', urgent: 'danger' } as any)[p] || 'info'
}

// 拉当前页的任务列表：GET /api/reminders?page=&size=&type=custom
async function load() {
  loading.value = true
  try {
    // 任务列表只显示自己设置的农事任务（custom）；
    const params: any = { page: page.value, size, type: 'custom' }
    const d = await http.get('/reminders', { params })
    list.value = d.records
    total.value = d.total
  } finally {
    loading.value = false
  }
}

// 拉任务完成情况统计（GET /api/reminders/stats），统计失败不影响下方列表显示
async function loadStats() {
  try {
    stats.value = await http.get('/reminders/stats')
  } catch {
    /* 统计失败不影响列表 */
  }
}

/** 切到完成记录/生长记录视图时刷新对应列表（ownerId 不传=后端限定本人才可见） */
function onViewMode() {
  if (viewMode.value === 'done') loadDoneHistory()
  else if (viewMode.value === 'grow') loadGrowRecords()
}

/** 植物筛选变化：按当前视图重载 */
function onFilterChange() {
  onViewMode()
}

/** 完成记录：本人全类型完成痕迹 + 孤儿执行记录，按时间倒序（plantId 非空只看该植物） */
async function loadDoneHistory() {
  try {
    const params: any = { limit: 500 }
    if (donePlantId.value) params.plantId = donePlantId.value
    doneHistory.value = await http.get('/reminders/history', { params })
  } catch { /* 历史加载失败不影响页面 */ }
}

/** 生长记录：本人植物逐株拉取合并（GET /plants 即本人植物，plantId 筛选叠加；与管理端同法） */
async function loadGrowRecords() {
  try {
    // 没有筛选就遍历我的全部植物，有筛选就只取选中的那一株
    const targets = donePlantId.value ? plants.value.filter((p: any) => p.id === donePlantId.value) : plants.value
    const list: any[] = []
    // 逐株调 GET /api/plants/{id}/records，把结果打上 kind:'grow' 和植物名后合并到一个数组
    for (const p of targets) {
      const rows: any[] = await http.get(`/plants/${p.id}/records`)
      rows.forEach((r: any) => list.push({ kind: 'grow', time: r.recordTime, plantName: p.name, plantEmoji: p.speciesEmoji, ...r }))
    }
    list.sort((a: any, b: any) => String(b.time || '').localeCompare(String(a.time || '')))
    growRecords.value = list
  } catch { /* 生长记录加载失败不影响页面 */ }
}

/** 删除自己的生长记录（生长记录视图行内「删除」） */
async function delGrow(r: any) {
  await ElMessageBox.confirm('确定删除这条生长记录？', '提示')
  await http.delete(`/records/${r.id}`)
  ElMessage.success('已删除')
  loadGrowRecords()
}

/** 完成记录条目「查看」：回看该任务的执行记录弹窗（孤儿任务无补记入口，仅查看） */
function openDone(h: any) {
  execReminder.value = { id: h.reminderId, title: h.title, status: 'done', type: h.type }
  execDialog.value = true
}

/** 常用农事作业快捷入口 */
const quickTasks = ['💧 浇水', '🌿 施肥', '🧴 打药', '🌾 采收', '🔧 除草', '🪓 松土']

// 打开提醒弹窗：带 row = 编辑（把整行数据填进表单），不带 row = 新建（给上默认值）；
function openDialog(row?: any, quickTitle?: string) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, row ? { ...row } : {
    repeatType: 'none', intervalDays: null, priority: 'normal', status: 'pending', type: 'custom', source: '人工创建',
    title: quickTitle || '',
  })
  dialog.value = true
}

// 点「保存」：标题和提醒时间必填；带 id 走 PUT 更新、否则走 POST 新建；
async function save() {   // 【表单校验】
  if (!form.title || !form.remindTime) {
    ElMessage.warning('标题和提醒时间不能为空')
    return
  }
  if (form.repeatType === 'interval' && !form.intervalDays) {
    ElMessage.warning('选了「每隔 N 天」就要填间隔天数')
    return
  }
  // 不是「每隔 N 天」就把天数清掉，免得旧值留在库里（后端只看 repeatType）
  if (form.repeatType !== 'interval') form.intervalDays = null
  if (form.id) await http.put('/reminders', form)
  else await http.post('/reminders', form)
  ElMessage.success(form.repeatType && form.repeatType !== 'none' ? '已设置重复任务，到日子会出现在「今日农事清单」' : '已设置，将显示在首页「今日农事清单」')
  dialog.value = false
  load()
}

/** 完成 → 打开执行记录弹窗（可跳过）；仅 custom 农事任务弹窗，其余类型直接完成 */
function mark(row: any, status: string) {
  // 自己建的农事任务：先弹执行记录弹窗，让农户登记农资用量和照片；
  if (status === 'done' && row.type === 'custom') {
    execReminder.value = row
    execDialog.value = true
  } else {
    setStatus(row, status)
  }
}
// 改任务状态：PUT /api/reminders/{id}/status?status=xxx，
async function setStatus(row: any, status: string) {
  await http.put(`/reminders/${row.id}/status`, null, { params: { status } })
  ElMessage.success('操作成功')
  load()
  loadStats()
  loadExecStats()
}
/** 弹窗收尾（2026-10-16 修）：判据是**这条提醒自己的状态**，不是当前视图 ——
 *  「待办」视图里对一条**已完成**的任务点「记录」补记时，viewMode 仍是 'pending'，
 *  老写法（viewMode === 'done' ? … : finishTask）会误走 finishTask ⇒ 对已完成任务再发一次 done
 *  ⇒ 后端 400「任务已完成，今日无需重复完成；补充记录请使用「记录」功能」，
 *  于是"执行记录已保存"和这条红字同时弹出来（用户实测，2026-10-16）。 */
function finishFromDialog(recorded: boolean) {
  if (execReminder.value?.status === 'done') {
    // 补记：记录由弹窗自己保存，这里只刷新列表与统计，绝不改状态
    load()
    loadStats()
    loadExecStats()
    return
  }
  finishTask(recorded)
}

/** 弹窗关闭后统一完成 */
async function finishTask(recorded: boolean) {
  const row = execReminder.value
  await http.put(`/reminders/${row.id}/status`, null, { params: { status: 'done' } })
  ElMessage.success(recorded ? '已完成并记录' : '已完成')
  load()
  loadStats()
  loadExecStats()
}
/** 回看执行记录（done 行） */
function openExec(row: any) {
  execReminder.value = row
  execDialog.value = true
}

/** 执行记录统计（个人=自己） */
async function loadExecStats() {
  try {
    execStats.value = await http.get('/executions/stats')
  } catch { /* 统计失败不影响列表 */ }
}

// 删除提醒：先弹确认框，确认后 DELETE /api/reminders/{id}，成功后刷新列表
async function del(row: any) {
  await ElMessageBox.confirm(`确定删除提醒「${row.title}」？`, '提示')
  await http.delete(`/reminders/${row.id}`)
  ElMessage.success('已删除')
  load()
}

// 进入页面一次性拉四份数据：任务列表、任务统计、执行统计、我的植物（下拉用）
onMounted(() => {
  load()
  loadStats()
  loadExecStats()
  loadPlants()
})
</script>

<style scoped>
/* 视图切换 + 完成记录筛选条 */
.view-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.h-count {
  font-size: 12px;
  color: var(--neu-ink3);
}
/* 闭环统计：白胶囊数据条，数字用低饱和语义色 */
.stats {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.stat-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 8px 16px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: var(--shadow-xs);
}
.stat-item .num {
  font-size: 22px;
  font-weight: 700;
  color: var(--neu-ink);
  font-variant-numeric: tabular-nums;
}
.stat-item .num.warn {
  color: #f5a623;
}
.stat-item .num.ok {
  color: #52ae5e;
}
.stat-item .num.rate {
  color: #3f7d4c;
}
.stat-item .lbl {
  font-size: 12px;
  color: var(--neu-ink2);
}
.stat-item.type .num {
  font-size: 16px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
/* 快捷任务条：浅井槽 */
.quick {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding: 11px 14px;
  background: var(--well);
  border-radius: 14px;
  box-shadow: none;
}
.quick-lbl {
  font-size: 13px;
  font-weight: 600;
  color: var(--neu-ink);
}
/* 操作列三个按钮单行排列，禁止换行堆叠 */
:deep(.op-col .cell) {
  white-space: nowrap;
}
</style>
