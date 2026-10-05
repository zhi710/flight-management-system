<template>
  <div class="airport-board">
    <!-- 工具栏 -->
    <div class="board-toolbar">
      <div class="board-toolbar-left">
        <el-radio-group v-model="direction" size="large">
          <el-radio-button value="DEP">{{ $t('board.departures') }}</el-radio-button>
          <el-radio-button value="ARR">{{ $t('board.arrivals') }}</el-radio-button>
        </el-radio-group>

        <el-select
          v-model="airportCode"
          filterable
          :placeholder="$t('board.airport')"
          class="airport-select"
        >
          <el-option
            v-for="a in airports"
            :key="a.code"
            :value="a.code"
            :label="a.city + ' · ' + a.name"
          />
        </el-select>

        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          class="date-picker"
          @change="load"
        />

        <el-input
          v-model="keyword"
          :placeholder="$t('board.searchPlaceholder')"
          clearable
          class="search-input"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <div class="board-clock">{{ nowText }}</div>
    </div>

    <!-- 表格 -->
    <div v-loading="loading" class="board-body">
      <div class="board-table">
        <div class="board-row board-head" :class="rowClass">
          <span>{{ $t('board.flightNo') }}</span>
          <span>{{ $t('board.airline') }}</span>
          <span>{{ direction === 'DEP' ? $t('board.destination') : $t('board.origin') }}</span>
          <span>{{ $t('board.scheduled') }}</span>
          <span>{{ $t('board.actual') }}</span>
          <span v-if="direction === 'DEP'">{{ $t('board.gate') }}</span>
          <span>{{ $t('board.status') }}</span>
        </div>

        <div
          v-for="f in filtered"
          :key="f.flightId"
          class="board-row"
          :class="rowClass"
        >
          <span class="flight-no">{{ f.flightNo }}</span>
          <span class="airline">{{ f.airlineName || '—' }}</span>
          <span class="route">
            <span class="route-city">{{ direction === 'DEP' ? (f.arrivalCity || f.arrival) : (f.departureCity || f.departure) }}</span>
            <span class="route-airport">{{ direction === 'DEP' ? f.arrivalAirportName : f.departureAirportName }}</span>
          </span>
          <span class="time">{{ direction === 'DEP' ? fmtTime(f.departureTime) : fmtTime(f.arrivalTime) }}</span>
          <span class="time actual">{{ direction === 'DEP' ? fmtTime(f.actualDepartureTime) : fmtTime(f.actualArrivalTime) }}</span>
          <span v-if="direction === 'DEP'" class="gate">{{ f.gate || '—' }}</span>
          <span :class="['status', statusClass(f.status)]">{{ f.statusText }}</span>
        </div>

        <div v-if="!loading && filtered.length === 0" class="board-empty">
          <el-empty :description="$t('board.empty')" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getFlightBoard, getAirports } from '@/api/flight'

const direction = ref<'DEP' | 'ARR'>('DEP')
const airportCode = ref('')
const date = ref(new Date().toISOString().split('T')[0])
const keyword = ref('')
const airports = ref<any[]>([])
const allFlights = ref<any[]>([])
const loading = ref(false)
const nowText = ref('')

const rowClass = computed(() => (direction.value === 'DEP' ? 'is-dep' : 'is-arr'))

