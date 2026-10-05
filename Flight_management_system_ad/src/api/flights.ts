import request from '@/utils/request'

/** 航班计划列表 */
export function getFlightList(params: {
  date?: string
  route?: string
  status?: string
  airline?: string
  keyword?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/admin/flights', { params })
}

/** 创建航班 */
export function createFlight(data: {
  flightNo: string
  date: string
  route: { departure: string; arrival: string }
  schedule: { departureTime: string; arrivalTime: string }
  aircraft: { type: string; registration: string }
  flightType: string
  airlineId?: number
  stops?: any[]
  remark?: string
}) {
  return request.post('/admin/flights', data)
}

/** 编辑航班 */
export function updateFlight(flightId: string, data: Record<string, any>) {
  return request.put(`/admin/flights/${flightId}`, data)
}

/** 删除航班 */
export function deleteFlight(flightId: string) {
  return request.delete(`/admin/flights/${flightId}`)
}

/** 批量操作 */
export function batchFlightAction(data: {
  flightIds: string[]
  action: string
  params?: Record<string, any>
}) {
  return request.post('/admin/flights/batch', data)
}

/** 航班时刻表 */
export function getFlightSchedule(params: {
  view: string
  startDate: string
  endDate: string
  route?: string
}) {
  return request.get('/admin/flights/schedule', { params })
}

/** 不正常航班处理 */
export function handleIrregularFlight(flightId: string, data: {
  type: string
  reason: string
  newSchedule?: Record<string, any>
  notifyPassengers: boolean
  arrangements?: Record<string, any>
}) {
  return request.post(`/admin/flights/${flightId}/irregular`, data)
}

/** 航班日志 */
export function getFlightLogs(flightId: string) {
  return request.get(`/admin/flights/${flightId}/logs`)
}

/** 航线地图数据（机场点位 + 航线连线聚合） */
export function getRouteMap(params?: { date?: string }) {
  return request.get('/admin/flights/route-map', { params })
}

/** 获取航班舱位列表 */
export function getFlightCabins(flightId: string) {
  return request.get(`/admin/flights/${flightId}/cabins`)
}

/** 更新航班舱位 */
export function updateFlightCabin(flightId: string, cabinId: string, data: Record<string, any>) {
  return request.put(`/admin/flights/${flightId}/cabins/${cabinId}`, data)
}
