<template>
  <div class="detail-page page-container">
    <div v-if="loading" class="loading">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else-if="flight">
      <!-- Flight Header -->
      <div class="flight-header card-shadow">
        <div class="header-top">
          <div class="airline-badge">
            <span class="airline-name">{{ flight.airline?.name }}</span>
            <span class="flight-no">{{ flight.flightNo }}</span>
          </div>
          <div class="flight-date">{{ formatDate(flight.departure?.dateTime) }}</div>
        </div>

        <div class="route-visual">
          <div class="endpoint departure">
            <div class="time">{{ formatTime(flight.departure?.dateTime) }}</div>
            <div class="airport-code">{{ transAirport(flight.departure?.airport) }}</div>
            <div class="airport-name">{{ transAirport(flight.departure?.airportName) }}</div>
            <div class="terminal">{{ $t('flight.terminal')  }} {{ flight.departure?.terminal }}</div>
          </div>

          <div class="route-line">
            <div class="duration">{{ formatDuration(flight.duration) }}</div>
            <div class="line-visual">
              <span class="dot start"></span>
              <span class="dash"></span>
              <el-icon v-if="flight.stops > 0" class="stop-icon"><Location /></el-icon>
              <span class="dash"></span>
              <span class="dot end"></span>
            </div>
            <div class="flight-type">{{ flight.stops > 0 ? $t('flight.stops', { count: flight.stops }) : $t('flight.direct') }}</div>
          </div>

          <div class="endpoint arrival">
            <div class="time">{{ formatTime(flight.arrival?.dateTime) }}</div>
            <div class="airport-code">{{ transAirport(flight.arrival?.airport) }}</div>
            <div class="airport-name">{{ transAirport(flight.arrival?.airportName) }}</div>
            <div class="terminal">{{ $t('flight.terminal')  }} {{ flight.arrival?.terminal }}</div>
          </div>
        </div>

        <!-- Flight info tags -->
        <div class="info-tags">
          <el-tag v-if="flight.aircraft" type="info">{{ flight.aircraft.name }}</el-tag>
          <el-tag v-if="flight.punctuality" type="success">{{ $t('flight.punctuality') }} {{ flight.punctuality.rate }}%</el-tag>
          <el-tag v-if="flight.baggage" type="info">{{ $t('flight.freeBaggage')  }} {{ flight.baggage.free }}</el-tag>
          <el-tag v-if="flight.meal?.provided" type="info">{{ flight.meal.type }}{{ $t('flight.meal') }}</el-tag>
        </div>
      </div>

      <!-- Cabin Selection -->
      <div class="cabin-section card-shadow">
        <h3 class="section-title">{{ $t('flight.selectCabin') }}</h3>
        <div
          v-for="cabin in flight.cabins"
          :key="cabin.class"
          :class="['cabin-card', { selected: selectedCabin?.class === cabin.class, 'sold-out': cabin.seats <= 0 }]"
          @click="cabin.seats > 0 && (selectedCabin = cabin)"
        >
          <div class="cabin-left">
            <div class="cabin-class-name">{{ cabin.className }}</div>
            <div class="cabin-meta">
              <template v-if="cabin.seats > 0">
                <span>{{ $t('flight.seatsCount', { count: cabin.seats }) }}</span>
              </template>
              <span v-else class="sold-out-badge">{{ $t('flight.soldOut') }}</span>
              <span>{{ $t('flight.baggage') }} {{ cabin.baggage }}</span>
            </div>
          </div>

          <div class="cabin-rules">
            <div class="rule">
              <span class="rule-label">{{ $t('flight.refundRule') }}: </span>
              <span>{{ cabin.refundRule?.before2h || cabin.refundRule }}</span>
            </div>
            <div class="rule">
              <span class="rule-label">{{ $t('flight.changeRule') }}: </span>
              <span>{{ cabin.changeRule?.before2h || cabin.changeRule }}</span>
            </div>
          </div>

          <div class="cabin-price-area">
            <template v-if="cabin.seats > 0">
              <div class="price-breakdown">
                <span class="fare">{{ $t('flight.fare') }} ¥{{ cabin.fare }}</span>
                <span class="tax">+ {{ $t('flight.tax') }} ¥{{ cabin.tax }}</span>
              </div>
              <div class="total-price">
                <span class="currency">¥</span>
                <span class="amount">{{ cabin.totalPrice }}</span>
              </div>
            </template>
            <span v-else class="sold-out-large">{{ $t('flight.soldOut') }}</span>
          </div>
        </div>
      </div>

      <!-- Services -->
      <div v-if="flight.meal" class="services-section card-shadow">
        <h3 class="section-title">{{ $t('flight.services') }}</h3>
        <div class="services-grid">
          <div v-if="flight.meal" class="service-item">
            <el-icon :size="24" color="var(--color-primary)"><Food /></el-icon>
            <div>
              <div class="service-name">{{ $t('flight.mealProvided') }}</div>
              <div class="service-desc">{{ flight.meal.type }}, {{ $t('flight.specialMeal') }}: {{ flight.meal.special?.join(', ') }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Book button -->
      <div class="book-footer card-shadow">
        <div class="footer-price" v-if="selectedCabin">
          <span class="label">{{ $t('common.total') }}</span>
          <span class="currency">¥</span>
          <span class="amount">{{ selectedCabin.totalPrice }}</span>
          <span class="per-pax">/{{ $t('flight.perPerson') }}</span>
        </div>
        <el-button type="primary" size="large" :disabled="!selectedCabin || selectedCabin.seats <= 0" @click="handleBook">
          {{ $t('flight.selectThisCabin') }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useFlightStore } from '@/stores/flight'
import { useOrderStore } from '@/stores/order'
import { useMappings } from '@/locales/mappings'

const route = useRoute()
const router = useRouter()
const flightStore = useFlightStore()
const orderStore = useOrderStore()
const { t } = useI18n()
const { transAirport } = useMappings()

const loading = ref(true)
const flight = ref<any>(null)
const selectedCabin = ref<any>(null)

function formatTime(dateTime?: string) {
  if (!dateTime) return '--:--'
  const d = new Date(dateTime)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatDate(dateTime?: string) {
  if (!dateTime) return ''
  const d = new Date(dateTime)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatDuration(minutes?: number) {
  if (!minutes) return '--'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}h${m > 0 ? m + 'min' : ''}` : `${m}min`
}

function handleBook() {
  if (!selectedCabin.value) {
    ElMessage.warning(t('flight.selectCabinFirst') )
    return
  }
  orderStore.selectedFlight = {
    ...flight.value,
    searchId: flightStore.searchId,
  }
  orderStore.selectedCabin = selectedCabin.value
  router.push('/home/booking')
}

onMounted(async () => {
  try {
    const flightId = route.params.id as string
    flight.value = await flightStore.getFlightDetail(flightId)
    if (flight.value.cabins?.length > 0) {
      selectedCabin.value = flight.value.cabins.find((c: any) => c.seats > 0) || null
    }
  } catch {
    ElMessage.error(t('error.serverError'))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.detail-page {
  padding-top: 24px;
  padding-bottom: 100px;
}

.flight-header {
  margin-bottom: 16px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.airline-badge {
  display: flex;
  align-items: center;
  gap: 12px;
}

.airline-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.flight-no {
  font-size: 14px;
  color: var(--text-secondary);
  background: var(--bg-color);
  padding: 2px 10px;
  border-radius: 4px;
}

.flight-date {
  font-size: 14px;
  color: var(--text-secondary);
}

.route-visual {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 0;
}

.endpoint {
  flex: 1;
  text-align: center;
}

.endpoint .time {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.endpoint .airport-code {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
  margin-top: 4px;
}

.endpoint .airport-name {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.endpoint .terminal {
  font-size: 12px;
  color: var(--text-secondary);
}

.route-line {
  flex: 2;
  text-align: center;
}

.route-line .duration {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.line-visual {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid var(--color-primary);
}

.dot.start {
  background: var(--color-primary);
}

.dash {
  flex: 1;
  height: 2px;
  background: var(--color-secondary);
  max-width: 100px;
}

.stop-icon {
  color: var(--color-warning);
  margin: 0 4px;
}

.flight-type {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 8px;
}

.info-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

/* Cabin Selection */
.cabin-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.cabin-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 20px;
  border: 2px solid var(--border-color);
  border-radius: var(--radius-base);
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.cabin-card:hover {
  border-color: var(--color-primary-light);
}

.cabin-card.selected {
  border-color: var(--color-primary);
  background: rgba(var(--color-primary-bright-rgb), 0.03);
}

.cabin-card.sold-out {
  opacity: 0.5;
  cursor: not-allowed;
  border-color: var(--border-color);
}

.cabin-card.sold-out:hover {
  border-color: var(--border-color);
}

.sold-out-badge {
  color: var(--text-secondary);
  background: var(--bg-muted);
  padding: 0 6px;
  border-radius: 3px;
  font-size: 11px;
}

.sold-out-large {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-muted);
  padding: 6px 16px;
  border-radius: 6px;
  white-space: nowrap;
}

.cabin-left {
  width: 120px;
  flex-shrink: 0;
}

.cabin-class-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.cabin-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.cabin-rules {
  flex: 1;
  font-size: 13px;
}

.rule {
  margin-bottom: 4px;
  color: var(--text-regular);
}

.rule-label {
  color: var(--text-secondary);
}

.cabin-price-area {
  text-align: right;
  flex-shrink: 0;
}

.price-breakdown {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.total-price .currency {
  font-size: 16px;
  color: var(--color-danger);
}

.total-price .amount {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-danger);
}

/* Services */
.services-section {
  margin-bottom: 16px;
}

.services-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}

.service-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--bg-color);
  border-radius: var(--radius-base);
}

.service-name {
  font-weight: 600;
  color: var(--text-primary);
}

.service-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

/* Book Footer */
.book-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  z-index: 100;
  border-radius: 0;
  max-width: 100%;
  box-shadow: var(--shadow-bar-top);
}

.footer-price .label {
  font-size: 14px;
  color: var(--text-secondary);
  margin-right: 8px;
}

.footer-price .currency {
  font-size: 18px;
  color: var(--color-danger);
}

.footer-price .amount {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-danger);
}

.footer-price .per-pax {
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
