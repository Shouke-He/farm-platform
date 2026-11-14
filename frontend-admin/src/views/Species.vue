<!-- 品种库管理：188 条增删改 + 阶段行录入 -->
<template>
  <div>
    <!-- 顶部：品种总数说明 + 新增按钮/搜索框 + 农作物/盆栽/全部选项卡 -->
    <el-card shadow="never">
      <div class="head">
        <div>
          <div class="title">🌾 品种库</div>
          <div class="sub">共 {{ species.length }} 个品种（农作物 {{ cropCount }} + 盆栽 {{ potCount }}），上传种植物时自动匹配适宜区间与生长周期</div>
        </div>
        <div class="head-right">
          <el-button v-if="isAdmin" type="primary" @click="openDialog()">新增品种</el-button>
          <el-input v-model="kw" placeholder="搜索品种名" clearable style="width: 220px" :prefix-icon="Search" />
        </div>
      </div>
      <el-tabs v-model="catTab" style="margin-top: 8px">
        <el-tab-pane label="🌾 农作物" name="crop" />
        <el-tab-pane label="🪴 盆栽" name="pot" />
        <el-tab-pane label="全部" name="all" />
      </el-tabs>
    </el-card>

    <!-- 品种卡片网格：一屏三列，每张卡是品种图片 + 参数表 + 养护要点 +（管理员可见的）编辑/删除。 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8" v-for="s in filtered" :key="s.id">
        <el-card shadow="hover" class="sp-card" @click="openDetail(s)">
          <div class="sp-photo" @click.stop="preview(s)">
            <img v-if="imgOf(s) && !imgFailed.has(s.name)" :src="imgOf(s)" alt="" loading="lazy" @error="imgFailed.add(s.name)" />
            <div v-else class="sp-photo-emoji">{{ s.category === 'pot' ? '🪴' : '🌾' }}</div>
            <!-- hover 才浮出来的提示条，让「图片可以点」一眼看得见 -->
            <div v-if="imgOf(s) && !imgFailed.has(s.name)" class="sp-zoom">
              <el-icon><ZoomIn /></el-icon> 点击放大
            </div>
          </div>
          <div class="sp-head">
            <span class="sp-name">{{ s.name }}</span>
            <el-tag size="small" :type="s.category === 'pot' ? 'success' : 'warning'" style="margin-left: 6px">
              {{ s.category === 'pot' ? '盆栽' : '农作物' }}
            </el-tag>
            <el-tag size="small" :type="s.lightLevel === '喜光' ? 'warning' : s.lightLevel === '耐阴' ? 'info' : 'primary'" style="margin-left: auto">
              {{ s.lightLevel }}
            </el-tag>
          </div>
          <el-descriptions :column="2" size="small" style="margin-top: 10px">
            <el-descriptions-item label="土壤湿度">{{ s.soilMin }}% ~ {{ s.soilMax }}%</el-descriptions-item>
            <el-descriptions-item label="气温">{{ s.tempMin }}℃ ~ {{ s.tempMax }}℃</el-descriptions-item>
            <el-descriptions-item label="浇水周期">约每 {{ s.waterCycle }} 天</el-descriptions-item>
            <el-descriptions-item label="施肥周期">约每 {{ s.fertilizeCycle }} 天</el-descriptions-item>
          </el-descriptions>
          <div class="sp-stages" v-if="stageChain(s)">🌱 生长周期：{{ stageChain(s) }}</div>
          <div class="sp-tips">💡 {{ s.careTips }}</div>
          <div class="sp-diseases" v-if="s.diseases">⚠️ 易发：{{ s.diseases }}</div>
          <!-- 编辑/删除这层必须 @click.stop：卡片现在挂了 openDetail，不拦的话点「编辑」 -->
          <div class="sp-ops" v-if="isAdmin" @click.stop>
            <el-button size="small" type="primary" plain @click="openDialog(s)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="del(s)">删除</el-button>
          </div>
          <div class="sp-more">点击卡片查看详情 →</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 详情弹窗：左边大图、右边关键指标，下面是需要整行铺开的几段长文本。 -->
    <el-dialog v-model="detailOpen" width="760px" top="7vh">
      <template #header>
        <div class="spd-title">
          <span class="spd-name">{{ detail?.name }}</span>
          <el-tag size="small" :type="detail?.category === 'pot' ? 'success' : 'warning'">
            {{ detail?.category === 'pot' ? '盆栽' : '农作物' }}
          </el-tag>
          <el-tag size="small" :type="detail?.lightLevel === '喜光' ? 'warning' : detail?.lightLevel === '耐阴' ? 'info' : 'primary'">
            {{ detail?.lightLevel }}
          </el-tag>
        </div>
      </template>

      <div class="spd-top" v-if="detail">
        <div class="spd-photo" @click="preview(detail)">
          <img v-if="imgOf(detail) && !imgFailed.has(detail.name)" :src="imgOf(detail)" alt="" @error="imgFailed.add(detail.name)" />
          <div v-else class="spd-photo-emoji">{{ detail.category === 'pot' ? '🪴' : '🌾' }}</div>
          <div v-if="imgOf(detail) && !imgFailed.has(detail.name)" class="sp-zoom">
            <el-icon><ZoomIn /></el-icon> 点击放大
          </div>
        </div>
        <el-descriptions :column="1" border size="small" class="spd-desc">
          <el-descriptions-item label="土壤湿度">{{ detail.soilMin }}% ~ {{ detail.soilMax }}%</el-descriptions-item>
          <el-descriptions-item label="适宜气温">{{ detail.tempMin }}℃ ~ {{ detail.tempMax }}℃</el-descriptions-item>
          <el-descriptions-item label="浇水周期">约每 {{ detail.waterCycle }} 天</el-descriptions-item>
          <el-descriptions-item label="施肥周期">约每 {{ detail.fertilizeCycle }} 天</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="spd-block" v-if="detail && stageChain(detail)">
        <div class="spd-lbl">🌱 生长周期</div>
        <div class="spd-val">{{ stageChain(detail) }}</div>
      </div>
      <div class="spd-block ok" v-if="detail?.careTips">
        <div class="spd-lbl">💡 养护要点</div>
        <div class="spd-val">{{ detail.careTips }}</div>
      </div>
      <div class="spd-block warn" v-if="detail?.diseases">
        <div class="spd-lbl">⚠️ 易发病虫害</div>
        <div class="spd-val">{{ detail.diseases }}</div>
      </div>
    </el-dialog>

    <!-- 全屏放大预览：Element Plus 自带的图片查看器，缩放/拖动/旋转/1:1 都是内置的，不用自己写。 -->
    <el-image-viewer v-if="previewUrl" :url-list="[previewUrl]" teleported hide-on-click-modal
      @close="previewUrl = ''" />

    <!-- 品种新增/编辑（管理端完整维护，用户只读） -->
    <!-- 宽 700px：原来 560px 时表单内容区只有 370px，「生长阶段」那行（阶段名 + 天数 + 说明 + 删除） -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑品种' : '新增品种'" width="700px" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如 水稻" />
        </el-form-item>
        <el-form-item label="品种图片">
          <el-upload :show-file-list="false" :before-upload="beforeUpload">
            <div class="upload-box">
              <img v-if="form.photo" :src="form.photo" class="upload-img" />
              <div v-else class="upload-empty"><el-icon :size="20"><Plus /></el-icon> 上传图片</div>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="农作物" value="crop" />
            <el-option label="盆栽" value="pot" />
          </el-select>
        </el-form-item>
        <el-form-item label="土壤湿度">
          <el-input-number v-model="form.soilMin" :min="0" :max="100" /> % ~ <el-input-number v-model="form.soilMax" :min="0" :max="100" /> %
        </el-form-item>
        <el-form-item label="适宜气温">
          <el-input-number v-model="form.tempMin" :min="-20" :max="50" /> ℃ ~ <el-input-number v-model="form.tempMax" :min="-20" :max="50" /> ℃
        </el-form-item>
        <el-form-item label="光照">
          <el-select v-model="form.lightLevel" style="width: 100%">
            <el-option label="喜光" value="喜光" />
            <el-option label="散光" value="散光" />
            <el-option label="耐阴" value="耐阴" />
          </el-select>
        </el-form-item>
        <el-form-item label="浇水周期">
          <el-input-number v-model="form.waterCycle" :min="1" /> 天
        </el-form-item>
        <el-form-item label="施肥周期">
          <el-input-number v-model="form.fertilizeCycle" :min="1" /> 天
        </el-form-item>
        <!-- 两个多行框改成自适应高度（2~5 行）：固定 rows=2 时内容超两行就在框内滚动， -->
        <el-form-item label="养护要点">
          <el-input v-model="form.careTips" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" />
        </el-form-item>
        <el-form-item label="易发病虫害">
          <el-input v-model="form.diseases" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="逗号分隔，如：稻瘟病,稻飞虱" />
        </el-form-item>
        <el-form-item label="生长阶段">
          <!-- 三段固定宽（阶段名 / 天数 / 删除）+ 说明自适应，宽度统一写在 <style> 的 .stage-* 里， -->
          <div v-for="(s, i) in form.stages" :key="i" class="stage-row">
            <el-input v-model="s.stage" placeholder="阶段名，如 幼苗期" class="stage-name" />
            <el-input-number v-model="s.days" :min="1" :max="9999" class="stage-days" /> 天
            <el-input v-model="s.desc" placeholder="说明（可选）" class="stage-desc" />
            <el-button link type="danger" class="stage-del" @click="form.stages.splice(i, 1)">删除</el-button>
          </div>
          <el-button type="primary" plain size="small" @click="form.stages.push({ stage: '', days: 30, desc: '' })">
            + 添加阶段
          </el-button>
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
// 品种库（管理端专属页，管理员可维护）：卡片形式展示平台支持的所有品种（农作物 + 盆栽），
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import http from '../api'
import { useUserStore } from '../stores/user'

