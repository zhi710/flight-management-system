import request from '@/utils/request'

export function getFareList(params?: { airlineId?: number }) {
  return request.get('/admin/fares', { params })
}

export function createFare(data: {
  airlineId: number
  cabinClass: string
  fare: number
  tax: number
  baggage: string
  refundRule: string
  changeRule: string
}) {
  return request.post('/admin/fares', data)
}

export function updateFare(id: string, data: Record<string, any>) {
  return request.put(`/admin/fares/${id}`, data)
}

export function deleteFare(id: string) {
  return request.delete(`/admin/fares/${id}`)
}
