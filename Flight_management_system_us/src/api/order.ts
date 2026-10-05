import request from '@/utils/request'

// POST /orders — doc section 2.3.1
export function createOrder(data: {
  searchId: string
  flightId: string
  cabinClass: string
  passengers: Array<{
    name: string
    gender: string
    birthday?: string
    idType: string
    idNumber: string
    phone?: string
    email?: string
    frequentFlyerNo?: string
    passengerType: string
  }>
  contactInfo: {
    name: string
    phone: string
    email?: string
  }
  services?: Array<{
    type: string
    code: string
    quantity: number
    passengerIndex: number
  }>
}) {
  return request.post('/orders', data)
}

// GET /orders/{orderId} — doc section 2.3.2
export function getOrderDetail(orderId: string) {
  return request.get(`/orders/${orderId}`)
}

// GET /orders — doc section 2.3.3
export function getOrderList(params?: {
  status?: string
  startDate?: string
  endDate?: string
  keyword?: string
}) {
  return request.get('/orders', { params })
}

// POST /orders/{orderId}/cancel — 后端使用 @RequestBody Map 接收 reason
export function cancelOrder(orderId: string, reason?: string) {
  return request.post(`/orders/${orderId}/cancel`, { reason })
}

// POST /orders/{orderId}/change — 后端 @RequestBody OrderChangeDTO
export function applyChange(orderId: string, data: {
  segmentIndex: number
  newFlightId: string
  newCabinClass: string
  passengers?: number[]
}) {
  return request.post(`/orders/${orderId}/change`, data)
}

// POST /orders/{orderId}/refund — 后端 @RequestBody OrderRefundDTO
export function applyRefund(orderId: string, data: {
  reason: string
  passengers?: number[]
}) {
  return request.post(`/orders/${orderId}/refund`, data)
}

// GET /orders/{orderId}/change/{changeId} — doc section 2.5.3
export function getChangeStatus(orderId: string, changeId: string) {
  return request.get(`/orders/${orderId}/change/${changeId}`)
}

// GET /orders/{orderId}/refund/{refundId} — doc section 2.5.3
export function getRefundStatus(orderId: string, refundId: string) {
  return request.get(`/orders/${orderId}/refund/${refundId}`)
}

// GET /orders/changes — 我的改签记录列表
export function getMyChanges() {
  return request.get('/orders/changes')
}

// GET /orders/refunds — 我的退票记录列表
export function getMyRefunds() {
  return request.get('/orders/refunds')
}
