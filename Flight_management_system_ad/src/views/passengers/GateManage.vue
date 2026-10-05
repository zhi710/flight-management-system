<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">登机口管理</div>
        <div>
          <el-select v-model="terminal" placeholder="航站楼" clearable style="width: 120px">
            <el-option label="T1" value="T1" />
            <el-option label="T2" value="T2" />
            <el-option label="T3" value="T3" />
          </el-select>
          <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" :clearable="false" style="width: 150px; margin-left: 12px" @change="fetchGates" />
          <el-button type="primary" style="margin-left: 12px" @click="fetchGates"><el-icon><Search /></el-icon> 查询</el-button>
        </div>
      </div>
    </div>

    <div class="card-panel">
      <el-table scrollbar-always-on :data="gateList" stripe v-loading="loading">
        <el-table-column prop="gateCode" label="登机口" width="100" />
        <el-table-column prop="terminal" label="航站楼" width="80" />
        <el-table-column label="当日排班" min-width="340">
          <template #default="{ row }">
            <div v-if="row.assignments && row.assignments.length">
              <div v-for="a in row.assignments" :key="a.id" class="assign-item">
                <span class="assign-flight">{{ a.flightNo }}</span>
                <span class="assign-time">{{ fmtTime(a.startTime) }} ~ {{ fmtTime(a.endTime) }}</span>
                <el-button type="danger" link size="small" @click="handleRelease(row, a)">释放</el-button>
              </div>
            </div>
            <span v-else class="text-sub">空闲</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showAssignDialog(row)">分配</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分配登机口对话框 -->
    <el-dialog v-model="assignDialogVisible" title="分配登机口" width="560px" destroy-on-close>
      <el-form :model="assignForm" label-width="88px">
        <el-form-item label="登机口">
          <el-input :model-value="assignForm.gateCode" disabled />
        </el-form-item>
        <el-form-item label="航班号">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="assignForm.flightNo" placeholder="输入航班号" style="flex: 1" @keyup.enter="searchFlights" @blur="searchFlights" />
            <el-button @click="searchFlights">查询班次</el-button>
          </div>
        </el-form-item>
        <el-form-item label="选择班次">
          <el-select v-model="assignForm.flightId" placeholder="选择具体航班班次" style="width: 100%" :loading="flightLoading">
            <el-option v-for="f in flightOptions" :key="f.flightId" :label="optionLabel(f)" :value="f.flightId" />
          </el-select>
          <div v-if="assignForm.flightNo && !flightLoading && flightOptions.length === 0" style="font-size:12px;color:var(--text-secondary);margin-top:4px">未找到匹配班次，请检查航班号</div>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="assignForm.startTime" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:00+08:00" placeholder="默认按计划时刻" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="assignForm.endTime" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:00+08:00" placeholder="默认按计划时刻" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getGateList, assignGate, releaseGate, lookupFlight } from '@/api/passengers'

const loading = ref(false)
const terminal = ref('')
const date = ref(new Date().toISOString().slice(0, 10))
const gateList = ref<any[]>([])

async function fetchGates() {
  loading.value = true
  try {
    const data = await getGateList({ terminal: terminal.value || undefined, date: date.value }) as any
    gateList.value = Array.isArray(data) ? data : []
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
    gateList.value = []
  } finally {
    loading.value = false
  }
}

function fmtTime(dt?: string) {
  if (!dt) return '--:--'
  const d = new Date(dt)
  if (isNaN(d.getTime())) return dt
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// ---- 分配 ----
const assignDialogVisible = ref(false)
const flightLoading = ref(false)
const flightOptions = ref<any[]>([])
const assignForm = reactive({ gateCode: '', flightNo: '', flightId: '', startTime: '', endTime: '' })

async function searchFlights() {
  const val = assignForm.flightNo?.trim()
  assignForm.flightId = ''
  if (!val || val.length < 3) {
    flightOptions.value = []
    return
  }
  flightLoading.value = true
  try {
    // 不传日期，返回该航班号今天及之后的所有班次，由操作员按日期/时刻选择
    const res = await lookupFlight(val) as any
    flightOptions.value = Array.isArray(res) ? res : []
  } catch {
    flightOptions.value = []
  } finally {
    flightLoading.value = false
  }
}

function optionLabel(f: any) {
  const depTxt = f.departureTime ? fmtTime(f.departureTime) : ''
  return `${f.flightNo}  ${f.date || ''}  ${depTxt}  ${f.route || ''}`.trim()
}

function showAssignDialog(row: any) {
  flightOptions.value = []
  assignForm.gateCode = row.gateCode
  assignForm.flightNo = ''
  assignForm.flightId = ''
  assignForm.startTime = ''
  assignForm.endTime = ''
  assignDialogVisible.value = true
}

async function submitAssign() {
  try {
    if (!assignForm.flightId) throw new Error('请选择航班班次')
    await assignGate({
      gateCode: assignForm.gateCode,
      flightId: assignForm.flightId,
      startTime: assignForm.startTime,
      endTime: assignForm.endTime,
    })
    ElMessage.success('分配成功')
    assignDialogVisible.value = false
    fetchGates()
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

async function handleRelease(row: any, assignment: any) {
  try {
    await releaseGate({ assignmentId: assignment.id })
    ElMessage.success(`已释放 ${row.gateCode} 的 ${assignment.flightNo}`)
    fetchGates()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '释放失败')
  }
}

onMounted(fetchGates)
</script>

<style scoped>
.assign-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
}

.assign-flight {
  font-weight: 600;
  color: var(--text-primary);
}

.assign-time {
  color: var(--text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
</style>
