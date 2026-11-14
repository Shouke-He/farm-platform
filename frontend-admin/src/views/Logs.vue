<!-- 操作日志：按模块/结果筛选，可导出 Excel -->
<template>
  <el-card shadow="hover">
    <template #header>操作日志（AOP 审计，登录/派发/填报等关键操作自动记录）</template>

    <!-- 筛选栏：按模块 / 结果筛，或输关键词搜操作、账号、姓名 -->
    <el-form inline>
      <el-form-item label="模块">
        <el-select v-model="filters.module" clearable placeholder="全部模块" style="width: 130px">
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="filters.status" clearable placeholder="全部" style="width: 110px">
          <el-option label="成功" value="success" />
          <el-option label="失败" value="error" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="filters.keyword" placeholder="操作/账号/姓名" clearable style="width: 180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="primary" :loading="exporting" @click="exportLogs">导出 Excel</el-button>
      </el-form-item>
    </el-form>

    <!-- 日志列表：一行一条操作记录，含操作人、模块、操作、结果、错误信息 -->
    <!-- ⚠️ 每列都带 show-overflow-tooltip（超长省略号 + hover 看全文），为的是**强制单行**： -->
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column prop="username" label="账号" width="100" show-overflow-tooltip />
      <el-table-column prop="realName" label="姓名" width="110" show-overflow-tooltip />
      <el-table-column prop="roleName" label="角色" width="120" show-overflow-tooltip />
      <el-table-column prop="module" label="模块" width="90" show-overflow-tooltip />
      <el-table-column prop="operation" label="操作" min-width="160" show-overflow-tooltip />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
            {{ row.status === 'success' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="错误信息" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.errorMsg || '—' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页：每页 10 条，翻页会重新请求后端 -->
    <el-pagination style="margin-top: 12px" layout="total, prev, pager, next" :total="total"
      :page-size="size" v-model:current-page="page" @current-change="load" />
  </el-card>
</template>

<script setup lang="ts">
// 操作日志（管理端专属页）：查看平台上的操作审计记录 —— 谁、什么时候、在哪个模块、做了什么、成功还是失败。
import { onMounted, reactive, ref } from 'vue'
import http from '../api'

// 当前页的日志列表（表格数据）
const list = ref<any[]>([])
// 表格加载态
const loading = ref(false)
// 「导出 Excel」按钮的加载态
const exporting = ref(false)
// 当前页码（分页组件双向绑定，翻页会重新请求）
const page = ref(1)
// 每页条数（固定 10 条，不是响应式的）
const size = 10
// 记录总条数（分页组件据此算页数）
const total = ref(0)
// 筛选条件：模块 / 结果（success|error）/ 关键词，三者会作为查询参数发给后端
const filters = reactive({ module: '', status: '', keyword: '' })

/** 日志模块全集（与后端 @OpLog 实扫一致，共 10 类） */
// 下拉选项写死在这里，与后端记录日志时填的模块名保持一致
const modules = ['账号', '设备', '品种库', '病虫害库', '盆栽', '提醒', '系统', '天气', '执行记录', '生长记录']

// 按当前页码与筛选条件查询日志；触发时机：页面打开、点「查询」、在关键词框回车、翻页
async function load() {
  loading.value = true
  try {
    const d = await http.get('/logs', {
      params: { page: page.value, size, module: filters.module || undefined, status: filters.status || undefined, keyword: filters.keyword || undefined },
    })
    list.value = d.records
    total.value = d.total
  } finally {
    loading.value = false
  }
}

// 导出日志：触发时机为点「导出 Excel」按钮；筛选条件与列表一致，但不受分页限制（导出当前筛选下的全量）
/** 导出操作日志 CSV（Excel 兼容：后端已加 UTF-8 BOM + 字段转义；导出范围 = 当前筛选全量） */
async function exportLogs() {
  exporting.value = true
  try {
    const csv = await http.get('/logs/export', {
      params: { module: filters.module || undefined, status: filters.status || undefined, keyword: filters.keyword || undefined },
    })
    // 后端返回的是 CSV 文本，这里在浏览器里造一个临时文件并触发下载（不经过服务器文件）
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    // 文件名带上当天日期，如 操作日志_2026-09-26.csv
    a.download = `操作日志_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    // 下载完释放临时地址，避免内存泄漏
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

// 页面打开即按默认条件查一次
onMounted(load)
</script>
