import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as orderApi from '@/api/order'

export interface PassengerForm {
  name: string
  gender: string
  birthday: string
  idType: string
  idNumber: string
  phone: string
  email: string
  frequentFlyerNo: string
  passengerType: string
}

export interface ContactInfo {
  name: string
  phone: string
  email: string
}

export interface ServiceItem {
  type: string
  code: string
  quantity: number
  passengerIndex: number
}

export const useOrderStore = defineStore('order', () => {
  // Booking flow state
  const selectedFlight = ref<any>(null)
  const selectedCabin = ref<any>(null)
  const passengers = ref<PassengerForm[]>([])
  const contactInfo = ref<ContactInfo>({ name: '', phone: '', email: '' })
  const services = ref<ServiceItem[]>([])

  // Order result
  const currentOrder = ref<any>(null)
  const orderList = ref<any[]>([])

  async function createOrder() {
    const res = await orderApi.createOrder({
      searchId: selectedFlight.value?.searchId || '',
      flightId: selectedFlight.value?.flightId || '',
      cabinClass: selectedCabin.value?.class || '',
      passengers: passengers.value,
      contactInfo: contactInfo.value,
      services: services.value,
    })
    currentOrder.value = res.data
    return res.data
  }

  async function fetchOrderDetail(orderId: string) {
    const res = await orderApi.getOrderDetail(orderId)
    currentOrder.value = res.data
    return res.data
  }

  async function fetchOrderList(params?: { status?: string; startDate?: string; endDate?: string; keyword?: string }) {
    const res = await orderApi.getOrderList(params)
    orderList.value = res.data.list
    return res.data
  }

  async function cancelOrder(orderId: string, reason?: string) {
    const res = await orderApi.cancelOrder(orderId, reason)
    return res.data
  }

  function resetBooking() {
    selectedFlight.value = null
    selectedCabin.value = null
    passengers.value = []
    contactInfo.value = { name: '', phone: '', email: '' }
    services.value = []
  }

  return {
    selectedFlight,
    selectedCabin,
    passengers,
    contactInfo,
    services,
    currentOrder,
    orderList,
    createOrder,
    fetchOrderDetail,
    fetchOrderList,
    cancelOrder,
    resetBooking,
  }
})
