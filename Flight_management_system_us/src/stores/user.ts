import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import * as memberApi from '@/api/member'

export interface UserInfo {
  userId: string
  name: string
  phone: string
  avatar: string
  memberLevel: string
  miles?: number
  memberNo?: string
  /** 实名证件类型，未实名时为空 */
  idType?: string
  /** 实名证件号，未实名时为空 */
  idNumber?: string
  /** 是否已完成实名认证 —— 购票的前置条件 */
  realnameVerified?: boolean
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const refreshToken = ref<string>(localStorage.getItem('refreshToken') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(phone: string, password: string, captcha?: string) {
    const res = await authApi.login({ phone, password, captcha })
    setAuth(res.data)
    return res.data
  }

  async function loginBySms(phone: string, smsCode: string) {
    const res = await authApi.loginBySms({ phone, smsCode })
    setAuth(res.data)
    return res.data
  }

  async function register(data: { phone: string; smsCode: string; password: string; name?: string }) {
    const res = await authApi.register(data)
    setAuth(res.data)
    return res.data
  }

  function setAuth(data: any) {
    // 后端返回的 token 字段名可能是 token 或 accessToken
    const authToken = data.token || data.accessToken || ''
    const authRefreshToken = data.refreshToken || ''
    token.value = authToken
    refreshToken.value = authRefreshToken
    localStorage.setItem('token', authToken)
    localStorage.setItem('refreshToken', authRefreshToken)

    const userId = data.userId || ''
    userInfo.value = {
      userId,
      name: data.name || '',
      phone: data.phone || '',
      avatar: data.avatar || '',
      memberLevel: data.memberLevel || '',
    }
    localStorage.setItem('userId', userId)
  }

  async function fetchProfile() {
    try {
      const res = await memberApi.getProfile()
      userInfo.value = {
        userId: res.data.userId,
        name: res.data.name,
        phone: res.data.phone,
        avatar: res.data.avatar,
        memberLevel: res.data.memberLevel,
        miles: res.data.miles,
        memberNo: res.data.memberNo,
        idType: res.data.idType || '',
        idNumber: res.data.idNumber || '',
        realnameVerified: !!res.data.realnameVerified,
      }
      return res.data
    } catch {
      console.warn('Failed to fetch user profile')
    }
  }

  function logout() {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
  }

  async function refreshAccessToken() {
    try {
      const res = await authApi.refreshToken(refreshToken.value)
      const newToken = res.data.token || res.data.accessToken || ''
      const newRefreshToken = res.data.refreshToken || ''
      token.value = newToken
      refreshToken.value = newRefreshToken
      localStorage.setItem('token', newToken)
      localStorage.setItem('refreshToken', newRefreshToken)
      return true
    } catch {
      logout()
      return false
    }
  }

  return {
    token,
    refreshToken,
    userInfo,
    isLoggedIn,
    login,
    loginBySms,
    register,
    fetchProfile,
    logout,
    refreshAccessToken,
  }
})
