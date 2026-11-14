<!-- 执行记录详情弹窗 -->
<template>
  <el-dialog v-model="visible" :title="mode === 'view' ? '执行记录 · ' + props.reminder?.title : '完成「' + props.reminder?.title + '」'"
    width="560px" append-to-body destroy-on-close>
    <!-- 查看模式：历史记录列表 -->
    <template v-if="mode === 'view'">
      <el-empty v-if="!records.length" description="暂无执行记录，可点右下角补记" :image-size="60" />
      <div v-else class="ex-list">
        <div v-for="r in records" :key="r.id" class="ex-item">
          <div class="ex-head">
            <span class="ex-time">🕐 {{ r.executeTime }}</span>
            <el-button size="small" type="danger" link @click="del(r)">删除</el-button>
          </div>
          <div v-if="parseArr(r.inputs).length" class="ex-inputs">
            <span v-for="(i, k) in parseArr(r.inputs)" :key="k" class="ex-chip">{{ i.name }} {{ i.amount }} {{ i.unit }}</span>
          </div>
          <div v-if="parseArr(r.photos).length" class="ex-photos">
            <el-image v-for="(p, k) in parseArr(r.photos)" :key="k" :src="p" :preview-src-list="parseArr(r.photos)"
              fit="cover" class="ex-photo" />
          </div>
          <div v-if="r.note" class="ex-note">{{ r.note }}</div>
        </div>
      </div>
    </template>

    <!-- 新建模式：登记表单 -->
    <template v-else>
      <el-form label-width="80px">
        <el-form-item label="执行时间">
          <el-date-picker v-model="form.executeTime" type="datetime" format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="农资用量">
          <div class="input-rows">
            <div v-for="(row, k) in form.inputs" :key="k" class="input-row">
              <el-input v-model="row.name" placeholder="名称（如复合肥）" style="width: 150px" />
              <el-input-number v-model="row.amount" :min="0" :precision="1" controls-position="right" style="width: 110px" />
              <el-input v-model="row.unit" placeholder="单位" style="width: 70px" />
              <el-button size="small" type="danger" link @click="form.inputs.splice(k, 1)">删</el-button>
            </div>
            <el-button size="small" type="primary" plain @click="form.inputs.push({ name: '', amount: 1, unit: '' })">
              + 添加农资
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="现场照片">
          <el-upload v-model:file-list="fileList" action="/api/upload" list-type="picture-card" :on-success="onUploadOk"
            :before-upload="checkImage" :headers="uploadHeaders" accept="image/*">
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="如：浇透，明天看土壤情况" />
        </el-form-item>
      </el-form>
    </template>

    <!-- 底部操作：按模式分支（具名插槽不能嵌在 v-if/v-else template 内，Vue 3.5 编译器会崩） -->
    <template #footer>
      <template v-if="mode === 'view'">
        <!-- 仅农事任务可补记执行记录（sensor 告警等完成即自动留痕，无需也不能登记农资） -->
        <el-button v-if="props.reminder?.type === 'custom'" type="primary" plain @click="toNew">补记</el-button>
        <el-button type="primary" @click="visible = false">关闭</el-button>
      </template>
      <template v-else>
        <el-button @click="skip">跳过（直接完成）</el-button>
        <el-button type="primary" :loading="saving" @click="save">完成并记录</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
// 执行记录弹窗（管理端复用组件）：一个任务「做完之后到底干了什么」的登记与查看窗口。
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import http from '../api'

/** el-upload 走原生 XHR，不经过 axios 拦截器，需手动带 Authorization 头 */
const uploadHeaders = { Authorization: 'Bearer ' + (localStorage.getItem('token') || '') }

/** el-upload 选完文件的第一道关（before-upload 钩子）：不是图片就直接拦下，返回 false 取消上传。 */
function checkImage(file: File) {
  const IMG_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!IMG_EXT.includes(ext)) {
    ElMessage.error('只支持 jpg / jpeg / png / gif / webp / bmp 格式的图片')
    return false
  }
  return true
}

const props = defineProps<{ modelValue: boolean; reminder: any }>()
const emit = defineEmits(['update:modelValue', 'skip', 'recorded', 'deleted'])

