<template>
  <div class="bp-page page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ $t('checkin.boardingPass') }}</h2>
        <p class="page-desc">{{ $t('checkin.boardingPassDesc') }}</p>
      </div>
      <div class="bp-actions no-print">
        <el-button @click="goBack">{{ $t('checkin.backToCheckin') }}</el-button>
        <el-button type="primary" :icon="Printer" :disabled="!passengers.length" @click="printPage">
          {{ $t('checkin.print') }}
        </el-button>
      </div>
    </div>

    <div v-loading="loading">
      <div v-if="!loading && !passengers.length" class="empty">
        <el-empty>
          <template #description>
            <p>{{ errorText || $t('checkin.boardingPassUnavailable') }}</p>
          </template>
          <el-button type="primary" @click="goBack">{{ $t('checkin.backToCheckin') }}</el-button>
        </el-empty>
      </div>

      <div v-for="(pax, i) in passengers" :key="i" class="bp">
        <!-- 主联：核验区 -->
        <div class="bp__main">
          <div class="bp__band">
            <span class="bp__brand">
              <svg class="bp__brand-mark" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 2 3 7v10l9 5 9-5V7l-9-5Zm0 2.3 7 3.9v7.6l-7 3.9-7-3.9V8.2l7-3.9Z" fill="currentColor" />
              </svg>
              SkyTrip
            </span>
            <span class="bp__doc">{{ $t('checkin.boardingPass') }}</span>
          </div>

          <div class="bp__route">
            <div class="bp__port">
              <div class="bp__code">{{ flight?.departure?.airport || '—' }}</div>
              <div class="bp__port-meta">
                <span v-if="flight?.departure?.terminal">{{ $t('checkin.terminal') }} {{ flight.departure.terminal }}</span>
                <span class="bp__port-time">{{ formatTime(flight?.departure?.dateTime) }}</span>
              </div>
            </div>

            <div class="bp__leg" aria-hidden="true">
              <span class="bp__leg-line"></span>
              <svg class="bp__leg-plane" viewBox="0 0 24 24">
                <path
                  d="M21 16v-2l-8-3V4.5a1.5 1.5 0 0 0-3 0V11l-8 3v2l8-1.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-4.5l8 1.5Z"
                  fill="currentColor"
                />
              </svg>
              <span class="bp__leg-line"></span>
            </div>

            <div class="bp__port bp__port--right">
              <div class="bp__code">{{ flight?.arrival?.airport || '—' }}</div>
              <div class="bp__port-meta">
                <span v-if="flight?.arrival?.terminal">{{ $t('checkin.terminal') }} {{ flight.arrival.terminal }}</span>
                <span class="bp__port-time">{{ formatTime(flight?.arrival?.dateTime) }}</span>
              </div>
            </div>
          </div>

          <dl class="bp__grid">
            <div class="bp__field">
              <dt>{{ $t('checkin.passenger') }}</dt>
              <dd class="bp__field-strong">{{ pax.name || '—' }}</dd>
            </div>
            <div class="bp__field">
              <dt>{{ $t('checkin.flightNo') }}</dt>
              <dd class="bp__field-strong">{{ flight?.flightNo || '—' }}</dd>
            </div>
            <div class="bp__field">
              <dt>{{ $t('order.ticketNo') }}</dt>
              <dd class="tnum">{{ pax.ticketNo || '—' }}</dd>
            </div>
            <div class="bp__field">
              <dt>{{ $t('checkin.date') }}</dt>
              <dd class="tnum">{{ formatDate(flight?.departure?.dateTime) || '—' }}</dd>
            </div>
            <div class="bp__field">
              <dt>{{ $t('checkin.boardingTime') }}</dt>
              <dd class="bp__field-strong tnum">{{ formatTime(pax.boardingTime) || '—' }}</dd>
            </div>
            <div class="bp__field">
              <dt>{{ $t('checkin.gate') }}</dt>
              <dd class="bp__field-strong">{{ pax.gate || $t('checkin.gateTbd') }}</dd>
            </div>
          </dl>

          <div v-if="barcodeOf(i)" class="bp__barcode">
            <svg
              class="bp__barcode-svg"
              :width="barcodeOf(i)!.width"
              :height="barcodeHeight"
              :viewBox="`0 0 ${barcodeOf(i)!.width} ${barcodeHeight}`"
              role="img"
              :aria-label="`${$t('checkin.flightNo')} ${pax.seat}`"
            >
              <rect
                v-for="(bar, bi) in barcodeOf(i)!.bars"
                :key="bi"
                :x="bar.x"
                y="0"
                :width="bar.w"
                :height="barcodeHeight"
                fill="#000000"
              />
            </svg>
            <div class="bp__barcode-text tnum">{{ pax.barcode }}</div>
          </div>
        </div>

        <!-- 存根：旅客留存区 -->
        <div class="bp__stub">
          <div class="bp__stub-item">
            <span class="bp__stub-label">{{ $t('checkin.seatNumber') }}</span>
            <span class="bp__stub-seat tnum">{{ pax.seat || '—' }}</span>
          </div>

          <img v-if="qrList[i]" class="bp__qr" :src="qrList[i]" :alt="$t('checkin.boardingPass')" />
          <div v-else class="bp__qr bp__qr--placeholder" aria-hidden="true"></div>

          <div class="bp__stub-row">
            <div class="bp__stub-item">
              <span class="bp__stub-label">{{ $t('checkin.gate') }}</span>
              <span class="bp__stub-value">{{ pax.gate || $t('checkin.gateTbd') }}</span>
            </div>
            <div class="bp__stub-item">
              <span class="bp__stub-label">{{ $t('checkin.flightNo') }}</span>
              <span class="bp__stub-value">{{ flight?.flightNo || '—' }}</span>
            </div>
          </div>
        </div>
      </div>

      <p v-if="passengers.length" class="bp-tip no-print">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ $t('checkin.boardingTip') }}</span>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Printer, InfoFilled } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { getBoardingPass } from '@/api/checkin'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(true)
