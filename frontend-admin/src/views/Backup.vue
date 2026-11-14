<!-- 数据备份（已停用，仅保留页面） -->
<template>
  <el-card shadow="hover">
    <template #header>
      <div class="head">
        数据备份（mysqldump 全量备份，每日 03:00 自动执行，保留 7 天）
        <el-button type="primary" size="small" :loading="running" @click="run">立即备份</el-button>
      </div>
    </template>

    <!-- 提示条：说明备份文件存在哪、怎么恢复 -->
    <el-alert type="info" :closable="false" show-icon
      title="备份文件保存在服务器 backend/backup 目录，可随时用 mysql < 文件名 恢复" style="margin-bottom: 14px" />

    <!-- 备份文件列表：文件名 / 大小 / 生成时间 / 操作 -->
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="name" label="备份文件" min-width="220" />
      <el-table-column label="大小" width="120">
        <template #default="{ row }">{{ formatSize(row.size) }}</template>
      </el-table-column>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatTime(row.time) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="download(row.name)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !list.length" description="暂无备份，点击右上角立即备份" :image-size="80" />
  </el-card>
</template>

<script setup lang="ts">
// 数据备份（管理端页，路由带 meta.disabled，左侧菜单里没有它，只能直接敲 /backup 打开）。
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

// 备份文件清单（表格数据）：每项含 name 文件名、size 字节数、time 生成时间
const list = ref<any[]>([])
// 表格/备份列表的加载态
const loading = ref(false)
// 「立即备份」按钮的加载态（备份比较慢，转圈期间按钮不可重复点）
const running = ref(false)

// 把字节数格式化成好读的大小：小于 1KB 显示 B，小于 1MB 显示 KB，再大显示 MB；没有值显示破折号
function formatSize(bytes: number) {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}
// 把时间戳格式化成「年-月-日 时:分」；p 是补零小工具（不足两位前面补 0）
function formatTime(ms: number) {
  const d = new Date(ms)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

// 拉取备份文件列表；触发时机：页面打开时、手工备份完成后
async function load() {
  loading.value = true
  try {
    list.value = await http.get('/backup/list')
  } finally {
    loading.value = false
  }
}

// 手工备份：触发时机为点右上角「立即备份」按钮
async function run() {
  running.value = true
  try {
    const name = await http.post('/backup/run')
    ElMessage.success(`备份完成：${name}`)
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    running.value = false
  }
}

// 触发时机：点某行操作列的「下载」按钮；方法体内只用 ElMessage.info 提示该备份文件在服务器上的位置
function download(name: string) {
  // 备份文件直接下载需要后端静态映射；简化：提示文件位置
  ElMessage.info(`备份文件 ${name} 在服务器 backend/backup 目录下`)
}

// 页面打开即加载备份清单
onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
