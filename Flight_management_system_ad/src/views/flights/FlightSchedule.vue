<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">航班时刻表</div>
        <el-radio-group v-model="viewMode" @change="fetchSchedule">
          <el-radio-button value="CALENDAR">日历</el-radio-button>
          <el-radio-button value="TIMELINE">时间线</el-radio-button>
          <el-radio-button value="ROUTE">航线</el-radio-button>
        </el-radio-group>
      </div>

      <el-form :inline="true" class="search-bar" style="margin-top: 16px">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="航线">
          <el-input v-model="routeFilter" placeholder="如 PEK-SHA" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchSchedule"><el-icon><Search /></el-icon> 查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel">
      <!-- 日历视图 -->
      <div v-if="viewMode === 'CALENDAR'" class="schedule-calendar">
        <el-table scrollbar-always-on :data="scheduleData" stripe v-loading="loading" max-height="560">
          <el-table-column prop="flightNo" label="航班号" width="100" fixed />
          <el-table-column label="航线" width="140">
            <template #default="{ row }">{{ row.route?.departure }} → {{ row.route?.arrival }}</template>
          </el-table-column>
          <el-table-column label="出发" width="80">
            <template #default="{ row }">{{ row.schedule?.departureTime }}</template>
          </el-table-column>
          <el-table-column label="到达" width="80">
            <template #default="{ row }">{{ row.schedule?.arrivalTime }}</template>
          </el-table-column>
          <el-table-column prop="date" label="日期" width="110" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="(FLIGHT_STATUS_MAP[row.status]?.type as any) || (row.isPast ? 'success' : 'info')" size="small">
                {{ FLIGHT_STATUS_MAP[row.status]?.label || (row.isPast ? '已起飞' : row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 时间线视图 -->
      <div v-else-if="viewMode === 'TIMELINE'" class="schedule-timeline">
        <div v-for="group in timelineGroups" :key="group.hour" class="timeline-group">
          <div class="timeline-hour">{{ group.hour }}:00</div>
          <div class="timeline-items">
            <el-tag
              v-for="f in group.flights"
              :key="f.flightNo"
              :type="(f.isPast ? 'danger' : (FLIGHT_STATUS_MAP[f.status]?.type || 'info')) as any"
              class="timeline-tag"
              size="large"
            >
              {{ f.flightNo }} {{ f.route?.departure }}→{{ f.route?.arrival }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- 航线视图 -->
      <div v-else class="schedule-route">
        <el-empty description="航线视图开发中" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus"
import { ref, computed, onMounted } from 'vue'
import { getFlightSchedule } from '@/api/flights'
import { FLIGHT_STATUS_MAP } from '@/utils/constants'

const loading = ref(false)
const viewMode = ref('CALENDAR')
const dateRange = ref<string[]>([])
const routeFilter = ref('')
const scheduleData = ref<any[]>([])

const timelineGroups = computed(() => {
  const map: Record<string, any[]> = {}
  scheduleData.value.forEach(f => {
    const hour = f.schedule?.departureTime?.substring(0, 2) || '00'
    if (!map[hour]) map[hour] = []
    map[hour].push(f)
  })
  return Object.keys(map).sort().map(h => ({ hour: h, flights: map[h] }))
})

async function fetchSchedule() {
  if (!dateRange.value?.length) return
  loading.value = true
  try {
    const data = await getFlightSchedule({
      view: viewMode.value,
      startDate: dateRange.value[0]!,
      endDate: dateRange.value[1]!,
      route: routeFilter.value || undefined,
    }) as any
    scheduleData.value = data.list || []
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    scheduleData.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 默认近7天
  const now = new Date()
  const start = now.toISOString().substring(0, 10)
  const end = new Date(now.getTime() + 6 * 86400000).toISOString().substring(0, 10)
  dateRange.value = [start, end]
  fetchSchedule()
})
</script>

<style scoped lang="scss">
.schedule-timeline {
  max-height: 560px;
  overflow-y: auto;
}

.timeline-group {
  display: flex;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--bg-page);
}

.timeline-hour {
  width: 60px;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-primary);
  flex-shrink: 0;
  padding-top: 4px;
}

.timeline-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.timeline-tag {
  cursor: pointer;
}
</style>
