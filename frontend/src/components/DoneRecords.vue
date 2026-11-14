<!-- 完成记录：任务执行 + 系统推送处理 -->
<template>
  <div class="dr-list">
    <div v-for="r in records" :key="r.kind + '-' + (r.execId != null ? 'e' + r.execId : 'r' + (r.reminderId ?? r.id))" class="dr-item">
      <div class="dr-head">
        <span class="dr-time">{{ (r.time || '').slice(0, 16) }}</span>
        <el-tag v-if="r.kind === 'grow'" size="small" type="primary">生长观察</el-tag>
        <el-tag v-else size="small" :type="typeTag(r.type)">{{ typeName(r.type) }}</el-tag>
        <el-tag v-if="r.kind === 'grow' && r.assessment" size="small" :type="growTag(r.assessment)">{{ r.assessment }}</el-tag>
        <el-tag v-if="r.kind === 'orphan'" size="small" type="info">任务已删除</el-tag>
        <el-tag v-if="showPlant && r.kind !== 'grow' && r.plantName" size="small" type="success">{{ r.plantEmoji }} {{ r.plantName }}</el-tag>
        <span class="dr-ops">
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
// 完成记录列表（公共展示组件）：把「已完成的任务」和「生长观察记录」混在同一条时间线上展示。
defineProps<{ records: any[]; showPlant?: boolean }>()
// 两个事件都交给父页面处理：view 查看执行详情，delete 删除这条生长记录
defineEmits(['view', 'delete'])

// 任务类型的中文名。后端 reminder.type 共 7 种；type 为空说明原任务已被删除，显示「已删除任务」
function typeName(t: string) {
  return ({ growth: '生长提醒', solar: '节气', weather: '天气', custom: '农事任务', sensor: '环境告警', plant_advice: '种植建议', plant_pest: '虫害预防' } as any)[t] || (t ? t : '已删除任务')
}
// 各类任务对应的标签颜色，未知类型一律用灰色
function typeTag(t: string) {
  return ({ growth: 'warning', solar: 'success', weather: 'danger', custom: 'info', sensor: 'danger', plant_advice: 'primary', plant_pest: 'warning' } as any)[t] || 'info'
}
// 生长观察结论对应的标签颜色：正常/低于预期/优于预期
function growTag(a: string) {
  return ({ 正常: 'success', 低于预期: 'warning', 优于预期: 'primary' } as any)[a] || 'info'
}
// 农资用量在数据库里存的是 JSON 字符串，这里解析成数组渲染成一排小标签；解析失败按空数组处理
function parseInputs(s: any): any[] {
  try { return s ? JSON.parse(s) : [] } catch { return [] }
}
/** 照片：任务条目取 photos(JSON数组)，生长条目取 photo(单图) */
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