const errorText = ref('')
const flight = ref<any>(null)
const passengers = ref<any[]>([])
const qrList = ref<string[]>([])

/* ---------------- 条码 ---------------- */

/**
 * Code 39 编码表：9 个元素交替「条 / 空」，n = 窄，w = 宽，每个字符恰好 3 个宽元素。
 * 索引偶数位是条、奇数位是空。用真实编码而非装饰性条纹，扫出来就是 pax.barcode 的内容。
 */
const CODE39: Record<string, string> = {
  '0': 'nnnwwnwnn', '1': 'wnnwnnnnw', '2': 'nnwwnnnnw', '3': 'wnwwnnnnn', '4': 'nnnwwnnnw',
  '5': 'wnnwwnnnn', '6': 'nnwwwnnnn', '7': 'nnnwnnwnw', '8': 'wnnwnnwnn', '9': 'nnwwnnwnn',
  A: 'wnnnnwnnw', B: 'nnwnnwnnw', C: 'wnwnnwnnn', D: 'nnnnwwnnw', E: 'wnnnwwnnn',
  F: 'nnwnwwnnn', G: 'nnnnnwwnw', H: 'wnnnnwwnn', I: 'nnwnnwwnn', J: 'nnnnwwwnn',
  K: 'wnnnnnnww', L: 'nnwnnnnww', M: 'wnwnnnnwn', N: 'nnnnwnnww', O: 'wnnnwnnwn',
  P: 'nnwnwnnwn', Q: 'nnnnnnwww', R: 'wnnnnnwwn', S: 'nnwnnnwwn', T: 'nnnnwnwwn',
  U: 'wwnnnnnnw', V: 'nwwnnnnnw', W: 'wwwnnnnnn', X: 'nwnnwnnnw', Y: 'wwnnwnnnn',
  Z: 'nwwnwnnnn', '-': 'nwnnnnwnw', '.': 'wwnnnnwnn', ' ': 'nwwnnnwnn', $: 'nwnwnwnnn',
  '/': 'nwnwnnnwn', '+': 'nwnnnwnwn', '%': 'nnnwnwnwn', '*': 'nwnnwnwnn',
}

