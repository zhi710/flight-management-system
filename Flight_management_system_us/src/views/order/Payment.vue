<template>
  <div class="payment-page page-container">
    <h2 class="page-title">{{ $t('payment.title') }}</h2>

    <div v-if="loading" class="loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else-if="order">
      <!-- Order summary -->
      <div class="order-summary card-shadow">
        <div class="summary-row">
          <span class="label">{{ $t('order.orderNo') }}</span>
          <span class="value">{{ order.orderNo }}</span>
        </div>
        <div class="summary-row">
          <span class="label">{{ $t('header.flights') }}</span>
          <span class="value">{{ order.flight?.flightNo }} {{ transAirport(order.flight?.departure?.airport) }} → {{ transAirport(order.flight?.arrival?.airport) }}</span>
        </div>
        <div class="summary-row">
          <span class="label">{{ $t('order.passenger') }}</span>
          <span class="value">{{ order.passengers?.map((p: any) => p.name).join(', ') }}</span>
        </div>
        <div class="summary-row total">
          <span class="label">{{ $t('order.totalAmount') }}</span>
          <span class="total-amount">
            <span class="currency">¥</span>{{ order.price?.total }}
          </span>
        </div>
      </div>

      <!-- Countdown -->
      <div v-if="countdown > 0" class="countdown-bar">
        <el-icon><Clock /></el-icon>
        <span>{{ $t('payment.pleasePayBefore')  }} <strong>{{ formatCountdown }}</strong> {{ $t('payment.payTimeoutDesc')  }}</span>
      </div>

      <!-- Payment methods -->
      <div class="payment-methods card-shadow">
        <h3>{{ $t('payment.selectMethod') }}</h3>
        <div class="methods-grid">
          <div
            v-for="method in payMethods"
            :key="method.value"
            :class="['method-card', { selected: selectedMethod === method.value }]"
            @click="selectedMethod = method.value"
          >
            <el-icon :size="32" :color="method.color"><component :is="method.icon" /></el-icon>
            <span class="method-name">{{ method.label }}</span>
            <el-icon v-if="selectedMethod === method.value" class="check-icon" color="var(--color-primary)"><CircleCheckFilled /></el-icon>
          </div>
        </div>
      </div>

      <!-- Pay button -->
      <div class="pay-actions">
        <el-button
          type="primary"
          size="large"
          :loading="paying"
          :disabled="!selectedMethod"
          @click="handlePay"
        >
          {{ $t('payment.confirmPay') }} ¥{{ order.price?.total }}
        </el-button>
      </div>

      <!-- QR Code dialog -->
      <el-dialog v-model="showQrDialog" :title="$t('payment.scanQrCodeTitle') " width="400px" :close-on-click-modal="false">
        <div class="qr-content">
          <div v-if="countdown > 0" class="qr-countdown">
            <el-icon><Clock /></el-icon>
            {{ $t('payment.pleasePayBefore')  }} <strong>{{ formatCountdown }}</strong> {{ $t('payment.payTimeoutDesc')  }}
          </div>
          <p>{{ $t('payment.scanQrCode', { method: selectedMethodLabel }) }}</p>
          <div class="qr-image-wrapper">
            <canvas ref="qrCanvas"></canvas>
          </div>
          <p class="qr-tip">{{ $t('payment.autoRedirect')  }}</p>
          <div class="simulate-section">
            <el-divider>{{ $t('payment.demoEnv')  }}</el-divider>
            <p class="simulate-desc">{{ $t('payment.simulateDesc')  }}</p>
            <el-button type="success" :loading="simulating" @click="handleSimulatePay">
              {{ $t('payment.simulatePay')  }}
            </el-button>
          </div>
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { createPayment, getPaymentStatus, simulatePay } from '@/api/payment'
import { useOrderStore } from '@/stores/order'
import { useMappings } from '@/locales/mappings'

const route = useRoute()
const router = useRouter()
const orderStore = useOrderStore()
const { t } = useI18n()
const { transAirport } = useMappings()

const loading = ref(true)
const paying = ref(false)
const simulating = ref(false)
const order = ref<any>(null)
const selectedMethod = ref('')
const showQrDialog = ref(false)
const paymentId = ref('')
const countdown = ref(0)
const qrCanvas = ref<HTMLCanvasElement | null>(null)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const payMethods = computed(() => [
  // 微信、支付宝两个渠道色是第三方品牌色，不属于我们的调色板，保持不变；
  // 其余两个用品牌令牌，改主色时会跟着走。
  { label: t('payment.wechat'), value: 'WECHAT', icon: 'ChatDotRound', color: '#07c160' },
  { label: t('payment.alipay'), value: 'ALIPAY', icon: 'Wallet', color: '#1677ff' },
  { label: t('payment.bankCard'), value: 'BANK_CARD', icon: 'CreditCard', color: 'var(--color-primary)' },
  { label: t('payment.credit'), value: 'CREDIT', icon: 'CreditCard', color: 'var(--color-warning)' },
])

