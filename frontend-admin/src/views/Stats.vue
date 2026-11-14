<!-- 统计看板：提醒处理率/在线率/品种分布/天气 -->
<template>
  <div class="stats-page">
    <!-- 顶部：四个关键数字（提醒总数 / 处理率 / 设备在线率 / 品种数） -->
    <el-row :gutter="16">
      <el-col :span="6"><div class="stat-card"><div class="num">{{ reminder.total }}</div><div class="label">提醒总数</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="num">{{ reminder.rate }}%</div><div class="label">提醒处理率</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="num" :style="{ color: deviceRateColor }">{{ device.rate }}%</div><div class="label">设备在线率</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="num">{{ speciesTotal }}</div><div class="label">种植品种数</div></div></el-col>
    </el-row>

    <!-- 第一排图表：提醒来源分布（柱状） + 设备在线状态（饼图） -->
    <el-row :gutter="16" class="row-gap">
      <el-col :span="12">
        <div class="panel">
          <div class="panel-title">提醒来源分布</div>
          <div ref="remindChartEl" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel">
          <div class="panel-title">设备在线状态</div>
          <div ref="deviceChartEl" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 第二排：品种种植分布（饼图） + 品种明细与健康度（表格） -->
    <el-row :gutter="16" class="row-gap">
      <el-col :span="12">
        <div class="panel">
          <div class="panel-title">品种种植分布</div>
          <div ref="speciesChartEl" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel">
          <div class="panel-title">品种明细与健康度</div>
          <el-table :data="speciesList" size="small">
            <el-table-column prop="emoji" label="" width="50" />
            <el-table-column prop="speciesName" label="品种" />
            <el-table-column prop="count" label="种植数" width="80" />
            <el-table-column label="健康度" width="180">
              <template #default="{ row }">
                <span v-for="(v, k) in healthLabels(row)" :key="k" class="health-chip" :style="{ background: v.color }">
                  {{ v.text }}
                </span>
                <span v-if="!row.health || (!row.health.normal && !row.health.lower && !row.health.upper)" class="muted">暂无记录</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <!-- 第三排：各区域天气预警统计（按城市分组的柱状图，无数据时显示空态） -->
    <el-row :gutter="16" class="row-gap">
      <el-col :span="24">
        <div class="panel">
          <div class="panel-title">各区域天气预警统计（weather 类提醒按城市分组）</div>
          <div v-if="regionStats.length" ref="regionChartEl" class="chart"></div>
          <el-empty v-else description="暂无天气预警数据" :image-size="60" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
// 统计看板（管理端专属页）：用数字卡片 + 四张 ECharts 图表看整个平台的运行情况 ——
import { computed, onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import http from '../api'

// 四个图表容器的 DOM 引用（模板里 ref="remindChartEl" 这种），echarts 需要真实 DOM 才能初始化
const remindChartEl = ref<HTMLElement>()
const deviceChartEl = ref<HTMLElement>()
const speciesChartEl = ref<HTMLElement>()
const regionChartEl = ref<HTMLElement>()
// 已创建的 ECharts 实例（顺序固定：0 提醒来源、1 设备在线、2 品种分布、3 区域预警），复用避免重复初始化
let charts: echarts.ECharts[] = []

// 提醒统计：total 总数、rate 处理率百分比、byType 按提醒类型分组的 { 总数, 已处理 }
const reminder = ref<any>({ total: 0, rate: 0, byType: {} })
// 设备统计：总数 / 在线数 / 离线数 / 在线率百分比
const device = ref<any>({ total: 0, online: 0, offline: 0, rate: 0 })
// 品种种植分布：每个品种种了多少株，附带健康度统计（正常/低于/优于）
const speciesList = ref<any[]>([])
/** 各区域天气预警统计（后端 stats/overview.regionStats：cityName + count） */
const regionStats = ref<any[]>([])

// 提醒类型编码 → 中文名：画「提醒来源分布」柱状图的横轴时用（后端存的类型是这个编码）
const TYPE_NAMES: Record<string, string> = {
  plant_advice: '养护建议', plant_pest: '病虫害预防', sensor: '传感器告警',
  weather: '天气预警', custom: '自定义', growth: '生长阶段', solar: '节气',
}
// 设备在线率的显示颜色：≥80% 绿、≥50% 橙、更低红（数字不好看的时候一眼能看出问题）
const deviceRateColor = computed(() => (device.value.rate >= 80 ? '#52ae5e' : device.value.rate >= 50 ? '#f5a623' : '#ff6b6b'))
// 顶部第四个卡片「种植品种数」= 品种列表长度
const speciesTotal = computed(() => speciesList.value.length)

// 把某品种的健康度统计转成三个彩色小标签（正常/低于/优于）；一条记录都没有时返回空数组
function healthLabels(row: any) {
  const h = row.health || {}
  if (!h.normal && !h.lower && !h.upper) return []
  return [
    { text: `正常 ${h.normal || 0}`, color: 'var(--el-color-success-light-9, #eaf6ec)', key: 'n' },
    { text: `低于 ${h.lower || 0}`, color: 'var(--el-color-warning-light-9, #fef8ea)', key: 'l' },
    { text: `优于 ${h.upper || 0}`, color: 'var(--el-color-primary-light-9, #ecf2ed)', key: 'u' },
  ]
}

// 画第 1 张图「提醒来源分布」：横轴是各种提醒类型，两根柱子分别是总数与已处理
function drawRemind() {
  const byType = reminder.value.byType || {}
  const names = Object.keys(byType)
  charts[0] = charts[0] || echarts.init(remindChartEl.value!)
  charts[0].setOption({
    tooltip: { trigger: 'axis' },
    // 图例固定右上角（容器尺寸变化时默认布局可能落到 X 轴标签区与类别名重叠，2026-08-24 用户反馈）
    legend: { data: ['总数', '已处理'], top: 0, right: 20 },
    grid: { left: 40, right: 20, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: names.map((n) => TYPE_NAMES[n] || n) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '总数', type: 'bar', barWidth: 18, itemStyle: { color: '#3f7d4c', borderRadius: [3, 3, 0, 0] }, data: names.map((n) => byType[n].total) },
      { name: '已处理', type: 'bar', barWidth: 18, itemStyle: { color: '#52ae5e', borderRadius: [3, 3, 0, 0] }, data: names.map((n) => byType[n].done) },
    ],
  })
}

