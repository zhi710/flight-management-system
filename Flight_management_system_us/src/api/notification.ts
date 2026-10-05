import request from '@/utils/request'

export function getNotificationList(page = 1, pageSize = 50) {
  return request.get('/notifications', { params: { page, pageSize } })
}

// POST /notifications/read/{id} — 标记单条已读
export function markNotificationRead(id: string) {
  return request.post(`/notifications/read/${id}`)
}

// POST /notifications/read-all — 全部已读
export function markAllNotificationsRead() {
  return request.post('/notifications/read-all')
}
