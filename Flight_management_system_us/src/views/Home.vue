<template>
  <div class="home-page">
    <!-- Hero Section with Search -->
    <section class="hero">
      <!-- 背景轮播：自包含的品牌渐变 + 几何点缀，不依赖任何图片资源 -->
      <el-carousel
        ref="carouselRef"
        class="hero-carousel"
        :interval="5000"
        arrow="hover"
        indicator-position="none"
        @change="onCarouselChange"
      >
        <el-carousel-item v-for="slide in slides" :key="slide.id">
          <div class="carousel-slide" :class="`slide-theme-${slide.theme}`">
            <!-- 后台配置的轮播图；加载失败时透出下层品牌渐变，不会白屏 -->
            <div
              v-if="slide.imageUrl"
              class="slide-photo"
              :style="{ backgroundImage: `url(${slide.imageUrl})` }"
            ></div>
            <div v-if="slide.imageUrl" class="slide-photo-tint"></div>
            <!-- 无图时用几何点缀补足画面 -->
            <template v-else>
              <div class="slide-grid"></div>
              <div class="slide-deco deco-arc-1"></div>
              <div class="slide-deco deco-arc-2"></div>
              <div class="slide-deco deco-arc-3"></div>
              <div class="slide-plane"><el-icon><Promotion /></el-icon></div>
            </template>
          </div>
        </el-carousel-item>
      </el-carousel>

      <!-- 左侧压暗遮罩，保证标题可读 -->
      <div class="hero-veil"></div>

      <!-- 轮播指示器：进度条式 -->
      <div class="carousel-tabs" v-if="slides.length > 1">
        <span
          v-for="(slide, idx) in slides"
          :key="slide.id"
          :class="['carousel-tab', { active: activeIndex === idx }]"
          @click="goToSlide(idx)"
        >
          <span class="tab-progress" v-if="activeIndex === idx"></span>
        </span>
      </div>

      <div class="hero-content">
        <div class="hero-copy">
          <h1 class="hero-title">{{ $t('home.heroTitle')  }}</h1>
          <p class="hero-subtitle">{{ $t('home.heroSubtitle')  }}</p>
          <!-- 随轮播切换的目的地提示 -->
          <div class="hero-chip" v-if="slides[activeIndex]?.title">
            <span class="chip-dot"></span>
            <strong>{{ slides[activeIndex]?.title }}</strong>
            <span class="chip-sub" v-if="slides[activeIndex]?.subtitle">{{ slides[activeIndex]?.subtitle }}</span>
          </div>
        </div>

        <!-- Search Card -->
        <div class="search-card">
          <!-- Trip type tabs -->
          <div class="trip-types">
            <span
              v-for="t in tripTypes"
              :key="t.value"
              :class="['trip-type', { active: search.tripType === t.value }]"
              @click="search.tripType = t.value"
            >{{ t.label }}</span>
          </div>

          <el-form :model="search" class="search-form">
            <div class="form-row">
              <!-- Departure -->
              <el-form-item class="form-item city-item">
                <template #label>{{ $t('home.from') }}</template>
                <CityPicker v-model="search.departure" :placeholder="$t('home.selectCity')" />
              </el-form-item>

              <!-- Swap -->
              <div class="swap-btn" @click="handleSwap" :title="$t('home.swapCities')">
                <el-icon><Sort /></el-icon>
              </div>

              <!-- Arrival -->
              <el-form-item class="form-item city-item">
                <template #label>{{ $t('home.to') }}</template>
                <CityPicker v-model="search.arrival" :placeholder="$t('home.selectCity')" />
              </el-form-item>

              <!-- Depart Date -->
              <el-form-item class="form-item date-item">
                <template #label>{{ $t('home.departDate') }}</template>
                <el-date-picker
                  v-model="search.departDate"
                  type="date"
                  :placeholder="$t('home.selectDate')"
                  value-format="YYYY-MM-DD"
                  :disabled-date="disablePastDate"
                  style="width: 100%"
                />
              </el-form-item>

              <!-- Return Date (round trip) -->
              <el-form-item v-if="search.tripType === 'ROUND'" class="form-item date-item">
                <template #label>{{ $t('home.returnDate') }}</template>
                <el-date-picker
                  v-model="search.returnDate"
                  type="date"
                  :placeholder="$t('home.selectDate')"
                  value-format="YYYY-MM-DD"
                  :disabled-date="disableReturnDate"
                  style="width: 100%"
                />
              </el-form-item>

              <!-- Passengers -->
              <el-form-item class="form-item pax-item">
                <template #label>{{ $t('home.passengers') }}</template>
                <el-popover trigger="click" :width="280">
                  <template #reference>
                    <el-input :model-value="paxText" readonly>
                      <template #prefix><el-icon><User /></el-icon></template>
                    </el-input>
                  </template>
                  <div class="pax-selector">
                    <div class="pax-row">
                      <span>{{ $t('home.adults') }} ({{ $t('home.adultDesc') }})</span>
                      <el-input-number v-model="search.adults" :min="1" :max="9" size="small" />
                    </div>
                    <div class="pax-row">
                      <span>{{ $t('home.children') }} ({{ $t('home.childDesc') }})</span>
                      <el-input-number v-model="search.children" :min="0" :max="9" size="small" />
                    </div>
                    <div class="pax-row">
                      <span>{{ $t('home.infants') }} ({{ $t('home.infantDesc') }})</span>
                      <el-input-number v-model="search.infants" :min="0" :max="9" size="small" />
                    </div>
                  </div>
                </el-popover>
              </el-form-item>

              <!-- Cabin Class -->
              <el-form-item class="form-item cabin-item">
                <template #label>{{ $t('home.cabinClass') }}</template>
                <el-select v-model="search.cabinClass" style="width: 100%">
                  <el-option :label="$t('home.economy')" value="ECONOMY" />
                  <el-option :label="$t('home.business')" value="BUSINESS" />
                  <el-option :label="$t('home.first')" value="FIRST" />
                </el-select>
              </el-form-item>
            </div>

            <div class="form-actions">
              <el-checkbox v-model="search.directOnly">{{ $t('home.directOnly') }}</el-checkbox>
              <el-button type="primary" size="large" round :loading="searching" @click="handleSearch">
                <el-icon><Search /></el-icon> {{ $t('home.searchFlights') }}
              </el-button>
            </div>
          </el-form>
        </div>
      </div>
    </section>

    <!-- Hot Routes -->
    <section class="section page-container">
      <div class="section-header">
        <h2>🔥 {{ $t('home.hotRoutes') }}</h2>
        <p>{{ $t('home.hotRoutesDesc')  }}</p>
      </div>
      <div class="hot-routes">
        <div v-for="route in hotRoutes" :key="route.flightId" class="route-card" @click="goToSearch(route)">
          <div class="route-cities">
            <span>{{ transCity(route.departure?.city) }}</span>
            <el-icon><Right /></el-icon>
            <span>{{ transCity(route.arrival?.city) }}</span>
          </div>
          <div class="route-price">
            <span class="currency">¥</span>
            <span class="amount">{{ route.price }}</span>
            <span class="suffix">{{ $t('home.fromPrice') }}</span>
          </div>
          <div class="route-date">📅 {{ route.date }}</div>
        </div>
      </div>
    </section>

    <!-- Deals -->
    <section class="section page-container">
      <div class="section-header">
        <h2>✈️ {{ $t('home.specialOffers') }}</h2>
        <p>{{ $t('home.specialOffersDesc')  }}</p>
      </div>
      <div class="deals-grid">
        <div v-for="deal in deals" :key="deal.flightId" class="deal-card" @click="goToDealDetail(deal)">
          <div class="deal-route">
            <span>{{ transCity(deal.departure?.city) }}</span>
            <el-icon><Right /></el-icon>
            <span>{{ transCity(deal.arrival?.city) }}</span>
          </div>
          <div class="deal-price">
            <span class="currency">¥</span>
            <span class="amount">{{ deal.price }}</span>
          </div>
          <div class="deal-info">{{ deal.date }} · {{ transAirline(deal.airline?.name || deal.airline) }}</div>
        </div>
      </div>
    </section>

    <!-- Features -->
    <section class="section features-section">
      <div class="page-container">
        <div class="features-grid">
          <div class="feature-item">
            <el-icon :size="40" color="var(--color-primary)"><Search /></el-icon>
            <h3>{{ $t('home.featureSearch')  }}</h3>
            <p>{{ $t('home.featureSearchDesc')  }}</p>
          </div>
          <div class="feature-item">
            <el-icon :size="40" color="var(--color-primary)"><CreditCard /></el-icon>
            <h3>{{ $t('home.featurePayment')  }}</h3>
            <p>{{ $t('home.featurePaymentDesc')  }}</p>
          </div>
          <div class="feature-item">
            <el-icon :size="40" color="var(--color-primary)"><Ticket /></el-icon>
            <h3>{{ $t('home.featureCheckin')  }}</h3>
            <p>{{ $t('home.featureCheckinDesc')  }}</p>
          </div>
          <div class="feature-item">
            <el-icon :size="40" color="var(--color-primary)"><Bell /></el-icon>
            <h3>{{ $t('home.featureStatus')  }}</h3>
            <p>{{ $t('home.featureStatusDesc')  }}</p>
          </div>
        </div>
      </div>
    </section>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useFlightStore } from '@/stores/flight'
