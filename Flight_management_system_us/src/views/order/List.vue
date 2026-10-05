<template>
  <div class="order-list-page page-container">
    <h2 class="page-title">{{ $t('order.title') }}</h2>

    <!-- Status tabs -->
    <el-tabs v-model="activeStatus" @tab-change="loadOrders">
      <el-tab-pane :label="$t('order.all')" name="" />
      <el-tab-pane :label="$t('order.pending')" name="PENDING_PAYMENT" />
      <el-tab-pane :label="$t('order.paid')" name="PAID" />
      <el-tab-pane :label="$t('order.completed')" name="COMPLETED" />
      <el-tab-pane :label="$t('order.cancelled')" name="CANCELLED" />
    </el-tabs>

    <!-- Search -->
    <div class="search-row">
      <el-input
        v-model="keyword"
        :placeholder="$t('order.searchPlaceholder') "
        clearable
        style="width: 300px"
        @keyup.enter="loadOrders"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- Order cards -->
    <div v-loading="loading">
      <div v-if="orders.length === 0" class="empty">
        <el-empty :description="$t('order.noOrders') " />
      </div>
      <div
        v-for="order in orders"
        :key="order.orderId"
        class="order-card card-shadow"
        @click="$router.push(`/home/orders/${order.orderId}`)"
      >
        <div class="order-header">
          <span class="order-no">{{ $t('order.orderNo') }}: {{ order.orderNo }}</span>
          <el-tag :type="statusTagType(order.status)" size="small">{{ statusText(order.status) }}</el-tag>
        </div>
        <div class="order-body">
          <div class="flight-info">
            <div class="route">
              <span class="city">{{ transAirport(order.flight?.departure?.airport) }}</span>
              <el-icon><Right /></el-icon>
              <span class="city">{{ transAirport(order.flight?.arrival?.airport) }}</span>
            </div>
            <div class="flight-no">{{ order.flight?.flightNo }}</div>
            <div class="pax-list">
              <span v-for="(p, i) in order.passengers" :key="i" class="pax-name">{{ p.name }}</span>
            </div>
          </div>
          <div class="order-price">
            <span class="currency">¥</span>
            <span class="amount">{{ order.price?.total }}</span>
          </div>
        </div>
        <div class="order-footer">
          <span class="create-time">{{ $t('order.createTime') }}: {{ formatDateTime(order.createdAt) }}</span>
          <span v-if="getCountdown(order.orderId) > 0" class="pay-countdown">
            <el-icon><Clock /></el-icon>
            {{ formatCountdown(getCountdown(order.orderId)) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useOrderStore } from '@/stores/order'
import { useMappings } from '@/locales/mappings'

const orderStore = useOrderStore()
const { t } = useI18n()
const { transAirport } = useMappings()

const activeStatus = ref('')
const keyword = ref('')
const loading = ref(false)
const orders = ref<any[]>([])
const countdowns = ref<Record<string, number>>({})
let listTimer: ReturnType<typeof setInterval> | null = null

function getCountdown(orderId: string) {
  return countdowns.value[orderId] || 0
}

function formatCountdown(secs: number) {
  const h = Math.floor(secs / 3600)
  const m = Math.floor((secs % 3600) / 60)
  const s = secs % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function startCountdowns() {
  if (listTimer) clearInterval(listTimer)
  const map: Record<string, number> = {}
  orders.value.forEach(o => {
    if (o.status === 'PENDING_PAYMENT' && o.expireAt) {
      const secs = Math.max(0, Math.floor((new Date(o.expireAt).getTime() - Date.now()) / 1000))
      if (secs > 0) map[o.orderId] = secs
    }
  })
  countdowns.value = map

  listTimer = setInterval(() => {
    let hasActive = false
    for (const id in countdowns.value) {
      const remaining = countdowns.value[id]
      if (remaining !== undefined && remaining > 0) {
        countdowns.value[id] = remaining - 1
        hasActive = true
        if (countdowns.value[id] === 0) {
          const o = orders.value.find(o => o.orderId === id)
          if (o) o.status = 'CANCELLED'
        }
      }
    }
    if (!hasActive) clearInterval(listTimer!)
  }, 1000)
}

const statusMap = computed<Record<string, string>>(() => ({
  PENDING_PAYMENT: t('order.pending'),
  PAID: t('order.paid'),
  ISSUED: t('order.paid'),
  CHECKED_IN: t('order.paid'),
  BOARDING: t('order.paid'),
  DEPARTED: t('order.paid'),
  ARRIVED: t('order.paid'),
  COMPLETED: t('order.completed'),
  CANCELLED: t('order.cancelled'),
  REFUNDING: t('order.refunding') ,
  REFUNDED: t('order.refunded') ,
}))

function statusText(status: string) {
  return statusMap.value[status] || status
}

function statusTagType(status: string) {
  if (status === 'PENDING_PAYMENT') return 'warning'
  if (['PAID', 'ISSUED', 'CHECKED_IN', 'BOARDING', 'DEPARTED', 'ARRIVED'].includes(status)) return 'success'
  if (['COMPLETED'].includes(status)) return 'primary'
  if (['CANCELLED', 'REFUNDED'].includes(status)) return 'info'
  return 'primary'
}

function formatDateTime(dt?: string) {
  if (!dt) return ''
  return dt.replace('T', ' ').replace(/\+.*/, '')
}

async function loadOrders() {
  loading.value = true
  try {
    const data = await orderStore.fetchOrderList({
      status: activeStatus.value || undefined,
      keyword: keyword.value || undefined,
    })
    orders.value = data.list
    startCountdowns()
  } catch (err: any) {
    ElMessage.error(err?.message || t('error.serverError'))
  } finally {
    loading.value = false
  }
}

onMounted(loadOrders)

onUnmounted(() => {
  if (listTimer) clearInterval(listTimer)
})
</script>

<style scoped>
.order-list-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.search-row {
  margin-bottom: 16px;
}

.order-card {
  cursor: pointer;
  transition: all 0.25s;
  margin-bottom: 12px;
}

.order-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.order-no {
  font-size: 13px;
  color: var(--text-secondary);
}

.order-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.route {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.flight-no {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.pax-name {
  font-size: 13px;
  color: var(--text-regular);
  margin-right: 8px;
}

.order-price .currency {
  font-size: 14px;
  color: var(--color-danger);
}

.order-price .amount {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-danger);
}

.order-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.create-time {
  font-size: 12px;
  color: var(--text-secondary);
}

.pay-countdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-danger);
  background: var(--color-danger-soft);
  padding: 2px 10px;
  border-radius: 12px;
}

.empty {
  padding: 60px 0;
}
</style>
