/** WebSocket 实时消息管理器（单例） */
// 使用方式（全项目只用到这些）：
let ws: WebSocket | null = null
// 重连定时器句柄（配合下面的重试次数，实现「断了自动再连」）
let retryTimer: number | null = null
// 已经重试过几次：连上就清零，最多重试 5 次
let retries = 0
// 消息订阅者集合：谁想看实时消息就往里注册一个函数（Layout.vue 是唯一注册者）
const listeners = new Set<(msg: any) => void>()
// 还没有订阅者时收到的消息先暂存在这里（2026-10-16 加，与用户端同一处理）：
// 登录那一刻就连了 WS，而 Layout 还没挂载、listeners 是空的，此时到达的推送会被丢掉。
const pendingMsgs: any[] = []

// 建立连接（可重复调用，内部有去重判断）
export function connectWs() {
  const token = localStorage.getItem('token')
  if (!token) return // 没登录就没必要连
  // 已经连上或正在连 → 直接返回，避免重复建连
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
  // 网页是 https 时用 wss，否则用 ws；地址与页面同源，由 Vite 把 /ws 代理到后端 8080
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  // token 放在查询参数里，后端在握手阶段取出来校验身份
  ws = new WebSocket(`${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`)
  // 连上就清零重试计数：下次真断了还能重新享受 5 次重连机会
  ws.onopen = () => {
    retries = 0
  }
  // 收到后端推送：文本是 JSON，解析后逐个通知订阅者（解析失败的脏消息直接丢弃）
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      // 还没有订阅者 → 先存着，订阅者来了补发；最多留 20 条
      if (listeners.size === 0) {
        pendingMsgs.push(msg)
        if (pendingMsgs.length > 20) pendingMsgs.shift()
        return
      }
      listeners.forEach((fn) => fn(msg))
    } catch {
      /* 忽略非法消息 */
    }
  }
  // 连接关闭：断线自动重连，最多 5 次，等待时间逐次加长（3s、6s、9s、12s、15s）避免频繁重试
  ws.onclose = () => {
    ws = null
    if (retries < 5) {
      retryTimer = window.setTimeout(() => connectWs(), 3000 * (retries + 1))
      retries++
    }
  }
  // 出错时主动关掉，交给上面的 onclose 去安排重连
  ws.onerror = () => {
    ws?.close()
  }
}

// 关闭连接并清场：清掉待执行的重连定时器（否则关了还会自动连回来）、断开套接字、清空订阅者
export function closeWs() {
  if (retryTimer !== null) clearTimeout(retryTimer)
  retryTimer = null
  ws?.close()
  ws = null
  listeners.clear()
}

// 订阅实时消息：把回调函数登记进 listeners，每来一条消息就会被调用一次
export function onWsMessage(fn: (msg: any) => void) {
  listeners.add(fn)
  // 把「订阅之前」收到的消息按到达顺序补发给他
  if (pendingMsgs.length) pendingMsgs.splice(0).forEach((m) => fn(m))
  return () => listeners.delete(fn)
}
