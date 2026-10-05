import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as flightApi from '@/api/flight'

export interface SearchParams {
  tripType: string
  departure: string
  arrival: string
  departDate: string
  returnDate?: string
  adults: number
  children: number
  infants: number
  cabinClass: string
  directOnly: boolean
}

export const useFlightStore = defineStore('flight', () => {
  const searchParams = ref<SearchParams>({
    tripType: 'ONEWAY',
    departure: '',
    arrival: '',
    departDate: '',
    adults: 1,
    children: 0,
    infants: 0,
    cabinClass: 'ECONOMY',
    directOnly: false,
  })

  const searchResults = ref<any[]>([])
  const searchId = ref('')
  const priceCalendar = ref<Record<string, number>>({})
  const loading = ref(false)

  async function searchFlights(params?: SearchParams) {
    if (params) {
      searchParams.value = { ...params }
    }
    loading.value = true
    try {
      const res = await flightApi.searchFlights(searchParams.value)
      searchId.value = res.data.searchId
      searchResults.value = res.data.flights
      priceCalendar.value = res.data.priceCalendar || {}
      return res.data
    } finally {
      loading.value = false
    }
  }

  async function getFlightDetail(flightId: string) {
    const res = await flightApi.getFlightDetail(flightId)
    return res.data
  }

  async function getHotRoutes(city?: string) {
    const res = await flightApi.getHotRoutes(city)
    return res.data
  }

  async function getDeals(city?: string) {
    const res = await flightApi.getDeals(city)
    return res.data
  }

  function swapCities() {
    const temp = searchParams.value.departure
    searchParams.value.departure = searchParams.value.arrival
    searchParams.value.arrival = temp
  }

  return {
    searchParams,
    searchResults,
    searchId,
    priceCalendar,
    loading,
    searchFlights,
    getFlightDetail,
    getHotRoutes,
    getDeals,
    swapCities,
  }
})
