<template>
  <div class="change-list-page page-container">
    <h2 class="page-title">{{ $t('orderChange.title') }}</h2>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane :label="$t('orderChange.changeTab')" name="change">
        <div v-loading="changeLoading">
          <el-empty v-if="changes.length === 0" :description="$t('orderChange.emptyChange')" />
          <div v-else v-for="c in changes" :key="c.changeId" class="record-card card-shadow">
            <div class="rec-head">
              <span class="rec-no">{{ $t('orderChange.orderNo') }}: {{ c.orderNo || '-' }}</span>
              <el-tag :type="statusTag(c.status)" size="small">{{ statusLabel(c.status) }}</el-tag>
            </div>
            <div class="rec-body">
              <div class="rec-row">
                <span>{{ $t('orderChange.newFlightNo') }}</span>
                <b>{{ c.newFlightNo || '-' }}</b>
              </div>
              <div class="rec-row">
                <span>{{ $t('orderChange.newCabinClass') }}</span>
                <b>{{ cabinLabel(c.newCabinClass) }}</b>
              </div>
              <div class="rec-row">
                <span>{{ $t('orderChange.changeFee') }}</span>
                <b>¥{{ c.changeFee ?? '0' }}</b>
              </div>
              <div class="rec-row">
                <span>{{ $t('orderChange.totalFee') }}</span>
                <b>¥{{ c.totalFee ?? '0' }}</b>
              </div>
              <!-- 净额为负时后端会返回 refundable：改签不但不用补款，还应退差价 -->
              <div v-if="Number(c.refundable) > 0" class="rec-row">
                <span>{{ $t('orderChange.refundable') }}</span>
                <b class="refund-amount">¥{{ c.refundable }}</b>
              </div>
            </div>
            <div class="rec-time">{{ formatTime(c.createTime) }}</div>

            <!-- 审核已通过但尚未补款：行程还没变更，这里给出补款入口 -->
            <div v-if="c.status === 'PENDING_PAYMENT'" class="pay-block">
              <p class="pay-tip">{{ $t('orderChange.awaitPayTip') }}</p>
              <el-button type="primary" size="small" @click="openPay(c)">
                {{ $t('orderChange.payNow') }} ¥{{ c.totalFee }}
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="$t('orderChange.refundTab')" name="refund">
        <div v-loading="refundLoading">
          <el-empty v-if="refunds.length === 0" :description="$t('orderChange.emptyRefund')" />
          <div v-else v-for="r in refunds" :key="r.refundId" class="record-card card-shadow">
            <div class="rec-head">
              <span class="rec-no">{{ $t('orderChange.orderNo') }}: {{ r.orderNo || '-' }}</span>
              <el-tag :type="statusTag(r.status)" size="small">{{ statusLabel(r.status) }}</el-tag>
            </div>
            <div class="rec-body">
              <div class="rec-row">
                <span>{{ $t('orderChange.flightNo') }}</span>
                <b>{{ r.flightNo || '-' }}</b>
              </div>
              <div class="rec-row">
                <span>{{ $t('orderChange.refundFee') }}</span>
                <b>¥{{ r.refundFee ?? '0' }}</b>
              </div>
              <div class="rec-row">
                <span>{{ $t('orderChange.refundAmount') }}</span>
                <b>¥{{ r.refundAmount ?? '0' }}</b>
              </div>
              <div v-if="r.refundTime" class="rec-row">
                <span>{{ $t('orderChange.refundTime') }}</span>
                <b>{{ formatTime(r.refundTime) }}</b>
              </div>
              <div v-if="r.reason" class="rec-row">
                <span>{{ $t('orderChange.reason') }}</span>
                <b>{{ r.reason }}</b>
              </div>
            </div>
            <div class="rec-time">{{ formatTime(r.createTime) }}</div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 改签补款：复用支付链路，扫码后轮询支付状态，成功即代表改签已生效 -->
    <el-dialog
      v-model="payDialog"
      :title="$t('orderChange.payTitle')"
      width="400px"
      :close-on-click-modal="false"
      @closed="stopPoll"
    >
      <div class="pay-dialog-content">
        <div class="pay-amount-line">
          <span>{{ $t('orderChange.payAmount') }}</span>
          <strong>¥{{ current?.totalFee ?? '0' }}</strong>
        </div>
        <p class="pay-scan-tip">{{ $t('orderChange.payScanTip') }}</p>
        <div class="qr-wrapper">
          <canvas ref="qrCanvas"></canvas>
        </div>
        <p class="pay-checking">{{ $t('orderChange.payChecking') }}</p>
        <el-divider>{{ $t('payment.demoEnv') }}</el-divider>
        <el-button type="success" :loading="simulating" @click="handleSimulate">
          {{ $t('payment.simulatePay') }}
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { getMyChanges, getMyRefunds } from '@/api/order'
import { createChangePayment, getPaymentStatus, simulatePay } from '@/api/payment'