const filtered = computed(() => {
  const code = airportCode.value
  const kw = keyword.value.trim().toUpperCase()
  let list = allFlights.value.filter((f) =>
    direction.value === 'DEP' ? f.departure === code : f.arrival === code,
  )
  if (kw) {
    list = list.filter((f) => String(f.flightNo || '').toUpperCase().includes(kw))
  }
  return [...list].sort((a, b) => {
    const ta = direction.value === 'DEP' ? a.departureTime : a.arrivalTime
    const tb = direction.value === 'DEP' ? b.departureTime : b.arrivalTime
    return String(ta || '').localeCompare(String(tb || ''))
  })
})

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function fmtTime(dt?: string) {
  if (!dt) return '—'
  const d = new Date(dt)
  if (Number.isNaN(d.getTime())) return '—'
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function statusClass(s: string) {
  const map: Record<string, string> = {
    SCHEDULED: 'st-scheduled',
    BOARDING: 'st-boarding',
    DEPARTED: 'st-departed',
    FLYING: 'st-departed',
    ARRIVED: 'st-arrived',
    COMPLETED: 'st-arrived',
    DELAYED: 'st-delayed',
    CANCELLED: 'st-cancelled',
    DIVERTED: 'st-delayed',
    RETURNED: 'st-delayed',
  }
  return map[s] || 'st-scheduled'
}

async function loadAirports() {
  try {
    const res = (await getAirports()) as any
    airports.value = res?.data || []
    if (!airportCode.value && airports.value.length) {
      airportCode.value = airports.value[0].code
    }
  } catch {
    // 静默失败，选择器保持为空
  }
}

async function load() {
  loading.value = true
  try {
    const res = (await getFlightBoard(date.value)) as any
    allFlights.value = res?.data || []
  } catch {
    allFlights.value = []
  } finally {
    loading.value = false
  }
}

function tick() {
  const n = new Date()
  nowText.value = `${pad(n.getHours())}:${pad(n.getMinutes())}:${pad(n.getSeconds())}`
}

let clockTimer: ReturnType<typeof setInterval> | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  loadAirports()
  load()
  tick()
  clockTimer = setInterval(tick, 1000)
  pollTimer = setInterval(load, 30_000)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.airport-board {
  background: var(--bg-board);
  border-radius: 12px;
  overflow: hidden;
  color: var(--text-on-dark);
}

.board-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-wrap: wrap;
}

.board-toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.airport-select {
  width: 220px;
}

.date-picker {
  width: 150px;
}

.search-input {
  width: 160px;
}

.board-clock {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-primary-on-dark);
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
}

.board-table {
  background: rgba(255, 255, 255, 0.03);
}

.board-row {
  display: grid;
  gap: 16px;
  padding: 14px 20px;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 15px;
}

.board-row.is-dep {
  grid-template-columns: 1.1fr 1.5fr 1.8fr 0.9fr 0.9fr 0.8fr 1fr;
}

.board-row.is-arr {
  grid-template-columns: 1.1fr 1.5fr 1.8fr 0.9fr 0.9fr 1fr;
}

.board-row:last-child {
  border-bottom: none;
}

.board-head {
  background: rgba(var(--color-primary-bright-rgb), 0.15);
  color: var(--text-on-dark-muted);
  font-size: 13px;
  font-weight: 600;
}

.flight-no {
  font-weight: 700;
  font-size: 17px;
  color: var(--text-on-dark-strong);
  font-variant-numeric: tabular-nums;
}

.airline {
  color: var(--text-on-dark-secondary);
}

.route {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.route-city {
  font-weight: 600;
  color: var(--text-on-dark-strong);
}

.route-airport {
  font-size: 12px;
  color: var(--text-on-dark-muted);
}

.time {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.time.actual {
  color: var(--text-on-dark-muted);
}

.gate {
  color: var(--color-primary-on-dark);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.status {
  font-weight: 600;
}

/* 状态色语义（深色底专用令牌）：
   这里是深色航班板（--bg-board），不能复用浅色主题的语义色 ——
   --text-secondary 在板底上只有 3.45:1、--color-primary 只有 3.89:1，都不达标。
   延误=警告琥珀、取消=异常红、登机中=品牌亮蓝、计划=中性灰、已起飞/到达=成功绿 */
.st-scheduled { color: var(--text-on-dark-muted); }
.st-boarding { color: var(--color-primary-on-dark); }
.st-departed { color: var(--color-success-on-dark); }
.st-arrived { color: var(--color-success-on-dark); }
.st-delayed { color: var(--color-warning-on-dark); }
.st-cancelled { color: var(--color-danger-on-dark); }

.board-empty {
  padding: 40px 0;
}

.board-empty :deep(.el-empty__description p) {
  color: var(--text-on-dark-muted);
}
</style>
