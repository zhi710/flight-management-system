<template>
  <div class="page-container">
    <!-- 搜索航班 -->
    <div class="card-panel">
      <el-form :inline="true" class="search-bar">
        <el-form-item label="航班号">
          <el-input v-model="searchForm.flightNo" placeholder="如 CA1234" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="航班日期">
          <el-date-picker v-model="searchForm.date" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <template v-if="checkinData">
      <!-- 航班信息 -->
      <div class="card-panel">
        <el-descriptions title="航班信息" :column="4" border>
          <el-descriptions-item label="航班号">{{ checkinData.flightNo }}</el-descriptions-item>
          <el-descriptions-item label="出发">{{ checkinData.departure?.airportName }}</el-descriptions-item>
          <el-descriptions-item label="到达">{{ checkinData.arrival?.airportName }}</el-descriptions-item>
          <el-descriptions-item label="值机状态">
            <el-tag :type="checkinData.isPast ? 'danger' : (checkinData.checkinOpen ? 'success' : 'info')">
              {{ checkinData.isPast ? '已起飞' : (checkinData.checkinOpen ? '已开放' : '未开放') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="checkinData.isPast" label="提示">
            <span style="color: var(--color-danger)">航班已起飞，无法进行值机操作</span>
          </el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 16px; display: flex; gap: 12px">
          <el-button type="primary" :disabled="checkinData.isPast" @click="handleOpenCheckin">开放值机</el-button>
          <el-button type="warning" :disabled="checkinData.isPast" @click="handleCloseCheckin">关闭值机</el-button>
          <el-button :disabled="checkinData.isPast" @click="handleAutoAssign">自动分配座位</el-button>
          <el-button @click="handleExport">导出名单</el-button>
        </div>
      </div>

      <!-- 旅客值机列表 -->
      <div class="card-panel">
        <h3 class="card-title">旅客值机状态</h3>
        <el-table scrollbar-always-on :data="checkinData.passengers || []" stripe>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column prop="checkedIn" label="值机状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.checkedIn ? 'success' : 'info'" size="small">
                {{ row.checkedIn ? '已值机' : '未值机' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="seat" label="座位" width="80" />
          <el-table-column label="操作" width="180">
            <template #default="{ row, $index }">
              <el-button v-if="!row.checkedIn" :disabled="checkinData.isPast" type="primary" link size="small" @click="showManualCheckin($index)">手动值机</el-button>
              <el-button v-if="row.checkedIn" :disabled="checkinData.isPast" type="warning" link size="small" @click="handleCancelCheckin($index)">取消值机</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="请输入航班ID查询值机信息" />

    <!-- 手动值机对话框 -->
    <el-dialog v-model="manualDialogVisible" title="手动值机" width="420px">
      <el-form :model="manualForm" label-width="88px">
        <el-form-item label="排号">
          <el-input-number v-model="manualForm.seatRow" :min="1" :max="60" />
        </el-form-item>
        <el-form-item label="列号">
          <el-select v-model="manualForm.seatColumn" style="width: 100px">
            <el-option v-for="c in ['A','B','C','D','E','F']" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitManualCheckin">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCheckinInfo, openCheckin, closeCheckin, manualCheckin, cancelCheckin, autoAssignSeats, exportCheckinList } from '@/api/passengers'
import { getFlightList } from '@/api/flights'

const route = useRoute()
const loading = ref(false)
const flightIdInput = ref((route.params.flightId as string) || '')
const checkinData = ref<any>(null)
const selectedFlight = ref<any>(null)

const searchForm = reactive({ flightNo: '', date: '' })

const manualDialogVisible = ref(false)
const manualPassengerIndex = ref(0)
const manualForm = reactive({ seatRow: 1, seatColumn: 'A' })

async function handleSearch() {
  if (!searchForm.flightNo || !searchForm.date) {
    ElMessage.warning('请输入航班号和日期')
    return
  }
  loading.value = true
  try {
    const res = await getFlightList({ keyword: searchForm.flightNo, date: searchForm.date, page: 1, pageSize: 1 }) as any
    if (res.list?.length) {
      const flight = res.list[0]
      selectedFlight.value = flight
      flightIdInput.value = flight.flightId
      checkinData.value = await getCheckinInfo(flight.flightId)
    } else {
      ElMessage.warning('未找到该航班')
      checkinData.value = null
      selectedFlight.value = null
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '查询失败')
    checkinData.value = null
    selectedFlight.value = null
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  Object.assign(searchForm, { flightNo: '', date: '' })
  checkinData.value = null
  selectedFlight.value = null
}

async function fetchCheckinInfo() {
  if (!flightIdInput.value) return
  loading.value = true
  try {
    checkinData.value = await getCheckinInfo(flightIdInput.value)
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    checkinData.value = null
  } finally {
    loading.value = false
  }
}

async function handleOpenCheckin() {
  try {
    await openCheckin(flightIdInput.value)
    ElMessage.success('值机已开放')
    fetchCheckinInfo()
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

async function handleCloseCheckin() {
  try {
    await closeCheckin(flightIdInput.value)
    ElMessage.success('值机已关闭')
    fetchCheckinInfo()
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

function showManualCheckin(index: number) {
  manualPassengerIndex.value = index
  manualForm.seatRow = 1
  manualForm.seatColumn = 'A'
  manualDialogVisible.value = true
}

async function submitManualCheckin() {
  try {
    await manualCheckin(flightIdInput.value, {
      passengerIndex: manualPassengerIndex.value,
      seatRow: manualForm.seatRow,
      seatColumn: manualForm.seatColumn,
    })
    ElMessage.success('值机成功')
    manualDialogVisible.value = false
    fetchCheckinInfo()
  } catch (err: any) {
    ElMessage.error(err?.message || '手动值机失败')
  }
}

async function handleCancelCheckin(index: number) {
  try {
    await cancelCheckin(flightIdInput.value, { passengerIndex: index })
    ElMessage.success('已取消值机')
    fetchCheckinInfo()
  } catch (err: any) {
    ElMessage.error(err?.message || '取消值机失败')
  }
}

async function handleAutoAssign() {
  try {
    await autoAssignSeats(flightIdInput.value)
    ElMessage.success('自动分配完成')
    fetchCheckinInfo()
  } catch (err: any) {
    ElMessage.error(err?.message || '自动分配座位失败')
  }
}

async function handleExport() {
  try {
    const data = await exportCheckinList(flightIdInput.value) as any
    const blob = data instanceof Blob ? data : new Blob([data])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `值机名单_${flightIdInput.value}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (err: any) {
    ElMessage.error(err?.message || '导出失败')
  }
}

onMounted(() => {
  if (route.params.flightId) {
    searchForm.flightNo = ''
    searchForm.date = ''
    flightIdInput.value = route.params.flightId as string
    fetchCheckinInfo()
  }
})
</script>

<style scoped>
.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
}
</style>
