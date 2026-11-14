// 全局 HTTP 客户端（axios 封装）：管理端所有页面都通过它请求后端。
import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 全站唯一的 axios 实例：统一前缀 /api、统一 15 秒超时
const instance = axios.create({ baseURL: '/api', timeout: 15000 })

// 请求拦截器：登录时 token 存进了 localStorage，这里自动补 Authorization 头，页面里不用自己拼
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截器：后端统一返回 { code, msg, data } 三段式，这里按 code 分流
instance.interceptors.response.use(
  (resp) => {
    const r = resp.data
    // code=200 表示业务成功：直接把 data 拆出来返回，页面 await 到的就是数据本身（不含 code/msg）
    if (r.code === 200) return r.data
    // code=401 表示未登录或 token 失效：清掉本地登录态并跳回登录页
    if (r.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
    }
    // 其余业务错误：弹一句后端返回的提示，并让 Promise 失败（页面可不写 catch，错误已被提示过）
    ElMessage.error(r.msg || '请求失败')
    return Promise.reject(new Error(r.msg))
  },
  // 网络层异常（后端没启动、超时、跨域失败、页面切走被浏览器中止）走这一支
  (err) => {
    const cfg: any = err?.config
    const isNetwork = err?.code === 'ERR_NETWORK' || err?.message === 'Network Error'
    // GET 先静默重试一次：切页面/网络抖动造成的一次性失败，用户无感；POST/PUT/DELETE 不重试，避免重复提交
    if (isNetwork && cfg && String(cfg.method || '').toLowerCase() === 'get' && !cfg.__retried) {
      cfg.__retried = true
      return instance.request(cfg)
    }
    // 仍失败才提示，并带上接口路径（原来只弹 axios 英文原文，看不出是哪条请求）
    ElMessage.error(`网络中断：${cfg?.url || '未知接口'}`)
    return Promise.reject(err)
  },
)

/** 类型化封装：拦截器已解包 data，返回类型直接是响应数据 */
// 对外导出的就是这四个方法（get/post/put/delete），泛型 T 写接口返回的数据类型，不写则默认 any
const http = {
  get: <T = any>(url: string, config?: AxiosRequestConfig) => instance.get(url, config) as Promise<T>,
  post: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) => instance.post(url, data, config) as Promise<T>,
  put: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) => instance.put(url, data, config) as Promise<T>,
  delete: <T = any>(url: string, config?: AxiosRequestConfig) => instance.delete(url, config) as Promise<T>,
}

export default http
