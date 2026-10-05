import request from '@/utils/request'

// ==================== 用户管理 ====================
export function getUserList(params?: Record<string, any>) {
  return request.get('/admin/system/users', { params })
}

export function createUser(data: Record<string, any>) {
  return request.post('/admin/system/users', data)
}

export function updateUser(userId: string, data: Record<string, any>) {
  return request.put(`/admin/system/users/${userId}`, data)
}

export function disableUser(userId: string) {
  return request.post(`/admin/system/users/${userId}/disable`)
}

export function enableUser(userId: string) {
  return request.post(`/admin/system/users/${userId}/enable`)
}

export function resetPassword(userId: string) {
  return request.post(`/admin/system/users/${userId}/reset-password`)
}

// ==================== 角色管理 ====================
export function getRoleList(params?: Record<string, any>) {
  return request.get('/admin/system/roles', { params })
}

export function createRole(data: Record<string, any>) {
  return request.post('/admin/system/roles', data)
}

export function updateRole(roleId: string, data: Record<string, any>) {
  return request.put(`/admin/system/roles/${roleId}`, data)
}

export function deleteRole(roleId: string) {
  return request.delete(`/admin/system/roles/${roleId}`)
}

// ==================== 权限列表 ====================
export function getPermissionList() {
  return request.get('/admin/system/permissions')
}

// ==================== 基础数据 ====================
export function getMasterData(type: string, params?: Record<string, any>) {
  return request.get(`/admin/system/master-data/${type}`, { params })
}

export function createMasterData(type: string, data: Record<string, any>) {
  return request.post(`/admin/system/master-data/${type}`, data)
}

export function updateMasterData(type: string, id: string, data: Record<string, any>) {
  return request.put(`/admin/system/master-data/${type}/${id}`, data)
}

export function deleteMasterData(type: string, id: string) {
  return request.delete(`/admin/system/master-data/${type}/${id}`)
}

// ==================== 操作日志 ====================
export function getOperationLogs(params?: {
  operator?: string
  module?: string
  action?: string
  startDate?: string
  endDate?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/admin/system/logs', { params })
}

// ==================== 系统配置 ====================
export function getSystemConfig() {
  return request.get('/admin/system/config')
}

export function updateSystemConfig(data: Record<string, any>) {
  return request.put('/admin/system/config', data)
}

// ==================== 个人信息 ====================
export function getAdminProfile() {
  return request.get('/admin/system/profile')
}

export function updateAdminProfile(data: Record<string, any>) {
  return request.put('/admin/system/profile', data)
}

export function changeAdminPassword(data: { oldPassword: string; newPassword: string }) {
  return request.post('/admin/system/change-password', data)
}