import CityPicker from '@/components/CityPicker.vue'
import { useMappings } from '@/locales/mappings'
import { getCarousels } from '@/api/carousel'
import { getImageUrl } from '@/utils/image'

const router = useRouter()
const flightStore = useFlightStore()
const { t } = useI18n()
const { transCity, transAirline } = useMappings()

const tripTypes = computed(() => [
  { label: t('home.oneWay'), value: 'ONEWAY' },
  { label: t('home.roundTrip'), value: 'ROUND' },
])

const search = ref({
  tripType: 'ONEWAY',
  departure: '',
  arrival: '',
  departDate: '',
  returnDate: '',
  adults: 1,
  children: 0,
  infants: 0,
  cabinClass: 'ECONOMY',
  directOnly: false,
})

const searching = ref(false)
const hotRoutes = ref<any[]>([])
const deals = ref<any[]>([])
const activeIndex = ref(0)
const carouselRef = ref<any>(null)
const carousels = ref<any[]>([])

/**
 * 轮播数据源：优先用后台「轮播图管理」配置的图片（/api/carousel），
 * 接口无数据时回退到三套品牌渐变主题——纯 CSS 绘制，保证首页永远有内容，
 * 也不会因为图片外链失效而白屏。
 */
const slides = computed(() => {
  if (carousels.value.length > 0) {
    return carousels.value.map((item: any, i: number) => ({
      id: item.id ?? `c-${i}`,
      theme: (i % 3) + 1,
      title: item.title || '',
      subtitle: item.subtitle || '',
      imageUrl: getImageUrl(item.imageUrl),
    }))
  }
  return [
    { id: 'fb-1', theme: 1, title: t('home.heroSlide1Title'), subtitle: t('home.heroSlide1Subtitle'), imageUrl: '' },
    { id: 'fb-2', theme: 2, title: t('home.heroSlide2Title'), subtitle: t('home.heroSlide2Subtitle'), imageUrl: '' },
    { id: 'fb-3', theme: 3, title: t('home.heroSlide3Title'), subtitle: t('home.heroSlide3Subtitle'), imageUrl: '' },
  ]
})

