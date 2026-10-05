<template>
  <div class="checkin-page page-container">
    <h2 class="page-title">{{ $t('checkin.title') }}</h2>
    <p class="page-desc">{{ $t('checkin.desc')  }}</p>

    <div v-loading="loading">
      <div v-if="flights.length === 0" class="empty">
        <el-empty>
          <template #description>
            <p>{{ $t('checkin.noAvailable') }}</p>
            <p class="empty-tip">{{ $t('checkin.noAvailableDesc') }}</p>
          </template>
          <el-button type="primary" @click="$router.push('/home/orders')">{{ $t('checkin.viewOrders') }}</el-button>
        </el-empty>
      </div>

      <div v-for="flight in flights" :key="flight.orderId" class="checkin-card card-shadow">
        <div class="card-header">
          <div class="flight-info">
            <span class="flight-no">{{ flight.flightNo }}</span>
            <span class="route">
              {{ transAirport(flight.departure?.airport) }} → {{ transAirport(flight.arrival?.airport) }}
            </span>
            <el-tag v-if="flight.allCheckedIn" type="success" size="small" style="margin-left: 8px">{{ $t('order.checkedIn') }}</el-tag>
            <el-tag v-else-if="flight.anyCheckedIn" type="warning" size="small" style="margin-left: 8px">{{ $t('checkin.partialCheckin')  }}</el-tag>
          </div>
          <div class="checkin-time">
            <el-icon><Clock /></el-icon>
            {{ $t('checkin.openTime') }}: {{ formatDateTime(flight.checkinOpenAt) }} - {{ formatDateTime(flight.checkinCloseAt) }}
          </div>
        </div>

        <div class="card-body">
          <div class="departure-info">
            <div class="time">{{ formatTime(flight.departure?.dateTime) }}</div>
            <div class="terminal">{{ flight.departure?.terminal }}</div>
          </div>

          <div class="pax-list">
            <div v-for="(pax, idx) in flight.passengers" :key="idx" class="pax-item">
              <span class="pax-name">{{ pax.name }}</span>
              <el-tag v-if="pax.checkedIn" type="success" size="small">
                {{ $t('order.checkedIn') }} {{ pax.seat ? pax.seat : '' }}
              </el-tag>
              <el-tag v-else type="info" size="small">{{ $t('order.notCheckedIn') }}</el-tag>
            </div>
          </div>
        </div>

        <div class="card-footer">
          <template v-if="flight.orderStatus === 'CHECKED_IN'">
            <el-button
              v-if="flight.checkinId"
              type="primary"
              @click="goBoardingPass(flight)"
            >{{ $t('checkin.viewBoardingPass') }}</el-button>
            <el-button
              type="warning"
              plain
              :loading="cancelingOrderId === flight.orderId"
              @click="handleCancelCheckin(flight)"
            >{{ $t('checkin.cancelCheckin')  }}</el-button>
          </template>
          <template v-else>
            <el-button
              type="primary"
              @click="goSeatMap(flight)"
            >{{ $t('checkin.checkinNow') }}</el-button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAvailableCheckin, cancelCheckin } from '@/api/checkin'
import { useMappings } from '@/locales/mappings'
import { flightStatusSocket } from '@/utils/websocket'

const router = useRouter()
const { t } = useI18n()
const { transAirport } = useMappings()

const loading = ref(false)
const cancelingOrderId = ref('')
const flights = ref<any[]>([])
const cleanups: (() => void)[] = []

function formatTime(dt?: string) {
  if (!dt) return '--:--'
  const d = new Date(dt)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatDateTime(dt?: string) {
  if (!dt) return ''
  const d = new Date(dt)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function goSeatMap(flight: any) {
  router.push(`/home/checkin/seat-map/${flight.flightId}?orderId=${flight.orderId}`)
}

function goBoardingPass(flight: any) {
  router.push(`/home/checkin/boarding-pass/${flight.checkinId}`)
}

async function handleCancelCheckin(flight: any) {
  try {
    await ElMessageBox.confirm(
      t('checkin.cancelCheckinConfirm') ,
      t('checkin.cancelCheckin') ,
      { type: 'warning' }
    )
    cancelingOrderId.value = flight.orderId
    await cancelCheckin(flight.orderId)
    ElMessage.success(t('checkin.cancelCheckinSuccess') )
    loadFlights()
  } catch {
    // cancelled or error
  } finally {
    cancelingOrderId.value = ''
  }
}

async function loadFlights() {
  loading.value = true
  try {
    const res = await getAvailableCheckin()
    flights.value = res.data
    // 订阅每个航班的 WebSocket 通知
    cleanups.forEach(fn => fn())
    cleanups.length = 0
    flights.value.forEach(f => {
      if (f.flightNo) {
        const unsub = flightStatusSocket.subscribe(f.flightNo, () => loadFlights())
        cleanups.push(unsub)
      }
    })
  } catch {
    // error handled
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  flightStatusSocket.connect()
  loadFlights()
})

onUnmounted(() => {
  cleanups.forEach(fn => fn())
  cleanups.length = 0
  flightStatusSocket.disconnect()
})
</script>

<style scoped>
.checkin-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.page-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 24px;
}

.checkin-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.flight-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.flight-no {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.route {
  font-size: 14px;
  color: var(--text-secondary);
}

.checkin-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}

.card-body {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px;
  background: var(--bg-color);
  border-radius: var(--radius-base);
}

.departure-info .time {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.departure-info .terminal {
  font-size: 13px;
  color: var(--text-secondary);
}

.pax-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.pax-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pax-name {
  font-size: 14px;
  color: var(--text-primary);
}

.card-footer {
  margin-top: 16px;
  text-align: right;
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.card-footer :deep(.el-button + .el-button) {
  margin-left: 0;
}

.empty {
  padding: 60px 0;
}

.empty-tip {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}
</style>
