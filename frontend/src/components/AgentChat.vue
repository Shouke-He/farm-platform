<!-- 智能助手浮窗（用户端）。 -->
<template>
  <div class="agent-fab" @click="open = !open">
    <el-icon v-if="!open"><ChatDotRound /></el-icon>
    <el-icon v-else><Close /></el-icon>
  </div>

  <div v-show="open" class="agent-panel">
    <div class="agent-head">
      <span>农事助手</span>
      <!-- 离线模式标记：降级时必须显示出来，不许藏 —— 用缓存冒充实时结果是欺骗 -->
      <span v-if="offline" class="agent-offline">离线模式</span>
    </div>

    <div ref="listRef" class="agent-list">
      <div v-if="!msgs.length" class="agent-tip">
        问点关于你盆栽的事，比如「我薄荷现在的土壤湿度是多少？」
      </div>
      <div v-for="(m, i) in msgs" :key="i" :class="['msg', m.role]">
        <div class="bubble">{{ m.text }}</div>
        <div v-if="showTools && m.tools && m.tools.length" class="tools">调用了：{{ m.tools.join('、') }}</div>
        <!-- 读数闸门的诊断行：同样只在 ?agentDebug=1 下显示，默认态一个字都不出现 -->
        <div v-if="showTools && guardNote(m)" class="guard-note">{{ guardNote(m) }}</div>
      </div>
      <div v-if="loading" class="msg assistant"><div class="bubble">思考中…</div></div>
    </div>

    <div class="agent-input">
      <el-input v-model="draft" placeholder="问点关于你盆栽的事…" @keyup.enter="send" :disabled="loading" />
      <el-button type="primary" :loading="loading" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import http from '../api'

interface Msg { role: 'user' | 'assistant'; text: string; tools?: string[]; guard?: string }

const open = ref(false)
const draft = ref('')
const loading = ref(false)
const offline = ref(false)
const msgs = ref<Msg[]>([])
const listRef = ref<HTMLElement>()
// 一个页面会话一个 id；刷新页面就换新会话（历史本来就在后端内存里，重启即清）
const conversationId = `c${Date.now()}${Math.floor(Math.random() * 1000)}`

/** 「调用了：xxx」这行**默认不给终端用户看** —— getSensorReading 这种函数名属于实现细节， */
const showTools = new URLSearchParams(location.search).get('agentDebug') === '1'

/** 把后端的读数闸门结论翻译成一行给人看的诊断（只在 ?agentDebug=1 下出现）。 */
function guardNote(m: Msg): string {
  if (m.guard === 'hit') return '⚠️ 读数闸门命中：本轮未调用任何工具却给出了读数'
  if (m.guard === 'replaced') return '⚠️ 读数闸门命中且重试仍在编：这条读数是系统直接查库替换的，不是模型生成的'
  if (m.guard === 'no-tool') return '本轮未调用任何工具'
  return ''
}

async function send() {   // 【浮窗发问】
  const text = draft.value.trim()
  if (!text || loading.value) return
  msgs.value.push({ role: 'user', text })
  draft.value = ''
  loading.value = true
  await scrollToEnd()
  try {
    const r = await http.post<{ reply: string; toolsUsed: string[]; degraded: boolean; guard: string }>(
      '/agent/chat',
      { conversationId, message: text },
      { timeout: 60000 },   // 覆盖全局 15s：大模型 + 工具调用必然更慢
    )
    offline.value = r.degraded
    msgs.value.push({ role: 'assistant', text: r.reply, tools: r.toolsUsed, guard: r.guard })   // 【流式】
  } catch (e: any) {
    // 网络层异常：标记离线并给出可读提示（后端不可达、或助手开关关闭时真的会走到这）
    offline.value = true
    msgs.value.push({ role: 'assistant', text: '智能助手连不上了，系统其它功能不受影响。' })
    ElMessage.warning('智能助手暂时不可用')
  } finally {
    loading.value = false
    await scrollToEnd()
  }
}

async function scrollToEnd() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}
</script>

<style scoped>
.agent-fab {
  position: fixed; right: 28px; bottom: 28px; width: 52px; height: 52px;
  border-radius: 50%; background: #3f7d4c; color: #fff; display: flex;
  align-items: center; justify-content: center; font-size: 22px;
  cursor: pointer; box-shadow: 0 4px 14px rgba(63, 125, 76, .45); z-index: 2000;
}
.agent-panel {
  position: fixed; right: 28px; bottom: 92px; width: 380px; height: 480px;
  /* 视口矮时自动收缩，保证顶部标题栏（含「离线模式」标记）始终可见 —— */
  max-height: calc(100vh - 140px);
  background: #fff; border-radius: 10px; box-shadow: 0 8px 30px rgba(0, 0, 0, .18);
  display: flex; flex-direction: column; z-index: 2000; overflow: hidden;
}
.agent-head {
  padding: 12px 16px; font-weight: 600; border-bottom: 1px solid #eee;
  display: flex; justify-content: space-between; align-items: center;
}
.agent-offline {
  font-size: 12px; font-weight: 400; color: #e6a23c;
  border: 1px solid #e6a23c; border-radius: 4px; padding: 1px 6px;
}
.agent-list { flex: 1; overflow-y: auto; padding: 12px; }
.agent-tip { color: #909399; font-size: 13px; line-height: 1.7; padding: 8px 4px; }
.msg { margin-bottom: 10px; display: flex; flex-direction: column; }
.msg.user { align-items: flex-end; }
.bubble {
  max-width: 78%; padding: 8px 12px; border-radius: 8px;
  background: #f4f4f5; line-height: 1.6; white-space: pre-wrap; word-break: break-word;
}
.msg.user .bubble { background: #3f7d4c; color: #fff; }
.tools { font-size: 12px; color: #909399; margin-top: 4px; }
/* 诊断行用橙色区分于灰色的「调用了」行：一眼能看出这是告警而不是常规信息 */
.guard-note { font-size: 12px; color: #e6a23c; margin-top: 4px; }
.agent-input { display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid #eee; }
</style>
