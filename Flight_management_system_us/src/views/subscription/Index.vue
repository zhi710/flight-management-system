<template>
  <div class="subscription-page page-container">
    <h2 class="page-title">{{ $t('subscription.title') }}</h2>

    <div v-loading="loading">
      <div v-if="list.length === 0" class="empty">
        <el-empty :description="$t('subscription.empty')" />
      </div>

      <div v-for="s in list" :key="s.id" class="sub-card card-shadow">
        <div class="sub-info">
          <div class="sub-flight">
            <span class="flight-no">{{ s.flightNo }}</span>
            <span class="flight-date">{{ s.flightDate }}</span>
          </div>
          <div class="sub-channels">
            <el-tag v-for="c in s.channels" :key="c" size="small" type="info">{{ channelLabel(c) }}</el-tag>
          </div>
          <div class="sub-time">{{ formatTime(s.createTime) }}</div>
        </div>
        <el-button text type="danger" @click="handleCancel(s)">{{ $t('subscription.cancel') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMySubscriptions, unsubscribeFlight } from '@/api/status'

const { t } = useI18n()
const loading = ref(false)
const list = ref<any[]>([])

function channelLabel(c: string) {
  const map: Record<string, string> = {
    SMS: t('subscription.channelSms'),
    EMAIL: t('subscription.channelEmail'),
    PUSH: t('subscription.channelPush'),
    WECHAT: t('subscription.channelWechat'),
  }
  return map[c] || c
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
    const res = await getMySubscriptions()
    list.value = res.data || []
  } catch {
    // error handled
  } finally {
    loading.value = false
  }
}

async function handleCancel(s: any) {
  try {
    await ElMessageBox.confirm(t('subscription.cancelConfirm'), t('subscription.cancel'), { type: 'warning' })
    await unsubscribeFlight(s.id)
    ElMessage.success(t('subscription.cancelSuccess'))
    await load()
  } catch {
    // cancelled
  }
}

onMounted(load)
</script>

<style scoped>
.subscription-page {
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

.sub-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 16px;
}

.sub-flight {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.flight-no {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.flight-date {
  font-size: 13px;
  color: var(--text-secondary);
}

.sub-channels {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.sub-time {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
