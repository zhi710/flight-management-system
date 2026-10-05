<template>
  <div class="main-layout">
    <AppHeader />
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <AppFooter />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { ElNotification } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { notificationSocket } from '@/utils/websocket'
import { getNotificationList } from '@/api/notification'

const userStore = useUserStore()
const notificationStore = useNotificationStore()

// 页面加载时，如果已登录但用户信息为空，重新获取
onMounted(async () => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    userStore.fetchProfile()
  }

  // 连接通知 WebSocket
  if (userStore.isLoggedIn) {
    // 从接口加载历史通知
    try {
      const res: any = await getNotificationList()
      if (res?.code === 200 && Array.isArray(res.data)) {
        const list = res.data.map((n: any) => ({
          id: n.id || Date.now().toString(),
          title: n.title || '',
          content: n.content || '',
          type: 'NOTIFICATION' as const,
          refType: n.refType,
          refId: n.refId,
          timestamp: n.timestamp || new Date().toISOString(),
          read: n.read || false,
        }))
        notificationStore.setNotifications(list)
      }
    } catch { /* 静默失败，不影响实时推送 */ }

    notificationSocket.connect()

    // 监听通用通知
    notificationSocket.onNotification((data: any) => {
      notificationStore.addNotification({
        id: data.id || Date.now().toString() + Math.random().toString(36).slice(2, 8),
        title: data.title || '新消息',
        content: data.content || '',
        type: 'NOTIFICATION',
        refType: data.refType,
        refId: data.refId,
        timestamp: data.timestamp || new Date().toISOString(),
        read: false,
      })
      // 弹窗提醒
      ElNotification({
        title: data.title || '新消息',
        message: data.content,
        type: 'info',
        duration: 4000,
      })
    })

    // 监听改签结果
    notificationSocket.onRebooking((data: any) => {
      notificationStore.addNotification({
        id: Date.now().toString() + Math.random().toString(36).slice(2, 8),
        title: '航班改签通知',
        content: `您的航班 ${data.originalFlightNo} 已取消，已自动为您改签至 ${data.newFlightNo}`,
        type: 'REBOOKING',
        timestamp: data.timestamp || new Date().toISOString(),
        read: false,
      })
      ElNotification({
        title: '航班改签通知',
        message: `您的航班 ${data.originalFlightNo} 已取消，已自动改签至 ${data.newFlightNo}`,
        type: 'warning',
        duration: 8000,
      })
    })

    // 监听SSR审核结果
    notificationSocket.onSsrUpdate((data: any) => {
      const approved = data.status === 'APPROVED'
      const title = data.title || (approved ? '特殊服务申请已通过' : '特殊服务申请被拒绝')
      const content = data.content || `您的 ${data.ssrName || data.ssrCode} 服务申请${approved ? '已通过' : '已拒绝'}`
      // 如果有追加费用，在通知中明确显示
      const feeInfo = approved && data.extraFee > 0 ? `（费用¥${data.extraFee}已追加到订单）` : ''
      notificationStore.addNotification({
        id: Date.now().toString() + Math.random().toString(36).slice(2, 8),
        title: title,
        content: content + feeInfo,
        type: 'SSR_UPDATE',
        refType: 'order',
        refId: data.orderId,
        timestamp: new Date().toISOString(),
        read: false,
      })
      ElNotification({
        title: title,
        message: content + feeInfo,
        type: approved ? 'success' : 'warning',
        duration: 5000,
      })
    })
  }
})

onUnmounted(() => {
  notificationSocket.disconnect()
})
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  padding-top: var(--header-height);
}
</style>
