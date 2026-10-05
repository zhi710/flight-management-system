import request from '@/utils/request'

// GET /help/faq — doc section 2.9.1
export function getFaqList(params?: {
  category?: string
  keyword?: string
}) {
  return request.get('/help/faq', { params })
}

// GET /help/refund-policy — doc section 2.9.2
export function getRefundPolicy() {
  return request.get('/help/refund-policy')
}

// GET /help/baggage-rules — doc section 2.9.3
export function getBaggageRules() {
  return request.get('/help/baggage-rules')
}

// POST /help/feedback — doc section 2.9.4
export function submitFeedback(data: {
  type: 'COMPLAINT' | 'SUGGESTION'
  orderId?: string
  content: string
  contactPhone?: string
  attachments?: string[]
}) {
  return request.post('/help/feedback', data)
}

// GET /help/feedback — 我的反馈列表
export function getMyFeedback() {
  return request.get('/help/feedback')
}
