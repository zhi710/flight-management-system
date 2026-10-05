<template>
  <div class="page-container">
    <div class="card-panel">
      <el-form :inline="true" :model="searchForm" class="search-bar">
        <el-form-item label="姓名">
          <el-input v-model="searchForm.name" placeholder="旅客姓名" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="证件号">
          <el-input v-model="searchForm.idNumber" placeholder="证件号码" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="航班号">
          <el-input v-model="searchForm.flightNo" placeholder="航班号" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="PNR">
          <el-input v-model="searchForm.pnr" placeholder="订座编号" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="searchForm.phone" placeholder="手机号" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">旅客列表</div>
        <div class="text-secondary">点击任意行查看旅客详情</div>
      </div>

      <el-table
        scrollbar-always-on
        v-loading="loading"
        :data="passengerList"
        stripe
        class="passenger-table"
        @row-click="openDetail"
      >
        <!-- 原先 11 列合计 1230px，1440px 的容器只有 1146px，必然横向滚动。
             这里把三组"天然成对"的字段各合成一列，列数 11 → 8，总宽降到 925px -->
        <el-table-column label="旅客ID" width="110">
          <template #default="{ row }">
            <span class="text-mono tnum" :title="row.passengerId">{{ shortId(row.passengerId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="姓名 / 性别" width="100">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main">{{ row.name }}</div>
              <div class="cell-stack__sub">{{ GENDER_MAP[row.gender] || row.gender }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="证件号" width="165">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ row.idNumber }}</div>
              <div class="cell-stack__sub">{{ ID_TYPE_MAP[row.idType] || row.idType }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="110" />
        <el-table-column label="航班号 / PNR" width="110">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ row.flightNo }}</div>
              <div class="cell-stack__sub tnum">{{ row.pnr }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="会员等级" width="95">
          <template #default="{ row }">
            <!-- 会员标签走「浅底深字」：底色与字色都来自 --tier-* 令牌 -->
            <el-tag
              v-if="MEMBER_LEVEL_MAP[row.memberLevel]"
              :style="{
                background: MEMBER_LEVEL_MAP[row.memberLevel]!.bg,
                color: MEMBER_LEVEL_MAP[row.memberLevel]!.fg,
                border: 'none',
              }"
              size="small"
            >
              {{ MEMBER_LEVEL_MAP[row.memberLevel]!.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="黑名单" width="80">
          <template #default="{ row }">
            <el-tag :type="row.blacklist ? 'danger' : 'success'" size="small">
              {{ row.blacklist ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近行程" min-width="155" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.recentTrips?.length">
              {{ row.recentTrips[0].flightNo }} {{ row.recentTrips[0].route }} {{ row.recentTrips[0].date }}
            </span>
            <span v-else class="text-sub">暂无</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 旅客详情抽屉 -->
    <el-drawer v-model="detailVisible" size="620px" :title="detail?.name ? `${detail.name} · 旅客详情` : '旅客详情'" destroy-on-close>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="detail-head">
            <span class="detail-name">{{ detail.name }}</span>
            <el-tag
              v-if="detail.memberLevel && MEMBER_LEVEL_MAP[detail.memberLevel]"
              :style="{
                background: MEMBER_LEVEL_MAP[detail.memberLevel]!.bg,
                color: MEMBER_LEVEL_MAP[detail.memberLevel]!.fg,
                border: 'none',
              }"
              size="small"
            >
              {{ MEMBER_LEVEL_MAP[detail.memberLevel]!.label }}
            </el-tag>
            <el-tag :type="detail.blacklist ? 'danger' : 'success'" size="small">
              {{ detail.blacklist ? '黑名单' : '正常' }}
            </el-tag>
          </div>

          <div class="section-title">基本信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="旅客ID">
              <span class="text-mono tnum">{{ detail.passengerId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="性别">{{ GENDER_MAP[detail.gender] || detail.gender || '—' }}</el-descriptions-item>
            <el-descriptions-item label="证件类型">{{ ID_TYPE_MAP[detail.idType] || detail.idType || '—' }}</el-descriptions-item>
            <el-descriptions-item label="证件号"><span class="tnum">{{ detail.idNumber || '—' }}</span></el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detail.phone || '—' }}</el-descriptions-item>
            <el-descriptions-item label="出生日期">{{ detail.birthday || '—' }}</el-descriptions-item>
            <el-descriptions-item label="旅客类型">{{ PASSENGER_TYPE_MAP[detail.passengerType] || detail.passengerType || '—' }}</el-descriptions-item>
            <el-descriptions-item label="常旅客号"><span class="text-mono">{{ detail.frequentFlyerNo || '—' }}</span></el-descriptions-item>
            <el-descriptions-item label="票号"><span class="tnum">{{ detail.ticketNo || '—' }}</span></el-descriptions-item>
            <el-descriptions-item label="座位 / 值机">{{ detail.seat || '—' }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-title mt">当前订单</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="订单号">{{ detail.orderNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="PNR"><span class="tnum">{{ detail.pnr || '—' }}</span></el-descriptions-item>
            <el-descriptions-item label="航班号">{{ detail.flightNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="(ORDER_STATUS_MAP[detail.orderStatus]?.type as any) || 'info'" size="small">
                {{ ORDER_STATUS_MAP[detail.orderStatus]?.label || detail.orderStatus || '—' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title mt">历史行程（{{ history.length }}）</div>
          <el-table v-if="history.length" scrollbar-always-on :data="history" size="small" stripe>
            <el-table-column label="航班 / 日期" width="116">
              <template #default="{ row }">
                <div class="cell-stack">
                  <div class="cell-stack__main tnum">{{ row.flightNo || '—' }}</div>
                  <div class="cell-stack__sub">{{ row.date || '—' }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="航线" min-width="96">
              <template #default="{ row }">{{ row.route || '—' }}</template>
            </el-table-column>
            <el-table-column label="舱位" width="72">
              <template #default="{ row }">{{ CABIN_CLASS_MAP[row.cabinClass] || row.cabinClass || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="86">
              <template #default="{ row }">
                <el-tag :type="(ORDER_STATUS_MAP[row.status]?.type as any) || 'info'" size="small">
                  {{ ORDER_STATUS_MAP[row.status]?.label || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="票号" min-width="132">
              <template #default="{ row }"><span class="tnum">{{ row.ticketNo || '—' }}</span></template>
            </el-table-column>
          </el-table>
          <div v-else class="text-secondary detail-empty">该旅客暂无其他行程记录</div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getPassengerList, getPassengerDetail } from '@/api/passengers'
import {
  GENDER_MAP,
  ID_TYPE_MAP,
  MEMBER_LEVEL_MAP,
  ORDER_STATUS_MAP,
  CABIN_CLASS_MAP,
  PASSENGER_TYPE_MAP,
} from '@/utils/constants'
import { useTable } from '@/composables/useTable'
import { shortId } from '@/utils/format'

/**
 * 分页 / 加载态 / 查询条件的样板（原先 14 行手写逻辑）收进 useTable，
 * 这里只声明"查什么接口、条件初值是什么"。
 * 解构时做了重命名，因此模板不需要做任何改动。
 */
const {
  loading,
  list: passengerList,
  pagination,
  query: searchForm,
  search: handleSearch,
  reset: resetSearch,
  load: fetchList,
} = useTable({
  api: getPassengerList,
  initialQuery: { name: '', idNumber: '', flightNo: '', pnr: '', phone: '' },
})

// ---- 旅客详情抽屉 ----
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>(null)
const history = computed<any[]>(() => detail.value?.history || [])

async function openDetail(row: any) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getPassengerDetail(row.passengerId) as any
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '加载旅客详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}
</script>

<style scoped>
/* .pagination-wrap / .text-secondary / .section-title 已收进全局样式 */

.passenger-table :deep(.el-table__row) {
  cursor: pointer;
}

.detail-body {
  min-height: 200px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.mt {
  margin-top: 20px;
}

.detail-empty {
  font-size: 13px;
  padding: 12px 0;
}
</style>