// 画第 2 张图「设备在线状态」：环形饼图，在线绿、离线红
function drawDevice() {
  charts[1] = charts[1] || echarts.init(deviceChartEl.value!)
  charts[1].setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '55%'],
      label: { formatter: '{b}: {c} 台 ({d}%)' },
      data: [
        { name: '在线', value: device.value.online, itemStyle: { color: '#52ae5e' } },
        { name: '离线', value: device.value.offline, itemStyle: { color: '#ff6b6b' } },
      ],
    }],
  })
}

// 画第 3 张图「品种种植分布」：环形饼图，一个品种一块，图例在底部可滚动
function drawSpecies() {
  const list = speciesList.value
  charts[2] = charts[2] || echarts.init(speciesChartEl.value!)
  charts[2].setOption({
    tooltip: { trigger: 'item' },
    legend: { type: 'scroll', bottom: 0 },
    // 显式主题色板（避免回落到 ECharts 默认的旧生态绿/橙系列色）
    color: ['#3f7d4c', '#52ae5e', '#f5a623', '#ff6b6b', '#979e8c', '#6f95a3'],
    series: [{
      type: 'pie', radius: ['35%', '65%'],
      label: { formatter: '{b}: {c} 株' },
      data: list.map((x) => ({ name: x.speciesName, value: x.count })),
    }],
  })
}

// 画第 4 张图「各区域天气预警统计」：横轴是城市名，柱子是预警条数
function drawRegion() {
  charts[3] = charts[3] || echarts.init(regionChartEl.value!)
  charts[3].setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: regionStats.value.map((r) => r.cityName) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '预警条数', type: 'bar', barWidth: 40,
      itemStyle: { color: '#f5a623', borderRadius: [3, 3, 0, 0] },
      data: regionStats.value.map((r) => r.count),
    }],
  })
}

// 浏览器窗口尺寸变化时让所有图表跟着重算大小（否则画布会被拉变形）
function resizeAll() { charts.forEach((c) => c?.resize()) }

// 页面打开时：拉一次统计数据 → 填进上面几个响应式变量 → 稍等 DOM 渲染出来再依次画四张图
onMounted(async () => {
  const d = await http.get('/stats/overview')
  reminder.value = d.reminderStats || {}
  device.value = d.deviceStats || {}
  speciesList.value = d.speciesStats || []
  regionStats.value = d.regionStats || []
  // 延迟 100ms 是为了等 el-row/el-col 布局完成、图表容器有宽高之后再初始化（否则图表尺寸为 0）
  setTimeout(() => { drawRemind(); drawDevice(); drawSpecies(); drawRegion() }, 100)
  window.addEventListener('resize', resizeAll)
})
// 离开页面时收尾：摘掉窗口监听、销毁图表实例（释放 canvas 占用的内存）
onUnmounted(() => {
  window.removeEventListener('resize', resizeAll)
  charts.forEach((c) => c?.dispose())
  charts = []
})
</script>

<style scoped>
/* 顶部统计格：白卡柔影（浅底上的数据小卡） */
.stat-card {
  text-align: center;
  padding: 18px 0;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: var(--shadow-sm);
}
.stat-card .num {
  font-size: 30px;
  font-weight: 700;
  color: var(--neu-ink);
  font-variant-numeric: tabular-nums;
}
.stat-card .label {
  margin-top: 6px;
  color: var(--neu-ink2);
  font-size: 13px;
}
.row-gap { margin-top: 16px; }
/* 统计面板：凸起区块（对应页面级卡片视觉高度），内部不再造凸起小卡 */
.panel {
  background: var(--neu-bg);
  border-radius: 18px;
  box-shadow: var(--shadow-card);
  padding: 14px 16px;
}
.panel-title {
  font-weight: 600;
  color: var(--neu-ink);
  font-size: 14px;
  margin-bottom: 10px;
}
/* 图表槽：浅井容器（ECharts canvas 透明底自然融入） */
.chart {
  height: 300px;
  background: var(--well);
  border-radius: 14px;
  box-shadow: none;
}
/* 健康度标签：同色系低饱和浅底（浅色来自 EP 语义色 token，配色随全局主题联动） */
.health-chip {
  display: inline-block;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  margin-right: 4px;
  color: var(--neu-ink);
}
.muted { font-size: 12px; color: var(--neu-ink3); }
</style>
