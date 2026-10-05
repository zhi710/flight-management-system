import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, removeToken, setRefreshToken, setUserInfo, getUserInfo, clearAuth } from '@/utils/auth'
import { adminLogin } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getToken())
  const userInfo = ref<Record<string, any>>(getUserInfo())

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value.username || '')
  const name = computed(() => userInfo.value.name || '')
  const roles = computed(() => userInfo.value.roles || [])
  const permissions = computed(() => userInfo.value.permissions || [])
  const avatar = computed(() => userInfo.value.avatar || '')

  async function login(loginForm: { username: string; password: string; mfaCode?: string }) {
    const data = await adminLogin(loginForm) as any
    token.value = data.token
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      name: data.name,
      roles: data.roles,
      permissions: data.permissions,
    }
    setToken(data.token)
    setRefreshToken(data.refreshToken || '')
    setUserInfo(userInfo.value)
    return data
  }

  function logout() {
    token.value = ''
    userInfo.value = {}
    clearAuth()
  }

  function hasPermission(perm: string): boolean {
    // 超级管理员（库中 code 为 SUPER_ADMIN，历史兼容 ADMIN）直接放行
    if (roles.value.includes('SUPER_ADMIN') || roles.value.includes('ADMIN')) return true
    return permissions.value.includes(perm)
  }

  return { token, userInfo, isLoggedIn, username, name, roles, permissions, avatar, login, logout, hasPermission }
})
