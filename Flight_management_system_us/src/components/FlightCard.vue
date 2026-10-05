<template>
  <div class="flight-card" @click="$emit('select', flight)">
    <div class="flight-main">
      <!-- Airline info -->
      <div class="airline-info">
        <img
          v-if="flight.airline?.logo"
          :src="getAirlineLogoUrl(flight.airline.logo)"
          :alt="transAirline(flight.airline.name)"
          class="airline-logo"
        />
        <div>
          <div class="airline-name">{{ transAirline(flight.airline?.name) }}</div>
          <div class="flight-no">{{ flight.flightNo }}</div>
        </div>
      </div>

      <!-- Departure -->
      <div class="time-info departure">
        <div class="time">{{ formatTime(flight.departure?.dateTime) }}</div>
        <div class="airport">{{ transAirport(flight.departure?.airport) }}</div>
        <div class="terminal">{{ flight.departure?.terminal }}</div>
      </div>

      <!-- Duration & stops -->
      <div class="duration-info">
        <div class="duration">{{ formatDuration(flight.duration) }}</div>
        <div class="duration-line">
          <span class="dot"></span>
          <span class="line"></span>
          <span v-if="flight.stops && flight.stops > 0" class="stop-dot">{{ flight.stops }}{{ $t('flight.stop') }}</span>
          <span v-else class="stop-label">{{ $t('flight.direct') }}</span>
          <span class="line"></span>
          <span class="dot"></span>
        </div>
        <div class="aircraft">{{ flight.aircraft?.name }}</div>
      </div>

      <!-- Arrival -->
      <div class="time-info arrival">
        <div class="time">{{ formatTime(flight.arrival?.dateTime) }}</div>
        <div class="airport">{{ transAirport(flight.arrival?.airport) }}</div>
        <div class="terminal">{{ flight.arrival?.terminal }}</div>
      </div>

      <!-- Price -->
      <div class="price-info">
        <div class="price">
          <span class="currency">¥</span>
          <span class="amount">{{ lowestPrice }}</span>
          <span class="suffix">{{ $t('flight.from') }}</span>
        </div>
        <div class="seats">
          <template v-if="lowestSeats > 0">{{ $t('flight.seats', { count: lowestSeats }) }}</template>
          <span v-else class="sold-out-tag">{{ $t('flight.soldOut') }}</span>
        </div>
      </div>
    </div>

    <!-- Services & punctuality -->
    <div class="flight-meta">
      <div class="services">
        <el-tag v-if="flight.services?.wifi" size="small" type="info">{{ $t('flight.wifi') }}</el-tag>
        <el-tag v-if="flight.services?.meal" size="small" type="info">{{ $t('flight.meal') }}</el-tag>
        <el-tag v-if="flight.services?.power" size="small" type="info">{{ $t('flight.power') }}</el-tag>
      </div>
      <div v-if="flight.punctuality" class="punctuality">
        <el-icon><CircleCheck /></el-icon>
        {{ $t('flight.punctuality') }} {{ flight.punctuality }}%
      </div>
    </div>

    <!-- Expanded cabin details -->
    <div v-if="showCabins" class="cabins-detail">
      <div
        v-for="cabin in sortedCabins"
        :key="cabin.class"
        :class="['cabin-row', { 'sold-out': cabin.seats <= 0 }]"
        @click.stop="cabin.seats > 0 && $emit('book', { flight, cabin })"
      >
        <span class="cabin-name">{{ cabin.className }}</span>
        <span class="cabin-baggage">{{ $t('flight.baggage') }} {{ cabin.baggage }}</span>
        <span class="cabin-refund">{{ cabin.refundRule }}</span>
        <span class="cabin-price">
          <template v-if="cabin.seats > 0">
            <em>¥{{ cabin.totalPrice }}</em>
            <el-button type="primary" size="small">{{ $t('flight.book') }}</el-button>
          </template>
          <span v-else class="sold-out-label">{{ $t('flight.soldOut') }}</span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getAirlineLogoUrl } from '@/utils/image'
import { useMappings } from '@/locales/mappings'

const { transAirport, transAirline } = useMappings()

const props = defineProps<{
  flight: any
  showCabins?: boolean
}>()

defineEmits<{
  select: [flight: any]
  book: [data: { flight: any; cabin: any }]
}>()

/** 按价格升序，让「起价」那一档排在最前，与右侧「¥xxx 起」对得上 */
const sortedCabins = computed(() =>
  [...(props.flight.cabins || [])].sort((a: any, b: any) => a.totalPrice - b.totalPrice)
)

/**
 * 起价舱位：优先取「有余票」里最便宜的一档；全部售罄时回退到最便宜的一档（余票为 0 → 显示已售罄）。
 * 价格与余票必须取自**同一个舱位**：此前分别取「最低价」与「所有舱位里最少的余票」，
 * 会串成「¥1010 起 · 剩余 4 座」（1010 是经济舱、4 座是头等舱）。
 */
