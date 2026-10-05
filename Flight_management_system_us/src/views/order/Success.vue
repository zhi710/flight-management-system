<template>
  <div class="success-page page-container">
    <div v-if="loading" class="loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else-if="order">
      <!-- 支付成功标识 -->
      <div class="success-hero card-shadow">
        <div class="hero-icon">
          <el-icon :size="42"><CircleCheckFilled /></el-icon>
        </div>
        <h1 class="hero-title">{{ $t('payment.paySuccess') }}</h1>
        <p class="hero-sub">{{ $t('order.paySuccessSubtitle') }}</p>
      </div>

      <!-- 航班信息 -->
      <div class="card-shadow section">
        <h3 class="section-title">{{ $t('order.flightInfo') }}</h3>
        <div class="flight-line">
          <div class="endpoint">
            <div class="time">{{ formatTime(order.flight?.departure?.dateTime) }}</div>
            <div class="airport">{{ transAirport(order.flight?.departure?.airportName) }}</div>
          </div>
          <div class="route-mid">
            <span class="flight-no">{{ order.flight?.flightNo }}</span>
            <div class="line-visual">
              <span class="dot"></span>
              <span class="dash"></span>
              <span class="dot"></span>
            </div>
          </div>
          <div class="endpoint">
            <div class="time">{{ formatTime(order.flight?.arrival?.dateTime) }}</div>
            <div class="airport">{{ transAirport(order.flight?.arrival?.airportName) }}</div>
          </div>
        </div>
      </div>

      <!-- 订单信息 -->
      <div class="card-shadow section">
        <h3 class="section-title">{{ $t('order.referenceInfo') }}</h3>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">{{ $t('order.orderNo') }}</span>
            <span class="value">{{ order.orderNo }}</span>
          </div>
          <div class="info-item">
            <span class="label">{{ $t('order.bookingRef') }}</span>
            <span class="value pnr">{{ order.pnr }}</span>
          </div>
          <div class="info-item">
            <span class="label">{{ $t('order.passenger') }}</span>
            <span class="value">{{ passengerNames }}</span>
          </div>
          <div class="info-item">
            <span class="label">{{ $t('order.totalAmount') }}</span>
            <span class="value amount">¥{{ order.price?.total }}</span>
          </div>
        </div>
      </div>

      <!-- 里程归属提示：只有订单里带了本平台常旅客号才显示 -->
      <div v-if="ffpNos.length" class="miles-tip">
        <el-icon><Medal /></el-icon>
        <span>{{ $t('order.ffpNoBound', { no: ffpNos.join('、') }) }}</span>
      </div>

      <!-- 后续操作 -->
      <div class="actions">
        <el-button type="primary" size="large" @click="goDetail">
          {{ $t('order.viewOrderDetail') }}
        </el-button>
        <el-button size="large" @click="goHome">
          {{ $t('order.backToHome') }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useOrderStore } from '@/stores/order'
import { useMappings } from '@/locales/mappings'

const route = useRoute()
const router = useRouter()
const orderStore = useOrderStore()
const { t } = useI18n()
const { transAirport } = useMappings()

const loading = ref(true)
const order = ref<any>(null)

const passengerNames = computed(() =>
  (order.value?.passengers || [])
    .map((p: any) => p.name)
    .filter(Boolean)
    .join('、')
)

/** 本单涉及的常旅客号（去重）。为空表示本单不累积里程，不展示提示条 */
const ffpNos = computed(() => {
  const set = new Set<string>()
  for (const p of order.value?.passengers || []) {
    if (p.frequentFlyerNo) set.add(p.frequentFlyerNo)
  }
  return Array.from(set)
})

function formatTime(dt?: string) {
  if (!dt) return '--:--'
  const d = new Date(dt)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function goDetail() {
  router.replace(`/home/orders/${order.value.orderId}`)
}

function goHome() {
  router.replace('/home')
}

onMounted(async () => {
  try {
    order.value = await orderStore.fetchOrderDetail(route.params.orderId as string)
  } catch {
    ElMessage.error(t('error.serverError'))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.success-page {
  padding-top: 24px;
  padding-bottom: 48px;
  max-width: 700px;
}

.success-hero {
  text-align: center;
  padding: 32px 24px;
  margin-bottom: 16px;
}

.hero-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-success-soft);
  color: var(--color-success);
  border: 1px solid var(--color-success-border);
}

.hero-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.hero-sub {
  font-size: 14px;
  color: var(--text-secondary);
}

.section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.flight-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.endpoint {
  flex: 1;
  min-width: 0;
}

.endpoint:last-child {
  text-align: right;
}

.endpoint .time {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.endpoint .airport {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.route-mid {
  flex: none;
  text-align: center;
}

.route-mid .flight-no {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.line-visual {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 90px;
}

.line-visual .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  flex: none;
}

.line-visual .dash {
  flex: 1;
  height: 1px;
  background: var(--border-color);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  padding: 6px 0;
}

.info-item .label {
  color: var(--text-secondary);
  flex: none;
}

.info-item .value {
  color: var(--text-primary);
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}

.info-item .pnr {
  font-family: var(--font-mono, monospace);
  letter-spacing: 1px;
}

.info-item .amount {
  color: var(--color-danger);
  font-size: 16px;
  font-weight: 600;
}

.miles-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--color-success-soft);
  border: 1px solid var(--color-success-border);
  border-radius: var(--radius-base);
  color: var(--color-success);
  font-size: 14px;
  margin-bottom: 24px;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

@media (max-width: 768px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .actions {
    flex-direction: column;
  }

  .actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
