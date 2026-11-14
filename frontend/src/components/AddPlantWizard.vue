<!-- 添加盆栽四步向导：品种/日期/环境/绑定设备 -->
<template>
  <el-dialog :model-value="modelValue" title="添加种植物" width="640px" append-to-body :close-on-click-modal="false"
             class="wizard-dialog" @update:model-value="$emit('update:modelValue', $event)">
    <!-- 顶部信息卡：品种 + 进度 -->
    <div class="wiz-top">
      <div class="wiz-top-left">
        <span class="wiz-spec">{{ curSpec ? `${curSpec.emoji} ${curSpec.name}` : '选择品种' }}</span>
        <span class="wiz-step">{{ `第 ${step} 步，共 4 步` }}</span>
        <el-progress :percentage="step * 25" :show-text="false" :stroke-width="6" class="wiz-bar" />
      </div>
      <span v-if="curSpec" class="wiz-cat">{{ catText(curSpec.category) }}</span>
    </div>

    <!-- Step 1 品种与起点 -->
    <div v-if="step === 1" class="wiz-body">
      <div class="wiz-q">1. 选一个品种开始</div>
      <el-input v-model="kw" placeholder="搜索品种名称…" clearable class="wiz-kw" />
      <el-scrollbar max-height="230px" class="wiz-grid-wrap">
        <div class="wiz-grid">
          <div v-for="s in filtered" :key="s.id" class="wiz-card" :class="{ sel: form.speciesId === s.id }"
               @click="pick(s)">
            <span class="wiz-name">{{ s.name }}</span>
            <span class="wiz-tag">{{ catText(s.category) }}</span>
          </div>
        </div>
      </el-scrollbar>
      <el-input v-model="form.name" placeholder="给你的植物起个名（自动按品种预填）" class="wiz-name-input" />
      <div class="wiz-q">这株植物现在属于哪种情况？</div>
      <div class="wiz-opts">
        <div class="wiz-opt" :class="{ sel: form.fromToday }" @click="setStart(true)">🌱 从今天开始种</div>
        <div class="wiz-opt" :class="{ sel: !form.fromToday }" @click="setStart(false)">⏳ 已经种了一段时间</div>
      </div>
      <el-date-picker v-if="!form.fromToday" v-model="form.plantDate" type="date" value-format="YYYY-MM-DD"
                      placeholder="选择种植日期" class="wiz-date" />
    </div>

    <!-- Step 2 环境与方式 -->
    <div v-else-if="step === 2" class="wiz-body">
      <div class="wiz-q">2. 选择种植环境</div>
      <div class="wiz-opts">
        <div v-for="o in LOCATIONS" :key="o" class="wiz-opt" :class="{ sel: form.location === o }"
             @click="form.location = o">{{ o }}</div>
      </div>
      <div class="wiz-sub">种植方式</div>
      <div class="wiz-opts">
        <div v-for="o in METHODS" :key="o" class="wiz-opt" :class="{ sel: form.growMethod === o }"
             @click="form.growMethod = o">{{ o }}</div>
      </div>
    </div>

    <!-- Step 3 养护信息 -->
    <div v-else-if="step === 3" class="wiz-body">
      <div class="wiz-q">3. 补充养护信息</div>
      <div class="wiz-sub">每天光照时长</div>
      <div class="wiz-opts">
        <div v-for="o in SUNLIGHTS" :key="o" class="wiz-opt" :class="{ sel: form.sunlight === o }"
             @click="form.sunlight = o">{{ o }}</div>
      </div>
      <template v-if="needContainer">
        <div class="wiz-sub">容器大小</div>
        <div class="wiz-opts">
          <div v-for="o in CONTAINERS" :key="o" class="wiz-opt" :class="{ sel: form.container === o }"
               @click="form.container = o">{{ o }}</div>
        </div>
      </template>
      <div class="wiz-sub">当前生长阶段</div>
      <div class="wiz-opts">
        <div v-for="o in stageOpts" :key="o" class="wiz-opt" :class="{ sel: form.stageName === o }"
             @click="form.stageName = o">{{ o }}</div>
      </div>
      <div class="wiz-more">
        <div class="wiz-sub">照片（选填）</div>
        <el-upload :show-file-list="false" :before-upload="beforeUpload">
          <div class="upload-box">
            <img v-if="form.photo" :src="form.photo" class="upload-img" />
            <div v-else class="upload-empty"><el-icon :size="20"><Plus /></el-icon> 上传照片</div>
          </div>
        </el-upload>
        <el-select v-model="form.deviceId" placeholder="绑定我的传感器设备（选填）" clearable style="width: 100%; margin-top: 10px">
          <el-option v-for="d in devices" :key="d.id" :value="d.id" :label="`${d.deviceName}（${d.deviceNo}）`" />
        </el-select>
        <el-input v-model="form.remark" placeholder="备注（选填）" style="margin-top: 10px" />
      </div>
    </div>

    <!-- Step 4 系统建议 -->
    <div v-else class="wiz-body">
      <div class="wiz-q">4. 系统建议</div>
      <div v-if="advLoading" class="wiz-loading">正在生成建议…</div>
      <template v-else>
        <div class="wiz-adv">
          <el-tag v-if="advice.match" :type="matchTag" class="wiz-match-tag">{{ advice.match.level }}匹配度</el-tag>
          <div class="wiz-adv-text">{{ advice.match?.text || '建议生成失败，仍可加入' }}</div>
        </div>
        <div class="wiz-adv-title">养护计划</div>
        <div v-for="(p, i) in advice.plan || []" :key="i" class="wiz-adv-item">
          <b>{{ p.title }}：</b>{{ p.text }}
        </div>
        <div class="wiz-adv-title">提醒生成方式</div>
        <div v-for="(r, i) in advice.remind || []" :key="'r' + i" class="wiz-adv-item">🔔 {{ r.text }}</div>
      </template>
    </div>

    <template #footer>
      <el-button v-if="step > 1" @click="step--">上一步</el-button>
      <el-button v-if="step < 4" type="primary" :disabled="!canNext" @click="next">下一步</el-button>
      <el-button v-else type="primary" :loading="saving" @click="save">确认加入我的植物</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
