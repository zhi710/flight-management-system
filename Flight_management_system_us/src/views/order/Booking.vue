<template>
  <div class="booking-page page-container">
    <h2 class="page-title">{{ $t('order.fillOrder') }}</h2>

    <!-- Flight summary -->
    <div class="flight-summary card-shadow" v-if="orderStore.selectedFlight">
      <div class="summary-route">
        <span class="airline">{{ transAirline(orderStore.selectedFlight.airline?.name) }}</span>
        <span class="flight-no">{{ orderStore.selectedFlight.flightNo }}</span>
        <span class="route">
          {{ transCity(orderStore.selectedFlight.departure?.city) }} → {{ transCity(orderStore.selectedFlight.arrival?.city) }}
        </span>
        <span class="date">{{ formatDate(orderStore.selectedFlight.departure?.dateTime) }}</span>
      </div>
      <div class="summary-cabin">
        <span>{{ orderStore.selectedCabin?.className }}</span>
        <span class="summary-price">¥{{ orderStore.selectedCabin?.totalPrice }}/{{ $t('flight.perPerson') }}</span>
      </div>
    </div>

    <!-- Passenger forms -->
    <div class="passengers-section card-shadow">
      <div class="section-header">
        <h3>{{ $t('order.passengerInfo') }}</h3>
        <el-button text type="primary" @click="addPassenger">
          <el-icon><Plus /></el-icon> {{ $t('order.addPassenger') }}
        </el-button>
      </div>

      <div
        v-for="(pax, idx) in passengers"
        :key="idx"
        class="passenger-card"
      >
        <div class="pax-header">
          <span class="pax-label">{{ $t('order.passenger') }} {{ idx + 1 }}</span>
          <el-tag v-if="pax.isSelf" type="success" size="small">{{ $t('member.selfTag') }}</el-tag>
          <el-select v-model="pax.passengerType" size="small" style="width: 100px" :disabled="pax.isSelf">
            <el-option :label="$t('member.adult')" value="ADULT" />
            <el-option :label="$t('member.child')" value="CHILD" />
            <el-option :label="$t('member.infant')" value="INFANT" />
          </el-select>
          <!-- 本人不允许用常用旅客覆盖：本人由实名档案决定，覆盖后订单里就没了本人 -->
          <el-button v-if="!pax.isSelf" text type="primary" size="small" @click="openTravelerSelect(idx)">
            <el-icon><UserFilled /></el-icon> {{ $t('member.travelers') }}
          </el-button>
          <el-button v-if="idx > 0 && passengers.length > 1" text type="danger" size="small" @click="removePassenger(idx)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>

        <div v-if="pax.isSelf" class="self-tip">{{ $t('member.selfReadonlyTip') }}</div>

        <el-form :model="pax" label-width="80px" class="pax-form">
          <el-row :gutter="16">
            <el-col :md="8">
              <el-form-item :label="$t('order.passengerName')" required>
                <el-input
                  v-model="pax.name"
                  :placeholder="$t('order.namePlaceholder')"
                  :disabled="pax.isSelf"
                />
              </el-form-item>
            </el-col>
            <el-col :md="8">
              <el-form-item :label="$t('member.gender')" required>
                <el-radio-group v-model="pax.gender" :disabled="pax.isSelf">
                  <el-radio value="MALE">{{ $t('member.male') }}</el-radio>
                  <el-radio value="FEMALE">{{ $t('member.female') }}</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :md="8">
              <el-form-item :label="$t('member.idType')" required>
                <el-select v-model="pax.idType" style="width: 100%" :disabled="pax.isSelf">
                  <el-option :label="$t('member.idCard')" value="ID_CARD" />
                  <el-option :label="$t('member.passport')" value="PASSPORT" />
                  <el-option :label="$t('member.hmPassport')" value="HM_PASSPORT" />
                  <el-option :label="$t('member.taiwanPassport')" value="TAIWAN_PASSPORT" />
                  <el-option :label="$t('member.other')" value="OTHER" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :md="8">
              <el-form-item :label="$t('order.idNumber')" required>
                <el-input
                  v-model="pax.idNumber"
                  :placeholder="$t('order.idNumberPlaceholder')"
                  :disabled="pax.isSelf"
                  @blur="onIdNumberBlur(pax)"
                />
              </el-form-item>
            </el-col>
            <el-col :md="8">
              <el-form-item :label="$t('member.phone')">
                <el-input v-model="pax.phone" :placeholder="$t('help.required') " />
              </el-form-item>
            </el-col>
            <el-col :md="8">
              <el-form-item :label="$t('member.email')">
                <el-input v-model="pax.email" :placeholder="$t('help.required') " />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16" v-if="pax.passengerType !== 'ADULT'">
            <el-col :md="8">
              <el-form-item :label="$t('member.birthday')" required>
                <el-date-picker v-model="pax.birthday" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :md="8">
              <el-form-item :label="$t('order.frequentFlyerNo')">
                <!-- 只读：号由服务端按旅客身份解析，前端不可编辑（可填等于可刷里程） -->
                <el-input
                  :model-value="pax.frequentFlyerNo"
                  disabled
                  :placeholder="$t('order.ffpNoPlaceholderAuto')"
                />
                <div class="ffp-hint">
                  {{
                    pax.frequentFlyerNo
                      ? $t('order.ffpNoBound', { no: pax.frequentFlyerNo })
                      : $t('order.ffpNoNone')
                  }}
                </div>
              </el-form-item>
            </el-col>
            <el-col :md="8">
              <el-form-item label-width="0" v-if="!pax.isSelf">
                <el-checkbox v-model="pax.saveTraveler">{{ $t('member.saveAsTraveler') }}</el-checkbox>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- SSR 特殊服务 -->
        <div class="ssr-section">
          <el-divider content-position="left">
            <el-icon><Service /></el-icon> 特殊服务（选填）
          </el-divider>
          <el-alert type="info" :closable="false" style="margin-bottom:12px;font-size:13px">
            免费服务随订单自动确认；付费服务需后台审核，通过后费用追加到订单中另行支付
          </el-alert>
          <el-checkbox-group v-model="pax.ssrSelections" @change="onSsrChange(idx)">
            <div v-for="group in ssrGroups" :key="group.category" class="ssr-group">
              <span class="ssr-group-label">{{ group.label }}</span>
              <div class="ssr-options">
                <el-checkbox
                  v-for="code in group.codes"
                  :key="code.code"
                  :label="code.code"
                  :value="code.code"
                >
                  {{ code.nameCn }}
                  <span v-if="!code.extraFee" class="ssr-free">(免费)</span>
                  <span v-else class="ssr-fee">(需审核 ¥{{ code.extraFee }})</span>
                  <el-tooltip :content="code.description" placement="top">
                    <el-icon style="margin-left:4px;color:var(--text-secondary)"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </el-checkbox>
              </div>
            </div>
          </el-checkbox-group>
        </div>
      </div>
    </div>

    <!-- Contact info -->
    <div class="contact-section card-shadow">
      <h3>{{ $t('order.contactInfo') }}</h3>
      <el-form :model="contact" label-width="80px">
        <el-row :gutter="16">
          <el-col :md="8">
            <el-form-item :label="$t('order.passengerName')" required>
              <el-input v-model="contact.name" :placeholder="$t('order.contactNamePlaceholder') " />
            </el-form-item>
          </el-col>
          <el-col :md="8">
            <el-form-item :label="$t('member.phone')" required>
              <el-input v-model="contact.phone" :placeholder="$t('order.contactPhonePlaceholder') " />
            </el-form-item>
          </el-col>
          <el-col :md="8">
            <el-form-item :label="$t('member.email')">
              <el-input v-model="contact.email" :placeholder="$t('help.required') " />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <!-- Price summary -->
    <div class="price-summary card-shadow">
      <h3>{{ $t('order.feeDetail') }}</h3>
      <div class="price-row">
        <span>{{ $t('order.fare') }} × {{ passengers.length }}{{ $t('flight.perPerson') }}</span>
        <span>¥{{ (orderStore.selectedCabin?.fare || 0) * passengers.length }}</span>
      </div>
      <div class="price-row">
        <span>{{ $t('order.tax') }}</span>
        <span>¥{{ (orderStore.selectedCabin?.tax || 0) * passengers.length }}</span>
      </div>
      <div v-if="ssrTotalFee > 0" class="price-row">
        <span>特殊服务（付费项需审核）</span>
        <span>¥{{ ssrTotalFee }}</span>
      </div>
      <div class="price-row total">
        <span>{{ $t('order.totalAmount') }}</span>
        <span class="total-amount">
          <em>¥{{ totalPrice }}</em>
        </span>
      </div>
    </div>

    <!-- Submit -->
    <div class="submit-bar">
      <div class="submit-price">
        <span>{{ $t('order.pay') }}: </span>
        <em>¥{{ totalPrice }}</em>
      </div>
      <el-button type="primary" size="large" :loading="submitting" @click="handleSubmit">
        {{ $t('order.submit') }}
      </el-button>
    </div>
    <!-- Traveler select dialog -->
    <el-dialog v-model="showTravelerDialog" :title="$t('member.travelers')" width="500px">
      <div v-if="travelerList.length === 0" class="empty-travelers">
        <el-empty :description="$t('member.noTravelers')" />
      </div>
      <div v-else>
        <div
          v-for="t in travelerList"
          :key="t.id"
          class="traveler-option"
          @click="selectTraveler(t)"
        >
          <div class="traveler-option-name">{{ t.name }}</div>
          <div class="traveler-option-info">
            <span>{{ t.idType }}: {{ t.idNumber }}</span>
            <span v-if="t.frequentFlyerNo">{{ $t('order.frequentFlyerNo') }}: {{ t.frequentFlyerNo }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Service, QuestionFilled } from '@element-plus/icons-vue'
