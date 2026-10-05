<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">收入报表</div>
      </div>

      <el-form :inline="true" class="search-bar">
        <el-form-item label="报表类型">
          <el-select v-model="reportType" style="width: 140px">
            <el-option label="日报" value="DAILY" />
            <el-option label="按航线" value="ROUTE" />
            <el-option label="按舱位" value="CABIN" />
            <el-option label="辅营收入" value="AUXILIARY" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchReport"><el-icon><Search /></el-icon> 生成报表</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel">
      <div ref="chartRef" class="chart-container"></div>
    </div>

    <div class="card-panel" v-loading="loading">
      <div v-if="reportData">
        <el-table scrollbar-always-on :data="reportData" stripe max-height="400">
          <el-table-column v-for="col in columns" :key="col" :prop="col" :label="col" min-width="120" />
        </el-table>
      </div>
      <el-empty v-else description="请选择报表类型和日期范围后生成报表" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getRevenueReport } from '@/api/reports'
// ECharts 读不到 CSS 变量，色值统一从画布侧色板取
import { tokens } from '@/assets/styles/palette'

const loading = ref(false)
const reportType = ref('DAILY')
const dateRange = ref<string[]>([])
const reportData = ref<any[] | null>(null)
const columns = ref<string[]>([])
const chartRef = ref<HTMLDivElement>()

function renderChart(data: any[]) {
  if (!chartRef.value || !data.length) return
  const chart = echarts.init(chartRef.value)
  const xData = data.map((d: any) => d.date || d.route || d.cabin || d.type || '')
  const yData = data.map((d: any) => d.revenue || d.total || d.amount || 0)

  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: xData, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', name: '收入 (¥)' },
    grid: { top: 40, bottom: 60, left: 60, right: 20 },
    series: [{
      type: 'bar',
      data: yData,
      barWidth: 24,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: tokens.colorPrimary },
          { offset: 1, color: tokens.sky400 },
        ]),
        borderRadius: [4, 4, 0, 0],
      },
    }],
  })
  window.addEventListener('resize', () => chart.resize())
}

async function fetchReport() {
  if (!dateRange.value?.length) {
    ElMessage.warning('请选择日期范围')
    return
  }
  loading.value = true
  try {
    const data = await getRevenueReport({
      type: reportType.value,
      startDate: dateRange.value[0]!,
      endDate: dateRange.value[1]!,
    }) as any
    reportData.value = (data as any)?.data || []
    if (reportData.value && reportData.value.length > 0) {
      columns.value = Object.keys(reportData.value[0])
    }
    await nextTick()
    if (reportData.value) renderChart(reportData.value)
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    reportData.value = null
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.chart-container {
  height: 360px;
}
</style>
