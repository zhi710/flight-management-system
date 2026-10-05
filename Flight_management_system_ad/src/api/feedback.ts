import request from '@/utils/request'

// GET /admin/feedback — 反馈列表（可选 status）
export function getFeedbackList(params?: { status?: string }) {
  return request.get('/admin/feedback', { params })
}

// POST /admin/feedback/{id}/reply — 回复反馈
export function replyFeedback(id: string, reply: string) {
  return request.post(`/admin/feedback/${id}/reply`, { reply })
}
