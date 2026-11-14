// 全局请求入口（axios 封装）：前端所有访问后端的请求都从这一个文件发出去。
import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// axios 实例：所有请求走 /api 前缀，超时 15 秒（15000 毫秒）
const instance = axios.create({ baseURL: '/api', timeout: 15000 })   // 【统一出口】

// 请求拦截器：发请求前从 localStorage 取出登录令牌，塞进请求头，
instance.interceptors.request.use((config) => {   // 【自动带令牌】
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

instance.interceptors.response.use(
  // 后端约定所有接口都返回 { code, msg, data } 三层结构
  (resp) => {   // 【401清登录】
    const r = resp.data
    if (r.code === 200) return r.data
    // 401 = 未登录或令牌已过期：清掉本地登录信息并跳回登录页
    if (r.code === 401) {   // 【令牌过期】
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
    }
    // 其余业务错误码：弹红条提示后端给的 msg
    ElMessage.error(r.msg || '请求失败')
    return Promise.reject(new Error(r.msg))
  },
  // 网络层异常（后端没启动、断网、超时、页面切走被浏览器中止）走这个分支
  (err) => {
    const cfg: any = err?.config
    const isNetwork = err?.code === 'ERR_NETWORK' || err?.message === 'Network Error'
    // GET 先静默重试一次：切页面/网络抖动造成的一次性失败，用户无感；
    // POST/PUT/DELETE 不重试，避免重复提交。
    if (isNetwork && cfg && String(cfg.method || '').toLowerCase() === 'get' && !cfg.__retried) {
      cfg.__retried = true
      return instance.request(cfg)
    }
    // 仍失败才提示，并带上接口路径：原来只弹 axios 的英文原文，看不出是哪条请求
    ElMessage.error(`网络中断：${cfg?.url || '未知接口'}`)
    return Promise.reject(err)
  },
)

/** 类型化封装：拦截器已解包 data，返回类型直接是响应数据 */
// 对外导出的四个方法，页面按需取用：http.get / http.post / http.put / http.delete
const http = {
  get: <T = any>(url: string, config?: AxiosRequestConfig) => instance.get(url, config) as Promise<T>,
  post: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) => instance.post(url, data, config) as Promise<T>,
  put: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) => instance.put(url, data, config) as Promise<T>,
  delete: <T = any>(url: string, config?: AxiosRequestConfig) => instance.delete(url, config) as Promise<T>,
}

export default http
