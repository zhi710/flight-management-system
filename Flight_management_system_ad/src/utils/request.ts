import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from './auth'
import router from '@/router'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截器 — 注入 Token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器 — 统一错误处理
service.interceptors.response.use(
  (response: AxiosResponse) => {
    // 二进制响应(如文件导出)直接返回
    if (response.config.responseType === 'blob') {
      return response.data
    }

    const { code, message, data } = response.data

    if (code === 200) {
      return data
    }

    // Token 过期
    if (code === 401) {
      clearAuth()
      router.push('/login')
      const err = new Error(message)
      ;(err as any).handled = true
      ElMessage.error('登录已过期，请重新登录')
      return Promise.reject(err)
    }

    // 权限不足
    if (code === 403) {
      const err = new Error(message)
      ;(err as any).handled = true
      ElMessage.error('权限不足，无法执行此操作')
      return Promise.reject(err)
    }

    const bizErr = new Error(message)
    ;(bizErr as any).handled = true
    ElMessage.error(message || '请求失败')
    return Promise.reject(bizErr)
  },
  (error) => {
    ;(error as any).handled = true
    if (error.response) {
      const { status } = error.response
      if (status === 401) {
        clearAuth()
        router.push('/login')
        ElMessage.error('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error('权限不足')
      } else if (status === 429) {
        ElMessage.warning('请求过于频繁，请稍后再试')
      } else if (status >= 500) {
        ElMessage.error('服务器异常，请稍后再试')
      } else {
        ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络连接异常')
    }
    return Promise.reject(error)
  },
)

export default service