const lowestCabin = computed(() => {
  const list = props.flight.cabins || []
  if (!list.length) return null
  const bookable = list.filter((c: any) => c.seats > 0)
  const pool = bookable.length ? bookable : list
  return pool.reduce((a: any, b: any) => (b.totalPrice < a.totalPrice ? b : a))
})

const lowestPrice = computed(() =>
  lowestCabin.value ? lowestCabin.value.totalPrice : '--'
)

const lowestSeats = computed(() => lowestCabin.value?.seats ?? 0)

function formatTime(dateTime?: string) {
  if (!dateTime) return '--:--'
  const d = new Date(dateTime)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatDuration(minutes?: number) {
  if (!minutes) return '--'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}h${m > 0 ? m + 'm' : ''}` : `${m}m`
}
</script>

<style scoped>
.flight-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px 24px;
  cursor: pointer;
  transition: all 0.25s ease;
  margin-bottom: 12px;
}

.flight-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.flight-main {
  display: flex;
  align-items: center;
  gap: 24px;
}

.airline-info {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 140px;
  flex-shrink: 0;
}

.airline-logo {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  object-fit: contain;
}

.airline-name {
  font-size: 13px;
  color: var(--text-secondary);
}

.flight-no {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.time-info {
  text-align: center;
  width: 100px;
  flex-shrink: 0;
}

.time-info .time {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.time-info .airport {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-regular);
  margin-top: 4px;
}

.time-info .terminal {
  font-size: 12px;
  color: var(--text-secondary);
}

.duration-info {
  flex: 1;
  text-align: center;
  padding: 0 16px;
}

.duration {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.duration-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid var(--color-primary);
}

.line {
  flex: 1;
  height: 1px;
  background: var(--color-secondary);
  max-width: 60px;
}

.stop-label {
  font-size: 11px;
  color: var(--color-primary);
  padding: 2px 8px;
  background: rgba(var(--color-primary-bright-rgb), 0.08);
  border-radius: 10px;
  white-space: nowrap;
}

.stop-dot {
  font-size: 11px;
  color: var(--color-warning);
  padding: 2px 8px;
  background: rgba(var(--color-warning-rgb), 0.1);
  border-radius: 10px;
  white-space: nowrap;
}

.aircraft {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 6px;
}

.price-info {
  text-align: right;
  width: 120px;
  flex-shrink: 0;
}

.price .currency {
  font-size: 14px;
  color: var(--color-danger);
}

.price .amount {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-danger);
}

.price .suffix {
  font-size: 12px;
  color: var(--text-secondary);
}

.seats {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.flight-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.services {
  display: flex;
  gap: 6px;
}

.punctuality {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-success);
}

.cabins-detail {
  margin-top: 12px;
  border-top: 1px dashed var(--border-color);
  padding-top: 12px;
}

.cabin-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--bg-muted);
  transition: background 0.2s;
}

.cabin-row:hover {
  background: rgba(var(--color-primary-bright-rgb), 0.03);
}

.cabin-row:last-child {
  border-bottom: none;
}

.cabin-name {
  font-weight: 600;
  width: 70px;
  color: var(--text-primary);
}

.cabin-baggage,
.cabin-refund {
  font-size: 12px;
  color: var(--text-secondary);
  flex: 1;
}

.cabin-price {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.cabin-price em {
  font-style: normal;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-danger);
}

.sold-out {
  opacity: 0.5;
  cursor: not-allowed !important;
}

.sold-out-label {
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--bg-muted);
  padding: 4px 12px;
  border-radius: 4px;
}

.sold-out-tag {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-muted);
  padding: 2px 8px;
  border-radius: 4px;
}

/* 移动端：原 flex 单行的最小宽度约 556px（航司 140 + 时刻 100×2 + 价格 120 + gap 24×4），
   390px 下每张卡片都会把页面撑出约 292px 横向滚动。
   改为两行网格：第一行「航司 …… 价格」，第二行「出发 | 时长 | 到达」。 */
@media (max-width: 768px) {
  .flight-main {
    display: grid;
    grid-template-columns: 1fr auto 1fr;
    grid-template-areas:
      'air air price'
      'dep dur arr';
    gap: 14px 8px;
    align-items: center;
  }

  .airline-info {
    grid-area: air;
    width: auto;
  }

  .price-info {
    grid-area: price;
    width: auto;
  }

  .time-info.departure {
    grid-area: dep;
    width: auto;
  }

  .time-info.arrival {
    grid-area: arr;
    width: auto;
  }

  .duration-info {
    grid-area: dur;
    flex: none;
    padding: 0 4px;
  }

  .time-info .time {
    font-size: 22px;
  }

  .price .amount {
    font-size: 22px;
  }

  .flight-card {
    padding: 16px;
  }

  .cabin-row {
    gap: 10px;
  }

  .cabin-name {
    width: auto;
  }

  /* 退改规则文本最长，移动端收起（详情页有完整说明） */
  .cabin-refund {
    display: none;
  }
}
</style>
