<template>
  <div class="feedback-page page-container">
    <h2 class="page-title">{{ $t('feedback.title') }}</h2>

    <div v-loading="loading">
      <div v-if="list.length === 0" class="empty">
        <el-empty :description="$t('feedback.empty')" />
      </div>

      <div v-for="f in list" :key="f.id" class="feedback-card card-shadow">
        <div class="fb-head">
          <el-tag :type="f.type === 'COMPLAINT' ? 'danger' : 'success'" size="small">{{ typeLabel(f.type) }}</el-tag>
          <el-tag :type="f.reply ? 'success' : 'info'" size="small">
            {{ f.reply ? $t('feedback.statusReplied') : $t('feedback.statusPending') }}
          </el-tag>
          <span class="fb-time">{{ formatTime(f.createTime) }}</span>
        </div>
        <div class="fb-content">{{ f.content }}</div>
        <div v-if="f.orderId" class="fb-order">{{ $t('feedback.relatedOrder') }}: {{ f.orderId }}</div>
        <div v-if="f.reply" class="fb-reply">
          <span class="reply-label">{{ $t('feedback.replyLabel') }}：</span>{{ f.reply }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getMyFeedback } from '@/api/help'

const { t } = useI18n()
const loading = ref(false)
const list = ref<any[]>([])

function typeLabel(type: string) {
  return type === 'COMPLAINT' ? t('feedback.typeComplaint') : t('feedback.typeSuggestion')
}

function formatTime(ts: string): string {
  if (!ts) return ''
  try {
    return new Date(ts).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return ts
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getMyFeedback()
    list.value = res.data || []
  } catch {
    // error handled
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.feedback-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.empty {
  padding: 60px 0;
}

.feedback-card {
  margin-bottom: 12px;
  padding: 16px;
}

.fb-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.fb-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-secondary);
}

.fb-content {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.7;
}

.fb-order {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.fb-reply {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--bg-color);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.6;
}

.reply-label {
  color: var(--color-primary);
  font-weight: 500;
}
</style>