// 全部品种（后端一次返回全部，前端做分类和搜索）
const species = ref<any[]>([])
// 搜索框关键词：按品种名模糊过滤
const kw = ref('')
// 顶部选项卡当前选中项：crop=农作物、pot=盆栽、all=全部
const catTab = ref('crop')

/** 管理员：品种库完整维护（新增/编辑/删除）；种植用户只读浏览 */
const userStore = useUserStore()
// 是否管理员：决定「新增品种」「编辑」「删除」按钮显不显示
const isAdmin = computed(() => userStore.user?.roleCode === 'ADMIN')
// 新增/编辑弹窗开关
const dialog = ref(false)
// 品种表单（有 id 表示编辑；stages 是页面上编辑中的生长阶段行列表）
const form = reactive<any>({})

// 打开新增/编辑弹窗：清空表单 → 拷入该品种数据（编辑）或只给分类/光照默认值（新增）→ 把 growthStages 反解析成行列表
function openDialog(row?: any) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, row ? { ...row } : { category: 'crop', lightLevel: '喜光' })
  form.stages = parseStages(row?.growthStages)
  dialog.value = true
}

/** JSON 生长阶段 → 行列表（默认一行空阶段）；非法 JSON 回退单行 */
function parseStages(json?: string): { stage: string; days: number; desc: string }[] {
  if (json) {
    try {
      const arr = JSON.parse(json)
      if (Array.isArray(arr) && arr.length) {
        return arr.map((x: any) => ({ stage: x.stage || '', days: x.days || 30, desc: x.desc || '' }))
      }
    } catch { /* 非法 JSON，回退单行 */ }
  }
  return [{ stage: '', days: 30, desc: '' }]
}

