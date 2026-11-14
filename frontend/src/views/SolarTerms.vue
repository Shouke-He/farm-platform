<!-- 节气知识库：24 节气农事要点（只读） -->
<template>
  <!-- 节气表：一次性加载全部二十四节气，表格直接铺开展示 -->
  <el-card shadow="hover">
    <template #header>二十四节气知识库（供智能问答向量检索）</template>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="termName" label="节气" width="90" />
      <el-table-column prop="monthDay" label="公历日期" width="90" />
      <el-table-column prop="advice" label="应时农事建议" min-width="300" />
      <el-table-column prop="proverb" label="农谚" min-width="200" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
// 节气知识库：二十四节气的公历日期、应时农事建议和农谚。
import { onMounted, ref } from 'vue'
import http from '../api'

// 节气列表：后端返回的 24 条记录，直接交给表格显示
const list = ref<any[]>([])
// 表格加载中标记，控制 v-loading 的转圈遮罩
const loading = ref(false)

// 进入页面即加载一次；无论成功失败都要关掉 loading，所以放在 finally 里
onMounted(async () => {
  loading.value = true
  try {
    list.value = await http.get('/solar-terms')   // 【前端全量拉】
  } finally {
    loading.value = false
  }
})
</script>
