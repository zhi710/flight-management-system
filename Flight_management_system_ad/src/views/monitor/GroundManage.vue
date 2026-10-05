<template>
  <div class="page-container">
    <!-- 概览卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-value">{{ summary.totalFlights ?? 0 }}</div>
        <div class="stat-label">今日航班</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color: var(--color-success)">{{ summary.onTime ?? 0 }}</div>
        <div class="stat-label">正常保障</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color: var(--color-danger)">{{ summary.delayed }}</div>
        <div class="stat-label">保障延误</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ (summary.punctualityRate || 0).toFixed(1) }}%</div>
        <div class="stat-label">准点率</div>
      </div>
    </div>

    <!-- 搜索 -->
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">地面保障</div>
        <div>
          <el-date-picker v-model="searchDate" type="date" placeholder="日期" clearable style="width: 150px" @change="fetchFlights" />
        </div>
      </div>
    </div>

    <!-- 航班保障列表 -->
    <div class="card-panel">
      <el-table scrollbar-always-on :data="flightList" v-loading="loading" stripe>
        <el-table-column prop="flightNo" label="航班号" width="110" />
        <el-table-column prop="route" label="航线" width="120" />
        <!-- 时间只留 HH:mm，日期降为副行：既省 70px，也避免"计划起飞"里塞一整个 ISO 串 -->
        <el-table-column label="计划起飞" width="100">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ shortTime(row.departureTime) }}</div>
              <div class="cell-stack__sub">{{ fmtMonthDay(row.departureTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="计划到达" width="100">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ shortTime(row.arrivalTime) }}</div>
              <div class="cell-stack__sub">{{ fmtMonthDay(row.arrivalTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="飞行状态" width="90">
          <template #default="{ row }">
            <el-tag :type="flightStatusTag(row.status)" size="small">{{ flightStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="保障进度" width="145">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress :percentage="row.progress" :status="row.progress === 100 ? 'success' : undefined" :stroke-width="14">
                <span class="progress-text">{{ row.completedNodes }}/{{ row.totalNodes }}</span>
              </el-progress>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="保障状态" width="90">
          <template #default="{ row }">
            <el-tag :type="groundStatusTag(row.groundStatus)" size="small">{{ groundStatusLabel(row.groundStatus) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 3 个链接按钮（保障详情/起飞/到达）实测约 128px + 单元格内边距 24px ≈ 152px -->
        <el-table-column label="操作" width="165" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="primary" link size="small" @click="openDetail(row)">保障详情</el-button>
              <el-button v-if="row.status === 'BOARDING'" type="primary" link size="small" @click="handleDepart(row)">起飞</el-button>
              <el-button v-if="row.status === 'DEPARTED' || row.status === 'FLYING'" type="primary" link size="small" @click="handleArrive(row)">到达</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 保障详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="`${detailFlight?.flightNo} 地面保障详情`" width="720px" destroy-on-close append-to-body :close-on-click-modal="false">
      <template v-if="detailNodes.length">
        <div class="node-timeline">
          <div v-for="node in detailNodes" :key="node.nodeCode" class="node-row" :class="{ completed: node.status === 'COMPLETED', failed: node.status === 'FAILED' }">
            <div class="node-icon">
              <span v-if="node.status === 'COMPLETED'" class="icon-dot completed-dot">&#10003;</span>
              <span v-else-if="node.status === 'FAILED'" class="icon-dot failed-dot">&#10007;</span>
              <span v-else class="icon-dot pending-dot">&#9679;</span>
            </div>
            <div class="node-info">
              <div class="node-name">{{ node.nodeName }}</div>
              <div class="node-time">
                <span>计划: {{ node.planTime ? fmtDateTime(node.planTime) : '-' }}</span>
                <span v-if="node.actualTime" style="margin-left: 16px; color: var(--color-success)">实际: {{ fmtDateTime(node.actualTime) }}</span>
                <span v-if="node.delayMinutes != null && node.delayMinutes > 0" style="margin-left: 16px; color: var(--color-danger)">延误: +{{ node.delayMinutes }}min</span>
              </div>
            </div>
            <div class="node-action">
              <el-tag v-if="node.status === 'COMPLETED'" type="success" size="small">已完成</el-tag>
              <el-tag v-else-if="node.status === 'FAILED'" type="danger" size="small">已取消</el-tag>
              <el-button v-else type="primary" size="small" :loading="nodeCompleting.has(node.nodeCode)" @click="handleCompleteNode(node)">完成</el-button>
            </div>
          </div>
        </div>
      </template>
      <div v-else class="empty-state">暂无保障节点数据</div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getGroundTodaySummary, getFlightGroundSummary, getGroundNodes, departFlight, arriveFlight, completeNode } from '@/api/ground'
import { fmtDateTime, fmtMonthDay, shortTime } from '@/utils/format'
import { FLIGHT_STATUS_MAP } from '@/utils/constants'

const loading = ref(false)
const flightList = ref<any[]>([])
const summary = ref<any>({})
const searchDate = ref('')
const nodeCompleting = ref(new Set<string>())

// 保障详情弹窗
const detailVisible = ref(false)
const detailFlight = ref<any>(null)
const detailNodes = ref<any[]>([])

async function fetchSummary() {
  try {
    summary.value = await getGroundTodaySummary() as any || {}
  } catch {}
}

async function fetchFlights() {
  loading.value = true
  try {
    flightList.value = (await getFlightGroundSummary(searchDate.value || undefined)) as unknown as any[] || []
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '加载航班列表失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(row: any) {
  detailFlight.value = row
  detailNodes.value = []
  detailVisible.value = true
  try {
    detailNodes.value = (await getGroundNodes(row.flightId)) as unknown as any[] || []
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '加载保障详情失败')
  }
}

async function handleDepart(row: any) {
  try {
    await ElMessageBox.confirm(`确定标记航班 ${row.flightNo} 起飞？系统将自动完成离港节点。`, '提示', { type: 'info' })
  } catch { return }
  try {
    await departFlight(row.flightId)
    ElMessage.success('航班已起飞')
    fetchFlights()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '操作失败')
  }
}

async function handleArrive(row: any) {
  try {
    await ElMessageBox.confirm(`确定标记航班 ${row.flightNo} 到达？系统将自动完成到港节点。`, '提示', { type: 'info' })
  } catch { return }
  try {
    await arriveFlight(row.flightId)
    ElMessage.success('航班已到达')
    fetchFlights()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '操作失败')
  }
}

async function handleCompleteNode(node: any) {
  nodeCompleting.value.add(node.nodeCode)
  try {
    await completeNode(detailFlight.value.flightId, node.nodeCode)
    ElMessage.success(`${node.nodeName} 已完成`)
    // 刷新节点详情
    detailNodes.value = (await getGroundNodes(detailFlight.value.flightId)) as unknown as any[] || []
    fetchFlights()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '操作失败')
  } finally {
    nodeCompleting.value.delete(node.nodeCode)
  }
}

// 直接用共享的 FLIGHT_STATUS_MAP：这里原先自己维护了一份副本，漏了 COMPLETED，
// 于是"飞行状态"列把英文 "COMPLETED" 原样露给了运营。副本漂移是这类 bug 的常见来源。
// 共享表里 type 为 '' 表示"用 EP 默认色"，对应 el-tag 的 undefined。
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

function flightStatusTag(s: string): TagType | undefined {
  const t = FLIGHT_STATUS_MAP[s]?.type
  if (t === undefined) return 'info' // 未知状态兜底
  return t === '' ? undefined : (t as TagType)
}
function flightStatusLabel(s: string) {
  return FLIGHT_STATUS_MAP[s]?.label ?? s
}
function groundStatusTag(s: string) {
  return ({ PENDING: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', NOT_CONFIGURED: 'info' } as any)[s] || 'info'
}
function groundStatusLabel(s: string) {
  return ({ PENDING: '待处理', IN_PROGRESS: '进行中', COMPLETED: '已完成', NOT_CONFIGURED: '未配置' } as any)[s] || s
}

onMounted(() => {
  fetchSummary()
  fetchFlights()
})
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; background: var(--bg-card); border-radius: var(--radius-card); padding: var(--space-5) var(--space-card); box-shadow: var(--shadow-card); }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; color: var(--text-primary); }
.stat-label { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }
.progress-cell { display: flex; align-items: center; gap: 8px; }
.progress-text { font-size: 12px; color: var(--text-secondary); }
.empty-state { text-align: center; padding: 40px 0; color: var(--text-secondary); font-size: 14px; }

/* 节点时间线 */
.node-timeline { max-height: 520px; overflow-y: auto; padding: 8px 0; }
.node-row { display: flex; align-items: center; padding: 10px 16px; border-left: 2px solid var(--border-color-light); margin-left: 8px; position: relative; transition: background .15s; }
.node-row:last-child { border-left-color: transparent; }
.node-row:hover { background: var(--bg-page); }
.node-row.completed { border-left-color: var(--color-success); }
.node-row.failed { border-left-color: var(--color-danger); }
.node-icon { width: 28px; flex-shrink: 0; display: flex; justify-content: center; }
.icon-dot { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; font-size: 12px; font-weight: 700; }
.completed-dot { background: var(--color-success); color: var(--text-inverse); }
.failed-dot { background: var(--color-danger); color: var(--text-inverse); }
.pending-dot { color: var(--text-placeholder); font-size: 16px; }
.node-info { flex: 1; min-width: 0; }
.node-name { font-weight: 500; font-size: 14px; color: var(--text-primary); }
.node-time { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.node-action { flex-shrink: 0; margin-left: 12px; }
</style>
