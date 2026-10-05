<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">机组动态</div>
        <div>
          <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 120px" @change="fetchDynamics">
            <el-option v-for="(v, k) in CREW_STATUS_MAP" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-input v-model="crewIdFilter" placeholder="机组ID" clearable style="width: 150px; margin-left: 12px">
            <template #append>
              <el-button @click="fetchDynamics"><el-icon><Search /></el-icon></el-button>
            </template>
          </el-input>
        </div>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card" v-for="item in statusCards" :key="item.label">
        <div class="stat-icon" :style="{ background: item.bg }">
          <el-icon :size="28" :color="item.color"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
      </div>
    </div>

    <div class="card-panel">
      <el-table scrollbar-always-on :data="dynamicsList" stripe v-loading="loading">
        <el-table-column prop="crewId" label="机组ID" width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="(CREW_STATUS_MAP[row.status]?.type as any) || 'info'" size="small">
              {{ CREW_STATUS_MAP[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态详情" min-width="180">
          <template #default="{ row }">
            <template v-if="row.statusRemainMinutes != null">
              <span class="status-remain">剩余 {{ formatRemain(row.statusRemainMinutes) }}</span>
              <div class="status-until">
                <el-tag v-if="row.statusReason" size="small" type="info">
                  {{ REASON_MAP[row.statusReason] || row.statusReason }}
                </el-tag>
                <span class="until-text">至 {{ formatUntil(row.statusUntil) }}</span>
              </div>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="flightNo" label="当前/最近航班" width="110" />
        <el-table-column prop="location" label="当前位置" width="110" />
        <el-table-column prop="nextDuty" label="下次任务" min-width="140" />
        <el-table-column prop="flightHours" label="本月飞行时长" width="110">
          <template #default="{ row }">{{ row.flightHours || 0 }}h</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus"
import { ref, reactive, onMounted } from 'vue'
import { getCrewDynamics } from '@/api/crew'
import { CREW_STATUS_MAP } from '@/utils/constants'

const loading = ref(false)
const dynamicsList = ref<any[]>([])
const statusFilter = ref('')
const crewIdFilter = ref('')

/** 状态来源：与后端 CrewStatusService 的常量一致 */
const REASON_MAP: Record<string, string> = {
  FLIGHT_REST: '航班到达后休息',
  OVER_LIMIT: '超月飞行时限',
  QUAL_INVALID: '资质失效',
}

/** 剩余分钟 → 人话 */
function formatRemain(minutes: number): string {
  if (minutes == null || minutes < 0) return '—'
  if (minutes >= 1440) {
    const d = Math.floor(minutes / 1440)
    const h = Math.floor((minutes % 1440) / 60)
    return h > 0 ? `${d}天${h}小时` : `${d}天`
  }
  if (minutes >= 60) {
    const h = Math.floor(minutes / 60)
    const m = minutes % 60
    return m > 0 ? `${h}小时${m}分` : `${h}小时`
  }
  return `${minutes}分钟`
}

/** ISO 时间串 → MM-DD HH:mm */
function formatUntil(until: string | null): string {
  if (!until) return '—'
  const d = new Date(until)
  if (Number.isNaN(d.getTime())) return until
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const statusCards = reactive([
  { key: 'FLYING', label: '飞行中', value: 0, icon: 'Promotion', color: 'var(--color-success)', bg: 'rgba(var(--color-success-rgb), 0.1)' },
  { key: 'STANDBY', label: '待命', value: 0, icon: 'Clock', color: 'var(--color-warning)', bg: 'rgba(var(--color-warning-rgb), 0.1)' },
  { key: 'REST', label: '休息', value: 0, icon: 'Moon', color: 'var(--color-info)', bg: 'rgba(var(--color-info-rgb), 0.1)' },
  { key: 'TRAINING', label: '培训', value: 0, icon: 'Reading', color: 'var(--color-primary)', bg: 'rgba(var(--color-primary-rgb), 0.1)' },
  { key: 'GROUNDED', label: '停飞', value: 0, icon: 'WarningFilled', color: 'var(--color-danger)', bg: 'rgba(var(--color-danger-rgb), 0.1)' },
])

async function fetchDynamics() {
  loading.value = true
  try {
    const data = await getCrewDynamics({
      status: statusFilter.value || undefined,
      crewId: crewIdFilter.value || undefined,
    }) as any
    dynamicsList.value = Array.isArray(data) ? data : data.list || []

    // 统计各状态人数
    const counts: Record<string, number> = {}
    dynamicsList.value.forEach((d: any) => {
      counts[d.status] = (counts[d.status] || 0) + 1
    })
    statusCards.forEach((card) => { card.value = counts[card.key] || 0 })
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    dynamicsList.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchDynamics)
</script>

<style scoped>
.status-remain {
  font-weight: 600;
  color: var(--text-primary);
}
.status-until {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}
.until-text {
  font-size: 12px;
  color: var(--text-secondary);
}
.muted {
  color: var(--text-placeholder);
}
</style>
