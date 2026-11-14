<!-- 病虫害库管理：158 条增删改 + 图片上传 -->
<template>
  <div>
    <!-- 顶部：知识库条数说明 + 新增按钮/搜索框 -->
    <el-card shadow="never">
      <div class="head">
        <div>
          <div class="title">🐛 病虫害防治知识库</div>
          <div class="sub">共 {{ diseases.length }} 条防治方案，按植物或病虫害名搜索</div>
        </div>
        <div class="head-right">
          <el-button v-if="isAdmin" type="primary" @click="openDialog()">新增防治知识</el-button>
          <el-input v-model="kw" placeholder="搜索病虫害 / 植物名" clearable style="width: 240px" :prefix-icon="Search" />
        </div>
      </div>
    </el-card>

    <!-- 防治知识卡片网格：一屏三列，每张卡是图片 + 症状/诱因/防治/推荐用药 +（管理员可见的）编辑/删除。 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8" v-for="d in filtered" :key="d.id">
        <el-card shadow="hover" class="d-card" @click="openDetail(d)">
          <div class="d-photo" @click.stop="preview(d)">
            <img v-if="imgOf(d) && !imgFailed.has(d.name)" :src="imgOf(d)" alt="" loading="lazy" @error="imgFailed.add(d.name)" />
            <div v-else class="d-photo-empty">暂无图片</div>
            <!-- hover 才浮出来的提示条，让「图片可以点」一眼看得见 -->
            <div v-if="imgOf(d) && !imgFailed.has(d.name)" class="d-zoom">
              <el-icon><ZoomIn /></el-icon> 点击放大
            </div>
          </div>
          <div class="d-head">
            <span class="d-name">{{ d.name }}</span>
            <el-tag size="small" type="warning">{{ d.season }}高发</el-tag>
          </div>
          <div class="d-plants">易感植物：{{ d.plants }}</div>
          <div class="d-item"><b>症状：</b>{{ d.symptom }}</div>
          <div class="d-item"><b>诱因：</b>{{ d.cause }}</div>
          <div class="d-item"><b>防治：</b>{{ d.prevention }}</div>
          <div class="d-item drug"><b>推荐用药：</b>{{ d.drug }}</div>
          <!-- 编辑/删除这层必须 @click.stop：卡片现在挂了 openDetail，不拦的话点「编辑」 -->
          <div class="d-ops" v-if="isAdmin" @click.stop>
            <el-button size="small" type="primary" plain @click="openDialog(d)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="del(d)">删除</el-button>
          </div>
          <div class="d-more">点击卡片查看详情 →</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 详情弹窗：左边大图、右边易感植物与高发季节，下面四段说明整行铺开。 -->
    <el-dialog v-model="detailOpen" width="760px" top="7vh">
      <template #header>
        <div class="dd-title">
          <span class="dd-name">{{ detail?.name }}</span>
          <el-tag size="small" type="warning">{{ detail?.season }}高发</el-tag>
        </div>
      </template>

      <div v-if="detail">
        <div class="dd-top">
          <div class="dd-photo" @click="preview(detail)">
            <img v-if="imgOf(detail) && !imgFailed.has(detail.name)" :src="imgOf(detail)" alt="" @error="imgFailed.add(detail.name)" />
            <div v-else class="dd-photo-empty">暂无图片</div>
            <div v-if="imgOf(detail) && !imgFailed.has(detail.name)" class="d-zoom">
              <el-icon><ZoomIn /></el-icon> 点击放大
            </div>
          </div>
          <div class="dd-side">
            <div class="dd-side-lbl">🧪 易感植物</div>
            <div class="dd-side-val">{{ detail.plants }}</div>
            <div class="dd-side-lbl">📅 高发季节</div>
            <div class="dd-side-val">{{ detail.season }}</div>
          </div>
        </div>

        <div class="dd-block"><div class="dd-lbl">🔍 症状</div><div class="dd-val">{{ detail.symptom }}</div></div>
        <div class="dd-block"><div class="dd-lbl">❓ 诱因</div><div class="dd-val">{{ detail.cause }}</div></div>
        <div class="dd-block ok"><div class="dd-lbl">🛡 防治办法</div><div class="dd-val">{{ detail.prevention }}</div></div>
        <div class="dd-block drug"><div class="dd-lbl">💊 推荐用药</div><div class="dd-val">{{ detail.drug }}</div></div>
      </div>
    </el-dialog>

    <!-- 全屏放大预览：Element Plus 自带的图片查看器，缩放/拖动/旋转/1:1 都是内置的，不用自己写。 -->
    <el-image-viewer v-if="previewUrl" :url-list="[previewUrl]" teleported hide-on-click-modal
      @close="previewUrl = ''" />

    <!-- 病虫害新增/编辑（管理端完整维护，用户只读） -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑防治知识' : '新增防治知识'" width="560px" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如 稻瘟病" />
        </el-form-item>
        <el-form-item label="病虫害图片">
          <el-upload :show-file-list="false" :before-upload="beforeUpload">
            <div class="upload-box">
              <img v-if="form.photo" :src="form.photo" class="upload-img" />
              <div v-else class="upload-empty"><el-icon :size="20"><Plus /></el-icon> 上传图片</div>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item label="易感植物">
          <el-input v-model="form.plants" placeholder="逗号分隔，如 水稻,小麦" />
        </el-form-item>
        <el-form-item label="高发季节">
          <el-input v-model="form.season" placeholder="如 春/夏/秋/冬" style="width: 200px" />
        </el-form-item>
        <el-form-item label="症状">
          <el-input v-model="form.symptom" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="诱因">
          <el-input v-model="form.cause" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="防治方法">
          <el-input v-model="form.prevention" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="推荐用药">
          <el-input v-model="form.drug" type="textarea" :rows="2" />
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
// 病虫害防治（管理端专属页，管理员可维护）：卡片形式展示病虫害知识库 —— 每种病虫害的易感植物、
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import http from '../api'
import { useUserStore } from '../stores/user'

