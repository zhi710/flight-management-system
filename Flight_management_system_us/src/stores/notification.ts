import { defineStore } from 'pinia'
import { ref } from 'vue'
import { markAllNotificationsRead, markNotificationRead } from '@/api/notification'

export interface NotificationItem {
  id: string
  title: string
  content: string
  type: 'NOTIFICATION' | 'REBOOKING' | 'FLIGHT_UPDATE' | 'SSR_UPDATE'
  refType?: string
  refId?: string
  timestamp: string
  read: boolean
}

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<NotificationItem[]>([])
  const unreadCount = ref(0)
  /** 已从接口加载过的标记 */
  let fetched = false

  function addNotification(item: NotificationItem) {
    notifications.value.unshift(item)
    unreadCount.value++
    if (notifications.value.length > 100) {
      notifications.value = notifications.value.slice(0, 100)
    }
  }

  function setNotifications(list: NotificationItem[]) {
    notifications.value = list
    unreadCount.value = list.filter(n => !n.read).length
    fetched = true
  }

  function markAllRead() {
    notifications.value.forEach(n => { n.read = true })
    unreadCount.value = 0
    markAllNotificationsRead().catch(() => {})
  }

  function markRead(id: string) {
    const n = notifications.value.find(n => n.id === id)
    if (n && !n.read) {
      n.read = true
      unreadCount.value--
      markNotificationRead(id).catch(() => {})
    }
  }

  function hasFetched() {
    return fetched
  }

  return { notifications, unreadCount, addNotification, setNotifications, markAllRead, markRead, hasFetched }
})