// 弹窗显隐：把父组件的 v-model 转成内部可读可写（关闭时通知父组件）
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
// 当前是「新建」还是「查看」，由打开时任务的状态决定（见下面的 watch）
const mode = ref<'new' | 'view'>('new')
// 查看模式下该任务的执行记录列表
const records = ref<any[]>([])
// 提交按钮的加载态
const saving = ref(false)
// 新建模式的表单：执行时间、农资用量行数组、备注
const form = reactive<any>({ executeTime: '', inputs: [] as any[], note: '' })
// 已上传的照片文件列表（el-upload 绑定的那个）
const fileList = ref<any[]>([])

/** 打开时按任务状态决定模式：done → 查看（可补记）；pending → 新建 */
watch(() => props.modelValue, (v) => {
  if (!v) return
  if (props.reminder?.status === 'done') {
    mode.value = 'view'
    loadRecords()
  } else {
    mode.value = 'new'
    formInit()
  }
})

// 新建模式的表单复位：执行时间默认填当前时刻，其余清空
function formInit() {
  form.executeTime = dayjs().format('YYYY-MM-DD HH:mm:ss')
  form.inputs = []
  form.note = ''
  fileList.value = []
}

// 农资用量、照片这两个字段后端存的是 JSON 字符串，这里解析成数组给模板用；解析失败当作空
function parseArr(s: any): any[] {
  try { return s ? JSON.parse(s) : [] } catch { return [] }
}

// 查该任务已有的执行记录（查看模式用）；失败时置空列表
async function loadRecords() {
  try {
    records.value = await http.get('/executions', { params: { reminderId: props.reminder.id } })
  } catch { records.value = [] }
}

// 从查看模式切到新建模式（点「补记」时调用），顺便把表单清空成默认值
function toNew() {
  mode.value = 'new'
  formInit()
}

// 跳过：不填任何内容直接算完成；关掉弹窗并把 skip 事件抛给父页面（父页面据此刷新列表）
function skip() {
  visible.value = false
  emit('skip')
}

// 照片上传成功的回调：这里故意留空 —— el-upload 已经把后端响应放进了 fileList 每项的 response，提交时再取
function onUploadOk() { /* el-upload 已把响应放进 fileList 项的 response */ }

// 提交执行记录：触发时机为新建模式点「完成并记录」
async function save() {
  const photos = fileList.value.map((f: any) => f.response?.data?.url).filter(Boolean)
  const inputs = form.inputs.filter((i: any) => i.name)
  const hasContent = photos.length || inputs.length || (form.note || '').trim()
  if (!hasContent) { skip(); return } // 空内容视为直接完成
  saving.value = true
  try {
    await http.post('/executions', {
      reminderId: props.reminder.id,
      executeTime: form.executeTime,
      note: (form.note || '').trim() || undefined,
      inputs,
      photos,
    })
    ElMessage.success('执行记录已保存')
    visible.value = false
    emit('recorded')
  } finally {
    saving.value = false
  }
}

// 删除某条执行记录：触发时机为查看模式下点记录右上角的「删除」；确认后 DELETE /api/executions/{id}，
async function del(r: any) {
  await ElMessageBox.confirm('确定删除这条执行记录？', '提示')
  await http.delete(`/executions/${r.id}`)
  ElMessage.success('已删除')
  loadRecords()
  emit('deleted')
}
</script>

<style scoped>
.ex-list { max-height: 340px; overflow-y: auto; padding-right: 2px; }
.ex-item { border-radius: 12px; padding: 10px 12px; transition: background-color 0.3s ease-in-out; }
.ex-item:hover { background: #eef2ec; }
.ex-item + .ex-item { border-top: 1px solid rgba(60, 84, 66, 0.07); }
.ex-head { display: flex; justify-content: space-between; align-items: center; }
.ex-time { font-size: 13px; color: #3f7d4c; font-weight: 600; font-variant-numeric: tabular-nums; }
.ex-inputs { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.ex-chip { font-size: 12px; padding: 2px 9px; background: var(--well); color: var(--neu-ink2); border-radius: 999px; }
.ex-photos { display: flex; gap: 6px; margin-top: 6px; }
.ex-photo { width: 64px; height: 64px; border-radius: 10px; }
.ex-note { margin-top: 6px; font-size: 13px; color: var(--neu-ink2); line-height: 1.6; }
.input-rows { width: 100%; display: flex; flex-direction: column; gap: 8px; }
.input-row { display: flex; align-items: center; gap: 6px; }
</style>