const { t } = useI18n()
const activeTab = ref('change')
const changeLoading = ref(false)
const refundLoading = ref(false)
const changes = ref<any[]>([])
const refunds = ref<any[]>([])

const payDialog = ref(false)
const paying = ref(false)
const simulating = ref(false)
const current = ref<any>(null)
const paymentId = ref('')
const qrCanvas = ref<HTMLCanvasElement | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

function cabinLabel(c: string) {
  const map: Record<string, string> = {
    ECONOMY: t('orderChange.cabinEconomy'),
    BUSINESS: t('orderChange.cabinBusiness'),
    FIRST: t('orderChange.cabinFirst'),
  }
  return map[c] || c || '-'
}

function statusLabel(s: string) {
  const map: Record<string, string> = {
    PENDING: t('orderChange.statusPending'),
    PENDING_PAYMENT: t('orderChange.statusPendingPayment'),
    APPROVED: t('orderChange.statusApproved'),
    REJECTED: t('orderChange.statusRejected'),
    COMPLETED: t('orderChange.statusCompleted'),
  }
  return map[s] || s
}

function statusTag(s: string) {
  const map: Record<string, string> = {
    PENDING: 'info',
    PENDING_PAYMENT: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    COMPLETED: 'success',
  }
  return (map[s] || 'info') as any
}

function formatTime(ts: string): string {
  if (!ts) return ''
  try {
    return new Date(ts).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return ts
  }
}

async function loadChanges() {
  changeLoading.value = true
  try {
    const res = await getMyChanges()
    changes.value = res.data || []
  } catch {
    // error handled
  } finally {
    changeLoading.value = false
  }
}

async function loadRefunds() {
  refundLoading.value = true
  try {
    const res = await getMyRefunds()
    refunds.value = res.data || []
  } catch {
    // error handled
  } finally {
    refundLoading.value = false
  }
}

function handleTabChange(name: string | number) {
  if (name === 'change') loadChanges()
  else if (name === 'refund') loadRefunds()
}

/** 打开补款弹窗：向后端要支付凭证（同单重复打开会复用同一张待支付单） */
async function openPay(c: any) {
  current.value = c
  payDialog.value = true
  paying.value = true
  try {
    const res = await createChangePayment(c.changeId)
    paymentId.value = res.data.paymentId
    await nextTick()
    const text = res.data.qrCode || res.data.payUrl
    if (text && qrCanvas.value) {
      await QRCode.toCanvas(qrCanvas.value, text, { width: 220, margin: 1 })
    }
    startPoll()
  } catch (e: any) {
    payDialog.value = false
    ElMessage.error(e?.response?.data?.message || t('orderChange.payTitle'))
  } finally {
    paying.value = false
  }
}

function startPoll() {
  stopPoll()
  pollTimer = setInterval(async () => {
    try {
      const res = await getPaymentStatus(paymentId.value)
      if (res.data?.status === 'SUCCESS') {
        stopPoll()
        ElMessage.success(t('orderChange.paySuccess'))
        payDialog.value = false
        loadChanges()
      }
    } catch {
      // 轮询失败下一轮再试，不打断用户
    }
  }, 3000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function handleSimulate() {
  simulating.value = true
  try {
    await simulatePay(paymentId.value)
    stopPoll()
    ElMessage.success(t('orderChange.paySuccess'))
    payDialog.value = false
    loadChanges()
  } catch {
    // error handled
  } finally {
    simulating.value = false
  }
}

onMounted(loadChanges)
onUnmounted(stopPoll)
</script>

<style scoped>
.change-list-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.record-card {
  margin-bottom: 12px;
  padding: 16px;
}

.rec-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.rec-no {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.rec-body {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 24px;
}

.rec-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-secondary);
}

.rec-row b {
  color: var(--text-primary);
  font-weight: 500;
}

.rec-row b.refund-amount {
  color: var(--color-success);
}

.rec-time {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.pay-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 0.5px solid var(--border-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.pay-tip {
  margin: 0;
  font-size: 13px;
  color: var(--color-warning);
}

.pay-dialog-content {
  text-align: center;
}

.pay-amount-line {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 0 8px 12px;
  font-size: 14px;
  color: var(--text-secondary);
}

.pay-amount-line strong {
  font-size: 22px;
  color: var(--color-primary);
}

.pay-scan-tip,
.pay-checking {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 8px 0;
}

.qr-wrapper {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

@media (max-width: 768px) {
  .rec-body {
    grid-template-columns: 1fr;
  }
}
</style>
