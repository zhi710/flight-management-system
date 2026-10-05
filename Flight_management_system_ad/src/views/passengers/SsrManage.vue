<template>
  <div class="page-container">
    <!-- SSR代码字典 -->
    <div class="card-panel">
      <div class="section-header">
        <span class="section-title">SSR服务代码字典</span>
      </div>
      <el-table scrollbar-always-on :data="ssrCodes" v-loading="codeLoading" stripe size="small">
        <el-table-column prop="code" label="代码" width="90" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="nameCn" label="名称(中文)" min-width="120" />
        <el-table-column prop="nameEn" label="名称(英文)" min-width="120" />
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="extraFee" label="费用" width="80">
          <template #default="{ row }">
            <span>{{ row.extraFee ? '¥' + row.extraFee : '免费' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- SSR请求管理 -->
    <div class="card-panel" style="margin-top: 16px;">
      <div class="section-header">
        <span class="section-title">SSR请求管理</span>
        <div>
          <el-input v-model="searchFlightNo" placeholder="航班号" clearable style="width: 160px" @change="fetchList" />
          <el-select v-model="searchStatus" placeholder="状态" clearable style="width: 120px; margin-left: 12px" @change="fetchList">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </div>
      </div>
      <el-table scrollbar-always-on :data="list" v-loading="requestLoading" stripe>
        <el-table-column prop="flightNo" label="航班号" width="105" />
        <el-table-column prop="passengerName" label="旅客姓名" width="90" />
        <el-table-column prop="ssrCode" label="SSR代码" width="90" />
        <el-table-column prop="ssrName" label="服务名称" min-width="130" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="申请时间" width="140">
          <template #default="{ row }"><span class="tnum">{{ fmtDateTime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" type="success" link size="small" @click="handleProcess(row, 'APPROVED')">通过</el-button>
            <el-button v-if="row.status === 'PENDING'" type="danger" link size="small" @click="handleProcess(row, 'REJECTED')">拒绝</el-button>
            <span v-else class="text-sub">{{ row.status === 'APPROVED' ? '已通过' : '已拒绝' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" :total="pagination.total" layout="total, sizes, prev, pager, next" @size-change="fetchList" @current-change="fetchList" />
      </div>
    </div>

    <!-- 审批确认对话框 -->
    <el-dialog v-model="ssrActionVisible" title="提示" width="420px" destroy-on-close :close-on-click-modal="false">
      <div style="text-align:center">
        <el-icon :size="44" :color="ssrActionType === 'APPROVED' ? 'var(--color-success)' : 'var(--color-danger)'"><WarningFilled /></el-icon>
        <p style="font-size:15px;margin:12px 0 4px">
          确定<strong>{{ ssrActionType === 'APPROVED' ? '通过' : '拒绝' }}</strong>该SSR申请？
        </p>
        <p style="font-size:13px;color:var(--text-secondary)">{{ ssrActionName }} — {{ ssrActionPassenger }}</p>
      </div>
      <template #footer>
        <div style="display:flex;justify-content:center;gap:12px">
          <el-button @click="ssrActionVisible = false">取消</el-button>
          <el-button :type="ssrActionType === 'APPROVED' ? 'success' : 'danger'" :loading="ssrActionLoading" @click="confirmSsrAction">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSsrCodes, getSsrList, processSsrRequest } from '@/api/ssr'
import { getFlightList } from '@/api/flights'
import { fmtDateTime } from '@/utils/format'

// ---- SSR 代码字典 ----
const ssrCodes = ref<any[]>([])
const codeLoading = ref(false)
async function loadSsrCodes() {
  codeLoading.value = true
  try {
    ssrCodes.value = (await getSsrCodes()) as unknown as any[]
  } catch { console.warn('获取SSR代码失败') }
  finally { codeLoading.value = false }
}

// ---- SSR 请求列表 ----
const requestLoading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const searchFlightNo = ref('')
const searchStatus = ref('')
const flightIdMap = ref<Map<string, string>>(new Map())

async function fetchList() {
  requestLoading.value = true
  try {
    const flightId = searchFlightNo.value ? flightIdMap.value.get(searchFlightNo.value) : undefined
    const data = (await getSsrList({
      flightId,
      status: searchStatus.value || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })) as any
    list.value = data.list || []
    pagination.total = data.pagination?.total || 0
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  } finally {
    requestLoading.value = false
  }
}

const ssrActionVisible = ref(false)
const ssrActionLoading = ref(false)
const ssrActionType = ref('APPROVED')
const ssrActionName = ref('')
const ssrActionPassenger = ref('')
const ssrActionRequestId = ref('')

function handleProcess(row: any, action: string) {
  ssrActionType.value = action
  ssrActionName.value = row.ssrName || row.ssrCode
  ssrActionPassenger.value = row.passengerName || ''
  ssrActionRequestId.value = row.requestId
  ssrActionVisible.value = true
}

async function confirmSsrAction() {
  ssrActionLoading.value = true
  try {
    await processSsrRequest(ssrActionRequestId.value, ssrActionType.value)
    ElMessage.success(ssrActionType.value === 'APPROVED' ? '已通过' : '已拒绝')
    ssrActionVisible.value = false
    fetchList()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '操作失败')
  } finally {
    ssrActionLoading.value = false
  }
}

function statusTag(s: string) {
  return ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', COMPLETED: 'info' } as any)[s] || 'info'
}
function statusLabel(s: string) {
  return ({ PENDING: '待处理', APPROVED: '已通过', REJECTED: '已拒绝', COMPLETED: '已完成' } as any)[s] || s
}

onMounted(async () => {
  try {
    const data = (await getFlightList({ page: 1, pageSize: 200 })) as any
    const flights = data.list || []
    flights.forEach((f: any) => flightIdMap.value.set(f.flightNo, f.flightId))
  } catch { console.warn('获取航班列表失败') }
  loadSsrCodes()
  fetchList()
})
</script>

<style scoped>
/* .pagination-wrap / .text-secondary / .section-header / .section-title 均已收进全局样式 */
</style>