// 全部病虫害条目（后端一次返回全部，前端本地搜索）
const diseases = ref<any[]>([])
// 搜索框关键词：病虫害名或易感植物名包含它即命中
const kw = ref('')

/** 管理员：病虫害知识库完整维护（新增/编辑/删除）；种植用户只读浏览 */
const userStore = useUserStore()
// 是否管理员：决定「新增防治知识」「编辑」「删除」按钮显不显示
const isAdmin = computed(() => userStore.user?.roleCode === 'ADMIN')
// 新增/编辑弹窗开关
const dialog = ref(false)
// 防治知识表单（有 id 表示编辑）
const form = reactive<any>({})

// 打开新增/编辑弹窗：清空表单后把该行数据拷进来（编辑）或留空（新增）
function openDialog(row?: any) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, row ? { ...row } : {})
  dialog.value = true
}

// 保存：触发时机为弹窗里点「保存」；名称必填，有 id 走 PUT 修改、没有走 POST 新增，之后刷新列表
async function save() {
  if (!form.name) {
    ElMessage.warning('名称不能为空')
    return
  }
  if (form.id) await http.put('/plant-diseases', form)
  else await http.post('/plant-diseases', form)
  ElMessage.success('保存成功')
  dialog.value = false
  diseases.value = await http.get('/plant-diseases')
}

// 删除：触发时机为点卡片上的「删除」；确认后 DELETE /api/plant-diseases/{id} 并刷新列表
async function del(d: any) {
  await ElMessageBox.confirm(`确定删除「${d.name}」？`, '提示')
  await http.delete(`/plant-diseases/${d.id}`)
  ElMessage.success('已删除')
  diseases.value = await http.get('/plant-diseases')
}