import { getTravelers, addTraveler } from '@/api/member'
import { useOrderStore, type PassengerForm, type ContactInfo } from '@/stores/order'
import { useUserStore } from '@/stores/user'
import { useMappings } from '@/locales/mappings'
import { getSsrCodes } from '@/api/ssr'
import { checkIdNumber } from '@/utils/idNumber'

const router = useRouter()
const orderStore = useOrderStore()
const userStore = useUserStore()
const { t } = useI18n()
const { transCity, transAirline } = useMappings()

/**
 * 本人的常旅客号。订票时填在旅客行上，支付成功后后端按「航段距离 × 舱位系数 × 等级加成」
 * 给这个号对应的会员累积里程 —— 所以号码是里程唯一的挂钩点，不能让用户随便填错。
 */
const myMemberNo = computed(() => userStore.userInfo?.memberNo || '')

/**
 * 某行填写的证件号与本人实名一致时，带出本人常旅客号。
 * 判据是「证件号 + 证件类型」而不是姓名 —— 姓名会重名，证件号不会，
 * 而常旅客号本来就跟着证件号走。
 */
function syncFfpNo(pax: any) {
  const info = userStore.userInfo
  if (!pax || !myMemberNo.value || !info?.idNumber) return
  const sameDoc = (pax.idNumber || '').trim().toUpperCase() === info.idNumber.trim().toUpperCase()
  const sameType = (pax.idType || '').toUpperCase() === (info.idType || '').toUpperCase()
  if (sameDoc && sameType && !pax.frequentFlyerNo) {
    pax.frequentFlyerNo = myMemberNo.value
  }
}

