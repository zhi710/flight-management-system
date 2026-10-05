<template>
  <div class="notification-page page-container">
    <div class="page-header">
      <h2 class="page-title">{{ $t('notification.title') }}</h2>
      <el-button v-if="store.unreadCount > 0" text type="primary" @click="store.markAllRead()">
        {{ $t('notification.allRead') }}
      </el-button>
    </div>

    <div v-if="store.notifications.length === 0" class="empty">
      <el-empty :description="$t('notification.empty')" />
    </div>

    <div v-else class="notification-list">
      <div
        v-for="item in store.notifications"
        :key="item.id"
        class="notification-card card-shadow"
        :class="{ unread: !item.read }"
        @click="store.markRead(item.id)"
      >
        <div class="noti-title">
          <span v-if="!item.read" class="unread-dot" />
          <el-icon :size="16" :color="iconColor(item.type)">
            <component :is="iconComponent(item.type)" />
          </el-icon>
          <span class="title-text">{{ item.title }}</span>
          <el-tag size="small" :type="tagType(item.type)">{{ typeLabel(item.type) }}</el-tag>
        </div>
        <div class="noti-content">{{ item.content }}</div>
        <div class="noti-time">{{ formatTime(item.timestamp) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Bell, EditPen, InfoFilled, Select } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'

const store = useNotificationStore()
const { t } = useI18n()

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

function tagType(type: string) {
  const map: Record<string, string> = {
    NOTIFICATION: 'info',
    REBOOKING: 'warning',
    FLIGHT_UPDATE: '',
    SSR_UPDATE: 'success',
  }
  return (map[type] || 'info') as any
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    NOTIFICATION: t('notification.typeNotice'),
    REBOOKING: t('notification.typeRebooking'),
    FLIGHT_UPDATE: t('notification.typeFlight'),
    SSR_UPDATE: t('notification.typeSsr'),
  }
  return map[type] || t('notification.typeNotice')
}

function formatTime(timestamp: string): string {
  if (!timestamp) return ''
  try {
    const d = new Date(timestamp)
    const now = new Date()
    const diff = now.getTime() - d.getTime()
    if (diff < 60000) return t('notification.justNow')
    if (diff < 3600000) return `${Math.floor(diff / 60000)}${t('notification.minAgo')}`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}${t('notification.hourAgo')}`
    return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  } catch {
    return timestamp
  }
}
</script>

<style scoped>
.notification-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
}

.empty {
  padding: 60px 0;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notification-card {
  padding: 14px 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.notification-card:hover {
  box-shadow: var(--shadow-hover);
}

.notification-card.unread {
  background: var(--color-primary-soft);
}

.noti-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  flex-shrink: 0;
}

.title-text {
  font-size: 15px;
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.noti-content {
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.6;
}

.noti-time {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
