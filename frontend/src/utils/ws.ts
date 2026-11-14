/** WebSocket 实时消息管理器（单例） */
// === 这个文件解决的问题：后端怎么主动找前端 ===

// 当前连接句柄；重连定时器；已重试次数
let ws: WebSocket | null = null
let retryTimer: number | null = null
let retries = 0
// 消息订阅者集合：谁想接收推送就注册进来，一条消息会广播给所有订阅者
const listeners = new Set<(msg: any) => void>()
// 还没有订阅者时收到的消息先暂存在这里（2026-10-16 加）。
// 为什么：登录成功那一刻就 connectWs()，而后端「上线补推」是一连上就推 ——
// 此时 Layout 还没挂载、listeners 是空的，那批补推会被白白丢掉（实测就是这么丢的）。
// 现在改成：没人听就先存着，第一个订阅者注册时按到达顺序补发给他。
const pendingMsgs: any[] = []

// 建立连接（登录成功后调用）。没有 token 直接返回；
export function connectWs() {   // 【浏览器收】
  const token = localStorage.getItem('token')
  if (!token) return
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
  // 页面走 https 时对应要用 wss，否则用 ws
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`)
  ws.onopen = () => {
    // 连上了就把重试计数清零，下次断线又是从头 5 次
    retries = 0
  }
  // 收到后端推送：解析 JSON 后依次广播给所有订阅者；解析失败（非法消息）就丢弃
  ws.onmessage = (e) => {   // 【浏览器收】
    try {
      const msg = JSON.parse(e.data)
      // 还没有订阅者（登录后 Layout 尚未挂载）→ 先存着，等订阅者来了补发；最多留 20 条
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
  // 连接被关闭：置空句柄；未超过 5 次就延时重连，间隔随重试次数递增（3s、6s、9s…）
  ws.onclose = () => {
    ws = null
    if (retries < 5) {
      retryTimer = window.setTimeout(() => connectWs(), 3000 * (retries + 1))
      retries++
    }
  }
  // 出错时主动关闭，统一交给上面的 onclose 走重连逻辑
  ws.onerror = () => {
    ws?.close()
  }
}

// 断开连接并清空订阅（退出登录时调用）：
export function closeWs() {
  if (retryTimer !== null) clearTimeout(retryTimer)
  retryTimer = null
  ws?.close()
  ws = null
  listeners.clear()
}

// 订阅后端推送：页面把处理函数传进来，返回一个「取消订阅」的函数，
export function onWsMessage(fn: (msg: any) => void) {
  listeners.add(fn)
  // 把「订阅之前」收到的消息补发给他：按到达顺序补发，最后一条会排在最前（未读角标也一起补）
  if (pendingMsgs.length) pendingMsgs.splice(0).forEach((m) => fn(m))
  return () => listeners.delete(fn)
}
