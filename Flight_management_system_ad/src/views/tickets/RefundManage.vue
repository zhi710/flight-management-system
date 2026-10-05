<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">退改管理</div>
        <el-radio-group v-model="activeTab" @change="fetchData">
          <el-radio-button value="refunds">退票</el-radio-button>
          <el-radio-button value="changes">改签</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 退票列表 -->
    <div class="card-panel" v-if="activeTab === 'refunds'">
      <el-table scrollbar-always-on :data="refundList" stripe v-loading="loading">
        <!-- 退票ID 与订单ID 合成一列：两个都是 19 位流水号，各占 150px 太贵。
             19 位数字在 150px 里会折行把行高撑到 71px，这里截短显示、完整值挂 title -->
        <el-table-column label="退票ID / 订单ID" width="150">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main text-mono tnum" :title="row.refundId">{{ shortId(row.refundId, 8, 6) }}</div>
              <div class="cell-stack__sub text-mono tnum" :title="row.orderId">{{ shortId(row.orderId, 8, 6) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="passengerName" label="旅客" width="90" />
        <el-table-column prop="flightNo" label="航班号" width="100" />
        <el-table-column prop="refundAmount" label="退款金额" width="100">
          <template #default="{ row }">¥{{ row.refundAmount }}</template>
        </el-table-column>
        <el-table-column prop="refundFee" label="手续费" width="90">
          <template #default="{ row }">¥{{ row.refundFee }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="110" />
        <!-- 状态与退款时间叠成两行：都回答「这笔退款现在什么情况」，不占两列 -->
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <div class="cell-stack">
              <el-tag :type="refundTag(row.status)" size="small">{{ refundLabel(row.status) }}</el-tag>
              <div v-if="row.refundTime" class="cell-stack__sub">{{ fmtDateTime(row.refundTime) }}</div>
              <div v-else-if="row.failReason" class="cell-stack__sub" :title="row.failReason">退款失败</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button type="success" link size="small" @click="handleApproveRefund(row, true)">通过</el-button>
              <el-button type="danger" link size="small" @click="handleApproveRefund(row, false)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 改签列表 -->
    <div class="card-panel" v-if="activeTab === 'changes'">
      <el-table scrollbar-always-on :data="changeList" stripe v-loading="loading">
        <el-table-column label="改签ID / 订单ID" width="150">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main text-mono tnum" :title="row.changeId">{{ shortId(row.changeId, 8, 6) }}</div>
              <div class="cell-stack__sub text-mono tnum" :title="row.orderId">{{ shortId(row.orderId, 8, 6) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="passengerName" label="旅客" width="90" />
        <el-table-column prop="originalFlightNo" label="原航班" width="100" />
        <el-table-column prop="newFlightNo" label="新航班" width="100" />
        <el-table-column prop="changeFee" label="改签费" width="90">
          <template #default="{ row }">¥{{ row.changeFee }}</template>
        </el-table-column>
        <el-table-column prop="fareDiff" label="差价" width="90">
          <template #default="{ row }">{{ money(row.fareDiff) }}</template>
        </el-table-column>
        <el-table-column label="需补款" width="90">
          <template #default="{ row }">
            <span v-if="Number(row.totalFee) > 0" style="color: var(--color-warning);">¥{{ row.totalFee }}</span>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="changeTag(row.status)" size="small">{{ changeLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button type="success" link size="small" @click="handleApproveChange(row, true)">通过</el-button>
              <el-button type="danger" link size="small" @click="handleApproveChange(row, false)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRefundList, approveRefund, getChangeList, approveChange } from '@/api/tickets'
import { shortId, fmtDateTime } from '@/utils/format'

const activeTab = ref('refunds')
const loading = ref(false)
const refundList = ref<any[]>([])
const changeList = ref<any[]>([])

/** 退票状态：PENDING 待审核 → COMPLETED 已退款 / REJECTED 已拒绝 */
function refundLabel(s: string) {
  return { PENDING: '待审核', COMPLETED: '已退款', REJECTED: '已拒绝' }[s] || s
}
function refundTag(s: string) {
  return ({ PENDING: 'warning', COMPLETED: 'success', REJECTED: 'danger' } as Record<string, any>)[s] || 'info'
}

/** 改签状态：PENDING 待审核 → PENDING_PAYMENT 待补款 → COMPLETED 已完成 / REJECTED 已拒绝 */
function changeLabel(s: string) {
  return { PENDING: '待审核', PENDING_PAYMENT: '待补款', COMPLETED: '已完成', REJECTED: '已拒绝' }[s] || s
}
function changeTag(s: string) {
  return ({ PENDING: 'warning', PENDING_PAYMENT: 'warning', COMPLETED: 'success', REJECTED: 'danger' } as Record<string, any>)[s] || 'info'
}

/** 金额带符号：差价可能为负（改签后应退旅客钱），按 -¥100 而不是 ¥-100 展示 */
function money(v: any) {
  const n = Number(v ?? 0)
  if (!Number.isFinite(n)) return '—'
  return n < 0 ? `-¥${Math.abs(n)}` : `¥${n}`
}

async function fetchData() {
  loading.value = true
  try {
    if (activeTab.value === 'refunds') {
      const data = await getRefundList() as any
      refundList.value = data.list || data || []
    } else {
      const data = await getChangeList() as any
      changeList.value = data.list || data || []
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
    if (activeTab.value === 'refunds') refundList.value = []
    else changeList.value = []
  } finally {
    loading.value = false
  }
}

async function handleApproveRefund(row: any, approved: boolean) {
  const action = approved ? '通过' : '拒绝'
  // 通过会真的走渠道退款并释放座位库存，这里把动作后果说清楚，避免当成一次纯状态变更
  const tip = approved
    ? `确定通过该退票申请？\n将通过原支付渠道退还 ¥${row.refundAmount}，并回补座位库存、作废值机与登机牌。`
    : '确定拒绝该退票申请？订单与座位都不会变动。'
  try {
    await ElMessageBox.confirm(tip, '提示', { type: 'warning', confirmButtonText: action })
  } catch { return }
  try {
    await approveRefund(row.refundId, { approved })
    ElMessage.success(`已${action}`)
    fetchData()
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

async function handleApproveChange(row: any, approved: boolean) {
  const action = approved ? '通过' : '拒绝'
  const payable = Number(row.totalFee ?? 0)
  let tip = '确定拒绝该改签申请？'
  if (approved) {
    tip = payable > 0
      ? `确定通过该改签申请？\n需旅客补款 ¥${payable}，审核通过后改签单进入「待补款」，付款成功后行程才会变更。`
      : '确定通过该改签申请？\n本次无需补款，将立即变更行程：释放原航班座位与库存、扣减新航班库存。'
  }
  try {
    await ElMessageBox.confirm(tip, '提示', { type: 'warning', confirmButtonText: action })
  } catch { return }
  try {
    await approveChange(row.changeId, { approved })
    ElMessage.success(`已${action}`)
    fetchData()
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

onMounted(fetchData)
</script>
