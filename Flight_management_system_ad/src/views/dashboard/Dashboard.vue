<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card" v-for="item in statCards" :key="item.label">
        <div class="stat-icon" :style="{ background: item.bg }">
          <el-icon :size="28" :color="item.color"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
      </div>
    </div>

    <el-row :gutter="16">
      <!-- 航班状态分布 -->
      <el-col :md="12">
        <div class="card-panel">
          <h3 class="card-title">航班状态分布</h3>
          <div ref="statusChartRef" class="chart-container"></div>
        </div>
      </el-col>

      <!-- 延误原因分析 -->
      <el-col :md="12">
        <div class="card-panel">
          <h3 class="card-title">延误原因分析</h3>
          <div ref="delayChartRef" class="chart-container"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 实时航班列表 -->
      <el-col :md="14">
        <div class="card-panel">
          <h3 class="card-title">实时航班动态</h3>
          <el-table scrollbar-always-on :data="realtimeFlights" stripe size="small" max-height="320">
            <el-table-column prop="flightNo" label="航班号" width="100" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="(FLIGHT_STATUS_MAP[row.status]?.type as any) || 'info'" size="small">
                  {{ FLIGHT_STATUS_MAP[row.status]?.label || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="router.push('/flights')">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 最近事件 -->
      <el-col :md="10">
        <div class="card-panel">
          <h3 class="card-title">最近事件</h3>
          <el-timeline class="event-timeline">
            <el-timeline-item
              v-for="(event, idx) in recentEvents"
              :key="idx"
              :timestamp="formatTime(event.time)"
              placement="top"
              :color="idx === 0 ? 'var(--color-primary)' : 'var(--text-placeholder)'"
            >
              {{ event.event }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, reactive, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getDashboardData } from '@/api/monitor'
import { adminIrregularSocket } from '@/utils/websocket'
import { FLIGHT_STATUS_MAP } from '@/utils/constants'
// ECharts 读不到 CSS 变量，色值统一从画布侧色板取，改品牌色时会跟着走
import { tokens, ramp, chartPalette } from '@/assets/styles/palette'

const router = useRouter()

const statusChartRef = ref<HTMLDivElement>()
const delayChartRef = ref<HTMLDivElement>()

const dashboard = ref<any>({})
const realtimeFlights = ref<any[]>([])
const recentEvents = ref<any[]>([])

// 图标色与底衬用 CSS 变量：这五张卡渲染在 DOM 上（不是画布），能读到令牌，
// 将来换品牌色时会自动跟随，不必回来改这个数组。
const statCards = reactive([
  { label: '计划航班', value: 0, icon: 'Promotion', color: 'var(--color-primary)', bg: 'rgba(var(--color-primary-rgb), 0.1)' },
  { label: '执行航班', value: 0, icon: 'SuccessFilled', color: 'var(--color-success)', bg: 'rgba(var(--color-success-rgb), 0.1)' },
  { label: '延误航班', value: 0, icon: 'WarningFilled', color: 'var(--color-warning)', bg: 'rgba(var(--color-warning-rgb), 0.1)' },
  { label: '取消航班', value: 0, icon: 'CircleCloseFilled', color: 'var(--color-danger)', bg: 'rgba(var(--color-danger-rgb), 0.1)' },
  // 第 5 个强调位不引入新色相（原来是紫 #7B68EE），改用品牌亮蓝：
  // 调色板限定为 primary / primary-bright + 四个功能色，超出这个集合的色相不允许进来。
  { label: '今日旅客', value: 0, icon: 'User', color: 'var(--color-primary-bright)', bg: 'rgba(var(--color-primary-bright-rgb), 0.1)' },
])

/** 今天的事件只显示 HH:mm，非今天补上日期 */
function formatTime(t: string) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = (n: number) => String(n).padStart(2, '0')
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const now = new Date()
  const sameDay = d.getFullYear() === now.getFullYear()
    && d.getMonth() === now.getMonth()
    && d.getDate() === now.getDate()
  if (sameDay) return time
  const date = `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  return d.getFullYear() === now.getFullYear() ? `${date} ${time}` : `${d.getFullYear()}-${date} ${time}`
}

let pollTimer: ReturnType<typeof setInterval> | null = null
let statusChart: echarts.ECharts | null = null
let delayChart: echarts.ECharts | null = null

async function refreshDashboard() {
  try {
    const data = await getDashboardData() as any
    dashboard.value = data
    const s = data.summary || {}
    statCards[0]!.value = s.planned || 0
    statCards[1]!.value = s.executed || 0
    statCards[2]!.value = s.delayed || 0
    statCards[3]!.value = s.cancelled || 0
    statCards[4]!.value = s.passengers || 0
    realtimeFlights.value = data.realtimeFlights || []
    recentEvents.value = data.recentEvents || []

    if (statusChart) {
      const dist = data.statusDistribution || {}
      if (Object.keys(dist).length > 0) initStatusChart(dist)
    } else {
      // 首次渲染：图表尚未初始化，直接 init
      await nextTick()
      initStatusChart(data.statusDistribution || {})
    }
    if (delayChart) {
      const analysis = data.delayAnalysis || {}
      if (Object.keys(analysis).length > 0) initDelayChart(analysis)
    } else {
      await nextTick()
      if (data.delayAnalysis && Object.keys(data.delayAnalysis).length > 0) {
        initDelayChart(data.delayAnalysis)
      }
    }
  } catch {
    // 静默失败，不影响已有数据展示
  }
}

function initStatusChart(data: Record<string, number>) {
  if (!statusChartRef.value) return
  if (!statusChart) statusChart = echarts.init(statusChartRef.value)
  const chart = statusChart
  // 状态色语义：计划=中性灰、登机/起飞=品牌蓝、飞行/到达/完成=绿、延误=警告橙、取消=异常红
  const colorMap: Record<string, string> = {
    SCHEDULED: tokens.colorInfo, BOARDING: tokens.colorPrimary, DEPARTED: tokens.sky400,
    FLYING: tokens.colorSuccess, ARRIVED: ramp.success.light3, COMPLETED: ramp.success.light5,
    DELAYED: tokens.colorWarning, CANCELLED: tokens.colorDanger,
  }
  const pieData = Object.entries(data).map(([k, v]) => ({
    name: FLIGHT_STATUS_MAP[k]?.label || k,
    value: v,
    itemStyle: { color: colorMap[k] || tokens.colorInfo },
  }))

  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 班 ({d}%)' },
    legend: { bottom: 0, textStyle: { color: tokens.textRegular, fontSize: 11 }, itemWidth: 10, itemHeight: 10 },
    series: [{
      type: 'pie', radius: ['40%', '65%'], center: ['50%', '43%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 4, borderColor: tokens.bgCard, borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: pieData,
    }],
  }, true) // notMerge=true，用新数据替换旧配置
}

function initDelayChart(data: Record<string, number>) {
  if (!delayChartRef.value) return
  if (!delayChart) delayChart = echarts.init(delayChartRef.value)
  const chart = delayChart
  const keys = Object.keys(data)
  const vals = Object.values(data)
  // 品牌色板来自 palette.ts（顺序定义在 design-system/gen-ramp.mjs），
  // 不再手写，避免像原来那样混进 EP 默认蓝 #409EFF、又在改色时漏掉这里
  const colors = chartPalette

  chart.setOption({
    tooltip: { trigger: 'axis', formatter: '{b}: {c} 次' },
    xAxis: { type: 'category', data: keys, axisLabel: { color: tokens.textRegular, rotate: keys.length > 4 ? 20 : 0 } },
    yAxis: { type: 'value', axisLabel: { color: tokens.textRegular }, minInterval: 1 },
    grid: { top: 20, bottom: 40, left: 40, right: 20 },
    series: [{
      type: 'bar',
      data: vals.map((v, i) => ({ value: v, itemStyle: { color: colors[i % colors.length] } })),
      barWidth: 36, itemStyle: { borderRadius: [4, 4, 0, 0] },
    }],
  }, true)
}

const onFlightUpdate = () => { refreshDashboard() }
const onIrregularUpdate = () => { refreshDashboard() }

const onResize = () => {
  statusChart?.resize()
  delayChart?.resize()
}

onMounted(async () => {
  await refreshDashboard()
  pollTimer = setInterval(refreshDashboard, 30_000)
  window.addEventListener('resize', onResize)
  adminIrregularSocket.on('IRREGULAR_UPDATE', onIrregularUpdate)
  adminIrregularSocket.on('FLIGHT_STATUS', onFlightUpdate)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  window.removeEventListener('resize', onResize)
  if (statusChart) { statusChart.dispose(); statusChart = null }
  if (delayChart) { delayChart.dispose(); delayChart = null }
  adminIrregularSocket.off('IRREGULAR_UPDATE', onIrregularUpdate)
  adminIrregularSocket.off('FLIGHT_STATUS', onFlightUpdate)
})
</script>

<style scoped lang="scss">

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 16px;
}

.chart-container {
  height: 280px;
}

.event-timeline {
  padding: 0 8px;
  max-height: 320px;
  overflow-y: auto;
}
</style>
