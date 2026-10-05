<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">运营报表</div>
      </div>

      <el-form :inline="true" class="search-bar">
        <el-form-item label="报表类型">
          <el-select v-model="reportType" style="width: 140px">
            <el-option label="日报" value="DAILY" />
            <el-option label="延误报表" value="DELAY" />
            <el-option label="旅客报表" value="PASSENGER" />
            <el-option label="机组报表" value="CREW" />
            <el-option label="机场报表" value="AIRPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchReport"><el-icon><Search /></el-icon> 生成报表</el-button>
          <el-button @click="exportCSV"><el-icon><Download /></el-icon> 导出Excel</el-button>
          <el-button @click="exportPDF"><el-icon><Document /></el-icon> 导出PDF</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel" v-loading="loading">
      <div v-if="reportData">
        <el-table scrollbar-always-on :data="reportData" stripe max-height="560">
          <el-table-column v-for="col in columns" :key="col" :prop="col" :label="col" min-width="120" />
        </el-table>
      </div>
      <el-empty v-else description="请选择报表类型和日期范围后生成报表" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOperationReport } from '@/api/reports'

const loading = ref(false)
const reportType = ref('DAILY')
const dateRange = ref<string[]>([])
const reportData = ref<any[] | null>(null)
const columns = ref<string[]>([])

async function fetchReport() {
  if (!dateRange.value?.length) {
    ElMessage.warning('请选择日期范围')
    return
  }
  loading.value = true
  try {
    const data = await getOperationReport({
      type: reportType.value,
      startDate: dateRange.value[0]!,
      endDate: dateRange.value[1]!,
    }) as any
    reportData.value = (data as any)?.data || []
    if (reportData.value && reportData.value.length > 0) {
      columns.value = Object.keys(reportData.value[0])
    }
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    reportData.value = null
  } finally {
    loading.value = false
  }
}

function exportCSV() {
  if (!reportData.value || reportData.value.length === 0) {
    ElMessage.warning('请先生成报表数据')
    return
  }
  const cols = columns.value
  // BOM 保证 Excel 正确识别 UTF-8 中文
  let csv = '﻿' + cols.join(',') + '\n'
  for (const row of reportData.value) {
    csv += cols.map(c => {
      const v = row[c]
      if (v == null) return ''
      const s = String(v)
      // 含逗号、引号或换行时用引号包裹
      return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
    }).join(',') + '\n'
  }
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${reportType.value}_${dateRange.value[0]}_${dateRange.value[1]}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('Excel 导出成功')
}

function exportPDF() {
  if (!reportData.value || reportData.value.length === 0) {
    ElMessage.warning('请先生成报表数据')
    return
  }
  const cols = columns.value
  const rows = reportData.value
  const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>运营报表</title>
<style>body{font-family:Microsoft YaHei,sans-serif;padding:20px}
h2{text-align:center;margin-bottom:8px}
.sub{text-align:center;color:#666;margin-bottom:20px;font-size:13px}
table{width:100%;border-collapse:collapse}
th,td{border:1px solid #ddd;padding:8px 12px;text-align:left;font-size:13px}
th{background:#f5f7fa;font-weight:600}</style></head><body>
<h2>运营报表</h2>
<div class="sub">类型: ${reportType.value}　日期: ${dateRange.value[0]} ~ ${dateRange.value[1]}</div>
<table><thead><tr>${cols.map(c => `<th>${c}</th>`).join('')}</tr></thead>
<tbody>${rows.map(r => '<tr>' + cols.map(c => `<td>${r[c] ?? ''}</td>`).join('') + '</tr>').join('')}</tbody></table>
<script>window.onload=function(){window.print();setTimeout(function(){window.close()},500)}</` + `script></body></html>`
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const w = window.open(url, '_blank')
  if (!w) {
    ElMessage.warning('弹窗被浏览器拦截，请允许弹窗后重试')
  }
  URL.revokeObjectURL(url)
}
</script>