/** 病虫害真实照片（本地静态资源 public/disease/，首批 22 张英文名逐张识图验证过症状； */
const DISEASE_IMG: Record<string, string> = {
  稻瘟病: '/disease/rice-blast.jpg', 稻飞虱: '/disease/planthopper.jpg',
  纹枯病: '/disease/sheath-blight.jpg', 玉米螟: '/disease/corn-borer.jpg',
  大斑病: '/disease/leaf-blight.jpg', 花生叶斑病: '/disease/peanut-leafspot.jpg',
  青枯病: '/disease/bacterial-wilt.jpg', '螟虫（甘蔗）': '/disease/sugarcane-borer.jpg',
  甘蔗赤腐病: '/disease/cane-redrot.jpg', 晚疫病: '/disease/late-blight.jpg',
  番茄病毒病: '/disease/tomato-virus.jpg', 辣椒炭疽病: '/disease/pepper-anthracnose.jpg',
  霜霉病: '/disease/downy-mildew.jpg', 白粉病: '/disease/powdery-mildew.jpg',
  西瓜枯萎病: '/disease/watermelon-wilt.jpg', 红薯黑斑病: '/disease/sweetpotato-blackrot.jpg',
  香蕉叶斑病: '/disease/banana-leafspot.jpg', 柑橘黄龙病: '/disease/citrus-huanglongbing.jpg',
  柑橘溃疡病: '/disease/citrus-canker.jpg', 红蜘蛛: '/disease/red-spider.jpg',
  蚜虫: '/disease/aphid.jpg', 锈病: '/disease/rust.jpg',
}
// 记录哪些条目的图片加载失败（模板 @error 时加进来），这些卡片显示「暂无图片」占位
const imgFailed = ref<Set<string>>(new Set())

// 决定某个条目显示哪张图：① 管理员上传的图优先；② 内置英文名静态图；③ 按中文名拼路径动态加载
function imgOf(d: any): string | undefined {
  if (d.photo) return d.photo // 管理员上传的病虫害图优先
  if (DISEASE_IMG[d.name]) return DISEASE_IMG[d.name] // 首批英文名静态图
  return `/disease/${encodeURIComponent(d.name)}.jpg` // 全量中文名图，按名动态加载
}

/** 病虫害图片上传（复用通用上传接口 /api/upload） */
// 触发时机：编辑弹窗里选完图片文件立刻上传；返回 false 阻止 el-upload 自己再发一次请求
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

// 卡片实际展示哪些条目：关键词为空就全展示，否则按病虫害名或易感植物名匹配
const filtered = computed(() => {
  const k = kw.value.trim()
  if (!k) return diseases.value
  return diseases.value.filter(
    (d) => d.name.includes(k) || (d.plants || '').includes(k),
  )
})

// ── 下面是 2026-09-27 加的两个「看得更清楚」的交互，与用户端品种库/病虫害两页写法一致 ──

// 详情弹窗开关；detail 是当前看的那一条（为 null 时弹窗内容整体不渲染，避免读空对象）
const detailOpen = ref(false)
const detail = ref<any>(null)
// 全屏放大预览的图片地址；空串 = 不显示查看器
const previewUrl = ref('')

/** 点卡片：打开详情弹窗 */
function openDetail(d: any) {
  detail.value = d
  detailOpen.value = true
}

/** 点图片：全屏放大预览。图没加载出来（imgFailed 里有它）就不开，免得弹出一个裂图 */
function preview(d: any) {
  const u = imgOf(d)
  // 注意：script 里读 ref 要带 .value（只有模板里才自动解包）
  if (u && !imgFailed.value.has(d.name)) previewUrl.value = u
}

