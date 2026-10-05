import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Router } from 'vue-router'

let _router: Router | null = null

export function injectRouter(router: Router) {
  _router = router
}

/**
 * 登录失效统一处理：清空登录态 + 单次提示 + 跳转登录页。
 *
 * 必须同时清空 Pinia 里的 token：只清 localStorage 时 store 的 token ref 仍是旧值，
 * isLoggedIn 依旧为 true，路由守卫会把 /login 又弹回 /home，形成
 * 401 → 跳登录 → 被弹回 → 再 401 的无限循环（表现为页面反复刷新、界面不显示）。
 * 另外做 1.5s 节流，避免并发请求同时 401 时连续弹窗。
 */
let lastAuthFailAt = 0
async function handleUnauthorized() {
  const now = Date.now()
  const shouldNotify = now - lastAuthFailAt > 1500
  lastAuthFailAt = now

  try {
    const { useUserStore } = await import('@/stores/user')
    useUserStore().logout()
  } catch {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
  }

  if (!shouldNotify) return
  ElMessage.error('登录已过期，请重新登录')

  const current = _router?.currentRoute.value
  if (_router && current && current.path !== '/login') {
    const query = current.fullPath && current.fullPath !== '/' ? { redirect: current.fullPath } : {}
    _router.push({ path: '/login', query })
  }
}

// API response structure matching backend Result<T>
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

// Paginated response structure matching backend doc section 1.4
export interface PaginatedData<T> {
  list: T[]
  pagination: {
    page: number
    pageSize: number
    total: number
    totalPages: number
  }
}

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor — attach JWT token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor — handle errors per error codes in doc section 1.3
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code !== 200) {
      // Handle specific error codes
      switch (res.code) {
        case 401:
          handleUnauthorized()
          break
        case 403:
          ElMessage.error('权限不足')
          break
        case 429:
          // 优先透传后端文案：短信验证码的冷却/日限提示会带具体秒数或分钟数，
          // 用一句写死的「请求过于频繁」会把有用的信息吞掉
          ElMessage.warning(res.message || '请求过于频繁，请稍后再试')
          break
        case 409:
          ElMessage.error('资源冲突')
          break
        case 10001:
          ElMessage.error('航班不存在')
          break
        case 10002:
          ElMessage.error('航班已满')
          break
        case 10003:
          ElMessage.error('座位已被占用')
          break
        case 10004:
          ElMessage.error('订单已过期')
          break
        case 10005:
          ElMessage.error('支付失败')
          break
        case 10006:
          ElMessage.error('退改签规则不允许')
          break
        case 20001:
          ElMessage.error('用户不存在')
          break
        case 20002:
          ElMessage.error('密码错误')
          break
        case 20003:
          ElMessage.error('验证码错误')
          break
        case 20004:
          ElMessage.error('证件已存在')
          break
        case 30001:
          ElMessage.error('机组资质不满足')
          break
        case 30002:
          ElMessage.error('排班冲突')
          break
        case 30003:
          ElMessage.error('飞行时限超限')
          break
        default:
          ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error) => {
    if (error.response) {
      switch (error.response.status) {
        case 401:
          handleUnauthorized()
          break
        case 403:
          ElMessage.error('权限不足')
          break
        case 404:
          ElMessage.error('资源不存在')
          break
        case 409:
          ElMessage.error('资源冲突')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(error.message || '网络错误')
      }
    } else {
      ElMessage.error('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  }
)

export default service
