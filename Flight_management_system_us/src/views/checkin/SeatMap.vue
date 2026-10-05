<template>
  <div class="seat-map-page page-container">
    <h2 class="page-title">{{ $t('checkin.selectSeat') }}</h2>

    <div v-if="loading" class="loading">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else-if="seatData">
      <!-- Legend -->
      <div class="legend">
        <span class="legend-item">
          <span class="legend-sample"><SeatGlyph /><span class="sample-dot available"></span></span>
          {{ $t('checkin.availableSeat') }}
        </span>
        <span class="legend-item">
          <span class="legend-sample"><SeatGlyph /><span class="sample-dot occupied"></span></span>
          {{ $t('checkin.occupied') }}
        </span>
        <span class="legend-item">
          <span class="legend-sample"><SeatGlyph /><span class="sample-dot selected"></span></span>
          {{ $t('checkin.selectedSeat') }}
        </span>
        <span class="legend-item">
          <span class="legend-sample"><SeatGlyph /><span class="sample-dot exit"></span></span>
          {{ $t('checkin.exitRow') }}
        </span>
      </div>

      <!-- Aircraft visualization -->
      <div class="aircraft-visual card-shadow">
        <div class="aircraft-nose">
          <el-icon :size="24"><Promotion /></el-icon>
          <span>{{ $t('checkin.nose') }}</span>
        </div>

        <div class="seat-grid">
          <!-- Column headers -->
          <div class="row-header">
            <span class="row-num"></span>
            <span v-for="col in seatData.layout.columns" :key="col" class="col-label">{{ col }}</span>
          </div>

          <!-- Seat rows -->
          <div
            v-for="row in seatData.layout.rows"
            :key="row"
            :class="['seat-row', { 'exit-row': seatData.layout.exitRows.includes(row) }]"
          >
            <span class="row-num">{{ row }}</span>
            <template v-for="col in seatData.layout.columns" :key="`${row}-${col}`">
              <!-- Aisle gap -->
              <span
                v-if="isAisle(col)"
                class="aisle"
              ></span>
              <span
                :class="['seat', seatClass(row, col)]"
                @click="handleSeatClick(row, col)"
                :title="`${row}${col}`"
              >
                <SeatGlyph />
                <span class="seat-dot"></span>
              </span>
            </template>
          </div>
        </div>

        <div class="aircraft-tail">
          <span>{{ $t('checkin.tail') }}</span>
        </div>
      </div>

      <!-- Selected seat info & confirm -->
      <div v-if="selectedSeat" class="selected-info card-shadow">
        <div class="selected-detail">
          <span class="selected-label">{{ $t('checkin.selectedSeat') }}: </span>
          <span class="selected-value">{{ selectedSeat.row }}{{ selectedSeat.column }}</span>
          <el-tag v-if="isExtraSeat(selectedSeat.row, selectedSeat.column)" type="warning" size="small">
            {{ $t('checkin.extraFee') }} +¥{{ getExtraFee(selectedSeat.row, selectedSeat.column) }}
          </el-tag>
        </div>
        <el-button type="primary" :loading="confirming" @click="handleConfirm">{{ $t('checkin.confirmSeat') }}</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, h, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getSeatMap, doCheckin } from '@/api/checkin'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(true)
const confirming = ref(false)
const seatData = ref<any>(null)
const selectedSeat = ref<{ row: number; column: string } | null>(null)

/** 椅子图形：所有座位统一用同一个中性色图标，状态靠下方色块表达 */
const SeatGlyph = () => h('svg', {
  class: 'chair-icon',
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  'stroke-width': 1.7,
  'stroke-linecap': 'round',
  'stroke-linejoin': 'round',
  'aria-hidden': 'true',
}, [
  h('path', { d: 'M19 9V6a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v3' }),
  h('path', { d: 'M3 16a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5a2 2 0 0 0-4 0v2H7v-2a2 2 0 0 0-4 0Z' }),
  h('path', { d: 'M5 18v2' }),
  h('path', { d: 'M19 18v2' }),
])

function isAisle(col: string) {
  if (!seatData.value) return false
  const cols = seatData.value.layout.columns
  const mid = Math.floor(cols.length / 2)
  return cols.indexOf(col) === mid - 1
}

function getSeat(row: number, col: string) {
  return seatData.value?.seats?.find((s: any) => s.row === row && s.column === col)
}

function seatStatus(row: number, col: string) {
  const seat = getSeat(row, col)
  if (!seat) return 'UNAVAILABLE'
  return seat.status
}

