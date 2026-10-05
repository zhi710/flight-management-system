<template>
  <div class="app-layout">
    <Sidebar />
    <div class="layout-main" :class="{ collapsed: appStore.sidebarCollapsed }">
      <AppHeader />
      <div class="layout-content">
        <Breadcrumb />
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { ElNotification } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useNotificationStore } from '@/stores/notification'
import { adminIrregularSocket } from '@/utils/websocket'
import { getAlertStats } from '@/api/monitor'
import Sidebar from './Sidebar.vue'
import AppHeader from './AppHeader.vue'
import Breadcrumb from './Breadcrumb.vue'

const appStore = useAppStore()
const notificationStore = useNotificationStore()

// 平板竖屏及以下自动收起侧边栏（复用 appStore.sidebarCollapsed）
const tabletMq = window.matchMedia('(max-width: 991px)')
function handleTablet() {
  if (tabletMq.matches) appStore.sidebarCollapsed = true
}

async function syncPendingCount() {
  try {
    const data = await getAlertStats() as any
    notificationStore.updatePendingCount(data.pendingCount || 0)
  } catch { /* 静默失败，不影响UI */ }
}

onMounted(() => {
  handleTablet()
  tabletMq.addEventListener('change', handleTablet)

  adminIrregularSocket.connect()
  syncPendingCount()

  // 监听不正常航班更新
  adminIrregularSocket.on('IRREGULAR_UPDATE', (data: any) => {
    notificationStore.addAlert({
      id: Date.now().toString(),
      type: 'IRREGULAR',
      title: `航班异常: ${data.flightNo} - ${data.type}`,
      description: data.reason,
      level: 'IMPORTANT',
      timestamp: new Date().toISOString(),
      read: false,
    })
    syncPendingCount()
    ElNotification({
      title: '不正常航班处理通知',
      message: `航班 ${data.flightNo} 状态变更为 ${data.newStatus}，原因: ${data.reason}`,
      type: 'warning',
      duration: 6000,
    })
  })

  // 监听管理员通知
  adminIrregularSocket.on('ADMIN_NOTIFICATION', (data: any) => {
    notificationStore.addAlert({
      id: Date.now().toString(),
      type: 'NOTIFICATION',
      title: data.title || '系统通知',
      description: data.content,
      level: 'NORMAL',
      timestamp: new Date().toISOString(),
      read: false,
    })
    syncPendingCount()
  })
})

onUnmounted(() => {
  tabletMq.removeEventListener('change', handleTablet)
  adminIrregularSocket.disconnect()
})
</script>

<style scoped lang="scss">

.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.layout-main {
  flex: 1;
  margin-left: $sidebar-width;
  transition: margin-left 0.3s ease;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  &.collapsed {
    margin-left: $sidebar-collapsed-width;
  }
}

.layout-content {
  flex: 1;
  overflow-y: auto;
  background: $bg-page;
  padding: 0;
}

// 路由切换动画
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.25s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>
