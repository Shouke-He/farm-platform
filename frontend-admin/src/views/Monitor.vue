<!-- 环境监控：设备增删改 + 全平台告警 -->
<template>
  <div>
    <!-- 管理端按用户筛选设备（个人用户无筛选，只看自己的设备） -->
    <div v-if="isAdmin" class="filter-bar">
      <span class="filter-lbl">设备所属用户：</span>
      <el-select v-model="filterUser" clearable placeholder="全部用户" style="width: 220px" @change="loadDevices">
        <el-option v-for="u in users" :key="u.id" :label="u.realName + '（' + u.username + '）'" :value="u.id" />
      </el-select>
      <!-- 运营手动触发一次天气预警扫描：平时由 WeatherWarnScheduler 每 2 小时自动跑一轮 -->
      <el-button size="small" type="primary" plain :loading="scanning" style="margin-left: 16px" @click="scanWeather">
        刷新天气扫描
      </el-button>
      <!-- 运营手动触发一次种植建议扫描：平时由 PlantAdviceEngine 每 5 分钟自动跑一轮 -->
      <el-button size="small" type="primary" plain :loading="advising" @click="scanAdvice">
        刷新种植建议
      </el-button>
    </div>

    <!-- 顶部统计 -->
    <el-row :gutter="16">
      <el-col :span="8" v-for="c in cards" :key="c.label">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="num" :style="{ color: c.color }">{{ c.value }}</div>
            <div class="label">{{ c.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 设备卡片网格 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8" v-for="d in devices" :key="d.id">
        <el-card shadow="hover" class="dev-card" :class="{ active: selected?.id === d.id }" @click="select(d)">
          <div class="dev-head">
            <span class="dot" :class="d.status"></span>
            <span class="dev-name">{{ d.deviceName }}</span>
            <el-tag size="small" :type="d.status === 'online' ? 'success' : 'info'">
              {{ d.status === 'online' ? '在线' : '离线' }}
            </el-tag>
            <!-- 归属标签：用户设备（user_id 非空）标出来，平台设备不标（管理员自己建的，是默认情形） -->
            <el-tag v-if="d.userId" size="small" type="warning" effect="plain">用户设备</el-tag>
            <span class="dev-no">{{ d.deviceNo }}</span>
          </div>
          <div class="dev-metrics">
            <div class="m" v-for="m in metricList" :key="m.key">
              <span v-if="d._checking" class="m-val dots" :style="{ color: m.color }"><i></i><i></i><i></i></span>
              <span v-else-if="d.status !== 'online'" class="m-val off">--</span>
              <span v-else class="m-val" :style="{ color: m.color }">{{ fmt(d.latest?.[m.key], m) }}</span>
              <span class="m-lbl">{{ m.short }}</span>
            </div>
          </div>
          <div class="dev-foot">
            <span class="dev-time">最后上报 {{ d.lastOnlineTime ? d.lastOnlineTime.slice(5, 16) : '—' }}</span>
            <span class="dev-cd" :class="{ warn: countdown <= 3 }">自动刷新 {{ countdown }}s</span>
            <div class="dev-ops">
              <el-button size="small" type="primary" link :loading="d._checking" @click.stop="checkDev(d)">刷新</el-button>
              <!-- 编辑/删除只对「平台设备」（user_id 为空）开放：用户自己的设备由设备主人在用户端维护。 -->
              <el-tooltip :disabled="!d.userId" content="用户设备，请由设备主人自行修改" placement="top">
                <span>
                  <el-button size="small" type="primary" link :disabled="!!d.userId" @click.stop="openDialog(d)">编辑</el-button>
                </span>
              </el-tooltip>
              <el-tooltip :disabled="!d.userId" content="用户设备，请由设备主人自行删除" placement="top">
                <span>
                  <el-button size="small" type="danger" link :disabled="!!d.userId" @click.stop="del(d)">删除</el-button>
                </span>
              </el-tooltip>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="dev-card add-card" @click="openDialog()">
          <el-icon :size="34" color="#9aa59c"><Plus /></el-icon>
          <span>新增设备</span>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实时曲线（全宽） -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="chart-head">
              <span>{{ selected?.deviceName || '请选择设备' }} · 实时趋势（最近 60 分钟，3 秒刷新）</span>
              <el-radio-group v-model="metric" size="small" @change="loadHistory">
                <el-radio-button v-for="m in metricList" :key="m.key" :value="m.key">{{ m.short }}</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div v-if="selected" class="chart-wrap">
            <div ref="chartEl" style="height: 320px"></div>
            <el-empty v-if="noData" class="chart-empty" description="该设备近 60 分钟暂无上报数据（设备离线或刚开机）" :image-size="70" />
          </div>
          <el-empty v-else :description="isAdmin && filterUser ? '该用户暂无设备，切换其他用户查看' : '点击上方设备卡片，查看实时趋势曲线'" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 设备告警（环境异常）：管理端=全平台告警（管理员+设备主人双可见），个人=自己的设备告警 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>{{ isAdmin ? '设备告警（全平台）' : '设备告警（我的设备）' }} · 传感器数据超限自动告警</template>
          <el-table v-if="alarms.length" :data="alarms" size="small" stripe>
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ row.remindTime?.slice(0, 16) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="alarmTag(row.status)" size="small">{{ alarmName(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <!-- 管理员只查看告警，确认处理由设备主人执行（无法代替用户处理现实农事问题） -->
                <el-button v-if="!isAdmin && row.status === 'pending'" size="small" type="primary" @click="ackAlarm(row)">确认处理</el-button>
                <span v-else-if="row.status === 'done'" class="acked">已处理</span>
                <span v-else class="acked warn">待处理</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无设备告警" :image-size="50" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 设备新增/编辑弹窗：新增走"检测在线设备 + 填表"两个分支； -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑设备' : '新增设备'" width="480px" append-to-body>
      <div v-if="!form.id" class="discover-box">
        <div class="disc-head">
          <span>📡 检测在线设备</span>
          <el-button size="small" type="primary" link :loading="discovering" @click="loadDiscover">刷新检测</el-button>
        </div>
        <div class="disc-tip">最近 5 分钟上报过数据的真实硬件（新烧录的 ESP32 通电后自动出现在这里）</div>
        <div v-if="!found.length" class="disc-empty">未检测到设备 —— 确认设备已通电联网，等待其上报</div>
        <div v-for="f in found" :key="f.deviceNo" class="disc-item">
          <span class="disc-no">{{ f.deviceNo }}</span>
          <span class="disc-time">最后上报 {{ f.lastSeen?.slice(5, 19) }}</span>
          <el-tag v-if="f.mine" size="small" type="success">我的设备</el-tag>
          <el-button v-else-if="f.registered && f.claimable" size="small" type="primary" plain @click="claimDevice(f)">绑定</el-button>
          <el-tag v-else-if="f.registered" size="small" type="info">已在平台</el-tag>
          <el-button v-else size="small" type="primary" plain @click="pickDevice(f)">添加</el-button>
        </div>
        <el-divider />
      </div>
      <el-form :model="form" label-width="90px">
        <el-form-item label="设备编号" required>
          <el-input v-model="form.deviceNo" :disabled="!!form.id" placeholder="如 SN-006" />
        </el-form-item>
        <el-form-item label="设备名称" required>
          <el-input v-model="form.deviceName" placeholder="如 水田6号土壤站" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
// 环境监控（管理端首页）：实时看全平台传感器设备的状态与数据，是管理员最常停留的页面。
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import http from '../api'
import { useUserStore } from '../stores/user'

// 六项传感器指标的定义表：key 是后端字段名，short 是界面显示名，unit 单位，color 曲线/数值颜色
const metricList = [
  { key: 'soil_humidity', short: '土壤湿度', unit: '%', color: '#3f7d4c' },
  { key: 'soil_temp', short: '土壤温度', unit: '℃', color: '#52ae5e' },
  { key: 'air_temp', short: '气温', unit: '℃', color: '#ff6b6b' },
  { key: 'air_humidity', short: '空气湿度', unit: '%', color: '#f5a623' },
  { key: 'light', short: '光照', unit: 'lux', color: '#6f95a3' },
  { key: 'rssi', short: '信号强度', unit: 'dBm', color: '#979e8c' },
]

const userStore = useUserStore()
/** 管理员：全平台设备 + 按用户筛选 + 全平台告警；个人用户：只看自己的设备与告警 */
const isAdmin = computed(() => userStore.user?.roleCode === 'ADMIN')
/** 管理端按用户筛选：用户下拉选项（种植用户）+ 当前筛选的用户 id（空=全部） */
// users：用户下拉的选项数据（只装 roleId=2 的种植用户）
const users = ref<any[]>([])
// filterUser：当前选中的用户 id，空值代表看全部用户的设备；改它只重新拉设备列表，不重连其他东西
const filterUser = ref<number | null>(null)

// 手动触发一次天气预警扫描（POST /weather/scan，仅管理员可调）：天气预警平时由后端每 2 小时自动扫一轮，
// 这个按钮是运营想立刻跑一遍时的入口（比如刚收到暴雨消息），命中规则的会写入提醒表并推送给对应城市的用户
const scanning = ref(false)
async function scanWeather() {
  scanning.value = true
  try {
    await http.post('/weather/scan')
    ElMessage.success('已触发一次天气预警扫描：命中规则的会写入提醒并推送给对应城市的用户')
  } finally {
    scanning.value = false
  }
}

// 手动触发一次种植建议扫描（POST /plants/advice/scan，仅管理员可调）：接口返回本次变动的条数。
// 平时每 5 分钟自动扫一轮，新添加的植物最多要等 5 分钟才出建议 —— 这个按钮就是"立刻出结果"的入口
const advising = ref(false)
async function scanAdvice() {
  advising.value = true
  try {
    const n = await http.post('/plants/advice/scan')
    ElMessage.success(n > 0 ? '本次扫描有 ' + n + ' 条建议变动（新增或自动取消）' : '本次扫描暂无需变动的建议')
  } finally {
    advising.value = false
  }
}

// 当前显示在网格里的设备列表（每项含 status 在线状态、latest 最新各项指标）
const devices = ref<any[]>([])
// 当前选中的设备（点卡片选中，下方曲线图画它的数据）
const selected = ref<any>(null)
// 曲线图当前展示哪个指标（默认土壤湿度），切换时重新拉该指标的历史数据
const metric = ref('soil_humidity')
// 该设备近 60 分钟有没有数据（没有就盖一层空态提示）
const noData = ref(false)
// 新增/编辑设备弹窗开关
const dialog = ref(false)
// 设备表单（有 id 表示编辑）
const form = reactive<any>({})
// 曲线图的容器 DOM（echarts 要靠它初始化）
const chartEl = ref<HTMLElement>()
// 曲线图实例（只保留一个，切设备/切指标时复用并 setOption）
let chart: echarts.ECharts | null = null
// 自动刷新的定时器句柄（每秒跑一次 tick），离开页面时清掉
let timer: any = null
/** 自动刷新倒计时（秒）：每秒 -1，归零触发 refresh 并重置 3 */
const countdown = ref(3)
/** 设备告警列表（type=sensor：管理端全平台，个人用户自己的设备告警） */
const alarms = ref<any[]>([])

// 顶部三张统计卡：在线设备数、离线设备数、设备总数（都由 devices 现算，不用额外请求）
const cards = computed(() => [
  { label: '在线设备', value: devices.value.filter((d) => d.status === 'online').length, color: '#52ae5e' },
  { label: '离线设备', value: devices.value.filter((d) => d.status !== 'online').length, color: '#979e8c' },
  { label: '设备总数', value: devices.value.length, color: '#3f7d4c' },
])

// 把指标数值格式化成带单位的样子：光照和信号强度取整，其余保留一位小数；没值显示破折号
function fmt(v: any, m: any) {
  if (v === undefined || v === null) return '—'
  const n = Number(v)
  return m.key === 'light' || m.key === 'rssi' ? `${Math.round(n)} ${m.unit}` : `${n.toFixed(1)}${m.unit}`
}

// 拉设备列表（GET /api/devices，管理员可带 userId 只看某人的设备）
async function loadDevices() {
  // 筛选参数写在加载函数内：3 秒自动刷新 tick 会反复调 loadDevices，必须保持当前筛选不丢失
  const params: any = {}
  if (isAdmin.value && filterUser.value) params.userId = filterUser.value
  devices.value = await http.get('/devices', { params })
  // 已选设备被删除/不存在 → 清空；未选中时自动选中第一台，趋势图直接出数据
  if (selected.value && !devices.value.some((d) => d.id === selected.value.id)) {
    selected.value = null
  }
  if (!selected.value && devices.value.length) select(devices.value[0])
}

/** 管理端加载用户下拉选项（仅种植用户） */
// 拉全部账号后只留 roleId=2 的种植用户；非管理员直接不请求
async function loadUsers() {
  if (!isAdmin.value) return
  try {
    const list = await http.get('/auth/users')
    users.value = (list || []).filter((u: any) => u.roleId === 2)
  } catch { /* 用户列表失败不影响监控主流程 */ }
}

/** 加载设备告警（管理端全平台 sensor 告警；个人用户后端按可见范围返回自己的） */
// 只取第 1 页 50 条（最新的告警），type=sensor 表示环境告警这一类提醒
async function loadAlarms() {
  try {
    const d = await http.get('/reminders', { params: { type: 'sensor', page: 1, size: 50 } })
    alarms.value = d.records || []
  } catch { /* 告警加载失败不影响主面板 */ }
}

/** 确认处理告警（sensor 类型不可人工编辑，只做处理闭环） */
// 触发时机：点告警行的「确认处理」按钮（管理端不显示该按钮，由设备主人在用户端处理）
async function ackAlarm(row: any) {
  await http.put(`/reminders/${row.id}/status`, null, { params: { status: 'done' } })
  ElMessage.success('已确认处理')
  loadAlarms()
}

// 告警状态编码 → 标签颜色 / 中文名（表格里那列状态标签用）
function alarmTag(s: string) {
  return ({ pending: 'warning', done: 'success', cancelled: 'info' } as any)[s] || 'info'
}
function alarmName(s: string) {
  return ({ pending: '待处理', done: '已处理', cancelled: '已取消' } as any)[s] || s
}

// 拉选中设备某个指标最近 60 分钟的历史点，画成折线图
async function loadHistory() {
  if (!selected.value) return
  const list = await http.get(`/devices/${selected.value.id}/history`, {
    params: { metric: metric.value, minutes: 60 },
  })
  noData.value = !list.length
  const m = metricList.find((x) => x.key === metric.value)!
  if (!chartEl.value) return
  // 曲线容器由 v-if="selected" 控制：切换用户筛选会清空选中 → 容器从 DOM 移除再重建，
  if (chart && !chartEl.value.contains(chart.getDom())) {
    chart.dispose()
    chart = null
  }
  chart = chart || echarts.init(chartEl.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: list.map((p: any) => p.collectTime.slice(11, 16)), boundaryGap: false },
    yAxis: { type: 'value', name: m.unit, scale: true },
    series: [{
      name: m.short,
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: list.map((p: any) => Number(p.value)),
      lineStyle: { color: m.color, width: 2 },
      areaStyle: { color: m.color + '22' },
    }],
  })
}

// 选中一台设备：触发时机为点设备卡片（卡片上绑了 @click）
function select(d: any) {
  selected.value = d
  // 等 DOM 挂载出图表容器（v-if 渲染）再拉历史数据
  nextTick(loadHistory)
}

// 「检测在线设备」的结果列表：最近几分钟上报过数据的真实硬件（新设备也在这里被发现）
const found = ref<any[]>([])
// 检测请求的加载态（点「刷新检测」时转圈）
const discovering = ref(false)

/** 打开设备弹窗：不传参 = 新增（顺带检测在线设备），传行数据 = 编辑 */
// 触发时机：点「新增设备」卡片（不传参）、点设备卡片上的「编辑」（传行数据，不触发检测）
function openDialog(d?: any) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, d ? { ...d } : { deviceType: 'sensor' })
  dialog.value = true
  if (!d) loadDiscover()
}

// 检测最近有上报的设备（GET /api/devices/discover），结果填进 found 列表
async function loadDiscover() {
  discovering.value = true
  try {
    found.value = await http.get('/devices/discover')
  } finally {
    discovering.value = false
  }
}

/** 点「添加」：把检测到的设备编号填入表单，改完名称点保存即注册 */
// 触发时机：检测列表里点某台未注册设备的「添加」按钮（只填表单，不发请求）
function pickDevice(f: any) {
  form.deviceNo = f.deviceNo
  form.deviceName = f.deviceNo
  ElMessage.success(`已填入设备编号 ${f.deviceNo}，可修改名称后保存`)
}

/** 个人用户认领：把平台已注册的全局/他人设备绑定到自己名下 */
// 触发时机：检测列表里点「绑定」（POST /api/devices/{id}/claim），成功后重拉检测列表与设备列表
async function claimDevice(f: any) {
  await http.post(`/devices/${f.id}/claim`)
  ElMessage.success(`已将「${f.deviceNo}」绑定到我的设备`)
  loadDiscover()
  loadDevices()
}

// 保存设备：触发时机为弹窗里点「保存」
async function save() {
  if (!form.deviceNo || !form.deviceName) {
    ElMessage.warning('设备编号和名称不能为空')
    return
  }
  if (form.id) await http.put('/devices', form)
  else await http.post('/devices', form)
  ElMessage.success('保存成功')
  dialog.value = false
  loadDevices()
}

// 删除设备：触发时机为点设备卡片右下角的「删除」（已阻止冒泡，不会连带选中卡片；用户设备的按钮已置灰）
async function del(d: any) {
  await ElMessageBox.confirm(`确定删除设备「${d.deviceName}」？`, '提示')
  await http.delete(`/devices/${d.id}`)
  ElMessage.success('已删除')
  if (selected.value?.id === d.id) selected.value = null
  loadDevices()
}

/** 手动刷新：点击立即检测设备在线状态 + 最新各项指标（不等待 60s 轮询） */
// 触发时机：点设备卡片右下角的「刷新」（已阻止冒泡，不会连带选中卡片）
async function checkDev(d: any) {
  d._checking = true
  countdown.value = 3 // 手动刷新后重新计时
  try {
    const r = await http.post(`/devices/${d.id}/refresh`)
    // 数值先缓存到 d.latest，动画保持到 3s 倒计时归零：
    d.status = r.status
    d.lastOnlineTime = r.lastOnlineTime
    d.latest = r.metrics
    if (selected.value?.id === d.id) loadHistory()
    ElMessage.success(`「${r.deviceName}」${r.status === 'online' ? '在线' : '离线'} · 已刷新`)
  } catch {
    d._checking = false // 刷新失败立即结束动画
  }
}

// 整体刷新：重拉设备列表 + 告警列表，若当前选中了设备再刷新它的曲线
async function refresh() {
  await loadDevices()
  loadAlarms() // 告警随自动刷新一起更新
  if (selected.value) loadHistory()
}

/** 每秒一跳：倒计时 -1，归零触发自动刷新并重置（与 refresh 节奏同步；固件 5s 上报，3s 轮询保证数据近实时） */
// 触发时机：onMounted 里用 setInterval 每 1000 毫秒调一次
function tick() {
  countdown.value -= 1
  if (countdown.value <= 0) {
    countdown.value = 3
    refresh()
  }
}

// 页面打开：拉筛选用的用户下拉 → 先整体刷新一次（不等轮询）→ 启动每秒倒计时 → 监听窗口缩放让曲线自适应
onMounted(async () => {
  loadUsers()
  await refresh()
  timer = setInterval(tick, 1000)
  window.addEventListener('resize', () => chart?.resize())
})
// 离开页面：停掉定时器（否则后台一直在轮询接口）并销毁图表实例
onUnmounted(() => {
  clearInterval(timer)
  chart?.dispose()
})
</script>

<style scoped>
/* 管理端按用户筛选栏：浅井槽（Soft UI 无内凹，靠浅底分层） */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding: 11px 14px;
  background: var(--well);
  border-radius: 14px;
  box-shadow: none;
}
.filter-lbl {
  font-size: 13px;
  font-weight: 600;
  color: var(--neu-ink);
}
.acked {
  font-size: 12px;
  color: var(--neu-ink2);
}
.acked.warn {
  color: #f5a623;
}
.stat-card {
  text-align: center;
  padding: 4px 0;
}
.stat-card .num {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-card .label {
  margin-top: 6px;
  color: var(--neu-ink2);
  font-size: 14px;
}
.dev-card {
  cursor: pointer;
  transition: transform 0.3s ease-in-out, box-shadow 0.3s ease-in-out;
}
.dev-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-card-hover); }
/* 选中设备：主绿光环（Halo Focus 语义 = 已选中），白卡底色不变 */
.dev-card.active {
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(63, 125, 76, 0.35), var(--shadow-sm);
}
.dev-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  display: inline-block;
}
.dot.online {
  background: #52ae5e;
  box-shadow: 0 0 0 3px rgba(82,174,94, 0.22);
}
.dot.offline {
  background: #b5beb3;
}
.dev-name {
  font-weight: 600;
  color: var(--neu-ink);
}
.dev-no {
  margin-left: auto;
  font-size: 12px;
  color: var(--neu-ink3);
}
.dev-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 12px;
}
.m {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 9px 0 7px;
  background: var(--well);
  border-radius: 12px;
  box-shadow: none;
}
.m-val {
  font-size: 17px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
/* 离线占位：设备离线时不显示旧数据 */
.m-val.off {
  color: var(--neu-ink3);
  font-weight: 500;
}
/* 刷新动画：三个省略点逐个跳动（animation-delay 错开） */
.dots {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 17px;
}
.dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.15;
  animation: dotJump 0.9s infinite;
}
.dots i:nth-child(2) { animation-delay: 0.3s; }
.dots i:nth-child(3) { animation-delay: 0.6s; }
@keyframes dotJump {
  0%, 100% { opacity: 0.15; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-3px); }
}
.m-lbl {
  margin-top: 3px;
  font-size: 11px;
  color: var(--neu-ink3);
}
.dev-foot {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px;
}
.dev-time {
  font-size: 12px;
  color: var(--neu-ink3);
}
.dev-cd {
  font-size: 12px;
  color: var(--neu-ink2);
}
.dev-cd.warn {
  color: #f5a623;
  font-weight: 600;
}
.dev-ops {
  display: flex;
  align-items: center;
  gap: 4px;
}
/* 新增设备占位：浅井 + 极淡虚线（空置待填语义） */
.add-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 150px;
  color: var(--neu-ink3);
  cursor: pointer;
  background: var(--well);
  border: 1.5px dashed #ccd8c2;
  transition: background-color 0.3s ease-in-out, border-color 0.3s ease-in-out;
}
.add-card:hover { background: var(--well-hover); border-color: #b5c6a5; }
/* 新增设备弹窗：在线设备检测 */
.discover-box { margin-bottom: 4px; }
.disc-head { display: flex; align-items: center; justify-content: space-between; font-weight: 600; color: var(--neu-ink); font-size: 13px; }
.disc-tip { font-size: 12px; color: var(--neu-ink2); margin: 4px 0 8px; line-height: 1.6; }
.disc-empty {
  font-size: 12px;
  color: var(--neu-ink2);
  background: var(--well);
  border-radius: 10px;
  padding: 12px;
  text-align: center;
  box-shadow: none;
}
.disc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  margin-bottom: 4px;
  transition: background-color 0.3s ease-in-out;
}
.disc-item:hover { background: #eef2ec; }
.disc-no { font-weight: 700; color: var(--neu-ink); font-size: 13px; }
.disc-time { font-size: 12px; color: var(--neu-ink3); flex: 1; }
.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--neu-ink);
  font-weight: 600;
}
.chart-wrap {
  position: relative;
}
/* 无数据覆盖：同色系遮罩，避免旧曲线残留（不用纯白） */
.chart-empty {
  position: absolute;
  inset: 0;
  background: var(--neu-bg);
}
</style>
