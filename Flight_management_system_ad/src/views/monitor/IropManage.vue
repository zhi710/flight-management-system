<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">不正常航班管理</div>
        <div>
          <el-select v-model="searchForm.type" placeholder="异常类型" clearable style="width: 130px" @change="fetchList">
            <el-option label="延误" value="DELAY" />
            <el-option label="取消" value="CANCEL" />
            <el-option label="备降" value="DIVERSION" />
            <el-option label="返航" value="RETURN" />
          </el-select>
          <el-select v-model="searchForm.status" placeholder="状态" clearable style="width: 120px; margin-left: 12px" @change="fetchList">
            <el-option label="已处理" value="PROCESSED" />
            <el-option label="待审核" value="PENDING_REVIEW" />
          </el-select>
          <el-input v-model="searchForm.flightNo" placeholder="航班号" clearable style="width: 160px; margin-left: 12px" @change="fetchList" />
        </div>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="card-panel">
      <el-table scrollbar-always-on :data="list" v-loading="loading" stripe>
        <!-- 航班号与日期合成一列：两者一一对应，合成后 10 列降到 8 列 -->
        <el-table-column label="航班/日期" width="125">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ row.flightNo }}</div>
              <div class="cell-stack__sub">{{ row.flightDate }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="route" label="航线" width="120" />
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="145" show-overflow-tooltip />
        <!-- 延误分钟与延误代码合成一列 -->
        <el-table-column label="延误" width="105">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ row.delayMinutes ? row.delayMinutes + ' 分钟' : '—' }}</div>
              <div class="cell-stack__sub">{{ row.iataDelayCode || '—' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="自动改签" width="85" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.type !== 'CANCEL'" type="info" size="small">—</el-tag>
            <el-tag v-else-if="row.autoRebooked" type="success" size="small">已执行</el-tag>
            <el-tag v-else type="warning" size="small">未执行</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" width="135">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <!-- 旅客/改签/通知 三个链接按钮实测需 ~126px + 内边距 24px；给 135px 会折成两行 -->
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewPassengers(row)">旅客</el-button>
            <el-button type="primary" link size="small" @click="viewRebookings(row)">改签</el-button>
            <el-button type="primary" link size="small" :loading="notifyLoadingSet.has(row.id)" @click="notifyPassengers(row)" :disabled="!row.flightId">
              通知
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 受影响的旅客对话框 -->
    <el-dialog v-model="passengerDialogVisible" :title="`受影响旅客 - ${dialogFlightNo}`" width="900px" destroy-on-close append-to-body :close-on-click-modal="false">
      <el-table scrollbar-always-on :data="passengerList" stripe v-loading="passengerLoading" max-height="420">
        <el-table-column prop="passengerName" label="姓名" width="120" />
        <el-table-column prop="pnr" label="PNR" width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="cabinClass" label="舱位" width="80" />
        <el-table-column prop="ticketStatus" label="客票状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.ticketStatus === 'ISSUED' ? 'success' : 'warning'" size="small">
              {{ row.ticketStatus === 'ISSUED' ? '已出票' : row.ticketStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="manualRebookDialog(row)">改签</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 改签结果对话框 -->
    <el-dialog v-model="rebookingDialogVisible" title="自动改签结果" width="900px" destroy-on-close append-to-body :close-on-click-modal="false">
      <el-table scrollbar-always-on :data="rebookingList" stripe v-loading="rebookingLoading" max-height="420">
        <!-- 旅客与 PNR 合成一列；新航班与新日期合成一列。
             原来 9 列合计 990px，而 900px 的对话框可用宽度只有 868px，必然横向滚动 -->
        <el-table-column label="旅客 / PNR" width="110">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main">{{ row.passengerName }}</div>
              <div class="cell-stack__sub tnum">{{ row.pnr }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="新航班 / 日期" width="130">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ row.newFlightNo }}</div>
              <div class="cell-stack__sub">{{ row.newFlightDate }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="newRoute" label="新航线" width="130" />
        <el-table-column prop="newCabinClass" label="新舱位" width="80" />
        <el-table-column prop="fareDiff" label="差价" width="90">
          <template #default="{ row }">
            <span class="tnum">{{ row.fareDiff != null ? '¥' + row.fareDiff : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="rebookingStatusTag(row.status)" size="small">
              {{ rebookingStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" type="success" link size="small" @click="handleConfirm(row, 'APPROVED')">确认</el-button>
            <el-button v-if="row.status === 'PENDING'" type="danger" link size="small" @click="handleConfirm(row, 'REJECTED')">拒绝</el-button>
            <span v-else class="text-sub">已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="pagination-wrap" v-if="rebookingPagination.total > 0">
          <el-pagination
            v-model:current-page="rebookingPagination.page"
            v-model:page-size="rebookingPagination.pageSize"
            :total="rebookingPagination.total"
            layout="total, sizes, prev, pager, next"
            size="small"
            @size-change="fetchRebookings"
            @current-change="fetchRebookings"
          />
        </div>
      </template>
    </el-dialog>

    <!-- 手动改签对话框 -->
    <el-dialog v-model="manualDialogVisible" title="手动改签" width="560px" destroy-on-close append-to-body :close-on-click-modal="false">
      <el-form :model="manualForm" label-width="88px" label-position="top">
        <el-form-item label="原订单号">
          <el-input v-model="manualForm.orderId" disabled />
        </el-form-item>
        <el-form-item label="旅客姓名">
          <el-input v-model="manualForm.passengerName" disabled />
        </el-form-item>
        <el-form-item label="新航班" required>
          <el-select v-model="manualForm.newFlightId" filterable placeholder="搜索航班号" style="width: 100%">
            <el-option
              v-for="f in availableFlights"
              :key="f.flightId"
              :label="`${f.flightNo} ${f.route?.departure}-${f.route?.arrival} ${f.date || ''} ${f.schedule?.departureTime || ''}→${f.schedule?.arrivalTime || ''}`"
              :value="f.flightId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新舱位" required>
          <el-select v-model="manualForm.newCabinClass" style="width: 100%">
            <el-option label="经济舱" value="ECONOMY" />
            <el-option label="超级经济舱" value="PREMIUM_ECONOMY" />
            <el-option label="商务舱" value="BUSINESS" />
            <el-option label="头等舱" value="FIRST" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSaving" @click="submitManualRebook">确认改签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getIropList, getAffectedPassengers, getRebookingList, confirmRebooking, manualRebook, notifyIropPassengers } from '@/api/irop'
import { getFlightList } from '@/api/flights'
import { fmtDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const searchForm = reactive({ flightNo: '', type: '', status: '' })
const notifyLoadingSet = ref(new Set<string>())

// 受影响旅客
const passengerDialogVisible = ref(false)
const passengerLoading = ref(false)
const passengerList = ref<any[]>([])
const dialogFlightNo = ref('')
const dialogFlightId = ref('')

// 改签结果
const rebookingDialogVisible = ref(false)
const rebookingLoading = ref(false)
const rebookingList = ref<any[]>([])
const rebookingPagination = reactive({ page: 1, pageSize: 20, total: 0 })

// 手动改签
const manualDialogVisible = ref(false)
const manualSaving = ref(false)
const manualForm = reactive({ orderId: '', passengerName: '', newFlightId: '', newCabinClass: 'ECONOMY' })
const availableFlights = ref<any[]>([])

async function fetchList() {
  loading.value = true
  try {
    const data = await getIropList({ ...searchForm, page: pagination.page, pageSize: pagination.pageSize }) as any
    list.value = data.list || []
    pagination.total = data.pagination?.total || 0
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '操作失败')
  } finally {
    loading.value = false
  }
}

async function viewPassengers(row: any) {
  passengerList.value = []
  passengerLoading.value = true
  dialogFlightNo.value = row.flightNo || ''
  dialogFlightId.value = row.flightId
  passengerDialogVisible.value = true
  try {
    const result = await getAffectedPassengers(row.flightId) as unknown as any[]
    passengerList.value = Array.isArray(result) ? result : []
    if (passengerList.value.length === 0) {
      // 无旅客时给个空提示
    }
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '请求失败')
    passengerList.value = []
  } finally {
    passengerLoading.value = false
  }
}

async function viewRebookings(row: any) {
  dialogFlightNo.value = row.flightNo || ''
  dialogFlightId.value = row.flightId
  rebookingList.value = []
  rebookingPagination.page = 1
  rebookingPagination.total = 0
  rebookingDialogVisible.value = true
  if (!row.flightId) {
    rebookingLoading.value = false
    return
  }
  await fetchRebookings()
}

async function fetchRebookings() {
  rebookingLoading.value = true
  try {
    const data = await getRebookingList({
      flightId: dialogFlightId.value || undefined,
      page: rebookingPagination.page,
      pageSize: rebookingPagination.pageSize,
    }) as any
    rebookingList.value = data.list || []
    rebookingPagination.total = data.pagination?.total || 0
  } catch (err: any) { if (!err?.handled) ElMessage.error(err?.message || '加载改签列表失败') } finally {
    rebookingLoading.value = false
  }
}

async function handleConfirm(row: any, action: string) {
  try {
    await ElMessageBox.confirm(`确定${action === 'APPROVED' ? '确认' : '拒绝'}该改签？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await confirmRebooking(row.rebookingId, action)
    ElMessage.success(action === 'APPROVED' ? '已确认改签' : '已拒绝改签')
    fetchRebookings()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '操作失败')
  }
}

function showToast(msg: string, type: 'success' | 'error') {
  const d = document.createElement('div')
  d.innerText = msg
  const bg = type === 'success' ? 'var(--color-success)' : 'var(--color-danger)'
  d.style.cssText = `position:fixed;top:20px;left:50%;transform:translateX(-50%);background:${bg};color:var(--text-inverse);padding:12px 24px;border-radius:6px;z-index:99999;font-size:14px;box-shadow:var(--shadow-md);transition:opacity 0.3s`
  document.body.appendChild(d)
  setTimeout(() => {
    d.style.opacity = '0'
    setTimeout(() => d.remove(), 300)
  }, 4000)
}

async function notifyPassengers(row: any) {
  const id = row.id
  notifyLoadingSet.value.add(id)
  try {
    await notifyIropPassengers(id)
    showToast('通知已发送', 'success')
  } catch (err: any) {
    showToast('通知失败: ' + (err?.message || '请稍后重试'), 'error')
  } finally {
    notifyLoadingSet.value.delete(id)
  }
}

async function manualRebookDialog(passenger: any) {
  manualForm.orderId = passenger.orderId || ''
  manualForm.passengerName = passenger.passengerName || ''
  manualForm.newFlightId = ''
  manualForm.newCabinClass = 'ECONOMY'
  manualDialogVisible.value = true

  try {
    const data = await getFlightList({ page: 1, pageSize: 100, status: 'SCHEDULED' }) as any
    availableFlights.value = (data.list || []).filter((f: any) => !f.isPast)
  } catch (err: any) { if (!err?.handled) ElMessage.error(err?.message || '加载航班列表失败') }
}

async function submitManualRebook() {
  if (!manualForm.newFlightId) {
    ElMessage.warning('请选择新航班')
    return
  }
  manualSaving.value = true
  try {
    await manualRebook({
      orderId: manualForm.orderId,
      newFlightId: manualForm.newFlightId,
      newCabinClass: manualForm.newCabinClass,
    })
    ElMessage.success('手动改签成功')
    manualDialogVisible.value = false
    fetchList()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '操作失败')
  } finally {
    manualSaving.value = false
  }
}

function typeTag(type: string) {
  return ({ DELAY: 'warning', CANCEL: 'danger', DIVERSION: 'info', RETURN: 'info' } as any)[type] || 'info'
}
function typeLabel(type: string) {
  return ({ DELAY: '延误', CANCEL: '取消', DIVERSION: '备降', RETURN: '返航' } as any)[type] || type
}
function rebookingStatusTag(s: string) {
  return ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CONFIRMED: 'success' } as any)[s] || 'info'
}
function rebookingStatusLabel(s: string) {
  return ({ PENDING: '待确认', APPROVED: '已通过', REJECTED: '已拒绝', CONFIRMED: '已确认' } as any)[s] || s
}

onMounted(fetchList)
</script>

<style scoped>
/* .pagination-wrap / .text-secondary / .page-header 已收进全局样式，此处不再重复 */
</style>