/** 品种封面图上传（复用通用上传接口 /api/upload） */
// 触发时机：在编辑弹窗里选了图片文件（el-upload 的 before-upload 钩子，选完文件立刻上传）
async function beforeUpload(file: File) {
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
  ElMessage.success('图片上传成功')
  return false
}

// 保存品种：触发时机为弹窗里点「保存」
async function save() {
  if (!form.name) {
    ElMessage.warning('品种名称不能为空')
    return
  }
  // emoji 字段已从表单移除（2026-08-24 用户要求）；按分类写默认值作兼容数据，
  form.emoji = form.category === 'pot' ? '🪴' : '🌾'
  // 生长阶段：行列表 → JSON 数组（去掉没填阶段名的空行）
  const valid = (form.stages || []).filter((s: any) => (s.stage || '').trim())
  // growth_stages 是 MySQL JSON 列，空阶段必须存合法空数组 []（存 '' 会报 Invalid JSON text）
  form.growthStages = valid.length
    ? JSON.stringify(valid.map((s: any) => ({ stage: s.stage.trim(), days: s.days || 30, desc: s.desc || '' })))
    : '[]'
  if (form.id) await http.put('/plant-species', form)
  else await http.post('/plant-species', form)
  ElMessage.success('保存成功')
  dialog.value = false
  species.value = await http.get('/plant-species')
}

