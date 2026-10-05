<template>
  <div class="page-container">
    <!-- 选择航班 -->
    <div class="card-panel">
      <el-form :inline="true" class="search-bar">
        <el-form-item label="航班号">
          <el-input v-model="query.keyword" placeholder="如 CA1234" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="query.date" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="searching" @click="handleSearch">
            <el-icon><Search /></el-icon> 查询
          </el-button>
        </el-form-item>
        <el-form-item label="航班" v-if="flightOptions.length">
          <el-select v-model="selectedFlightId" style="width: 320px" @change="handleFlightChange">
            <el-option
              v-for="f in flightOptions"
              :key="f.flightId"
              :label="`${f.flightNo} · ${f.date} · ${f.route?.departure}→${f.route?.arrival}`"
              :value="f.flightId"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <!-- 座位统计 -->
    <div v-if="selectedFlightId" class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">总座位</div>
        <div class="stat-value">{{ stats.total }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">可预订</div>
        <div class="stat-value c-available">{{ stats.available }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已占用</div>
        <div class="stat-value c-occupied">{{ stats.occupied }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已锁定</div>
        <div class="stat-value c-locked">{{ stats.locked }}</div>
      </div>
    </div>

    <!-- 座位图 -->
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">座位管理</div>
        <div class="seat-legend">
          <span class="legend-item"><i class="dot dot-available"></i>可预订</span>
          <span class="legend-item"><i class="dot dot-occupied"></i>已占用</span>
          <span class="legend-item"><i class="dot dot-locked"></i>已锁定</span>
        </div>
      </div>

      <div v-if="!selectedFlightId" class="empty">
        <p class="empty-tip">请先查询并选择一个航班</p>
      </div>

      <div v-else v-loading="loading" class="seat-map-body">
        <div v-if="!seats.length && !loading" class="empty">
          <p class="empty-tip">该航班暂无座位数据，可先到「值机管理」打开值机以生成座位</p>
        </div>

        <div v-else class="seat-map">
          <div v-for="r in rows" :key="r.row" class="seat-row">
            <span class="row-num tnum">{{ r.row }}</span>
            <div class="seat-cells">
              <template v-for="(col, idx) in COLUMNS" :key="col">
                <span v-if="idx === 3" class="aisle"></span>
                <button
                  type="button"
                  class="seat"
                  :class="seatClass(r.seats[col])"
                  :title="seatTitle(r.seats[col])"
                  @click="onSeatClick(r.seats[col])"
                >
                  {{ col }}
                </button>
              </template>
            </div>
            <span class="cabin-tag">{{ r.cabinClass === 'BUSINESS' ? '公务' : r.cabinClass === 'FIRST' ? '头等' : '经济' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFlightList } from '@/api/flights'
import { getSeatMap, lockSeat, unlockSeat } from '@/api/passengers'

const COLUMNS = ['A', 'B', 'C', 'D', 'E', 'F']

const query = reactive({ keyword: '', date: '' })
const searching = ref(false)
const flightOptions = ref<any[]>([])
const selectedFlightId = ref('')

const loading = ref(false)
const seats = ref<any[]>([])

/** 按排聚合，便于按 3+3 布局渲染 */
const rows = computed(() => {
  const map = new Map<number, any>()
  for (const s of seats.value) {
    if (!map.has(s.row)) map.set(s.row, { row: s.row, cabinClass: s.cabinClass, seats: {} })
    map.get(s.row).seats[s.column] = s
  }
  return Array.from(map.values()).sort((a, b) => a.row - b.row)
})

const stats = computed(() => {
  const s = { total: seats.value.length, available: 0, occupied: 0, locked: 0 }
  for (const x of seats.value) {
    if (x.status === 'OCCUPIED') s.occupied++
    else if (x.status === 'LOCKED') s.locked++
    else s.available++
  }
  return s
})

async function handleSearch() {
  searching.value = true
  try {
    const params: Record<string, any> = { page: 1, pageSize: 50 }
    if (query.keyword) params.keyword = query.keyword
    if (query.date) params.date = query.date
    const data = await getFlightList(params) as any
    flightOptions.value = data.list || []
    if (!flightOptions.value.length) {
      ElMessage.warning('没有匹配的航班')
      selectedFlightId.value = ''
      seats.value = []
      return
    }
    // 默认选中第一条，省去用户再点一次
    selectedFlightId.value = flightOptions.value[0].flightId
    await fetchSeats()
  } catch (err: any) {
    ElMessage.error(err?.message || '查询航班失败')
  } finally {
    searching.value = false
  }
}

function handleFlightChange() {
  fetchSeats()
}

async function fetchSeats() {
  if (!selectedFlightId.value) return
  loading.value = true
  try {
    seats.value = (await getSeatMap(selectedFlightId.value)) as unknown as any[] || []
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '加载座位图失败')
    seats.value = []
  } finally {
    loading.value = false
  }
}

function seatClass(seat: any) {
  if (!seat) return 'seat-missing'
  if (seat.status === 'OCCUPIED') return 'seat-occupied'
  if (seat.status === 'LOCKED') return 'seat-locked'
  return 'seat-available'
}

function seatTitle(seat: any) {
  if (!seat) return ''
  const label = seat.status === 'OCCUPIED' ? '已占用' : seat.status === 'LOCKED' ? '已锁定' : '可预订'
  const fee = seat.extraFee > 0 ? ` · 加价 ¥${seat.extraFee}` : ''
  return `${seat.row}${seat.column} · ${label}${fee}`
}

async function onSeatClick(seat: any) {
  if (!seat) return
  if (seat.status === 'OCCUPIED') {
    ElMessage.warning(`座位 ${seat.row}${seat.column} 已有旅客，不能锁定或解锁`)
    return
  }
  const locking = seat.status !== 'LOCKED'
  const action = locking ? '锁定' : '解锁'
  try {
    await ElMessageBox.confirm(
      `确定${action}座位 ${seat.row}${seat.column}？`,
      `${action}座位`,
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const payload = { row: seat.row, column: seat.column }
    if (locking) await lockSeat(selectedFlightId.value, payload)
    else await unlockSeat(selectedFlightId.value, payload)
    ElMessage.success(`${action}成功`)
    await fetchSeats()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || `${action}失败`)
  }
}
</script>

<style scoped lang="scss">
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: var(--space-section);
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  padding: 14px 16px;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.1;
}

.c-available {
  color: var(--color-success);
}

.c-occupied {
  color: var(--text-secondary);
}

.c-locked {
  color: var(--color-warning);
}

.seat-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--text-secondary);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}

