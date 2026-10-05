import request from '@/utils/request'

export function getGroundTodaySummary() {
  return request.get('/admin/ground/today')
}

export function getGroundNodes(flightId: string) {
  return request.get(`/admin/ground/nodes/${flightId}`)
}

export function getGroundList(params: {
  flightNo?: string
  date?: string
  status?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/admin/ground', { params })
}

export function updateNodeTime(nodeId: string, field: string, value: string) {
  return request.put(`/admin/ground/nodes/${nodeId}/time`, { field, value })
}

export function getFlightGroundSummary(date?: string) {
  return request.get('/admin/ground/flights', { params: { date } })
}

export function departFlight(flightId: string) {
  return request.post(`/admin/ground/flights/${flightId}/depart`)
}

export function arriveFlight(flightId: string) {
  return request.post(`/admin/ground/flights/${flightId}/arrive`)
}

export function completeNode(flightId: string, nodeCode: string) {
  return request.post('/admin/ground/nodes/complete', { flightId, nodeCode })
}