// 删除品种：触发时机为点卡片上的「删除」；先弹确认框（提示已关联的种植物会变成未知品种），
async function del(s: any) {
  await ElMessageBox.confirm(`确定删除品种「${s.name}」？已关联的种植物将显示为未知品种`, '提示')
  await http.delete(`/plant-species/${s.id}`)
  ElMessage.success('已删除')
  species.value = await http.get('/plant-species')
}

/** 品种真实照片（本地静态资源 public/species/，tools/download_species_imgs.js 从 Bing 抓取首批英文名； */
const SPECIES_IMG: Record<string, string> = {
  水稻: '/species/rice.jpg', 玉米: '/species/corn.jpg', 花生: '/species/peanut.jpg',
  甘蔗: '/species/sugarcane.jpg', 番茄: '/species/tomato.jpg', 辣椒: '/species/pepper.jpg',
  黄瓜: '/species/cucumber.jpg', 豆角: '/species/beans.jpg', 西瓜: '/species/watermelon.jpg',
  红薯: '/species/sweetpotato.jpg', 木薯: '/species/cassava.jpg', 香蕉: '/species/banana.jpg',
  荔枝: '/species/lychee.jpg', 火龙果: '/species/dragonfruit.jpg', 柑橘: '/species/citrus.jpg',
  茉莉花: '/species/jasmine.jpg', 绿萝: '/species/pothos.jpg', 薄荷: '/species/mint.jpg',
  多肉植物: '/species/succulent.jpg', 月季: '/species/rose.jpg',
}
// 记录哪些品种的图片加载失败了（模板里 @error 时加进来），这些卡片改显示分类 emoji 占位
const imgFailed = ref<Set<string>>(new Set())

// 决定某个品种卡片显示哪张图：① 管理员上传的图优先；② 内置英文名静态图；③ 按中文名拼路径动态加载（同名 .jpg）
function imgOf(s: any): string | undefined {
  if (s.photo) return s.photo // 管理员上传的品种图优先
  if (SPECIES_IMG[s.name]) return SPECIES_IMG[s.name] // 首批英文名静态图
  return `/species/${encodeURIComponent(s.name)}.jpg` // 全量中文名图，按名动态加载
}

// 页头副标题里的两个数字：农作物品种数、盆栽品种数
const cropCount = computed(() => species.value.filter((s) => s.category === 'crop').length)
const potCount = computed(() => species.value.filter((s) => s.category === 'pot').length)

// 卡片实际展示哪些品种：先按当前选项卡分类过滤，再按搜索关键词过滤（两个条件是叠加的）
const filtered = computed(() => {
  const k = kw.value.trim()
  let list = species.value
  if (catTab.value !== 'all') list = list.filter((s) => (s.category || 'crop') === catTab.value)
  if (k) list = list.filter((s) => s.name.includes(k))
  return list
})

/** 生长周期阶段链（带天数）：幼苗期(30天) → 营养生长期(45天) → 开花期 */
function stageChain(s: any): string {
  if (!s.growthStages) return ''
  try {
    const stages = JSON.parse(s.growthStages)
    if (!Array.isArray(stages) || !stages.length) return ''
    return stages.map((x: any) => (x.stage || '') + '(' + (x.days || '') + '天)').join(' → ')
  } catch {
    return ''
  }
}

// ── 下面是 2026-09-27 加的两个「看得更清楚」的交互，与用户端品种库/病虫害两页写法一致 ──

// 详情弹窗开关；detail 是当前看的那一条（为 null 时弹窗内容整体不渲染，避免读空对象）
const detailOpen = ref(false)
const detail = ref<any>(null)
// 全屏放大预览的图片地址；空串 = 不显示查看器
const previewUrl = ref('')

/** 点卡片：打开详情弹窗 */
function openDetail(s: any) {
  detail.value = s
  detailOpen.value = true
}

