<!-- 作物管理（已停用，仅保留页面） -->
<template>
  <el-card shadow="hover">
    <template #header>
      <div class="head">
        <span>作物管理（含生长阶段模板，供提醒引擎推算）</span>
        <el-button type="primary" @click="openDialog()">新增作物</el-button>
      </div>
    </template>

    <!-- 作物列表：名称 / 类别 / 生长周期 / 适宜季节 / 备注 / 操作 -->
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" width="100" />
      <el-table-column prop="category" label="类别" width="90" />
      <el-table-column prop="growthDays" label="周期(天)" width="90" />
      <el-table-column prop="season" label="适宜季节" width="90" />
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="openStages(row)">阶段模板</el-button>
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑作物弹窗 -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑作物' : '新增作物'" width="480px" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in ['粮食', '蔬菜', '油料', '水果', '经济作物']" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="生长周期(天)"><el-input-number v-model="form.growthDays" :min="1" /></el-form-item>
        <el-form-item label="适宜季节"><el-input v-model="form.season" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 生长阶段模板弹窗：上方列出该作物已有阶段，下方表单可追加新阶段 -->
    <el-dialog v-model="stageDialog" :title="`${stageCrop?.name} 生长阶段模板`" width="720px" append-to-body>
      <el-table :data="stages" size="small" border>
        <el-table-column prop="stageName" label="阶段" width="110" />
        <el-table-column prop="dayStart" label="起始天" width="80" />
        <el-table-column prop="dayEnd" label="结束天" width="80" />
        <el-table-column prop="advice" label="农事建议" min-width="240" />
        <!-- 操作列：点「删」调用 stages.splice($index, 1)，把该行从当前弹窗列表里移出 -->
        <el-table-column label="操作" width="60">
          <template #default="{ $index }">
            <el-button size="small" type="danger" link @click="stages.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider />
      <el-form inline>
        <el-form-item label="阶段"><el-input v-model="stageForm.stageName" style="width: 110px" /></el-form-item>
        <el-form-item label="起始天"><el-input-number v-model="stageForm.dayStart" :min="1" /></el-form-item>
        <el-form-item label="结束天"><el-input-number v-model="stageForm.dayEnd" :min="1" /></el-form-item>
        <el-form-item label="建议"><el-input v-model="stageForm.advice" style="width: 220px" /></el-form-item>
        <el-button type="primary" @click="addStage">添加</el-button>
      </el-form>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
// 作物管理（管理端页，路由带 meta.disabled，左侧菜单里没有它，只能直接敲 /crops 打开）。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'

// 作物列表（表格数据）
const list = ref<any[]>([])
// 表格加载态
const loading = ref(false)
// 新增/编辑作物弹窗开关
const dialog = ref(false)
// 作物表单（有 id 表示编辑，否则新增）
const form = reactive<any>({})
// 生长阶段模板弹窗开关
const stageDialog = ref(false)
// 当前正在编辑阶段的是哪种作物（弹窗标题显示它的名字）
const stageCrop = ref<any>(null)
// 该作物的阶段列表（弹窗里的小表格数据）
const stages = ref<any[]>([])
// 阶段弹窗底部的「添加」表单（阶段名 / 起始天 / 结束天 / 建议）
const stageForm = reactive<any>({})

// 拉取作物列表；触发时机：页面打开、保存或删除成功后
async function load() {
  loading.value = true
  try {
    list.value = await http.get('/crops')
  } finally {
    loading.value = false
  }
}

// 打开新增/编辑弹窗：先把表单清空，传了 row 就把该行数据拷进表单（编辑），否则是空表单（新增）
function openDialog(row?: any) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, row ? { ...row } : {})
  dialog.value = true
}

// 保存作物：触发时机为弹窗里点「保存」
async function save() {
  if (!form.name) {
    ElMessage.warning('名称不能为空')
    return
  }
  if (form.id) await http.put('/crops', form)
  else await http.post('/crops', form)
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

// 删除作物：触发时机为点某行的「删除」；先弹确认框，确认后 DELETE /api/crops/{id} 再刷新列表
async function del(row: any) {
  await ElMessageBox.confirm(`确定删除作物「${row.name}」？`, '提示')
  await http.delete(`/crops/${row.id}`)
  ElMessage.success('已删除')
  load()
}

// 打开某作物的生长阶段模板弹窗：GET /api/crops/{id}/stages 取阶段列表，同时把底部添加表单清空
async function openStages(row: any) {
  stageCrop.value = row
  stages.value = await http.get(`/crops/${row.id}/stages`)
  Object.keys(stageForm).forEach((k) => delete stageForm[k])
  stageDialog.value = true
}

// 添加一个生长阶段：触发时机为阶段弹窗里点「添加」
async function addStage() {
  if (!stageForm.stageName || !stageForm.dayStart || !stageForm.dayEnd) {
    ElMessage.warning('请填写阶段名称与天数范围')
    return
  }
  const saved = await http.post(`/crops/${stageCrop.value.id}/stages`, stageForm)
  stages.value.push(saved)
  Object.keys(stageForm).forEach((k) => delete stageForm[k])
}

// 页面打开即加载作物列表
onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
