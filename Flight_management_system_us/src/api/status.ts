import request from '@/utils/request'

// GET /flight-status — doc section 2.7.1
export function getFlightStatus(params: {
  flightNo?: string
  date: string
  departure?: string
  arrival?: string
}) {
  return request.get('/flight-status', { params })
}

// POST /flight-status/subscribe — 后端使用 @RequestBody FlightSubscribeDTO 接收
export function subscribeFlight(data: {
  flightNo: string
  date: string
  channels: string[]
  types: string[]
}) {
  return request.post('/flight-status/subscribe', data)
}

// DELETE /flight-status/subscribe/{subscriptionId} — doc section 2.7.3
export function unsubscribeFlight(subscriptionId: string) {
  return request.delete(`/flight-status/subscribe/${subscriptionId}`)
}

// GET /flight-status/subscriptions — 我的订阅列表
export function getMySubscriptions() {
  return request.get('/flight-status/subscriptions')
}