const NARROW = 1.6
const WIDE = NARROW * 3
const GAP = NARROW
const barcodeHeight = 52

/**
 * 生成 Code 39 条纹坐标。Code 39 只支持数字与大写字母等 ASCII 字符，
 * 因此姓名里的中文会被剔除（例如 `M1张三/E...` → `M1/E...`）。
 * 完整信息由右侧二维码承载，条码只作机场设备读取用。
 */
function encodeCode39(raw?: string | null) {
  if (!raw) return null
  const text = raw.toUpperCase().replace(/[^0-9A-Z\-. $/+%]/g, '')
  if (text.length < 3) return null

  const chars = ('*' + text + '*').split('')
  const bars: { x: number; w: number }[] = []
  let x = 0
  chars.forEach((ch, ci) => {
    const pattern = CODE39[ch]
    if (!pattern) return
    for (let i = 0; i < 9; i++) {
      const w = pattern[i] === 'w' ? WIDE : NARROW
      if (i % 2 === 0) bars.push({ x: Number(x.toFixed(2)), w })
      x += w
    }
    if (ci < chars.length - 1) x += GAP
  })
  if (!bars.length) return null
  return { bars, width: Math.ceil(x) }
}

const barcodeCache = ref<(ReturnType<typeof encodeCode39> | undefined)[]>([])
function barcodeOf(index: number) {
  return barcodeCache.value[index] || null
}

/* ---------------- 格式化 ---------------- */

function formatTime(dt?: string) {
  if (!dt) return ''
  const d = new Date(dt)
  if (Number.isNaN(d.getTime())) return ''
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatDate(dt?: string) {
  if (!dt) return ''
  const d = new Date(dt)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/* ---------------- 数据 ---------------- */

/** 二维码内容：与条码互补，携带完整信息，手机可直接扫出 */
function qrPayload(pax: any, checkinId: string) {
  return [
    'SKYTRIP',
    checkinId,
    flight.value?.flightNo || '',
    `${flight.value?.departure?.airport || ''}-${flight.value?.arrival?.airport || ''}`,
    formatDate(flight.value?.departure?.dateTime),
    pax.seat || '',
    pax.name || '',
  ].join('|')
}

async function buildCodes() {
  barcodeCache.value = passengers.value.map((p) => encodeCode39(p.barcode) || undefined)
  const urls: string[] = []
  for (const pax of passengers.value) {
    try {
      urls.push(
        await QRCode.toDataURL(qrPayload(pax, String(route.params.checkinId)), {
          width: 320,
          margin: 1,
          errorCorrectionLevel: 'M',
        })
      )
    } catch {
      urls.push('')
    }
  }
  qrList.value = urls
}

async function load() {
  loading.value = true
  errorText.value = ''
  flight.value = null
  passengers.value = []
  qrList.value = []
  barcodeCache.value = []
  try {
    const res = await getBoardingPass(String(route.params.checkinId))
    const data = res.data || {}
    flight.value = data.flight || null
    passengers.value = Array.isArray(data.passengers) ? data.passengers : []
    // 兼容只返回平铺结构的老响应
    if (!passengers.value.length && data.seat) {
      passengers.value = [
        {
          name: data.passenger?.name,
          seat: data.seat,
          gate: data.gate,
          boardingTime: data.boardingTime,
          qrCode: data.qrCode,
          barcode: data.barcode,
        },
      ]
    }
    await buildCodes()
  } catch (e: any) {
    errorText.value = e?.message || ''
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/home/checkin')
}

function printPage() {
  window.print()
}

onMounted(load)
watch(() => route.params.checkinId, load)
</script>

<style scoped>
.bp-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.bp-actions {
  display: flex;
  gap: var(--space-2);
}

/* ---------- 登机牌本体 ---------- */
.bp {
  position: relative;
  display: flex;
  align-items: stretch;
  background: var(--bg-card);
  border: 1px solid var(--border-color-lighter);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  margin-bottom: var(--space-4);
}

.bp__main {
  flex: 1;
  min-width: 0;
  padding: 20px 24px 22px;
}

.bp__band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color-lighter);
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--text-secondary);
}

