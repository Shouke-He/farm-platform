<!-- 完成任务弹窗：农资用量 / 照片 / 备注 -->
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
// 执行记录弹窗（公共组件）：农事任务的「完成登记」和「历史查看」二合一。
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

// 父页面传进来的：modelValue 控制显隐，reminder 是当前操作的那条农事任务
const props = defineProps<{ modelValue: boolean; reminder: any }>()
// 抛给父页面的事件：skip 跳过登记直接完成、recorded 已登记、deleted 已删除（父页面据此刷新列表和统计）
const emit = defineEmits(['update:modelValue', 'skip', 'recorded', 'deleted'])

// 弹窗显隐：内部改它时通过 emit 同步回父页面，实现 v-model 的双向绑定
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
// 当前模式：view = 查看历史记录，new = 新建一条执行登记
const mode = ref<'new' | 'view'>('new')
// 查看模式下这条任务的历史执行记录
const records = ref<any[]>([])
// 「完成并记录」按钮的加载状态
const saving = ref(false)
// 新建模式下的登记表单：执行时间 + 农资用量多行（每行是名称/数量/单位）+ 备注
const form = reactive<any>({ executeTime: '', inputs: [] as any[], note: '' })
// el-upload 的文件列表，上传成功后从每项的 response 里取图片地址
const fileList = ref<any[]>([])

/** 打开时按任务状态决定模式：done → 查看（可补记）；pending → 新建 */
// 每次弹窗打开都会跑一遍：已完成的任务去看历史，未完成的任务直接进登记表单
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

// 重置登记表单：执行时间默认填当前时刻（农户多半是干完活马上记），农资行、备注、照片全部清空
function formInit() {
  form.executeTime = dayjs().format('YYYY-MM-DD HH:mm:ss')
  form.inputs = []
  form.note = ''
  fileList.value = []
}

// 农资用量和照片在库里存的是 JSON 字符串，这里解析成数组渲染；解析失败按空数组处理
function parseArr(s: any): any[] {
  try { return s ? JSON.parse(s) : [] } catch { return [] }
}

// 查看模式下按任务 id 拉历史执行记录（GET /api/executions?reminderId=xxx），失败就显示空列表
async function loadRecords() {
  try {
    records.value = await http.get('/executions', { params: { reminderId: props.reminder.id } })
  } catch { records.value = [] }
}

// 点「补记」：从查看模式切回新建模式并重置表单，给已完成的任务再补一条记录
function toNew() {
  mode.value = 'new'
  formInit()
}

// 点「跳过（直接完成）」：不填记录，关掉弹窗并通知父页面把任务标记为已完成
function skip() {
  visible.value = false
  emit('skip')
}

function onUploadOk() { /* el-upload 已把响应放进 fileList 项的 response */ }

// 点「完成并记录」：整理表单后 POST /api/executions 新增一条执行记录，
async function save() {
  // 照片地址藏在 el-upload 每项的 response.data.url 里，这里挑出真正上传成功的那些
  const photos = fileList.value.map((f: any) => f.response?.data?.url).filter(Boolean)
  // 农资行只保留填了名称的，空行丢弃
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

// 删除某条执行记录：先弹确认框，确认后 DELETE /api/executions/{id}，
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