// 添加种植物向导（弹窗组件）：分 4 步引导农户登记一株新植物。
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import http from '../api'

// 父页面传进来的数据：modelValue 控制弹窗显隐；species 是品种库列表；devices 是我名下可绑定的传感器设备
const props = defineProps<{ modelValue: boolean; species: any[]; devices: any[] }>()
// 抛给父页面的两个事件：update:modelValue 同步弹窗显隐，saved 表示新增成功、请刷新列表
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'saved'): void }>()

// 各步骤的候选项都写死在前端，农户点一下就能选，不用自己敲字
const LOCATIONS = ['阳台', '窗台', '露台', '小院', '室内', '菜地']
const METHODS = ['土培', '水培']
const SUNLIGHTS = ['少于2小时', '2-4小时', '4-6小时', '6小时以上']
const CONTAINERS = ['小盆', '中盆', '大盆', '水培盒', '还没准备']
const DEFAULT_STAGES = ['种子育苗', '幼苗期', '生长期', '花期', '结果期']

// 当前在第几步（1~4），底部按钮按它决定显示「上一步 / 下一步 / 确认加入我的植物」
const step = ref(1)
// 第 1 步品种搜索框的关键词
const kw = ref('')
// 第 4 步「正在生成建议…」的加载状态
const advLoading = ref(false)
// 保存按钮的加载状态，防止连点重复提交
const saving = ref(false)
// 第 4 步后端算出的建议：match 是品种与环境匹配度，plan 是养护计划，remind 是提醒生成方式
const advice = ref<any>({})
// 向导收集的表单数据，四步共用一份；fromToday 为 true 表示「从今天开始种」，此时不需要另外选日期
const form = reactive<any>({ fromToday: true, speciesId: null, name: '', plantDate: '', location: '', growMethod: '', sunlight: '', container: '', stageName: '', photo: '', deviceId: null, remark: '' })   // 【四步共用】

// 当前选中的品种对象（拿 form.speciesId 去品种列表里找），用于顶部信息卡和生长阶段选项
const curSpec = computed(() => props.species.find((s) => s.id === form.speciesId))
// 第 1 步品种网格的显示列表：关键词为空时全显示，否则按品种名模糊匹配
const filtered = computed(() => {
  const k = kw.value.trim()
  return props.species.filter((s) => !k || s.name.includes(k))
})
// 第 3 步「当前生长阶段」的可选项：优先取该品种自带的生长阶段（库里存的是 JSON 字符串），
const stageOpts = computed(() => {
  const gs = curSpec.value?.growthStages
  if (!gs) return DEFAULT_STAGES
  try {
    const arr = JSON.parse(gs)
    const names = arr.map((x: any) => x.stage).filter(Boolean)
    return names.length ? names : DEFAULT_STAGES
  } catch {
    return DEFAULT_STAGES
  }
})
// 只有「有容器」的场景才问容器：菜地是露天大田，没有盆的概念（选了菜地 → 不显示、不参与必填、提交时置空）
const needContainer = computed(() => form.location !== '菜地')
// 「下一步」按钮的可点条件：每一步的必填项都填齐才放行（第 1 步要求选了品种、起了名、且有种/已种日期）
const canNext = computed(() => {
  if (step.value === 1) return !!form.speciesId && !!form.name.trim() && (form.fromToday || !!form.plantDate)
  if (step.value === 2) return !!form.location && !!form.growMethod
  if (step.value === 3) return !!form.sunlight && (!needContainer.value || !!form.container) && !!form.stageName
  return true
})
// 匹配度标签的颜色：高=绿、低=橙、其余=灰
const matchTag = computed(() => (advice.value.match?.level === '高' ? 'success' : advice.value.match?.level === '低' ? 'warning' : 'info'))

