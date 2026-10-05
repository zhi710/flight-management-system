import request from '@/utils/request'

/** 管理员登录 */
export function adminLogin(data: { username: string; password: string; mfaCode?: string }) {
  return request.post('/admin/auth/login', data)
}
