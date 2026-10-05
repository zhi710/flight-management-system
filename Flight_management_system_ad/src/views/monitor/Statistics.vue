<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">统计概览</div>
        <div>
          <el-select v-model="dimension" style="width: 120px">
            <el-option label="航班" value="FLIGHT" />
            <el-option label="旅客" value="PASSENGER" />
            <el-option label="收入" value="REVENUE" />
            <el-option label="服务" value="SERVICE" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            style="margin-left: 12px"
          />
          <el-select v-model="groupBy" style="width: 100px; margin-left: 12px">
            <el-option label="按天" value="DAY" />
            <el-option label="按周" value="WEEK" />
            <el-option label="按月" value="MONTH" />
          </el-select>
          <el-button type="primary" style="margin-left: 12px" @click="fetchStatistics"><el-icon><Search /></el-icon> 查询</el-button>
        </div>
      </div>
    </div>

    <div class="card-panel">
      <div ref="chartRef" class="chart-container"></div>
    </div>

    <div class="card-panel">
      <el-table scrollbar-always-on :data="tableData" stripe v-loading="loading">
        <el-table-column v-for="col in tableColumns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width" />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus"
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getStatistics } from '@/api/monitor'
// ECharts 读不到 CSS 变量，色值统一从画布侧色板取
import { tokens } from '@/assets/styles/palette'

const loading = ref(false)
const dimension = ref('FLIGHT')
const dateRange = ref<string[]>([])
const groupBy = ref('DAY')
const chartRef = ref<HTMLDivElement>()
const tableData = ref<any[]>([])
const tableColumns = ref<{ prop: string; label: string; width?: number }[]>([])

// 复用同一个 ECharts 实例，避免重复 init 导致图表空白
let chart: echarts.ECharts | null = null

function ensureChart() {
  if (!chartRef.value) return null
  if (!chart) chart = echarts.init(chartRef.value)
  return chart
}

function renderChart(data: any) {
  const c = ensureChart()
  if (!c) return
  const xData = data.labels || []
  const hasData = xData.length > 0 && (data.datasets || []).some((ds: any) =>
    Array.isArray(ds.data) && ds.data.some((v: any) => Number(v) > 0))

  if (!hasData) {
    c.clear()
    c.setOption({
      title: {
        text: '该时间范围内暂无统计数据显示，请扩大日期范围后重试',
        left: 'center', top: 'middle',
        textStyle: { color: tokens.textSecondary, fontSize: 14, fontWeight: 'normal' },
      },
    })
    return
  }

  const series = (data.datasets || []).map((ds: any) => ({
    name: ds.label,
    type: 'bar',
    data: ds.data,
    barWidth: 24,
    itemStyle: { borderRadius: [4, 4, 0, 0] },
  }))

  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    xAxis: { type: 'category', data: xData },
    yAxis: { type: 'value' },
    grid: { top: 30, bottom: 50, left: 50, right: 20 },
    series,
  })
}

function handleResize() {
  chart?.resize()
}

async function fetchStatistics() {
  if (!dateRange.value?.length) return
  loading.value = true
  try {
    const data = await getStatistics({
      dimension: dimension.value,
      startDate: dateRange.value[0]!,
      endDate: dateRange.value[1]!,
      groupBy: groupBy.value,
    }) as any

    await nextTick()
    renderChart(data)

    // 构建表格列和数据
    if (data.tableData) {
      tableColumns.value = data.columns || []
      tableData.value = data.tableData || []
    } else {
      tableColumns.value = []
      tableData.value = []
    }
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function pad(n: number) { return n < 10 ? '0' + n : String(n) }
function localDate(d: Date) { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }

onMounted(() => {
  window.addEventListener('resize', handleResize)
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29)
  const end = now
  dateRange.value = [localDate(start), localDate(end)]
  fetchStatistics()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.chart-container {
  height: 400px;
}
</style>