const selectedMethodLabel = computed(() =>
  payMethods.value.find(m => m.value === selectedMethod.value)?.label 
)

const formatCountdown = computed(() => {
  const h = Math.floor(countdown.value / 3600)
  const m = Math.floor((countdown.value % 3600) / 60)
  const s = countdown.value % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

/** 渲染二维码到 canvas */
async function renderQrCode(text: string) {
  await nextTick()
  if (qrCanvas.value) {
    await QRCode.toCanvas(qrCanvas.value, text, {
      width: 200,
      margin: 2,
      // 二维码的"黑 / 白"是扫码识别的物理对比，不是配色，只能写死。
      // 换成任何带色相的深浅都会降低识别率。
      color: { dark: '#000000', light: '#ffffff' },
    })
  }
}

async function handlePay() {
  if (!selectedMethod.value) {
    ElMessage.warning(t('payment.selectMethodFirst') )
    return
  }
  paying.value = true
  try {
    const res = await createPayment({
      orderId: order.value.orderId,
      payMethod: selectedMethod.value,
      amount: order.value.price.total,
    })
    paymentId.value = res.data.paymentId

    if (res.data.qrCode || res.data.payUrl) {
      showQrDialog.value = true
      const qrText = res.data.qrCode || res.data.payUrl
      await renderQrCode(qrText)
      startPolling()
    } else {
      ElMessage.success(t('payment.paySuccess'))
      router.push(`/home/orders/${order.value.orderId}/success`)
    }
  } catch {
    // error handled
  } finally {
    paying.value = false
  }
}

/** 模拟支付完成 */
async function handleSimulatePay() {
  simulating.value = true
  try {
    await simulatePay(paymentId.value)
    ElMessage.success(t('payment.paySuccess'))
    showQrDialog.value = false
    if (pollTimer) clearInterval(pollTimer)
    router.push(`/home/orders/${order.value.orderId}/success`)
  } catch {
    // error handled
  } finally {
    simulating.value = false
  }
}

let pollTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  pollTimer = setInterval(async () => {
    try {
      const res = await getPaymentStatus(paymentId.value)
      if (res.data.status === 'SUCCESS') {
        ElMessage.success(t('payment.paySuccess'))
        showQrDialog.value = false
        if (pollTimer) clearInterval(pollTimer)
        router.push(`/home/orders/${order.value.orderId}/success`)
      }
    } catch {
      // ignore polling errors
    }
  }, 3000)
}

onMounted(async () => {
  try {
    const orderId = route.params.orderId as string
    order.value = await orderStore.fetchOrderDetail(orderId)

    if (order.value.expireAt) {
      const expireTime = new Date(order.value.expireAt).getTime()
      const now = Date.now()
      countdown.value = Math.max(0, Math.floor((expireTime - now) / 1000))
      countdownTimer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0) {
          if (countdownTimer) clearInterval(countdownTimer)
          ElMessage.warning(t('payment.orderExpired'))
          router.push('/home/orders')
        }
      }, 1000)
    }
  } catch {
    ElMessage.error(t('error.serverError'))
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.payment-page {
  padding-top: 24px;
  padding-bottom: 48px;
  max-width: 700px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.order-summary {
  margin-bottom: 16px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 14px;
}

.summary-row .label {
  color: var(--text-secondary);
}

.summary-row .value {
  color: var(--text-primary);
  font-weight: 500;
}

.summary-row.total {
  border-top: 1px solid var(--border-color);
  margin-top: 8px;
  padding-top: 16px;
}

.total-amount {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-danger);
}

.total-amount .currency {
  font-size: 16px;
}

.countdown-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--color-warning-soft);
  border-radius: var(--radius-base);
  color: var(--color-warning);
  font-size: 14px;
  margin-bottom: 16px;
}

.countdown-bar strong {
  font-size: 16px;
}

.payment-methods {
  margin-bottom: 24px;
}

.payment-methods h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.methods-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.method-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 2px solid var(--border-color);
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.method-card:hover {
  border-color: var(--color-primary-light);
}

.method-card.selected {
  border-color: var(--color-primary);
  background: rgba(var(--color-primary-bright-rgb), 0.03);
}

.method-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}

.check-icon {
  position: absolute;
  top: 8px;
  right: 8px;
}

.pay-actions {
  text-align: center;
}

.qr-content {
  text-align: center;
  padding: 16px 0;
}

.qr-content p {
  font-size: 14px;
  color: var(--text-regular);
  margin-bottom: 16px;
}

.qr-image-wrapper {
  display: flex;
  justify-content: center;
  margin: 16px 0;
}

.qr-image-wrapper canvas {
  border-radius: 8px;
}

.qr-tip {
  font-size: 12px;
  color: var(--text-secondary);
}

.simulate-section {
  margin-top: 8px;
  text-align: center;
}

.simulate-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.qr-countdown {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 16px;
  background: var(--color-warning-soft);
  border-radius: 8px;
  color: var(--color-warning);
  font-size: 14px;
  margin-bottom: 16px;
}

.qr-countdown strong {
  font-size: 18px;
}
</style>