const submitting = ref(false)
const travelerList = ref<any[]>([])
const showTravelerDialog = ref(false)
const selectedPaxIdx = ref(-1)

// ---- SSR ----
const ssrCodes = ref<any[]>([])
const categoryLabels: Record<string, string> = {
  WHEELCHAIR: '轮椅服务',
  MEAL: '特殊餐食',
  UNACCOMPANIED: '无陪儿童',
  PET: '宠物托运',
  BAGGAGE: '额外行李',
}

const ssrGroups = computed(() => {
  const groups: Record<string, any[]> = {}
  for (const c of ssrCodes.value) {
    const cat = c.category || 'OTHER'
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(c)
  }
  return Object.entries(groups).map(([category, codes]) => ({
    category,
    label: categoryLabels[category] || category,
    codes,
  }))
})

function onSsrChange(idx: number) {
  const pax = passengers[idx]
  orderStore.services = pax.ssrSelections.map((code: string) => ({
    type: 'SSR',
    code,
    quantity: 1,
    passengerIndex: idx,
  }))
}

async function loadSsrCodes() {
  try {
    const result = await getSsrCodes() as any
    ssrCodes.value = result.data || []
  } catch { console.warn('获取SSR代码失败') }
}

async function openTravelerSelect(idx: number) {
  selectedPaxIdx.value = idx
  try {
    const res = await getTravelers()
    travelerList.value = res.data || []
  } catch {
    ElMessage.error(t('error.serverError'))
  }
  showTravelerDialog.value = true
}

function selectTraveler(t: any) {
  const idx = selectedPaxIdx.value
  if (idx < 0 || idx >= passengers.length) return
  const pax = passengers[idx]
  // 本人行不接受档案覆盖（按钮已隐藏，这里再兜一层）
  if (pax.isSelf) return
  pax.name = t.name || ''
  pax.gender = t.gender || 'MALE'
  pax.idType = t.idType || 'ID_CARD'
  pax.idNumber = t.idNumber || ''
  pax.passengerType = t.passengerType || 'ADULT'
  pax.phone = t.phone || ''
  pax.frequentFlyerNo = t.frequentFlyerNo || ''
  pax.birthday = ''
  showTravelerDialog.value = false
}