function onCarouselChange(index: number) {
  activeIndex.value = index
}

function goToSlide(index: number) {
  carouselRef.value?.setActiveItem(index)
  activeIndex.value = index
}

const paxText = computed(() => {
  const parts = []
  if (search.value.adults) parts.push(`${search.value.adults}${t('home.adults')}`)
  if (search.value.children) parts.push(`${search.value.children}${t('home.children')}`)
  if (search.value.infants) parts.push(`${search.value.infants}${t('home.infants')}`)
  return parts.join(' ')
})

function disablePastDate(date: Date) {
  return date.getTime() < Date.now() - 86400000
}

function disableReturnDate(date: Date) {
  if (!search.value.departDate) return disablePastDate(date)
  return date.getTime() < new Date(search.value.departDate).getTime()
}

function handleSwap() {
  const temp = search.value.departure
  search.value.departure = search.value.arrival
  search.value.arrival = temp
}

async function handleSearch() {
  if (!search.value.departure || !search.value.arrival || !search.value.departDate) {
    ElMessage.warning(t('home.searchRequired') )
    return
  }
  searching.value = true
  try {
    flightStore.searchParams = { ...search.value }
    await flightStore.searchFlights()
    router.push('/home/flights')
  } catch {
    // error handled by interceptor
  } finally {
    searching.value = false
  }
}

function goToSearch(route: any) {
  const dep = route.departure?.code || route.departure?.city
  const arr = route.arrival?.code || route.arrival?.city
  // 热门航线卡片背后是「该城市对未来可售的最低班次」（带具体日期），
  // 点击要搜卡片对应的那一天；若兜底成「今天」，当天已无班次时结果会是空的
  const date = route.date || ''
  const query: Record<string, string> = { departure: dep, arrival: arr }
  if (date) query.date = date
  router.push({ path: '/home/flights', query })
}

function goToDealDetail(deal: any) {
  router.push(`/home/flights/${deal.flightId}`)
}

