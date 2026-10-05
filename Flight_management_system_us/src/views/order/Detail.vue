<template>
  <div class="order-detail-page page-container">
    <div v-if="loading" class="loading">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else-if="order">
      <!-- Status banner -->
      <div :class="['status-banner', statusClass]">
        <div class="status-icon">
          <el-icon :size="32"><CircleCheckFilled v-if="isSuccess" /><WarningFilled v-else /></el-icon>
        </div>
        <div class="status-info">
          <h2>{{ statusText(order.status) }}</h2>
          <p v-if="order.status === 'PENDING_PAYMENT'">
            <template v-if="payCountdown > 0">
              {{ $t('order.pleasePayBefore')  }} <strong style="color: var(--color-warning); font-size: 18px;">{{ formatPayCountdown }}</strong> {{ $t('payment.payTimeoutDesc')  }}
            </template>
            <template v-else>{{ $t('payment.orderExpired')  }}</template>
          </p>
          <p v-else-if="order.status === 'PAID'">{{ $t('order.ticketIssued')  }}</p>
          <p v-else-if="order.status === 'CHECKED_IN'">{{ $t('order.checkedInSuccess')  }}</p>
        </div>
      </div>

      <!-- Flight info -->
      <div class="card-shadow section">
        <h3 class="section-title">{{ $t('order.flightInfo')  }}</h3>
        <div class="flight-detail">
          <div class="flight-route">
            <div class="endpoint">
              <div class="time">{{ formatTime(order.flight?.departure?.dateTime) }}</div>
              <div class="airport">{{ transAirport(order.flight?.departure?.airportName) }}</div>
              <div class="terminal">{{ order.flight?.departure?.terminal }} · {{ order.flight?.departure?.gate }}{{ $t('checkin.gate') }}</div>
            </div>
            <div class="route-line">
              <span class="flight-no">{{ order.flight?.flightNo }}</span>
              <div class="line-visual">
                <span class="dot"></span>
                <span class="dash"></span>
                <span class="dot"></span>
              </div>
            </div>
            <div class="endpoint">
              <div class="time">{{ formatTime(order.flight?.arrival?.dateTime) }}</div>
              <div class="airport">{{ transAirport(order.flight?.arrival?.airportName) }}</div>
              <div class="terminal">{{ order.flight?.arrival?.terminal }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Reference info -->
      <div class="card-shadow section">
        <h3 class="section-title">{{ $t('order.referenceInfo') }}</h3>
        <div class="ref-info">
          <div class="ref-item">
            <span class="label">{{ $t('order.orderNo') }}</span>
            <span class="value">{{ order.orderNo }}</span>
          </div>
          <div class="ref-item">
            <span class="label">PNR ({{ $t('order.bookingRef') }})</span>
            <span class="value pnr">{{ order.pnr }}</span>
          </div>
          <div class="ref-item">
            <span class="label">{{ $t('order.createTime') }}</span>
            <span class="value">{{ formatDateTime(order.createdAt) }}</span>
          </div>
          <div v-if="order.paidAt" class="ref-item">
            <span class="label">{{ $t('payment.paidTime') }}</span>
            <span class="value">{{ formatDateTime(order.paidAt) }}</span>
          </div>
        </div>
      </div>

      <!-- Passengers -->
      <div class="card-shadow section">
        <h3 class="section-title">{{ $t('order.passengerInfo') }}</h3>
        <el-table :data="order.passengers" style="width: 100%">
          <el-table-column prop="name" :label="$t('order.passengerName')" />
          <el-table-column prop="idNumber" :label="$t('order.idNumber')" />
          <el-table-column prop="ticketNo" :label="$t('order.ticketNo')" />
          <el-table-column prop="seat" :label="$t('order.seat')" />
          <el-table-column :label="$t('order.checkinStatus')">
            <template #default="{ row }">
              <el-tag :type="row.checkinStatus === 'CHECKED_IN' ? 'success' : 'info'" size="small">
                {{ row.checkinStatus === 'CHECKED_IN' ? $t('order.checkedIn') : $t('order.notCheckedIn') }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- SSR -->
      <div v-if="ssrList.length > 0" class="card-shadow section">
        <h3 class="section-title">特殊服务 (SSR)</h3>
        <el-table :data="ssrList" style="width: 100%">
          <el-table-column prop="ssrCode" label="代码" width="100" />
          <el-table-column prop="ssrName" label="服务名称" min-width="140" />
          <el-table-column prop="passengerName" label="旅客" width="120" />
          <el-table-column label="费用" width="100">
            <template #default="{ row }">
              <span :style="{ color: row.extraFee > 0 ? 'var(--color-warning)' : 'var(--color-success)' }">
                {{ row.extraFee > 0 ? '¥' + row.extraFee : '免费' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', COMPLETED: 'info' } as any)[row.status] || 'info'" size="small">
                {{ ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝', COMPLETED: '已完成' } as any)[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.extraFee > 0 && row.status === 'PENDING'">等待管理员审核，通过后费用¥{{ row.extraFee }}将追加到订单</span>
              <span v-else-if="row.extraFee > 0 && row.status === 'APPROVED'">已通过，费用¥{{ row.extraFee }}已追加</span>
              <span v-else>{{ row.remark }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Price -->
      <div class="card-shadow section">
        <h3 class="section-title">{{ $t('order.feeDetail') }}</h3>
        <div class="price-rows">
          <div class="price-row"><span>{{ $t('order.fare') }}</span><span>¥{{ order.price?.fare }}</span></div>
          <div class="price-row"><span>{{ $t('order.tax') }}</span><span>¥{{ order.price?.tax }}</span></div>
          <div v-if="order.price?.serviceFee > 0" class="price-row">
            <span>附加服务费{{ ssrHasApprovedPaid ? '（含SSR审核追加）' : '' }}</span>
            <span style="color:var(--color-warning)">¥{{ order.price?.serviceFee }}</span>
          </div>
          <div class="price-row total"><span>{{ $t('order.totalAmount') }}</span><span class="total-amount">¥{{ order.price?.total }}</span></div>
          <div v-if="ssrHasApprovedPaid" style="font-size:12px;color:var(--color-warning);margin-top:4px">
            付费SSR已通过审核，费用已加到订单总额中。如需补缴差额请联系客服，或等待后续功能支持在线补款。
          </div>
        </div>
      </div>

      <!-- Refund/Change rules -->
      <div v-if="order.refundChangeRules" class="card-shadow section">
        <h3 class="section-title">{{ $t('order.refundRules') }}</h3>
        <div class="rules-list">
          <div class="rule-item"><span class="label">{{ $t('order.refundPolicy') }}: </span>{{ order.refundChangeRules.refund }}</div>
          <div class="rule-item"><span class="label">{{ $t('order.changePolicy') }}: </span>{{ order.refundChangeRules.change }}</div>
          <div class="rule-item"><span class="label">{{ $t('order.transferPolicy') }}: </span>{{ order.refundChangeRules.transfer }}</div>
        </div>
      </div>

      <!-- Actions -->
      <div class="actions-bar">
        <el-button
          v-if="order.status === 'PENDING_PAYMENT'"
          type="primary"
          @click="$router.push(`/home/payment/${order.orderId}`)"
        >{{ $t('order.pay') }}</el-button>
        <el-button
          v-if="order.status === 'PENDING_PAYMENT'"
          type="danger"
          plain
          @click="handleCancel"
        >{{ $t('order.cancel') }}</el-button>
        <el-button
          v-if="order.actions?.includes('CHANGE')"
          @click="openChangeDialog"
        >{{ $t('order.change') }}</el-button>
        <el-button
          v-if="order.actions?.includes('REFUND')"
          type="danger"
          plain
          @click="showRefundDialog = true"
        >{{ $t('order.refund') }}</el-button>
      </div>
    </template>

    <!-- Refund Dialog -->
    <el-dialog v-model="showRefundDialog" :title="$t('order.refund')" width="500px">
      <el-form>
        <el-form-item :label="$t('order.refundReason')" required>
          <el-input v-model="refundReason" type="textarea" :rows="3" :placeholder="$t('order.refundReasonPlaceholder') " />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRefundDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="refundLoading" @click="handleRefund">{{ $t('order.confirmRefund')  }}</el-button>
      </template>
    </el-dialog>

    <!-- Change Dialog -->
    <el-dialog v-model="showChangeDialog" :title="$t('order.change')" width="520px">
      <el-form label-width="100px">
        <el-form-item label="出发机场">
          <el-input v-model="changeSearch.departure" placeholder="留空查全部航线" clearable @change="searchAlternativeFlights('')" />
        </el-form-item>
        <el-form-item label="到达机场">
          <el-input v-model="changeSearch.arrival" placeholder="留空查全部航线" clearable @change="searchAlternativeFlights('')" />
        </el-form-item>
        <el-form-item label="新航班号" required>
          <el-select v-model="changeForm.newFlightId" filterable remote clearable
            :remote-method="searchAlternativeFlights"
            :loading="flightSearchLoading"
            placeholder="输入航班号搜索（如 CA1501）"
            style="width: 100%"
            @focus="searchAlternativeFlights('')"
          >
            <el-option v-for="f in alternativeFlights" :key="f.flightNo"
              :label="f.label"
              :value="f.value"
            />
          </el-select>
          <div style="font-size:12px;color:var(--text-secondary);margin-top:4px">
            可指定出发/到达机场缩小搜索范围，留空则展示所有可用航班
          </div>
        </el-form-item>
        <el-form-item :label="$t('order.newCabin')">
          <el-select v-model="changeForm.newCabinClass" style="width: 100%">
            <el-option :label="$t('home.economy')" value="ECONOMY" />
            <el-option :label="$t('home.business')" value="BUSINESS" />
            <el-option :label="$t('home.first')" value="FIRST" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showChangeDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="changeLoading" @click="handleChange">{{ $t('order.confirmChange')  }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useOrderStore } from '@/stores/order'
import { applyRefund, applyChange } from '@/api/order'
import { getSsrByOrder } from '@/api/ssr'
import { searchFlights } from '@/api/flight'
import { useMappings } from '@/locales/mappings'

const route = useRoute()
const router = useRouter()
const orderStore = useOrderStore()
const { t } = useI18n()
const { transAirport } = useMappings()

const loading = ref(true)
const order = ref<any>(null)
const ssrList = ref<any[]>([])
const ssrHasApprovedPaid = computed(() =>
  Array.isArray(ssrList.value) && ssrList.value.some((s: any) => s.extraFee > 0 && s.status === 'APPROVED')
)
const showRefundDialog = ref(false)
const showChangeDialog = ref(false)
const refundReason = ref('')
const refundLoading = ref(false)
const changeLoading = ref(false)
const flightSearchLoading = ref(false)
const alternativeFlights = ref<any[]>([])
const changeSearch = reactive({ departure: '', arrival: '' })
const changeForm = reactive({
  newFlightId: '',
  newCabinClass: 'ECONOMY',
})

async function searchAlternativeFlights(query: string) {
  flightSearchLoading.value = true
  try {
    const data = await searchFlights({
      departure: changeSearch.departure || '',
      arrival: changeSearch.arrival || '',
      departDate: '',
      tripType: 'ONEWAY',
      adults: 1,
      children: 0,
      infants: 0,
      cabinClass: '',
    }) as any
    const rawList = data?.flights || data?.data?.flights || []
    const mapped = rawList
      .filter((f: any) => f.flightId !== order.value?.flight?.flightId)
      .map((f: any) => ({
        flightNo: f.flightNo,
        label: `${f.flightNo}  ${f.departure?.airport}→${f.arrival?.airport}  ${(f.departure?.dateTime||'').substring(0,10)}  ¥${(f.cabins||[]).reduce((min:number,c:any)=>Math.min(min,c.totalPrice||c.fare||99999),99999)}`,
        value: f.flightNo,
      }))
    if (query) {
      const q = query.toUpperCase()
      alternativeFlights.value = mapped.filter((f: any) => f.flightNo.toUpperCase().includes(q))
    } else {
      alternativeFlights.value = mapped
    }
  } catch {
    alternativeFlights.value = []
  } finally {
    flightSearchLoading.value = false
  }
}

const payCountdown = ref(0)
let payTimer: ReturnType<typeof setInterval> | null = null

const formatPayCountdown = computed(() => {
  const h = Math.floor(payCountdown.value / 3600)
  const m = Math.floor((payCountdown.value % 3600) / 60)
  const s = payCountdown.value % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

function startPayCountdown(expireAt: string) {
  const expireTime = new Date(expireAt).getTime()
  const now = Date.now()
  payCountdown.value = Math.max(0, Math.floor((expireTime - now) / 1000))
  if (payCountdown.value > 0) {
    payTimer = setInterval(() => {
      payCountdown.value--
      if (payCountdown.value <= 0) {
        if (payTimer) clearInterval(payTimer)
        order.value.status = 'CANCELLED'
      }
    }, 1000)
  }
}

const statusMap = computed<Record<string, string>>(() => ({
  PENDING_PAYMENT: t('order.pending'),
  PAID: t('order.paid'),
  ISSUED: t('order.paid'),
  CHECKED_IN: t('order.paid'),
  BOARDING: t('order.paid'),
  DEPARTED: t('order.paid'),
  ARRIVED: t('order.paid'),
  COMPLETED: t('order.completed'),
  CANCELLED: t('order.cancelled'),
  REFUNDING: t('order.refunding'),
  REFUNDED: t('order.refunded'),
}))

function statusText(status: string) {
  return statusMap.value[status] || status
}

const isSuccess = computed(() =>
  ['PAID', 'ISSUED', 'CHECKED_IN', 'COMPLETED'].includes(order.value?.status)
)

const statusClass = computed(() => {
  if (isSuccess.value) return 'status-success'
  if (order.value?.status === 'PENDING_PAYMENT') return 'status-warning'
  return 'status-info'
})

function formatTime(dt?: string) {
  if (!dt) return '--:--'
  const d = new Date(dt)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatDateTime(dt?: string) {
  if (!dt) return ''
  return dt.replace('T', ' ').replace(/\+.*/, '')
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm(t('order.cancelConfirm'), t('order.cancel'), { type: 'warning' })
    await orderStore.cancelOrder(order.value.orderId)
    ElMessage.success(t('order.cancelSuccess'))
    loadOrder()
  } catch {
    // cancelled or error
  }
}

async function handleRefund() {
  if (!refundReason.value) {
    ElMessage.warning(t('order.refundReasonRequired') )
    return
  }
  refundLoading.value = true
  try {
    await applyRefund(order.value.orderId, { reason: refundReason.value })
    ElMessage.success(t('order.refundSubmitted') )
    showRefundDialog.value = false
    loadOrder()
  } catch {
    // error handled
  } finally {
    refundLoading.value = false
  }
}

function openChangeDialog() {
  // 默认用当前订单的航线作为搜索起点
  changeSearch.departure = order.value?.flight?.departure?.airport || ''
  changeSearch.arrival = order.value?.flight?.arrival?.airport || ''
  changeForm.newFlightId = ''
  changeForm.newCabinClass = 'ECONOMY'
  showChangeDialog.value = true
  searchAlternativeFlights('')
}

async function handleChange() {
  if (!changeForm.newFlightId) {
    ElMessage.warning('请选择新航班')
    return
  }
  changeLoading.value = true
  try {
    await applyChange(order.value.orderId, {
      segmentIndex: 0,
      newFlightId: changeForm.newFlightId,
      newCabinClass: changeForm.newCabinClass,
    })
    ElMessage.success('改签申请已提交，请等待管理员审核')
    showChangeDialog.value = false
    loadOrder()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '改签申请失败')
  } finally {
    changeLoading.value = false
  }
}

async function loadOrder() {
  loading.value = true
  try {
    order.value = await orderStore.fetchOrderDetail(route.params.id as string)
    if (order.value.status === 'PENDING_PAYMENT' && order.value.expireAt) {
      startPayCountdown(order.value.expireAt)
    }
    getSsrByOrder(route.params.id as string).then((data: any) => {
      ssrList.value = Array.isArray(data) ? data : (data?.data && Array.isArray(data.data) ? data.data : [])
    }).catch(() => { ssrList.value = [] })
  } catch {
    ElMessage.error(t('error.serverError'))
  } finally {
    loading.value = false
  }
}

onMounted(loadOrder)

onUnmounted(() => {
  if (payTimer) clearInterval(payTimer)
})
</script>

<style scoped>
.order-detail-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.status-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  border-radius: var(--radius-lg);
  margin-bottom: 16px;
}

.status-success {
  background: linear-gradient(135deg, var(--color-success-soft), var(--color-success-soft-deep));
  color: var(--color-success);
}

.status-warning {
  background: linear-gradient(135deg, var(--color-warning-soft), var(--color-warning-soft-deep));
  color: var(--color-warning);
}

.status-info {
  background: linear-gradient(135deg, var(--bg-muted), var(--bg-muted-strong));
  color: var(--text-secondary);
}

.status-info h2 {
  font-size: 20px;
  font-weight: 600;
}

.status-info p {
  font-size: 14px;
  margin-top: 4px;
  opacity: 0.8;
}

.section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.flight-detail {
  padding: 16px;
  background: var(--bg-color);
  border-radius: var(--radius-base);
}

.flight-route {
  display: flex;
  align-items: center;
  gap: 24px;
}

.endpoint {
  flex: 1;
  text-align: center;
}

.endpoint .time {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.endpoint .airport {
  font-size: 14px;
  color: var(--text-regular);
  margin-top: 4px;
}

.endpoint .terminal {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.route-line {
  flex: 1;
  text-align: center;
}

.flight-no {
  font-size: 13px;
  color: var(--text-secondary);
  display: block;
  margin-bottom: 8px;
}

.line-visual {
  display: flex;
  align-items: center;
  justify-content: center;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid var(--color-primary);
}

.dash {
  flex: 1;
  height: 2px;
  background: var(--color-secondary);
  max-width: 80px;
}

.price-rows {
  padding: 0 16px;
}

.price-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 14px;
  color: var(--text-regular);
}

.price-row.total {
  border-top: 1px solid var(--border-color);
  margin-top: 8px;
  padding-top: 12px;
  font-weight: 600;
}

.total-amount {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-danger);
}

.rules-list {
  padding: 0 16px;
}

.rule-item {
  padding: 6px 0;
  font-size: 14px;
  color: var(--text-regular);
}

.rule-item .label {
  color: var(--text-secondary);
  font-weight: 500;
}

.ref-info {
  display: flex;
  gap: 40px;
  padding: 8px 16px;
}

.ref-item {
  display: flex;
  gap: 8px;
  align-items: center;
}

.ref-item .label {
  font-size: 14px;
  color: var(--text-secondary);
}

.ref-item .value {
  font-size: 14px;
  color: var(--text-regular);
  font-weight: 600;
}

.ref-item .pnr {
  font-family: var(--font-family-number);
  font-size: 16px;
  letter-spacing: 2px;
  color: var(--color-primary);
}

.actions-bar {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding: 16px 0;
}
</style>
