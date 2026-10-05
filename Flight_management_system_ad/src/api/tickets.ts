import request from '@/utils/request'

/** 订座列表 */
export function getBookingList(params: {
  pnr?: string
  passengerName?: string
  flightNo?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/admin/tickets/bookings', { params })
}

/** 订座详情 */
export function getBookingDetail(pnr: string) {
  return request.get(`/admin/tickets/bookings/${pnr}`)
}

/** 运价列表 */
export function getFareList(params?: Record<string, any>) {
  return request.get('/admin/tickets/fares', { params })
}

/** 创建运价 */
export function createFare(data: Record<string, any>) {
  return request.post('/admin/tickets/fares', data)
}

/** 更新运价 */
export function updateFare(fareId: string, data: Record<string, any>) {
  return request.put(`/admin/tickets/fares/${fareId}`, data)
}

/** 退票列表 */
export function getRefundList(params?: Record<string, any>) {
  return request.get('/admin/tickets/refunds', { params })
}

/** 审核退票 */
export function approveRefund(refundId: string, data?: Record<string, any>) {
  return request.post(`/admin/tickets/refunds/${refundId}/approve`, data)
}

/** 改签列表 */
export function getChangeList(params?: Record<string, any>) {
  return request.get('/admin/tickets/changes', { params })
}

/** 审核改签 */
export function approveChange(changeId: string, data?: Record<string, any>) {
  return request.post(`/admin/tickets/changes/${changeId}/approve`, data)
}
