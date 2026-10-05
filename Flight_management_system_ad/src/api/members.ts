import request from '@/utils/request'

// ==================== 前台用户（旅客会员）管理 ====================
export function getMemberList(params?: Record<string, any>) {
  return request.get('/admin/members', { params })
}

export function updateMember(id: string, data: Record<string, any>) {
  return request.put(`/admin/members/${id}`, data)
}

export function disableMember(id: string) {
  return request.post(`/admin/members/${id}/disable`)
}

export function enableMember(id: string) {
  return request.post(`/admin/members/${id}/enable`)
}

export function resetMemberPassword(id: string) {
  return request.post(`/admin/members/${id}/reset-password`)
}
