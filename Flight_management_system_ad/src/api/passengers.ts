import request from '@/utils/request'

/** 旅客查询 */
export function getPassengerList(params: {
  name?: string
  idNumber?: string
  flightNo?: string
  dateRange?: string[]
  pnr?: string
  phone?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/admin/passengers', { params })
}

/** 旅客详情（含历史行程） */
export function getPassengerDetail(passengerId: string) {
  return request.get(`/admin/passengers/${passengerId}`)
}

/** 获取航班值机信息 */
export function getCheckinInfo(flightId: string) {
  return request.get(`/admin/checkin/${flightId}`)
}

/** 开放值机 */
export function openCheckin(flightId: string) {
  return request.post(`/admin/checkin/${flightId}/open`)
}

/** 关闭值机 */
export function closeCheckin(flightId: string) {
  return request.post(`/admin/checkin/${flightId}/close`)
}

/** 手动值机 */
export function manualCheckin(flightId: string, data: {
  passengerIndex: number
  seatRow: number
  seatColumn: string
}) {
  return request.post(`/admin/checkin/${flightId}/checkin`, data)
}

/** 取消值机 */
export function cancelCheckin(flightId: string, data: { passengerIndex: number }) {
  return request.post(`/admin/checkin/${flightId}/cancel-checkin`, data)
}

/** 自动分配座位 */
export function autoAssignSeats(flightId: string) {
  return request.post(`/admin/checkin/${flightId}/auto-assign`)
}

/** 导出值机名单 */
export function exportCheckinList(flightId: string) {
  return request.get(`/admin/checkin/${flightId}/export`, { responseType: 'blob' })
}

/** 获取登机口列表 */
export function getGateList(params: { terminal?: string; date?: string }) {
  return request.get('/admin/gates', { params })
}

/** 分配登机口（按航班实例 ID + 时段） */
export function assignGate(data: {
  gateCode: string
  flightId: string
  startTime: string
  endTime: string
}) {
  return request.post('/admin/gates/assign', data)
}

/** 释放登机口（按分配记录） */
export function releaseGate(data: { assignmentId: string }) {
  return request.post('/admin/gates/release', data)
}

/** 根据航班号查询航班基本信息（日期可选，不传则查最近未来航班） */
export function lookupFlight(flightNo: string, date?: string) {
  const params: Record<string, string> = { flightNo }
  if (date) params.date = date
  return request.get('/admin/flights/lookup', { params })
}

/** 获取航班座位图 */
export function getSeatMap(flightId: string) {
  return request.get(`/admin/seats/${flightId}`)
}

/** 锁定座位 */
export function lockSeat(flightId: string, data: { row: number; column: string }) {
  return request.post(`/admin/seats/${flightId}/lock`, data)
}

/** 解锁座位 */
export function unlockSeat(flightId: string, data: { row: number; column: string }) {
  return request.post(`/admin/seats/${flightId}/unlock`, data)
}
