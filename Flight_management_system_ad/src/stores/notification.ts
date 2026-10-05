import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface AlertNotification {
  id: string
  type: string
  title: string
  description?: string
  level?: string
  timestamp: string
  read: boolean
}

export const useNotificationStore = defineStore('notification', () => {
  const alerts = ref<AlertNotification[]>([])
  const unreadCount = ref(0)
  /** 后端待处理告警数（铃铛红点用） */
  const pendingCount = ref(0)

  function addAlert(alert: AlertNotification) {
    alerts.value.unshift(alert)
    unreadCount.value++
    pendingCount.value++
    if (alerts.value.length > 200) {
      alerts.value = alerts.value.slice(0, 200)
    }
  }

  function markAllRead() {
    alerts.value.forEach(a => { a.read = true })
    unreadCount.value = 0
  }

  function markRead(id: string) {
    const a = alerts.value.find(a => a.id === id)
    if (a && !a.read) {
      a.read = true
      unreadCount.value--
    }
  }

  function clear() {
    alerts.value = []
    unreadCount.value = 0
    pendingCount.value = 0
  }

  function updatePendingCount(count: number) {
    pendingCount.value = count
  }

  return { alerts, unreadCount, pendingCount, addAlert, markAllRead, markRead, clear, updatePendingCount }
})
