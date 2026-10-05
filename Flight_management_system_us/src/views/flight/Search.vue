<template>
  <div class="search-page page-container">
    <!-- Compact search bar -->
    <div class="search-bar card-shadow">
      <div class="bar-fields">
        <div class="bar-field">
          <span class="label">{{ $t('home.from') }}</span>
          <CityPicker v-model="search.departure" size="small" :placeholder="$t('home.selectCity')" />
        </div>
        <el-icon class="swap-icon" @click="flightStore.swapCities()"><Sort /></el-icon>
        <div class="bar-field">
          <span class="label">{{ $t('home.to') }}</span>
          <CityPicker v-model="search.arrival" size="small" :placeholder="$t('home.selectCity')" />
        </div>
        <div class="bar-field">
          <span class="label">{{ $t('common.date') }}</span>
          <el-date-picker
            v-model="search.departDate"
            type="date"
            size="small"
            value-format="YYYY-MM-DD"
            :disabled-date="disablePastDate"
            style="width: 140px"
          />
        </div>
        <div class="bar-field">
          <span class="label">{{ $t('home.passengers') }}</span>
          <span class="pax-text">{{ search.adults }}{{ $t('home.adults') }}</span>
        </div>
        <el-button type="primary" size="small" :loading="flightStore.loading" @click="doSearch">
          <el-icon><Search /></el-icon> {{ $t('common.search') }}
        </el-button>
      </div>
    </div>

    <!-- Filters & Sort -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-checkbox v-model="search.directOnly" @change="doSearch">{{ $t('home.directOnly') }}</el-checkbox>
        <el-select v-model="search.cabinClass" size="small" style="width: 100px" @change="doSearch">
          <el-option :label="$t('home.economy')" value="ECONOMY" />
          <el-option :label="$t('home.business')" value="BUSINESS" />
          <el-option :label="$t('home.first')" value="FIRST" />
        </el-select>
      </div>
      <div class="sort-tabs">
        <span
          v-for="s in sortOptions"
          :key="s.value"
          :class="['sort-tab', { active: currentSort === s.value }]"
          @click="handleSort(s.value)"
        >{{ s.label }}</span>
      </div>
    </div>

    <!-- Results -->
    <div class="results-area">
      <div v-if="flightStore.loading" class="loading-state">
        <el-skeleton :rows="5" animated />
      </div>
      <template v-else-if="flightStore.searchResults.length > 0">
        <FlightCard
          v-for="flight in flightStore.searchResults"
          :key="flight.flightId"
          :flight="flight"
          show-cabins
          @select="goToDetail(flight)"
          @book="handleBook"
        />
      </template>
      <div v-else class="empty-state">
        <el-empty :description="$t('flight.noResults')">
          <el-button type="primary" @click="$router.push('/home')">{{ $t('flight.tryOtherDate')  }}</el-button>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useFlightStore } from '@/stores/flight'
import CityPicker from '@/components/CityPicker.vue'
import FlightCard from '@/components/FlightCard.vue'

const router = useRouter()
const route = useRoute()
const flightStore = useFlightStore()
const { t } = useI18n()
const search = flightStore.searchParams

const currentSort = ref('RECOMMEND')
const sortOptions = computed(() => [
  { label: t('flight.recommend'), value: 'RECOMMEND' },
  { label: t('flight.priceSort'), value: 'PRICE' },
  { label: t('flight.timeSort'), value: 'TIME' },
  { label: t('flight.durationSort'), value: 'DURATION' },
])

function disablePastDate(date: Date) {
  return date.getTime() < Date.now() - 86400000
}

/** 获取今天的日期字符串 YYYY-MM-DD */
function todayStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function doSearch() {
  if (!search.departure || !search.arrival) {
    ElMessage.warning(t('flight.searchRequired') )
    return
  }
  // 日期为空时默认今天
  if (!search.departDate) {
    search.departDate = todayStr()
  }
  try {
    await flightStore.searchFlights()
  } catch {
    ElMessage.error(t('flight.searchFailed') || '搜索失败，请重试')
  }
}

function handleSort(value: string) {
  currentSort.value = value
  // Re-search with sort params
  flightStore.searchFlights()
}

function goToDetail(flight: any) {
  router.push(`/home/flights/${flight.flightId}`)
}

function handleBook(data: { flight: any; cabin: any }) {
  router.push(`/home/flights/${data.flight.flightId}`)
}

onMounted(() => {
  // 从 URL query 参数读取出发/到达/日期（来自热门航线点击）
  const qDep = route.query.departure as string
  const qArr = route.query.arrival as string
  const qDate = route.query.date as string
  if (qDep) search.departure = qDep
  if (qArr) search.arrival = qArr
  if (qDate) search.departDate = qDate

  // 有出发和到达但没有日期时，默认今天
  if (search.departure && search.arrival && !search.departDate) {
    search.departDate = todayStr()
  }

  // 自动搜索
  if (search.departure && search.arrival && search.departDate) {
    if (flightStore.searchResults.length === 0) {
      doSearch()
    }
  }
})
</script>

<style scoped>
.search-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.search-bar {
  margin-bottom: 16px;
}

.bar-fields {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.bar-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bar-field .label {
  font-size: 12px;
  color: var(--text-secondary);
}

.pax-text {
  font-size: 14px;
  color: var(--text-primary);
  padding: 5px 0;
}

.swap-icon {
  cursor: pointer;
  color: var(--text-secondary);
  transition: color 0.2s;
  margin-bottom: 14px;
}

.swap-icon:hover {
  color: var(--color-primary);
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 0;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sort-tabs {
  display: flex;
  gap: 0;
  background: var(--bg-card);
  border-radius: 6px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.sort-tab {
  padding: 8px 16px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.sort-tab.active {
  background: var(--color-primary);
  color: var(--text-inverse);
}

.sort-tab:hover:not(.active) {
  background: rgba(var(--color-primary-bright-rgb), 0.06);
}

.loading-state {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: 24px;
}

.empty-state {
  padding: 80px 0;
  text-align: center;
}
</style>