/** 点图片：全屏放大预览。图没加载出来（imgFailed 里有它）就不开，免得弹出一个裂图 */
function preview(s: any) {
  const u = imgOf(s)
  // 注意：script 里读 ref 要带 .value（只有模板里才自动解包）
  if (u && !imgFailed.value.has(s.name)) previewUrl.value = u
}

// 页面打开即拉全部品种
onMounted(async () => {
  species.value = await http.get('/plant-species')
})
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; }
.head-right { display: flex; align-items: center; gap: 10px; }
.title { font-size: 17px; font-weight: 600; color: var(--neu-ink); }
.sub { font-size: 12px; color: var(--neu-ink2); margin-top: 4px; }
.sp-ops { margin-top: 10px; display: flex; gap: 8px; }
.sp-card {
  margin-bottom: 4px;
  cursor: pointer;
  transition: transform 0.3s ease-in-out, box-shadow 0.3s ease-in-out;
}
.sp-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-card-hover); }
.sp-photo { position: relative; height: 150px; border-radius: 14px; overflow: hidden; background: var(--well); box-shadow: none; margin-bottom: 10px; }
.sp-photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
.sp-photo-emoji { height: 150px; line-height: 150px; text-align: center; font-size: 64px; }
.sp-head { display: flex; align-items: center; gap: 8px; }
.sp-name { font-weight: 600; color: var(--neu-ink); font-size: 15px; }
.sp-tips { margin-top: 10px; font-size: 12px; color: var(--el-color-success-dark-2); background: var(--el-color-success-light-9); padding: 8px 10px; border-radius: 10px; line-height: 1.6; }
.sp-stages { margin-top: 8px; font-size: 12px; color: var(--el-color-success-dark-2); background: var(--el-color-success-light-9); padding: 6px 10px; border-radius: 10px; }
.sp-diseases { margin-top: 8px; font-size: 12px; color: var(--el-color-warning); line-height: 1.6; }
.sp-more { margin-top: 10px; text-align: right; font-size: 11px; color: var(--neu-ink3); }
/* 生长阶段行：阶段名/天数/删除三段固定宽，说明列自适应撑满剩下的空间。 */
.stage-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; width: 100%; }
.stage-name { width: 140px; flex: 0 0 auto; }
.stage-days { width: 120px; flex: 0 0 auto; }
.stage-desc { flex: 1 1 140px; min-width: 120px; }
.stage-del { flex: 0 0 auto; }
.upload-box { width: 100px; height: 100px; border: none; border-radius: 12px; display: flex; align-items: center; justify-content: center; overflow: hidden; cursor: pointer; background: var(--well); box-shadow: none; transition: background-color 0.3s ease-in-out; }
.upload-box:hover { background: var(--well-hover); }
.upload-img { width: 100%; height: 100%; object-fit: cover; }
.upload-empty { color: var(--neu-ink2); font-size: 12px; display: flex; flex-direction: column; align-items: center; gap: 4px; }

/* 图片上的「点击放大」提示条：默认透明，hover 卡片时浮出来 */
.sp-zoom {
  position: absolute; left: 0; right: 0; bottom: 0;
  display: flex; align-items: center; justify-content: center; gap: 5px;
  padding: 5px 0; font-size: 12px; color: #fff;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
  opacity: 0; transition: opacity 0.25s ease-in-out;
}
.sp-photo:hover .sp-zoom { opacity: 1; }

/* ── 详情弹窗 ── */
.spd-title { display: flex; align-items: center; gap: 10px; }
.spd-name { font-size: 18px; font-weight: 600; color: var(--neu-ink); }
.spd-top { display: flex; gap: 18px; }
.spd-photo {
  position: relative; flex: 0 0 300px; height: 260px;
  border-radius: 14px; overflow: hidden; background: var(--well); cursor: zoom-in;
}
.spd-photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
.spd-photo-emoji { height: 260px; line-height: 260px; text-align: center; font-size: 90px; }
.spd-desc { flex: 1; }
.spd-block { margin-top: 14px; border-radius: 12px; padding: 10px 14px; background: var(--well); }
.spd-block.ok { background: var(--el-color-success-light-9); }
.spd-block.warn { background: var(--el-color-warning-light-9); }
.spd-lbl { font-size: 12px; font-weight: 600; color: var(--neu-ink2); margin-bottom: 5px; }
.spd-val { font-size: 13px; line-height: 1.85; color: var(--neu-ink); }
</style>