.bp__brand {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--color-primary);
}

.bp__brand-mark {
  width: 16px;
  height: 16px;
}

/* ---------- 航段 ---------- */
.bp__route {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 20px 0 22px;
}

.bp__port {
  min-width: 84px;
}

.bp__port--right {
  text-align: right;
}

.bp__code {
  font-size: 34px;
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--text-primary);
}

.bp__port-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.bp__port--right .bp__port-meta {
  justify-content: flex-end;
}

.bp__port-time {
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
  font-weight: 600;
}

.bp__leg {
  flex: 1;
  min-width: 40px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-primary);
}

.bp__leg-line {
  flex: 1;
  height: 0;
  border-top: 1px dashed var(--color-primary-border);
}

.bp__leg-plane {
  width: 16px;
  height: 16px;
  flex: none;
}

/* ---------- 字段区 ---------- */
.bp__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 14px 16px;
  margin: 0;
}

.bp__field dt {
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-secondary);
  margin-bottom: 3px;
}

.bp__field dd {
  margin: 0;
  font-size: 15px;
  color: var(--text-primary);
}

.bp__field-strong {
  font-weight: 600;
}

/* ---------- 条码 ---------- */
.bp__barcode {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed var(--border-color-lighter);
}

.bp__barcode-svg {
  display: block;
  max-width: 100%;
  height: 52px;
}

.bp__barcode-text {
  margin-top: 6px;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--text-secondary);
  word-break: break-all;
}

/* ---------- 存根 ---------- */
.bp__stub {
  flex: none;
  width: 208px;
  padding: 20px 20px 22px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  background: var(--bg-color);
  border-left: 1px dashed var(--border-color);
}

.bp__stub-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.bp__stub-label {
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.bp__stub-seat {
  font-size: 30px;
  line-height: 1.15;
  font-weight: 700;
  color: var(--color-primary);
}

.bp__stub-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.bp__stub-row {
  display: flex;
  gap: 28px;
}

.bp__qr {
  width: 116px;
  height: 116px;
  display: block;
  border-radius: var(--radius-sm);
  background: #ffffff;
}

.bp__qr--placeholder {
  background: var(--bg-color);
  border: 1px dashed var(--border-color);
}

.bp-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  margin-top: var(--space-3);
}

.empty {
  padding: 60px 0;
}

/* ---------- 响应式：存根转为底部横条 ---------- */
@media (max-width: 768px) {
  .bp {
    flex-direction: column;
  }

  .bp__main {
    padding: 18px 18px 20px;
  }

  .bp__code {
    font-size: 28px;
  }

  .bp__stub {
    width: auto;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    border-left: none;
    border-top: 1px dashed var(--border-color);
  }

  .bp__stub-row {
    flex-direction: column;
    gap: 10px;
  }

  .bp__qr {
    width: 92px;
    height: 92px;
  }
}

/* ---------- 响应式：页头竖排（断点与 global.css 的 .page-header 一致） ---------- */
@media (max-width: 640px) {
  .bp-actions {
    width: 100%;
  }

  /* 两个按钮等分一行，避免在窄屏一左一右挤成一团 */
  .bp-actions .el-button {
    flex: 1;
  }
}

/* ---------- 打印：只留登机牌本体 ---------- */
@media print {
  /* 提高一级选择器优先级，避免用 !important */
  .bp-page .no-print {
    display: none;
  }

  .bp {
    box-shadow: none;
    border: 1px solid #000000;
    break-inside: avoid;
  }

  .bp-page {
    padding: 0;
  }
}
</style>
