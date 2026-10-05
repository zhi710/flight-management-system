<template>
  <div class="status-page page-container">
    <h2 class="page-title">{{ $t('flightStatus.title') }}</h2>
    <p class="page-desc">{{ $t('flightStatus.desc')  }}</p>

    <el-tabs v-model="activeTab" class="status-tabs">
      <el-tab-pane :label="$t('flightStatus.tabSingle')" name="single">

    <!-- Search form -->
    <div class="search-card card-shadow">
      <el-form :model="form" inline>
        <el-form-item :label="$t('flightStatus.flightNo')">
          <el-input v-model="form.flightNo" :placeholder="$t('flightStatus.flightNoPlaceholder') " clearable style="width: 150px" />
        </el-form-item>
        <el-form-item :label="$t('common.date')">
          <el-date-picker
            v-model="form.date"
            type="date"
            :placeholder="$t('home.selectDate')"
            value-format="YYYY-MM-DD"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item :label="$t('flightStatus.departure')">
          <el-input v-model="form.departure" :placeholder="$t('flightStatus.cityAirport') " clearable style="width: 120px" />
        </el-form-item>
        <el-form-item :label="$t('flightStatus.arrival')">
          <el-input v-model="form.arrival" :placeholder="$t('flightStatus.cityAirport') " clearable style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">
            <el-icon><Search /></el-icon> {{ $t('flightStatus.search') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Result -->
    <div v-if="flightStatus" class="result-area">
      <!-- Status card -->
      <div class="status-card card-shadow">
        <div class="status-header">
          <div class="flight-badge">
            <span class="flight-no">{{ flightStatus.flightNo }}</span>
            <span class="flight-date">{{ flightStatus.date }}</span>
          </div>
          <el-tag :type="statusTagType(flightStatus.status)" size="large" effect="dark">
            {{ flightStatus.statusText }}
          </el-tag>
        </div>

        <!-- Route -->
        <div class="status-route">
          <div class="endpoint">
            <div class="time-block">
              <div class="label">{{ $t('flightStatus.plannedTime') }}</div>
              <div class="time">{{ formatTime(flightStatus.departure?.scheduled) }}</div>
            </div>
            <div v-if="flightStatus.departure?.estimated" class="time-block estimated">
              <div class="label">{{ $t('flightStatus.estimatedTime') }}</div>
              <div class="time">{{ formatTime(flightStatus.departure.estimated) }}</div>
            </div>
            <div class="airport">{{ transAirport(flightStatus.departure?.airportName) }}</div>
            <div class="detail">{{ flightStatus.departure?.terminal }}{{ $t('flight.terminal') }} · {{ flightStatus.departure?.gate }}{{ $t('checkin.gate') }}</div>
          </div>

          <div class="route-line">
            <div class="line-visual">
              <span class="dot start"></span>
              <span class="dash"></span>
              <el-icon v-if="flightStatus.status === 'FLYING'" class="plane-icon"><Promotion /></el-icon>
              <span class="dash"></span>
              <span class="dot end"></span>
            </div>
            <div v-if="flightStatus.delay" class="delay-info">
              <el-icon><WarningFilled /></el-icon>
              {{ $t('flightStatus.delayed') }} {{ flightStatus.delay.minutes }}{{ $t('flightStatus.minutes') }} · {{ flightStatus.delay.reason }}
            </div>
          </div>

          <div class="endpoint">
            <div class="time-block">
              <div class="label">{{ $t('flightStatus.plannedTime') }}</div>
              <div class="time">{{ formatTime(flightStatus.arrival?.scheduled) }}</div>
            </div>
            <div v-if="flightStatus.arrival?.estimated" class="time-block estimated">
              <div class="label">{{ $t('flightStatus.estimatedTime') }}</div>
              <div class="time">{{ formatTime(flightStatus.arrival.estimated) }}</div>
            </div>
            <div class="airport">{{ transAirport(flightStatus.arrival?.airportName) }}</div>
            <div class="detail">{{ flightStatus.arrival?.terminal }}{{ $t('flight.terminal') }}</div>
          </div>
        </div>

        <div class="aircraft-info">
          <el-tag type="info">{{ flightStatus.aircraft }}</el-tag>
        </div>
      </div>

      <!-- Timeline -->
      <div v-if="flightStatus.timeline?.length" class="timeline-card card-shadow">
        <h3>{{ $t('flightStatus.timeline') }}</h3>
        <el-timeline>
          <el-timeline-item
            v-for="(item, idx) in flightStatus.timeline"
            :key="idx"
            :timestamp="formatTime(item.time)"
            :type="idx === flightStatus.timeline.length - 1 ? 'primary' : ''"
            placement="top"
          >
            {{ item.event }}
          </el-timeline-item>
        </el-timeline>
      </div>

      <!-- Subscribe -->
      <div class="subscribe-card card-shadow">
        <h3>{{ $t('flightStatus.subscribe') }}</h3>
        <p>{{ $t('flightStatus.subscribeDesc')  }}</p>
        <div class="subscribe-actions">
          <el-checkbox-group v-model="channels">
            <el-checkbox value="SMS">{{ $t('flightStatus.sms') }}</el-checkbox>
            <el-checkbox value="EMAIL">{{ $t('flightStatus.email') }}</el-checkbox>
            <el-checkbox value="PUSH">{{ $t('flightStatus.push') }}</el-checkbox>
            <el-checkbox value="WECHAT">{{ $t('flightStatus.wechat') }}</el-checkbox>
          </el-checkbox-group>
          <el-button type="primary" size="small" @click="handleSubscribe" :loading="subscribing">
            {{ $t('flightStatus.subscribe') }}
          </el-button>
        </div>
      </div>
    </div>

    <div v-else-if="searched && !loading" class="empty-result">
      <el-empty :description="$t('flightStatus.noResult')" />
    </div>
      </el-tab-pane>

      <el-tab-pane :label="$t('flightStatus.tabBoard')" name="board">
        <AirportBoard />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getFlightStatus, subscribeFlight } from '@/api/status'
import { useMappings } from '@/locales/mappings'
import AirportBoard from './AirportBoard.vue'

const { t } = useI18n()
const { transAirport } = useMappings()
const activeTab = ref('single')
const loading = ref(false)
const subscribing = ref(false)
const searched = ref(false)
const flightStatus = ref<any>(null)
const channels = ref<string[]>(['SMS'])

const form = reactive({
  flightNo: '',
  date: new Date().toISOString().split('T')[0],
  departure: '',
  arrival: '',
})

function formatTime(dt?: string) {
  if (!dt) return '--:--'
  const d = new Date(dt)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function statusTagType(status: string) {
  const map: Record<string, string> = {
    SCHEDULED: 'info',
    BOARDING: '',
    DEPARTED: 'success',
    FLYING: 'success',
    ARRIVED: 'success',
    DELAYED: 'warning',
    CANCELLED: 'danger',
    DIVERTED: 'warning',
    RETURNED: 'warning',
  }
  return map[status] 
}

async function handleSearch() {
  if (!form.date) {
    ElMessage.warning(t('flightStatus.selectDate') )
    return
  }
  loading.value = true
  searched.value = true
  try {
    const res = await getFlightStatus({
      flightNo: form.flightNo || undefined,
      date: form.date,
      departure: form.departure || undefined,
      arrival: form.arrival || undefined,
    })
    flightStatus.value = res.data
  } catch {
    flightStatus.value = null
    ElMessage.error(t('flightStatus.searchFailed') || '查询失败')
  } finally {
    loading.value = false
  }
}

async function handleSubscribe() {
  if (!flightStatus.value) return
  if (channels.value.length === 0) {
    ElMessage.warning(t('flightStatus.selectChannel') )
    return
  }
  subscribing.value = true
  try {
    await subscribeFlight({
      flightNo: flightStatus.value.flightNo,
      date: flightStatus.value.date,
      channels: channels.value,
      types: ['DELAY', 'GATE_CHANGE', 'BOARDING', 'CANCEL'],
    })
    ElMessage.success(t('flightStatus.subscribed'))
  } catch {
    // error handled
  } finally {
    subscribing.value = false
  }
}
</script>

<style scoped>
.status-page {
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

.search-card {
  margin-bottom: 24px;
}

.status-card {
  margin-bottom: 16px;
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.flight-badge {
  display: flex;
  align-items: center;
  gap: 12px;
}

.flight-no {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.flight-date {
  font-size: 14px;
  color: var(--text-secondary);
}

.status-route {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 16px;
}

.endpoint {
  flex: 1;
  text-align: center;
}

.time-block {
  margin-bottom: 4px;
}

.time-block .label {
  font-size: 12px;
  color: var(--text-secondary);
}

.time-block .time {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
}

.time-block.estimated .time {
  color: var(--color-danger);
}

.endpoint .airport {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 8px;
}

.endpoint .detail {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.route-line {
  flex: 1.5;
  text-align: center;
}

.line-visual {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
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
  max-width: 80px;
}

.plane-icon {
  color: var(--color-primary);
  font-size: 20px;
  margin: 0 8px;
}

.delay-info {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-danger);
  background: var(--color-danger-soft);
  padding: 4px 12px;
  border-radius: 12px;
}

.aircraft-info {
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.timeline-card {
  margin-bottom: 16px;
}

.timeline-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.subscribe-card {
  margin-bottom: 16px;
}

.subscribe-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.subscribe-card p {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.subscribe-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.empty-result {
  padding: 60px 0;
}
</style>
