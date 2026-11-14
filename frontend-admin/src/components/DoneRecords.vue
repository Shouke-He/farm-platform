<!-- 完成记录：管理端只读视图 -->
<template>
  <div class="dr-list">
    <div v-for="r in records" :key="r.kind + '-' + (r.execId != null ? 'e' + r.execId : 'r' + (r.reminderId ?? r.id))" class="dr-item">
      <div class="dr-head">
        <span class="dr-time">{{ (r.time || '').slice(0, 16) }}</span>
        <el-tag v-if="r.kind === 'grow'" size="small" type="primary">生长观察</el-tag>
        <el-tag v-else size="small" :type="typeTag(r.type)">{{ typeName(r.type) }}</el-tag>
        <el-tag v-if="r.kind === 'grow' && r.assessment" size="small" :type="growTag(r.assessment)">{{ r.assessment }}</el-tag>
        <el-tag v-if="r.kind === 'orphan'" size="small" type="info">任务已删除</el-tag>
        <el-tag v-if="showPlant && r.plantName" size="small" type="success">{{ r.plantEmoji }} {{ r.plantName }}</el-tag>
        <span v-if="!hideOps" class="dr-ops">
          <el-button v-if="r.kind !== 'grow'" size="small" type="primary" link @click="$emit('view', r)">查看</el-button>
          <el-button v-if="r.kind === 'grow'" size="small" type="danger" link @click="$emit('delete', r)">删除</el-button>
        </span>
      </div>
      <div class="dr-title">
        <template v-if="r.kind === 'grow'">
          <span v-if="r.heightCm != null">株高 {{ r.heightCm }}cm</span>
          <span v-if="r.leafColor">叶色 {{ r.leafColor }}</span>
          <span v-if="r.vigor">长势 {{ r.vigor }}</span>
          <span v-if="r.heightCm == null && !r.leafColor && !r.vigor">生长观察</span>
        </template>
        <template v-else>{{ r.title }}</template>
      </div>
      <div v-if="r.kind !== 'grow' && parseInputs(r.inputs).length" class="dr-inputs">
        <span v-for="(i, k) in parseInputs(r.inputs)" :key="k" class="dr-chip">{{ i.name }} {{ i.amount }} {{ i.unit }}</span>
      </div>
      <div v-if="r.note" class="dr-note">{{ r.note }}</div>
      <div v-if="r.remark" class="dr-note">{{ r.remark }}</div>
      <div v-if="photosOf(r).length" class="dr-photos">
        <el-image v-for="(p, k) in photosOf(r)" :key="k" :src="p" :preview-src-list="photosOf(r)" fit="cover"
          class="dr-photo" preview-teleported />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 记录列表展示组件（纯展示、不发请求）：把「完成记录」和「生长记录」两种数据渲染成一条条时间线卡片，
defineProps<{ records: any[]; showPlant?: boolean; hideOps?: boolean }>()
defineEmits(['view', 'delete'])

// 记录类型编码 → 中文名（完成记录用）；类型为空说明原任务已被删除，显示「已删除任务」
function typeName(t: string) {
  return ({ growth: '生长提醒', solar: '节气', weather: '天气', custom: '农事任务', sensor: '环境告警', plant_advice: '种植建议', plant_pest: '虫害预防' } as any)[t] || (t ? t : '已删除任务')
}
// 记录类型 → 标签颜色（不同来源的提醒用不同颜色区分）
function typeTag(t: string) {
  return ({ growth: 'warning', solar: 'success', weather: 'danger', custom: 'info', sensor: 'danger', plant_advice: 'primary', plant_pest: 'warning' } as any)[t] || 'info'
}
// 生长观察的评估结论 → 标签颜色（正常绿、低于预期橙、优于预期蓝）
function growTag(a: string) {
  return ({ 正常: 'success', 低于预期: 'warning', 优于预期: 'primary' } as any)[a] || 'info'
}
// 农资用量字段是后端存的 JSON 字符串，这里解析成数组用于渲染小胶囊；解析失败或为空就当没有
function parseInputs(s: any): any[] {
  try { return s ? JSON.parse(s) : [] } catch { return [] }
}
/** 照片：任务条目取 photos(JSON数组)，生长条目取 photo(单图) */
// 两种情况字段名不一样，所以统一在这里转成字符串数组，模板直接用
function photosOf(r: any): string[] {
  if (r.kind === 'grow') return r.photo ? [r.photo] : []
  try { return r.photos ? JSON.parse(r.photos) : [] } catch { return [] }
}
</script>

<style scoped>
.dr-list { display: flex; flex-direction: column; }
.dr-item { border-radius: 12px; padding: 10px 12px; transition: background-color 0.3s ease-in-out; }
.dr-item:hover { background: #eef2ec; }
.dr-item + .dr-item { border-top: 1px solid rgba(60, 84, 66, 0.07); }
.dr-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dr-time { font-size: 12px; font-weight: 600; color: var(--neu-ink2); font-variant-numeric: tabular-nums; }
.dr-ops { margin-left: auto; display: flex; align-items: center; }
.dr-title { font-size: 13px; color: var(--neu-ink); margin-top: 6px; display: flex; gap: 10px; flex-wrap: wrap; }
.dr-inputs { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
.dr-chip { font-size: 11px; padding: 1px 9px; background: var(--well); color: var(--neu-ink2); border-radius: 999px; }
.dr-note { font-size: 12px; color: var(--neu-ink2); margin-top: 4px; line-height: 1.6; }
.dr-photos { display: flex; gap: 6px; margin-top: 6px; }
.dr-photo { width: 64px; height: 64px; border-radius: 10px; }
</style>
