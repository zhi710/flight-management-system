import request from '@/utils/request'

// GET /checkin/available — doc section 2.6.1
export function getAvailableCheckin() {
  return request.get('/checkin/available')
}

// GET /checkin/seats/{flightId} — doc section 2.6.2
export function getSeatMap(flightId: string) {
  return request.get(`/checkin/seats/${flightId}`)
}

// POST /checkin/seats — 后端使用 @RequestBody SeatSelectDTO 接收
export function selectSeat(data: {
  orderId: string
  passengerIndex: number
  row: number
  column: string
}) {
  return request.post('/checkin/seats', data)
}

// POST /checkin — doc section 2.6.4
export function doCheckin(data: {
  orderId: string
  passengers: Array<{
    passengerIndex: number
    seatRow: number
    seatColumn: string
  }>
}) {
  return request.post('/checkin', data)
}

// GET /checkin/boarding-pass/{checkinId} — doc section 2.6.5
export function getBoardingPass(checkinId: string) {
  return request.get(`/checkin/boarding-pass/${checkinId}`)
}

// POST /checkin/{orderId}/cancel — 取消值机
export function cancelCheckin(orderId: string) {
  return request.post(`/checkin/${orderId}/cancel`)
}
