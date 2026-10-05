<template>
  <el-popover
    placement="bottom-end"
    :width="360"
    trigger="click"
    @show="handleShow"
  >
    <template #reference>
      <el-badge :value="store.unreadCount" :hidden="store.unreadCount === 0" class="notification-badge">
        <el-icon :size="22" style="cursor:pointer">
          <Bell />
        </el-icon>
      </el-badge>
    </template>

    <div class="notification-panel">
      <div class="notification-header">
        <span style="font-weight: 600; font-size: 14px;">消息通知</span>
        <div>
          <el-button text size="small" @click="goAll">查看全部</el-button>
          <el-button text size="small" @click="store.markAllRead()" v-if="store.unreadCount > 0">
            全部已读
          </el-button>
        </div>
      </div>

      <div class="notification-list" v-if="store.notifications.length > 0">
        <div
          v-for="item in store.notifications"
          :key="item.id"
          class="notification-item"
          :class="{ unread: !item.read }"
          @click="handleClick(item)"
        >
          <div class="notification-title">
            <span v-if="!item.read" class="unread-dot" />
            <el-icon :size="16" :color="iconColor(item.type)" style="flex-shrink:0">
              <component :is="iconComponent(item.type)" />
            </el-icon>
            <span class="title-text">{{ item.title }}</span>
            <el-tag v-if="item.type === 'NOTIFICATION'" size="small" type="info">通知</el-tag>
            <el-tag v-else-if="item.type === 'REBOOKING'" size="small" type="warning">改签</el-tag>
            <el-tag v-else-if="item.type === 'FLIGHT_UPDATE'" size="small">航班</el-tag>
            <el-tag v-else-if="item.type === 'SSR_UPDATE'" size="small" type="success">服务</el-tag>
          </div>
          <div class="notification-content">{{ item.content }}</div>
          <div class="notification-time">{{ formatTime(item.timestamp) }}</div>
        </div>
      </div>

      <el-empty v-else description="暂无消息" :image-size="60" />
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Bell, EditPen, InfoFilled, Select } from '@element-plus/icons-vue'
import { useNotificationStore, type NotificationItem } from '@/stores/notification'

const store = useNotificationStore()
const router = useRouter()

function goAll() {
  router.push('/home/notifications')
}

// 图标色走 CSS 变量：这些常量最终绑到 DOM 上的 <el-icon>，能读到令牌
const ICON_MAP: Record<string, { component: any; color: string }> = {
  NOTIFICATION: { component: Bell, color: 'var(--color-primary)' },
  REBOOKING: { component: EditPen, color: 'var(--color-warning)' },
  FLIGHT_UPDATE: { component: InfoFilled, color: 'var(--color-info)' },
  SSR_UPDATE: { component: Select, color: 'var(--color-success)' },
}

function iconComponent(type: string) {
  return ICON_MAP[type]?.component || Bell
}

function iconColor(type: string) {
  return ICON_MAP[type]?.color || 'var(--color-primary)'
}

function handleShow() {
  // popover 展开时的逻辑
}

function handleClick(item: NotificationItem) {
  store.markRead(item.id)
}

function formatTime(timestamp: string): string {
  if (!timestamp) return ''
  try {
    const d = new Date(timestamp)
    const now = new Date()
    const diff = now.getTime() - d.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  } catch {
    return timestamp
  }
}
</script>

<style scoped>
.notification-badge {
  display: flex;
  align-items: center;
}

.notification-panel {
  max-height: 400px;
  overflow: hidden;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color-lighter);
  margin-bottom: 8px;
}

.notification-list {
  max-height: 340px;
  overflow-y: auto;
}

.notification-item {
  padding: 10px 8px;
  border-bottom: 1px solid var(--bg-muted);
  cursor: pointer;
  transition: background 0.2s;
}

.notification-item:hover {
  background: var(--bg-page);
}

.notification-item.unread {
  background: var(--color-primary-soft);
}

.notification-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  flex-shrink: 0;
}

.title-text {
  font-size: 13px;
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-content {
  font-size: 12px;
  color: var(--text-regular);
  line-height: 1.4;
  margin-bottom: 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notification-time {
  font-size: 11px;
  color: var(--text-secondary);
}
</style>
