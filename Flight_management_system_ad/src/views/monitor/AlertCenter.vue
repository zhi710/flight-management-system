<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">告警中心</div>
        <div>
          <el-select v-model="searchForm.level" placeholder="告警级别" clearable style="width: 120px" @change="fetchAlerts">
            <el-option v-for="(v, k) in ALERT_LEVEL_MAP" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-select v-model="searchForm.status" placeholder="状态" clearable style="width: 120px; margin-left: 12px" @change="fetchAlerts">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已处理" value="RESOLVED" />
          </el-select>
        </div>
      </div>
    </div>

    <!-- 统计 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(var(--color-danger-rgb), 0.1)">
          <el-icon :size="28" color="var(--color-danger)"><WarningFilled /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ urgentCount }}</div>
          <div class="stat-label">紧急告警</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(var(--color-warning-rgb), 0.1)">
          <el-icon :size="28" color="var(--color-warning)"><Bell /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ pendingCountState }}</div>
          <div class="stat-label">待处理</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(var(--color-success-rgb), 0.1)">
          <el-icon :size="28" color="var(--color-success)"><SuccessFilled /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ resolvedCount }}</div>
          <div class="stat-label">已处理</div>
        </div>
      </div>
    </div>

    <div class="card-panel">
      <el-table scrollbar-always-on :data="alertList" stripe v-loading="loading">
        <el-table-column prop="alertId" label="告警ID" width="120" show-overflow-tooltip />
        <el-table-column prop="level" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="(ALERT_LEVEL_MAP[row.level]?.type as any) || 'info'" size="small">
              {{ ALERT_LEVEL_MAP[row.level]?.label || row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="90" />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="170" show-overflow-tooltip />
        <el-table-column label="时间" width="140">
          <template #default="{ row }"><span class="tnum">{{ fmtDateTime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'" size="small">
              {{ row.status === 'PENDING' ? '待处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING'" type="primary" link size="small" @click="handleResolve(row)">处理</el-button>
            <span v-else class="text-sub">已处理</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchAlerts"
          @current-change="fetchAlerts"
        />
      </div>
    </div>

    <!-- 告警详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="告警详情" width="560px">
      <el-descriptions v-if="detailData" :column="1" border>
        <el-descriptions-item label="告警ID">{{ detailData.alertId }}</el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="(ALERT_LEVEL_MAP[detailData.level]?.type as any) || 'info'" size="small">
            {{ ALERT_LEVEL_MAP[detailData.level]?.label || detailData.level }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="类型">{{ detailData.type }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ detailData.title }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detailData.description }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailData.status === 'PENDING' ? 'warning' : 'success'" size="small">
            {{ detailData.status === 'PENDING' ? '待处理' : '已处理' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="时间">{{ detailData.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAlertList, getAlertStats, resolveAlert } from '@/api/monitor'
import { useNotificationStore } from '@/stores/notification'
import { ALERT_LEVEL_MAP } from '@/utils/constants'
import { fmtDateTime } from '@/utils/format'
import { adminIrregularSocket } from '@/utils/websocket'

const loading = ref(false)
const alertList = ref<any[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const searchForm = reactive({ level: '', status: '' })

const notificationStore = useNotificationStore()

const urgentCount = ref(0)
const pendingCountState = ref(0)
const resolvedCount = ref(0)

const detailDialogVisible = ref(false)
const detailData = ref<any>(null)

async function fetchStats() {
  try {
    const data = await getAlertStats() as any
    urgentCount.value = data.urgentCount || 0
    pendingCountState.value = data.pendingCount || 0
    resolvedCount.value = data.resolvedCount || 0
    notificationStore.updatePendingCount(data.pendingCount || 0)
  } catch (err: any) {
    ElMessage.error(err?.message || '加载统计数据失败')
  }
}

async function fetchAlerts() {
  loading.value = true
  try {
    const data = await getAlertList({ ...searchForm, page: pagination.page, pageSize: pagination.pageSize }) as any
    alertList.value = data.list || data || []
    pagination.total = data.pagination?.total || alertList.value.length
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
    alertList.value = []
  } finally {
    loading.value = false
  }
}

function viewDetail(row: any) {
  detailData.value = row
  detailDialogVisible.value = true
}

async function handleResolve(row: any) {
  try {
    await ElMessageBox.confirm(`确定处理告警「${row.title}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await resolveAlert(row.alertId)
    ElMessage.success('已处理')
    fetchAlerts()
    fetchStats()
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

let wsHandler: ((data: any) => void) | null = null

onMounted(() => {
  fetchAlerts()
  fetchStats()

  // WebSocket 实时刷新
  wsHandler = () => {
    fetchAlerts()
    fetchStats()
  }
  adminIrregularSocket.on('IRREGULAR_UPDATE', wsHandler)
  adminIrregularSocket.on('ADMIN_NOTIFICATION', wsHandler)
})

onUnmounted(() => {
  if (wsHandler) {
    adminIrregularSocket.off('IRREGULAR_UPDATE', wsHandler)
    adminIrregularSocket.off('ADMIN_NOTIFICATION', wsHandler)
  }
})
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.text-secondary {
  color: var(--text-secondary);
  font-size: 12px;
}
.stat-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  flex: 1;
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  padding: var(--space-5) var(--space-card);
  box-shadow: var(--shadow-card);
}
.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  flex-shrink: 0;
}
.stat-content {
  display: flex;
  flex-direction: column;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--text-primary);
}
.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}
</style>