onMounted(async () => {
  try {
    await Promise.all([
      flightStore.getHotRoutes().then(data => { hotRoutes.value = data }),
      flightStore.getDeals().then(data => { deals.value = data }),
      // 轮播图单独兜底：接口挂了/没配图都不影响首页其它区块。
      // 注意 request 拦截器返回的是整个信封 { code, message, data }，要再取一层 .data
      getCarousels()
        .then((res: any) => {
          carousels.value = Array.isArray(res?.data) ? res.data : []
          activeIndex.value = 0
        })
        .catch(() => { carousels.value = [] }),
    ])
  } catch {
    console.warn('Failed to load home page data')
  }
})
</script>

<style scoped>
/* Hero */
.hero {
  position: relative;
  overflow: hidden;
  min-height: 560px;
  display: flex;
  align-items: center;
}

.hero-carousel {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.hero-carousel :deep(.el-carousel__container) {
  height: 100% !important;
}

/* 左右切换按钮：毛玻璃圆钮，比原来的黑色半透明更轻 */
.hero-carousel :deep(.el-carousel__arrow) {
  width: 44px;
  height: 44px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(8px);
  border-radius: 50%;
  font-size: 18px;
  color: var(--text-on-dark-strong);
  transition: all 0.2s;
}

.hero-carousel :deep(.el-carousel__arrow:hover) {
  background: rgba(255, 255, 255, 0.34);
}

.hero-carousel :deep(.el-carousel__arrow--left) {
  left: 24px;
}

.hero-carousel :deep(.el-carousel__arrow--right) {
  right: 24px;
}

.carousel-slide {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}

/* 后台轮播图：铺满 slide，叠在品牌渐变之上（图裂了也不会白屏） */
.slide-photo {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* 照片上的品牌色薄纱：统一色调 + 提升左侧标题可读性 */
.slide-photo-tint {
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, rgba(var(--sky-900-rgb), 0.42) 0%, rgba(var(--color-primary-bright-rgb), 0.16) 60%, rgba(var(--color-primary-bright-rgb), 0) 100%);
  pointer-events: none;
}

/* 三套品牌渐变主题（首屏装饰区，刻意使用明快的装饰色阶 --sky-*，
   不参与"文字可读性"约束；正文文字在此之上另用白色） */
.slide-theme-1 {
  background: linear-gradient(135deg, var(--sky-500) 0%, var(--sky-700) 55%, var(--sky-900) 100%);
}

.slide-theme-2 {
  background: linear-gradient(120deg, var(--sky-400) 0%, var(--sky-500) 45%, var(--sky-800) 100%);
}

.slide-theme-3 {
  background: linear-gradient(150deg, var(--sky-600) 0%, var(--sky-500) 60%, var(--sky-300) 100%);
}

/* 点阵网格：左侧可见，向右淡出 */
.slide-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(255, 255, 255, 0.16) 1px, transparent 1px);
  background-size: 28px 28px;
  /* #000 在这里是"完全不透明"的开关（左实右透的渐隐），不是颜色，保持字面量 */
  -webkit-mask-image: linear-gradient(115deg, #000 0%, transparent 62%);
  mask-image: linear-gradient(115deg, #000 0%, transparent 62%);
}

/* 几何点缀：同心圆弧 */
.slide-deco {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.16);
  pointer-events: none;
}

.deco-arc-1 {
  width: 620px;
  height: 620px;
  right: -160px;
  top: -220px;
}

.deco-arc-2 {
  width: 420px;
  height: 420px;
  right: 40px;
  top: -120px;
  border-style: dashed;
  border-color: rgba(255, 255, 255, 0.12);
}

.deco-arc-3 {
  width: 900px;
  height: 900px;
  left: -320px;
  bottom: -560px;
}

/* 飞机剪影（复用 Element Plus Promotion 图标） */
.slide-plane {
  position: absolute;
  right: 7%;
  bottom: 9%;
  font-size: 220px;
  line-height: 1;
  color: rgba(255, 255, 255, 0.08);
  transform: rotate(-12deg);
  pointer-events: none;
}

/* 轮播指示器：右上角，与左侧 hero 文案同一视觉行，避开下方搜索卡 */
.carousel-tabs {
  position: absolute;
  top: 60px;
  right: 48px;
  z-index: 3;
  display: flex;
  gap: 8px;
}

.carousel-tab {
  width: 32px;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  overflow: hidden;
  transition: width 0.3s;
  position: relative;
}

.carousel-tab:hover {
  background: rgba(255, 255, 255, 0.6);
}

.carousel-tab.active {
  width: 48px;
  background: rgba(255, 255, 255, 0.3);
}

.carousel-tab .tab-progress {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  width: 0;
  background: var(--bg-on-dark-strong);
  border-radius: 2px;
  animation: tabProgress 5s linear forwards;
}

