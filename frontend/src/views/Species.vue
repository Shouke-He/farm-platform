<!-- 品种库：188 个品种，按农作物/盆栽分类浏览 -->
<template>
  <div>
    <!-- 顶部：标题、品种统计、搜索框、分类页签 -->
    <el-card shadow="never">
      <div class="head">
        <div>
          <div class="title">🌾 品种库</div>
          <div class="sub">共 {{ species.length }} 个品种（农作物 {{ cropCount }} + 盆栽 {{ potCount }}），上传种植物时自动匹配适宜区间与生长周期</div>
        </div>
        <div class="head-right">
          <el-input v-model="kw" placeholder="搜索品种名" clearable style="width: 220px" :prefix-icon="Search" />
        </div>
      </div>
      <el-tabs v-model="catTab" style="margin-top: 8px">
        <el-tab-pane label="🌾 农作物" name="crop" />
        <el-tab-pane label="🪴 盆栽" name="pot" />
        <el-tab-pane label="全部" name="all" />
      </el-tabs>
    </el-card>

    <!-- 品种卡片墙：一行三张，每张一个品种 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8" v-for="s in filtered" :key="s.id">
        <!-- 卡片上挂了两种点法：点图片 → 全屏放大预览；点卡片其它任何位置 → 详情弹窗。 -->
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
  </div>
</template>

<script setup lang="ts">
// 品种库：农作物和盆栽的「说明书」图鉴。
import { computed, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import http from '../api'

// 品种列表（后端全量返回）
const species = ref<any[]>([])
// 搜索关键词：按品种名过滤
const kw = ref('')
// 分类页签：crop=农作物、pot=盆栽、all=全部，默认停在农作物
const catTab = ref('crop')

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
// 记录图片加载失败过的品种名：失败一次就不再重试，卡片上改显示分类 emoji
const imgFailed = ref<Set<string>>(new Set())

// 决定某个品种显示哪张图，按优先级依次尝试：
function imgOf(s: any): string | undefined {
  if (s.photo) return s.photo // 管理员上传的品种图优先
  if (SPECIES_IMG[s.name]) return SPECIES_IMG[s.name] // 首批英文名静态图
  return `/species/${encodeURIComponent(s.name)}.jpg` // 全量中文名图，按名动态加载
}

// 顶部统计数字：农作物、盆栽各有几个品种
const cropCount = computed(() => species.value.filter((s) => s.category === 'crop').length)
const potCount = computed(() => species.value.filter((s) => s.category === 'pot').length)

// 最终要显示的品种：先按页签过滤分类（选「全部」不过滤），再按关键词过滤名称
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

// ── 下面是 2026-09-27 加的两个「看得更清楚」的交互，两页（品种库 / 病虫害）写法一致 ──

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

// 进入页面就拉一次品种库全表
onMounted(async () => {
  species.value = await http.get('/plant-species')   // 【前端全量拉】
})
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; }
.head-right { display: flex; align-items: center; gap: 10px; }
.title { font-size: 17px; font-weight: 600; color: var(--neu-ink); }
.sub { font-size: 12px; color: var(--neu-ink2); margin-top: 4px; }
.sp-card {
  margin-bottom: 4px;
  transition: transform 0.3s ease-in-out, box-shadow 0.3s ease-in-out;
}
.sp-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-card-hover); }
.sp-photo { height: 150px; border-radius: 14px; overflow: hidden; background: var(--well); box-shadow: none; margin-bottom: 10px; }
.sp-photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
.sp-photo-emoji { height: 150px; line-height: 150px; text-align: center; font-size: 64px; }
.sp-head { display: flex; align-items: center; gap: 8px; }
.sp-name { font-weight: 600; color: var(--neu-ink); font-size: 15px; }
.sp-tips { margin-top: 10px; font-size: 12px; color: var(--el-color-success-dark-2); background: var(--el-color-success-light-9); padding: 8px 10px; border-radius: 10px; line-height: 1.6; }
.sp-stages { margin-top: 8px; font-size: 12px; color: var(--el-color-success-dark-2); background: var(--el-color-success-light-9); padding: 6px 10px; border-radius: 10px; }
.sp-diseases { margin-top: 8px; font-size: 12px; color: var(--el-color-warning); line-height: 1.6; }
.sp-more { margin-top: 10px; text-align: right; font-size: 11px; color: var(--neu-ink3); }
.sp-card { cursor: pointer; }

/* 图片上的「点击放大」提示条：默认透明，hover 卡片时浮出来 */
.sp-photo { position: relative; }
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