// 页面打开即拉全部防治知识
onMounted(async () => {
  diseases.value = await http.get('/plant-diseases')
})
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; }
.head-right { display: flex; align-items: center; gap: 10px; }
.title { font-size: 17px; font-weight: 600; color: var(--neu-ink); }
.sub { font-size: 12px; color: var(--neu-ink2); margin-top: 4px; }
.d-ops { margin-top: 10px; display: flex; gap: 8px; }
.d-card {
  margin-bottom: 4px;
  cursor: pointer;
  transition: transform 0.3s ease-in-out, box-shadow 0.3s ease-in-out;
}
.d-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-card-hover); }
.d-photo { position: relative; height: 150px; border-radius: 14px; overflow: hidden; background: var(--well); box-shadow: none; margin-bottom: 10px; }
.d-photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
.d-photo-empty { height: 150px; display: flex; align-items: center; justify-content: center; font-size: 12px; color: var(--neu-ink3); }
.d-head { display: flex; align-items: center; justify-content: space-between; }
.d-name { font-weight: 600; color: var(--neu-ink); font-size: 15px; }
.d-plants { margin-top: 8px; font-size: 12px; color: var(--el-color-warning); }
.d-item { margin-top: 6px; font-size: 12px; color: var(--neu-ink2); line-height: 1.7; }
.d-item.drug { color: var(--neu-accent); }
.d-more { margin-top: 10px; text-align: right; font-size: 11px; color: var(--neu-ink3); }
.upload-box { width: 100px; height: 100px; border: none; border-radius: 12px; display: flex; align-items: center; justify-content: center; overflow: hidden; cursor: pointer; background: var(--well); box-shadow: none; transition: background-color 0.3s ease-in-out; }
.upload-box:hover { background: var(--well-hover); }
.upload-img { width: 100%; height: 100%; object-fit: cover; }
.upload-empty { color: var(--neu-ink2); font-size: 12px; display: flex; flex-direction: column; align-items: center; gap: 4px; }

/* 图片上的「点击放大」提示条：默认透明，hover 卡片时浮出来 */
.d-zoom {
  position: absolute; left: 0; right: 0; bottom: 0;
  display: flex; align-items: center; justify-content: center; gap: 5px;
  padding: 5px 0; font-size: 12px; color: #fff;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
  opacity: 0; transition: opacity 0.25s ease-in-out;
}
.d-photo:hover .d-zoom { opacity: 1; }

/* ── 详情弹窗 ── */
.dd-title { display: flex; align-items: center; gap: 10px; }
.dd-name { font-size: 18px; font-weight: 600; color: var(--neu-ink); }
.dd-top { display: flex; gap: 18px; }
.dd-photo {
  position: relative; flex: 0 0 300px; height: 240px;
  border-radius: 14px; overflow: hidden; background: var(--well); cursor: zoom-in;
}
.dd-photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
.dd-photo-empty { height: 240px; display: flex; align-items: center; justify-content: center; font-size: 13px; color: var(--neu-ink3); }
/* 右侧信息栏：给一块「井」底板。父级 .dd-top 是 flex 且默认 align-items:stretch， */
.dd-side {
  flex: 1; min-width: 0; background: var(--well); border-radius: 14px; padding: 16px 18px;
  display: flex; flex-direction: column; justify-content: center; /* 内容垂直居中，空白左右对称而不是全堆在底部 */
}
.dd-side-lbl { font-size: 12px; color: var(--neu-ink2); margin-bottom: 4px; }
.dd-side-val { font-size: 14px; color: var(--neu-ink); line-height: 1.7; margin-bottom: 18px; }
.dd-side-val:last-child { margin-bottom: 0; }
.dd-block { margin-top: 12px; border-radius: 12px; padding: 10px 14px; background: var(--well); }
.dd-block.ok { background: var(--el-color-success-light-9); }
.dd-block.drug { background: var(--el-color-warning-light-9); }
.dd-lbl { font-size: 12px; font-weight: 600; color: var(--neu-ink2); margin-bottom: 5px; }
.dd-val { font-size: 13px; line-height: 1.85; color: var(--neu-ink); }
</style>