// 弹窗每次打开都重置一次：回到第 1 步、清空搜索词与建议、把表单恢复成初始值
watch(() => props.modelValue, (v) => {
  if (!v) return
  step.value = 1
  kw.value = ''
  advice.value = {}
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { fromToday: true, speciesId: null, name: '', plantDate: '', location: '', growMethod: '', sunlight: '', container: '', stageName: '', photo: '', deviceId: null, remark: '' })
})

// 环境改成「菜地」时把容器清掉：避免带着「大盆」这种旧值一路提交上去
watch(() => form.location, (v) => {
  if (v === '菜地') form.container = ''
})

// 品种大类的中文名：crop=农作物，pot=盆栽
function catText(c: string) {
  return c === 'crop' ? '农作物' : c === 'pot' ? '盆栽' : c || '其他'
}
/** 本地日期 YYYY-MM-DD（toISOString 是 UTC，北京时间深夜会差一天，禁用） */
function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
// 点某个品种卡片：记下它的 id，并把植物名默认填成品种名（农户可以再改）
function pick(s: any) {   // 【名字自动填】
  form.speciesId = s.id
  form.name = s.name
}
// 选「从今天开始种」时顺手把种植日期填成今天，省得农户再点一次日历
function setStart(fromToday: boolean) {
  form.fromToday = fromToday
  if (fromToday) form.plantDate = todayStr()
}
// 点「下一步」：步数加一；刚走到第 4 步时去后端点一次建议计算
function next() {
  step.value++
  if (step.value === 4) loadAdvice()
}
// 进第 4 步时调 GET /api/plants/onboard-advice：把前面填的品种、环境、方式、光照、容器、阶段传给后端，
async function loadAdvice() {   // 【入园建议】
  advLoading.value = true
  try {
    const d = await http.get('/plants/onboard-advice', {
      params: { speciesId: form.speciesId, location: form.location, growMethod: form.growMethod, sunlight: form.sunlight, container: needContainer.value ? form.container : '', stageName: form.stageName },
    })
    advice.value = d
  } catch {
    advice.value = {}
  } finally {
    advLoading.value = false
  }
}
// 选完照片先调 POST /api/upload 把文件传上去，拿到可访问的图片地址填回表单；
async function beforeUpload(file: File) {   // 【照片先传】
  // 只允许图片：这份后缀白名单必须和后端 UploadController.ALLOWED_EXT 保持一致。
  const IMG_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!IMG_EXT.includes(ext)) {
    ElMessage.error('只支持 jpg / jpeg / png / gif / webp / bmp 格式的图片')
    return false
  }
  const fd = new FormData()
  fd.append('file', file)
  const d = await http.post('/upload', fd)
  form.photo = d.url
  ElMessage.success('照片上传成功')
  return false
}
// 点「确认加入我的植物」：调 POST /api/plants 把整份表单存进数据库（种植日期按「从今天种 or 自选日期」取值），
async function save() {   // 【确认才落库】
  saving.value = true
  try {
    await http.post('/plants', {
      name: form.name.trim(), speciesId: form.speciesId,
      plantDate: form.fromToday ? todayStr() : form.plantDate,
      photo: form.photo, deviceId: form.deviceId, remark: form.remark,
      location: form.location, growMethod: form.growMethod, sunlight: form.sunlight,
      container: needContainer.value ? form.container : '', stageName: form.stageName,
    })
    ElMessage.success(`已加入我的植物「${form.name.trim()}」`)
    emit('update:modelValue', false)
    emit('saved')
  } catch {
    ElMessage.error('添加失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.wizard-dialog :deep(.el-dialog__header) { border-bottom: 1px solid rgba(60, 84, 66, 0.08); padding-bottom: 14px; }
/* 顶部：品种 + 步骤进度槽（浅井） */
.wiz-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  padding: 14px 16px;
  background: var(--well);
  border-radius: 14px;
  box-shadow: none;
}
.wiz-spec { font-size: 18px; font-weight: 700; color: var(--neu-ink); margin-right: 12px; }
.wiz-step { font-size: 12px; color: var(--neu-ink3); }
.wiz-bar { width: 180px; margin-top: 8px; }
.wiz-cat { background: var(--el-color-success-light-9); color: var(--el-color-success-dark-2); border-radius: 999px; padding: 3px 12px; font-size: 12px; white-space: nowrap; }
.wiz-body { min-height: 320px; }
.wiz-q { font-size: 15px; font-weight: 700; color: var(--neu-ink); margin: 14px 0 10px; }
.wiz-sub { font-size: 13px; color: var(--neu-ink2); margin: 14px 0 8px; font-weight: 500; }
.wiz-kw { margin-bottom: 10px; }
.wiz-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; padding-right: 8px; }
/* 品种卡：白胶囊可选，hover 上浮；选中 = 主绿浅染 + 光环 */
.wiz-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 11px 4px 9px;
  border-radius: 12px;
  cursor: pointer;
  background: #ffffff;
  box-shadow: var(--shadow-xs);
  transition: box-shadow 0.3s ease-in-out, transform 0.3s ease-in-out, background-color 0.3s ease-in-out, color 0.3s ease-in-out;
}
.wiz-card:hover { transform: translateY(-2px); box-shadow: 0 6px 14px -4px rgba(63, 125, 76, 0.3); }
.wiz-card.sel {
  background: var(--tint);
  box-shadow: 0 0 0 2px rgba(63, 125, 76, 0.25);
}
.wiz-card.sel:hover { transform: none; box-shadow: 0 0 0 2px rgba(63, 125, 76, 0.25); }
.wiz-card.sel .wiz-name { color: #32643d; }
.wiz-name { font-size: 13px; color: var(--neu-ink); }
.wiz-tag { font-size: 11px; color: var(--neu-ink2); }
.wiz-name-input { margin: 12px 0 6px; }
.wiz-opts { display: flex; flex-wrap: wrap; gap: 8px; }
/* 选项钮：白胶囊 → hover 上浮 → 选中浅染 + 光环 */
.wiz-opt {
  padding: 8px 16px;
  border-radius: 12px;
  font-size: 13px;
  color: var(--neu-ink);
  cursor: pointer;
  background: #ffffff;
  box-shadow: var(--shadow-xs);
  transition: box-shadow 0.3s ease-in-out, transform 0.3s ease-in-out, background-color 0.3s ease-in-out, color 0.3s ease-in-out;
  user-select: none;
}
.wiz-opt:hover { transform: translateY(-1px); box-shadow: 0 6px 14px -4px rgba(63, 125, 76, 0.28); }
.wiz-opt.sel {
  background: var(--tint);
  box-shadow: 0 0 0 2px rgba(63, 125, 76, 0.25);
  color: #32643d;
  font-weight: 600;
}
.wiz-opt.sel:hover { transform: none; box-shadow: 0 0 0 2px rgba(63, 125, 76, 0.25); }
.wiz-date { width: 100%; margin-top: 10px; }
.wiz-more { margin-top: 16px; padding-top: 12px; border-top: 1px solid rgba(60, 84, 66, 0.08); }
.wiz-loading { color: var(--neu-ink3); text-align: center; padding: 40px 0; }
.wiz-adv {
  background: var(--well);
  border-radius: 14px;
  padding: 12px 14px;
  margin-bottom: 12px;
  box-shadow: none;
}
.wiz-match-tag { margin-right: 8px; }
.wiz-adv-text { font-size: 13px; color: var(--neu-ink); line-height: 1.7; margin-top: 6px; }
.wiz-adv-title { font-size: 13px; font-weight: 700; color: var(--neu-ink); margin: 10px 0 6px; }
.wiz-adv-item { font-size: 13px; color: var(--neu-ink2); line-height: 1.7; padding: 6px 0; border-bottom: 1px solid rgba(60, 84, 66, 0.07); }
.wiz-adv-item:last-child { border-bottom: none; }
/* 上传槽：浅井可点击 */
.upload-box {
  width: 96px;
  height: 96px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background: var(--well);
  box-shadow: none;
  transition: background-color 0.3s ease-in-out;
}
.upload-box:hover { background: var(--well-hover); }
.upload-img { width: 96px; height: 96px; object-fit: cover; border-radius: 12px; }
.upload-empty { font-size: 12px; color: var(--neu-ink2); text-align: center; }
</style>