const passengers = reactive<any[]>([{
  name: '',
  gender: 'MALE',
  birthday: '',
  idType: 'ID_CARD',
  idNumber: '',
  phone: '',
  email: '',
  frequentFlyerNo: '',
  passengerType: 'ADULT',
  saveTraveler: false,
  ssrSelections: [] as string[],
  // 第一位固定为本人：由实名档案自动带出且只读，不可删除
  isSelf: true,
}])

const contact = reactive<ContactInfo>({
  name: '',
  phone: '',
  email: '',
})

const totalPrice = computed(() => {
  const cabin = orderStore.selectedCabin
  if (!cabin) return 0
  // 只加免费SSR和常规服务，付费SSR审核通过后才追加
  return cabin.totalPrice * passengers.length + (orderStore.services || [])
    .filter(s => s.type !== 'SSR' || !ssrIsPaid(s.code))
    .reduce((sum, s) => sum + (getServiceUnitPrice(s.code) * s.quantity), 0)
})

const ssrTotalFee = computed(() => {
  // 只计入免费SSR，付费SSR审核通过后由后台追加
  return (orderStore.services || [])
    .filter(s => s.type === 'SSR' && !ssrIsPaid(s.code))
    .reduce((sum, s) => sum + (getServiceUnitPrice(s.code) * s.quantity), 0)
})

function ssrIsPaid(code: string): boolean {
  const found = ssrCodes.value.find((c: any) => c.code === code)
  return found && found.extraFee > 0
}

function getServiceUnitPrice(code: string): number {
  const found = ssrCodes.value.find((c: any) => c.code === code)
  if (found) return found.extraFee || 0
  // 非SSR服务走旧逻辑
  const priceMap: Record<string, number> = { BAG20: 100, BAG30: 200, INS_ALL: 30 }
  return priceMap[code] || 0
}

function addPassenger() {
  passengers.push({
    name: '',
    gender: 'MALE',
    birthday: '',
    idType: 'ID_CARD',
    idNumber: '',
    phone: '',
    email: '',
    frequentFlyerNo: '',
    passengerType: 'ADULT',
    saveTraveler: false,
    ssrSelections: [] as string[],
  })
}

function removePassenger(idx: number) {
  passengers.splice(idx, 1)
}

function formatDate(dateTime?: string) {
  if (!dateTime) return ''
  const d = new Date(dateTime)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 证件号失焦：先做格式提示，再尝试按证件号带出本人常旅客号 */
function onIdNumberBlur(pax: any) {
  if (!pax.idNumber) return
  const err = checkIdNumber(pax.idType, pax.idNumber)
  if (err) {
    ElMessage.warning(t(err))
    return
  }
  syncFfpNo(pax)
}

function validate(): boolean {
  for (const pax of passengers) {
    if (!pax.name) { ElMessage.warning(t('order.nameRequired') ); return false }
    // 证件号格式校验与后端同一套规则，避免填完才被驳回
    const docError = checkIdNumber(pax.idType, pax.idNumber)
    if (docError) { ElMessage.warning(t(docError) ); return false }
    if (pax.passengerType !== 'ADULT' && !pax.birthday) { ElMessage.warning(t('order.birthdayRequired') ); return false }
  }
  if (!contact.name) { ElMessage.warning(t('order.contactNameRequired') ); return false }
  if (!contact.phone) { ElMessage.warning(t('order.contactPhoneRequired') ); return false }
  return true
}

async function handleSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    const orderPayload = passengers.map(({ saveTraveler, ssrSelections, ...rest }) => rest)
    orderStore.passengers = orderPayload
    orderStore.contactInfo = { ...contact }
    const order = await orderStore.createOrder()

    // 提示付费SSR需审核
    const paidSsrCount = (orderStore.services || []).filter(s => s.type === 'SSR' && ssrIsPaid(s.code)).length
    if (paidSsrCount > 0) {
      ElMessage.info(`已提交 ${paidSsrCount} 项付费特殊服务，需后台审核通过后方可生效`)
    }

    for (const pax of passengers) {
      if (!pax.saveTraveler) continue
      await addTraveler({
        name: pax.name,
        gender: pax.gender,
        idType: pax.idType,
        idNumber: pax.idNumber,
        passengerType: pax.passengerType,
        phone: pax.phone,
        frequentFlyerNo: pax.frequentFlyerNo,
      }).catch((err) => { console.error('Save traveler failed', err) })
    }

    ElMessage.success(t('order.submitSuccess'))
    router.push(`/home/payment/${order.orderId}`)
  } catch {
    // error handled
  } finally {
    submitting.value = false
  }
}