function seatClass(row: number, col: string) {
  const status = seatStatus(row, col)
  // 不可选（含已占/无座位记录）统一按灰色 occupied 处理，避免误点
  if (status !== 'AVAILABLE') return 'occupied'
  if (selectedSeat.value?.row === row && selectedSeat.value?.column === col) return 'selected'
  if (seatData.value?.layout?.exitRows.includes(row)) return 'exit'
  return 'available'
}

function isExtraSeat(row: number, col: string) {
  const seat = getSeat(row, col)
  return seat?.extra === true
}

function getExtraFee(row: number, col: string) {
  const seat = getSeat(row, col)
  return seat?.extraFee || 0
}

function handleSeatClick(row: number, col: string) {
  if (seatStatus(row, col) !== 'AVAILABLE') return
  selectedSeat.value = { row, column: col }
}

async function handleConfirm() {
  if (!selectedSeat.value) return
  confirming.value = true
  try {
    const orderId = route.query.orderId as string
    const res = await doCheckin({
      orderId,
      passengers: [{
        passengerIndex: 0,
        seatRow: selectedSeat.value.row,
        seatColumn: selectedSeat.value.column,
      }],
    })
    ElMessage.success(t('checkin.checkinSuccess'))
    // 值机响应里已带回值机号，直接进电子登机牌，不再丢弃
    const checkinId = res?.data?.checkinId
    if (checkinId) {
      router.push(`/home/checkin/boarding-pass/${checkinId}`)
    } else {
      router.push('/home/orders')
    }
  } catch {
    // error handled
  } finally {
    confirming.value = false
  }
}

onMounted(async () => {
  try {
    const flightId = route.params.flightId as string
    const res = await getSeatMap(flightId)
    seatData.value = res.data
  } catch {
    ElMessage.error(t('error.serverError'))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.seat-map-page {
  padding-top: 24px;
  padding-bottom: 48px;
  max-width: 600px;
  margin: 0 auto;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.legend {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
  justify-content: center;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

/* 图例：椅子图标 + 状态色块 */
.legend-sample {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.legend-sample .chair-icon {
  color: var(--text-placeholder);
}

.sample-dot {
  width: 11px;
  height: 11px;
  border-radius: 2px;
  box-sizing: border-box;
}

.sample-dot.available {
  background: var(--color-success);
}

.sample-dot.occupied {
  background: var(--fill-neutral);
  border: 1px solid var(--border-color-strong);
}

.sample-dot.selected {
  background: var(--color-primary);
}

.sample-dot.exit {
  background: var(--color-warning-soft);
  border: 1px solid var(--color-warning);
}

.aircraft-visual {
  padding: 24px;
  text-align: center;
}

.aircraft-nose,
.aircraft-tail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 16px;
}

.aircraft-tail {
  margin-top: 16px;
  margin-bottom: 0;
}

.seat-grid {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.row-header {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}

.row-num {
  width: 28px;
  font-size: 11px;
  color: var(--text-secondary);
  text-align: center;
}

.col-label {
  width: 36px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.seat-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.seat-row.exit-row {
  background: rgba(var(--color-warning-rgb), 0.05);
  border-radius: 4px;
  padding: 2px 0;
}

.aisle {
  width: 16px;
}

/* 每个座位：上方统一椅子图标，下方小色块表达状态 */
.seat {
  width: 36px;
  height: 42px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.15s;
  box-sizing: border-box;
}

.chair-icon {
  width: 22px;
  height: 20px;
  display: block;
  color: var(--text-placeholder); /* 椅子统一中性色，避免与状态色混淆 */
}

.seat-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  box-sizing: border-box;
  transition: transform 0.15s;
}

/* 可选（绿点） */
.seat.available:hover {
  background: rgba(var(--color-success-rgb), 0.1);
}
.seat.available .seat-dot {
  background: var(--color-success);
}
.seat.available:hover .seat-dot {
  transform: scale(1.2);
}

/* 已占 / 不可选（灰点） */
.seat.occupied {
  cursor: not-allowed;
}
.seat.occupied .chair-icon {
  color: var(--text-placeholder);
}
.seat.occupied .seat-dot {
  background: var(--fill-neutral);
  border: 1px solid var(--border-color-strong);
}

/* 已选中（主色点 + 浅色底） */
.seat.selected {
  background: rgba(var(--color-primary-bright-rgb), 0.1);
}
.seat.selected .seat-dot {
  background: var(--color-primary);
}

/* 安全出口排（橙点） */
.seat.exit .seat-dot {
  background: var(--color-warning-soft);
  border: 1px solid var(--color-warning);
}

.selected-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}

.selected-detail {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected-label {
  font-size: 14px;
  color: var(--text-secondary);
}

.selected-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary);
}
</style>
