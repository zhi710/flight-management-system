import request from '@/utils/request'

// POST /payments — doc section 2.4.1
export function createPayment(data: {
  orderId: string
  payMethod: string
  amount: number
}) {
  return request.post('/payments', data)
}

// GET /payments/{paymentId} — doc section 2.4.2
export function getPaymentStatus(paymentId: string) {
  return request.get(`/payments/${paymentId}`)
}

// POST /payments/change/{changeId} — 改签补款
// 改签审核通过但需补款时，改签单进入 PENDING_PAYMENT，旅客需在此完成支付后行程才变更。
// payMethod 不传则由后端沿用原订单的支付渠道（原路补款）。
export function createChangePayment(changeId: string, payMethod?: string) {
  return request.post(`/payments/change/${changeId}`, payMethod ? { payMethod } : {})
}

// POST /payments/{paymentId}/simulate — 模拟支付完成（演示环境用）
export function simulatePay(paymentId: string) {
  return request.post(`/payments/${paymentId}/simulate`)
}
