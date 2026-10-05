import request from '@/utils/request'

/** 不正常航班操作记录列表 */
export function getIropList(params: {
  flightNo?: string
  type?: string
  status?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/admin/irop/operations', { params })
}

/** 受影响旅客列表 */
export function getAffectedPassengers(flightId: string) {
  return request.get(`/admin/irop/passengers/${flightId}`)
}

/** 自动改签结果列表 */
export function getRebookingList(params: {
  flightId?: string
  status?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/admin/irop/rebookings', { params })
}

/** 确认/拒绝自动改签 */
export function confirmRebooking(rebookingId: string, action: string) {
  return request.post(`/admin/irop/rebookings/${rebookingId}/confirm`, { action })
}

/** 手动改签 */
export function manualRebook(data: {
  orderId: string
  newFlightId: string
  newCabinClass: string
}) {
  return request.post('/admin/irop/manual-rebook', data)
}

/** 重新通知受影响旅客 */
export function notifyIropPassengers(operationId: string) {
  return request.post(`/admin/irop/operations/${operationId}/notify`)
}

/** 补偿规则 */
export function getCompensationRules() {
  return request.get('/admin/irop/compensation-rules')
}

/** IATA延误原因代码 */
export function getIataDelayCodes() {
  return request.get('/admin/irop/delay-codes')
}