.dot-available {
  background: var(--bg-card);
  border: 1px solid var(--color-success);
}

.dot-occupied {
  background: var(--text-secondary);
  opacity: 0.55;
}

.dot-locked {
  background: var(--color-warning);
}

.seat-map-body {
  min-height: 120px;
}

.seat-map {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-x: auto;
  padding: 4px 0;
}

.seat-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.row-num {
  width: 26px;
  text-align: right;
  font-size: 13px;
  color: var(--text-secondary);
  flex: none;
}

.seat-cells {
  display: flex;
  align-items: center;
  gap: 6px;
}

.aisle {
  width: 18px;
  flex: none;
}

.seat {
  width: 34px;
  height: 30px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-primary);
  transition: transform 0.1s ease;
}

.seat:hover {
  transform: translateY(-1px);
}

.seat-available {
  border-color: var(--color-success);
  color: var(--color-success);
}

.seat-occupied {
  background: var(--text-secondary);
  border-color: var(--text-secondary);
  color: var(--bg-card);
  opacity: 0.55;
  cursor: not-allowed;
}

.seat-occupied:hover {
  transform: none;
}

.seat-locked {
  background: var(--color-warning-soft);
  border-color: var(--color-warning);
  color: var(--color-warning);
}

.seat-missing {
  visibility: hidden;
}

.cabin-tag {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: 8px;
  flex: none;
}

.empty {
  padding: 40px 0;
  text-align: center;
}

.empty-tip {
  font-size: 14px;
  color: var(--text-secondary);
}

@media (max-width: 900px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
