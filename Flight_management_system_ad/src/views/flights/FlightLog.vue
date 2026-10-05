<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="card-panel">
      <el-form :inline="true" :model="searchForm" class="search-bar">
        <el-form-item label="航班号">
          <el-input v-model="searchForm.keyword" placeholder="如 CA1234" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="searchForm.date" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(v, k) in FLIGHT_STATUS_MAP" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 航班列表 -->
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">航班日志</div>
        <div class="text-secondary">点击任意航班，查看它的状态变更历史</div>
      </div>

      <el-table
        scrollbar-always-on
        v-loading="loading"
        :data="flightList"
        stripe
        highlight-current-row
        class="log-flight-table"
        style="width: 100%"
        @current-change="handleCurrentChange"
      >
        <el-table-column label="航班号 / 日期" min-width="118">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ row.flightNo }}</div>
              <div class="cell-stack__sub">{{ row.date }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="航线" min-width="150">
          <template #default="{ row }">
            <div class="cell-route" :title="`${row.route?.departureName} → ${row.route?.arrivalName}`">
              {{ shortAirport(row.route?.departureName) }} ({{ row.route?.departure }})
              →
              {{ shortAirport(row.route?.arrivalName) }} ({{ row.route?.arrival }})
            </div>
          </template>
        </el-table-column>
        <el-table-column label="计划起飞" width="120">
          <template #default="{ row }">
            <span class="tnum">{{ shortTime(row.schedule?.departureTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="当前状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(FLIGHT_STATUS_MAP[row.status]?.type as any) || 'info'" size="small">
              {{ FLIGHT_STATUS_MAP[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 日志时间线 -->
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">
          状态变更日志
          <span v-if="currentFlight" class="log-subject">
            — {{ currentFlight.flightNo }} · {{ currentFlight.date }}
          </span>
        </div>
        <el-button v-if="currentFlight" size="small" :loading="logLoading" @click="fetchLogs">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <div v-if="!currentFlight" class="empty">
        <p class="empty-tip">请先在上方选择一个航班</p>
      </div>

      <div v-else v-loading="logLoading" class="log-body">
        <div v-if="!logs.length && !logLoading" class="empty">
          <p class="empty-tip">该航班暂无状态变更记录</p>
        </div>

        <el-timeline v-else>
          <el-timeline-item
            v-for="log in logs"
            :key="log.id"
            :type="(FLIGHT_STATUS_MAP[log.newStatus]?.type as any) || 'info'"
            :timestamp="fmtDateTime(log.createTime)"
            placement="top"
          >
            <div class="log-item">
              <div class="log-transition">
                <el-tag v-if="log.oldStatus" :type="(FLIGHT_STATUS_MAP[log.oldStatus]?.type as any) || 'info'" size="small" effect="plain">
                  {{ FLIGHT_STATUS_MAP[log.oldStatus]?.label || log.oldStatus }}
                </el-tag>
                <span v-else class="log-init">初始</span>
                <el-icon class="log-arrow"><Right /></el-icon>
                <el-tag :type="(FLIGHT_STATUS_MAP[log.newStatus]?.type as any) || 'info'" size="small">
                  {{ FLIGHT_STATUS_MAP[log.newStatus]?.label || log.newStatus }}
                </el-tag>
              </div>
              <div v-if="log.reason" class="log-reason">{{ log.reason }}</div>
              <div class="log-operator">
                {{ log.operatorName ? `操作人：${log.operatorName}` : '系统自动流转' }}
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFlightList, getFlightLogs } from '@/api/flights'
import { FLIGHT_STATUS_MAP } from '@/utils/constants'
import { shortAirport, shortTime, fmtDateTime } from '@/utils/format'

const loading = ref(false)
const flightList = ref<any[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const searchForm = reactive({ keyword: '', date: '', status: '' })

const currentFlight = ref<any>(null)
const logs = ref<any[]>([])
const logLoading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: pagination.page, pageSize: pagination.pageSize }
    for (const [k, v] of Object.entries(searchForm)) {
      if (v !== '' && v != null) params[k] = v
    }
    const data = await getFlightList(params) as any
    flightList.value = data.list || []
    pagination.total = data.pagination?.total || 0

    // 换页/查询后原来选中的航班可能已不在列表里，清掉避免日志区显示错位
    if (currentFlight.value && !flightList.value.some(f => f.flightId === currentFlight.value.flightId)) {
      currentFlight.value = null
      logs.value = []
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '加载航班列表失败')
    flightList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function resetSearch() {
  Object.assign(searchForm, { keyword: '', date: '', status: '' })
  handleSearch()
}

function handleCurrentChange(row: any) {
  currentFlight.value = row || null
  logs.value = []
  if (row) fetchLogs()
}

async function fetchLogs() {
  if (!currentFlight.value) return
  logLoading.value = true
  try {
    logs.value = (await getFlightLogs(currentFlight.value.flightId)) as unknown as any[] || []
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '加载日志失败')
    logs.value = []
  } finally {
    logLoading.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.log-flight-table {
  :deep(.el-table__row) {
    cursor: pointer;
  }
}

.log-subject {
  font-size: 13px;
  font-weight: 400;
  color: var(--text-secondary);
  margin-left: 2px;
}

.log-body {
  min-height: 120px;
  padding-top: 4px;
}

.log-item {
  padding-bottom: 4px;
}

.log-transition {
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-init {
  font-size: 12px;
  color: var(--text-secondary);
}

.log-arrow {
  color: var(--text-secondary);
}

.log-reason {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-primary);
}

.log-operator {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.cell-route {
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pagination-wrap {
  margin-top: var(--space-section);
}
</style>
