<template>
  <div class="page-container">
    <!-- 看板卡片 -->
    <div class="stat-row" v-if="dashboard.totalWarnings != null">
      <div class="stat-card warning-card">
        <div class="stat-value" style="color: var(--color-danger)">{{ dashboard.flightTimeWarnings || 0 }}</div>
        <div class="stat-label">飞行超时预警</div>
      </div>
      <div class="stat-card warning-card">
        <div class="stat-value" style="color: var(--color-warning)">{{ dashboard.expiringQualWarnings || 0 }}</div>
        <div class="stat-label">资质临期预警(30天内)</div>
      </div>
      <div class="stat-card warning-card">
        <div class="stat-value" style="color: var(--color-danger)">{{ dashboard.expiredQualWarnings || 0 }}</div>
        <div class="stat-label">资质已过期预警</div>
      </div>
      <div class="stat-card warning-card">
        <div class="stat-value" style="color: var(--color-danger)">{{ dashboard.groundedCrew || 0 }}</div>
        <div class="stat-label">停飞机组</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ dashboard.totalWarnings || 0 }}</div>
        <div class="stat-label">总预警数</div>
      </div>
    </div>

    <el-alert
      v-if="dashboard.groundedCrew"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom:16px"
      title="存在停飞机组：其名下资质已全部失效，系统已自动将其移出排班候选，请尽快安排续证/复训"
    />

    <!-- 预警列表 -->
    <div class="card-panel">
      <div class="section-title">合规预警列表</div>
      <el-table scrollbar-always-on :data="dashboard.warnings || []" stripe v-loading="loading">
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="crewName" label="姓名" width="110" />
        <el-table-column v-if="hasQualColumn" prop="qualName" label="资质名称" min-width="140">
          <template #default="{ row }">{{ row.qualName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="totalHours" label="本月已飞" width="110">
          <template #default="{ row }">{{ row.totalHours ? row.totalHours + 'h' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="expireDate" label="到期日" width="120">
          <template #default="{ row }">{{ row.expireDate || '—' }}</template>
        </el-table-column>
        <el-table-column prop="daysRemaining" label="剩余天数" width="100">
          <template #default="{ row }">
            <span v-if="row.daysRemaining != null" :style="{ color: row.daysRemaining < 0 || row.daysRemaining <= 7 ? 'var(--color-danger)' : 'var(--color-warning)' }">
              {{ row.daysRemaining < 0 ? '已过期' + Math.abs(row.daysRemaining) + '天' : row.daysRemaining + '天' }}
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCrewComplianceDashboard } from '@/api/crewCompliance'

const loading = ref(false)
const dashboard = ref<any>({})

const hasQualColumn = computed(() =>
  (dashboard.value.warnings || []).some((w: any) => w.qualName))

function typeTag(t: string) {
  return ({ FLIGHT_TIME_EXCEEDED: 'danger', QUALIFICATION_EXPIRING: 'warning', QUALIFICATION_EXPIRED: 'danger' } as any)[t] || 'info'
}
function typeLabel(t: string) {
  return ({ FLIGHT_TIME_EXCEEDED: '飞行超时', QUALIFICATION_EXPIRING: '资质临期', QUALIFICATION_EXPIRED: '资质已过期' } as any)[t] || t
}

onMounted(async () => {
  loading.value = true
  try {
    dashboard.value = await getCrewComplianceDashboard() as any || {}
  } catch (err: any) {
    ElMessage.error(err?.message || '机组合规数据加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.stat-card { flex: 1; min-width: 150px; background: var(--bg-card); border-radius: var(--radius-card); padding: var(--space-5) var(--space-card); box-shadow: var(--shadow-card); }
.warning-card { border-top: 3px solid transparent; }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; color: var(--text-primary); }
.stat-label { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }
.section-title { font-size: 16px; font-weight: 600; margin-bottom: 16px; color: var(--text-primary); }
</style>