/**
 * 第一位乘客固定为本人：姓名 / 证件类型 / 证件号由实名档案带出并置灰。
 * 其余乘客才是「代亲友购买」，可以自由填写或从常用旅客里选。
 */
function fillSelfPassenger() {
  const info = userStore.userInfo
  const self = passengers[0]
  if (!info || !self) return
  self.isSelf = true
  self.name = info.name || ''
  self.idType = info.idType || 'ID_CARD'
  self.idNumber = info.idNumber || ''
  self.frequentFlyerNo = info.memberNo || ''
  if (!contact.name) contact.name = info.name || ''
}

onMounted(async () => {
  if (!orderStore.selectedFlight || !orderStore.selectedCabin) {
    ElMessage.warning(t('order.selectFlightFirst') )
    router.push('/home/flights')
    return
  }
  loadSsrCodes()
  // 登录态下刷新页面时 store 可能是空的，先补一次资料
  if (userStore.isLoggedIn && !userStore.userInfo?.realnameVerified) {
    await userStore.fetchProfile()
  }
  // 实名是购票的前置条件：未实名先引导去个人中心，
  // 而不是让人把乘客信息一路填完、点提交才被后端驳回
  if (userStore.isLoggedIn && !userStore.userInfo?.realnameVerified) {
    try {
      await ElMessageBox.confirm(
        t('order.realnameRequiredTip'),
        t('order.realnameRequiredTitle'),
        {
          confirmButtonText: t('order.goRealname'),
          cancelButtonText: t('common.cancel'),
          type: 'warning',
        }
      )
      router.replace('/home/member')
    } catch {
      router.replace('/home/flights')
    }
    return
  }
  fillSelfPassenger()
})
</script>

<style scoped>
.booking-page {
  padding-top: 24px;
  padding-bottom: 100px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.flight-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.summary-route {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
}

.summary-route .airline {
  font-weight: 600;
  color: var(--text-primary);
}

.summary-route .flight-no {
  color: var(--text-secondary);
}

.summary-route .route {
  color: var(--color-primary);
  font-weight: 500;
}

.summary-route .date {
  color: var(--text-secondary);
}

.summary-cabin {
  text-align: right;
}

.summary-cabin .summary-price {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-danger);
  margin-left: 12px;
}

.passengers-section {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.passenger-card {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  padding: 16px;
  margin-bottom: 12px;
}

.pax-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.pax-label {
  font-weight: 600;
  color: var(--color-primary);
}

/* 本人行只读提示：让用户明白为什么这一栏改不动 */
.self-tip {
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: var(--radius-base);
  background: #f0f9eb;
  color: #529b2e;
  font-size: 12px;
  line-height: 1.5;
}

.contact-section {
  margin-bottom: 16px;
}

.contact-section h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.price-summary {
  margin-bottom: 16px;
}

.price-summary h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
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
  padding-top: 12px;
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
}

.total-amount em {
  font-style: normal;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-danger);
}

.submit-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 24px;
  padding: 16px 24px;
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-top);
  z-index: 100;
}

.submit-price {
  font-size: 14px;
  color: var(--text-secondary);
}

.submit-price em {
  font-style: normal;
  font-size: 28px;
  font-weight: 700;
  color: var(--color-danger);
}

.empty-travelers {
  padding: 40px 0;
}

.traveler-option {
  padding: 12px 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.traveler-option:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-light-9);
}

.traveler-option-name {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.traveler-option-info {
  font-size: 13px;
  color: var(--text-secondary);
  display: flex;
  gap: 16px;
}

.ssr-section { margin-top: 12px; }
.ssr-group { margin-bottom: 8px; }
.ssr-group-label { font-size: 13px; color: var(--text-secondary); margin-right: 12px; min-width: 70px; display: inline-block; }
.ssr-options { display: inline-flex; flex-wrap: wrap; gap: 8px; }
.ssr-fee { color: var(--color-danger); font-size: 12px; }
.ssr-free { color: var(--color-success); font-size: 12px; }

/* 常旅客号提示：占位符容易被忽略，用一行小字把「号从哪来」说清楚 */
.ffp-hint {
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
  margin-top: 4px;
  word-break: break-all;
}
</style>