@keyframes tabProgress {
  from { width: 0; }
  to { width: 100%; }
}

/* 左侧压暗遮罩：让白色标题在任意渐变上都能读清 */
.hero-veil {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(
    100deg,
    rgba(var(--scrim-hero-rgb), 0.58) 0%,
    rgba(var(--scrim-hero-rgb), 0.3) 42%,
    rgba(var(--scrim-hero-rgb), 0) 72%
  );
  pointer-events: none;
}

.hero-content {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 56px 24px 64px;
  text-align: left;
}

.hero-copy {
  max-width: 640px;
  margin-bottom: 28px;
}

.hero-title {
  font-size: 40px;
  font-weight: 700;
  color: var(--text-on-dark-strong);
  margin-bottom: 10px;
  letter-spacing: 1px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.28);
}

.hero-subtitle {
  font-size: 17px;
  color: rgba(255, 255, 255, 0.86);
}

/* 随轮播切换的目的地提示 */
.hero-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  padding: 7px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
  backdrop-filter: blur(8px);
  color: var(--text-on-dark-strong);
  font-size: 13px;
  line-height: 1.4;
}

.chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--bg-on-dark-strong);
  flex-shrink: 0;
}

.hero-chip strong {
  font-weight: 600;
}

.chip-sub {
  color: rgba(255, 255, 255, 0.82);
}

/* Search Card：毛玻璃，压在 hero 渐变上 */
.search-card {
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-float);
  padding: 24px 32px;
  text-align: left;
}

.trip-types {
  display: flex;
  gap: 0;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
}

.trip-type {
  padding: 10px 24px;
  font-size: 15px;
  color: var(--text-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.trip-type.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 600;
}

.form-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.form-item {
  flex: 1;
  min-width: 150px;
}

.city-item {
  flex: 1.5;
}

.date-item {
  flex: 1.2;
}

.pax-item,
.cabin-item {
  flex: 0.8;
  min-width: 130px;
}

.swap-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-color);
  border-radius: 50%;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.2s;
  flex-shrink: 0;
  margin-bottom: 22px;
}

.swap-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: rgba(var(--color-primary-bright-rgb), 0.05);
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}

.pax-selector {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pax-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pax-row span {
  font-size: 14px;
  color: var(--text-primary);
}

/* Sections */
.section {
  padding: 48px 0;
}

.section-header {
  margin-bottom: 24px;
}

.section-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.section-header p {
  font-size: 14px;
  color: var(--text-secondary);
}

/* Hot Routes */
.hot-routes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.route-card {
  background: var(--bg-card);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-card);
  padding: 16px;
  cursor: pointer;
  transition: all 0.25s;
}

.route-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-3px);
}

.route-cities {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.route-price {
  margin-bottom: 6px;
}

.route-price .currency {
  font-size: 13px;
  color: var(--color-danger);
}

.route-price .amount {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-danger);
}

.route-price .suffix {
  font-size: 12px;
  color: var(--text-secondary);
}

.route-date {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Deals */
.deals-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.deal-card {
  background: var(--bg-card);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-card);
  padding: 16px;
  cursor: pointer;
  transition: all 0.25s;
}

.deal-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-3px);
}

.deal-card:hover {
  box-shadow: var(--shadow-hover);
}

.deal-route {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.deal-price {
  margin-bottom: 6px;
}

.deal-price .currency {
  font-size: 13px;
  color: var(--color-danger);
}

.deal-price .amount {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-danger);
}

.deal-info {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Features */
.features-section {
  background: var(--bg-card);
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
  text-align: center;
}

.feature-item h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 16px 0 8px;
}

.feature-item p {
  font-size: 14px;
  color: var(--text-secondary);
}

/* 平板竖屏：适度缩小标题与搜索区，避免溢出 */
@media (max-width: 992px) {
  .hero-title {
    font-size: 32px;
  }

  .hero-subtitle {
    font-size: 16px;
  }

  .search-card {
    padding: 20px;
  }

  .form-item {
    min-width: 140px;
  }
}

@media (max-width: 768px) {
  .hero {
    min-height: 460px;
  }

  .carousel-tabs {
    top: 24px;
    right: 20px;
  }

  .carousel-tab {
    width: 24px;
  }

  .carousel-tab.active {
    width: 36px;
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-content {
    padding-top: 64px;
  }

  .form-row {
    flex-direction: column;
  }

  .swap-btn {
    margin: 0 auto;
    transform: rotate(90deg);
  }

  .features-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 24px;
  }
}
</style>
