import request from '@/utils/request'

// GET /flights/search — doc section 2.2.1
export function searchFlights(params: {
  tripType: string
  departure: string
  arrival: string
  departDate: string
  returnDate?: string
  adults: number
  children?: number
  infants?: number
  cabinClass?: string
  directOnly?: boolean
  sortBy?: string
  sortOrder?: string
}) {
  return request.get('/flights/search', { params })
}

// GET /flights/{flightId} — doc section 2.2.2
export function getFlightDetail(flightId: string) {
  return request.get(`/flights/${flightId}`)
}

// GET /flights/hot-routes — doc section 2.2.3
export function getHotRoutes(city?: string, limit?: number) {
  return request.get('/flights/hot-routes', { params: { city, limit } })
}

// GET /flights/deals — doc section 2.2.4
export function getDeals(city?: string, limit?: number) {
  return request.get('/flights/deals', { params: { city, limit } })
}

// GET /flights/board — 航班大屏（当日航班动态）
export function getFlightBoard(date?: string) {
  return request.get('/flights/board', { params: { date } })
}

// GET /flights/airports — 机场列表（机场大屏选择器）
export function getAirports() {
  return request.get('/flights/airports')
}
