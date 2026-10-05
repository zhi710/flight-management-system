import request from '@/utils/request'

export function getSsrCodes(category?: string) {
  return request.get('/admin/ssr/codes', { params: { category } })
}

export function getSsrList(params: {
  flightId?: string
  status?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/admin/ssr/requests', { params })
}

export function submitSsrRequest(data: {
  orderId: string
  orderPassengerId: string
  ssrCode: string
  remark?: string
}) {
  return request.post('/admin/ssr/submit', data)
}

export function processSsrRequest(requestId: string, action: string) {
  return request.post(`/admin/ssr/requests/${requestId}/process`, { action })
}

export function getSsrByOrder(orderId: string) {
  return request.get(`/admin/ssr/orders/${orderId}`)
}
