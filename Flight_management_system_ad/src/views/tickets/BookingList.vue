<template>
  <div class="page-container">
    <div class="card-panel">
      <el-form :inline="true" :model="searchForm" class="search-bar">
        <el-form-item label="PNR">
          <el-input v-model="searchForm.pnr" placeholder="PNR编号" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="旅客姓名">
          <el-input v-model="searchForm.passengerName" placeholder="姓名" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="航班号">
          <el-input v-model="searchForm.flightNo" placeholder="航班号" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">订座管理</div>
      </div>

      <el-table scrollbar-always-on v-loading="loading" :data="bookingList" stripe>
        <!-- 原先 10 列合计 1020px，1280px 的容器只有 986px。把两组"订的是哪一趟"的字段合并：
             PNR+航班号、出发+到达。列数 10 → 8，总宽 820px -->
        <el-table-column label="PNR / 航班号" width="110">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main text-mono tnum">{{ row.pnr }}</div>
              <div class="cell-stack__sub tnum">{{ row.flightNo }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="passengerName" label="旅客姓名" width="95" />
        <el-table-column label="出发 / 到达" width="115">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ row.departure }}</div>
              <div class="cell-stack__sub tnum">{{ row.arrival }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="cabinClass" label="舱位" width="80">
          <template #default="{ row }">{{ CABIN_CLASS_MAP[row.cabinClass] || row.cabinClass }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="(ORDER_STATUS_MAP[row.status]?.type as any) || 'info'" size="small">
              {{ ORDER_STATUS_MAP[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalPrice" label="总价" width="90">
          <template #default="{ row }"><span class="tnum">¥{{ row.totalPrice }}</span></template>
        </el-table-column>
        <el-table-column label="预订时间" width="140">
          <template #default="{ row }"><span class="tnum">{{ fmtDateTime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewDetail(row)">详情</el-button>
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

    <!-- 订座详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="订座详情" width="720px">
      <el-descriptions v-if="bookingDetail" :column="2" border>
        <el-descriptions-item label="PNR">{{ bookingDetail.pnr }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="(ORDER_STATUS_MAP[bookingDetail.status]?.type as any)">
            {{ ORDER_STATUS_MAP[bookingDetail.status]?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="航班号">{{ bookingDetail.flightNo }}</el-descriptions-item>
        <el-descriptions-item label="舱位">{{ CABIN_CLASS_MAP[bookingDetail.cabinClass] }}</el-descriptions-item>
        <el-descriptions-item label="出发">{{ bookingDetail.departure }}</el-descriptions-item>
        <el-descriptions-item label="到达">{{ bookingDetail.arrival }}</el-descriptions-item>
        <el-descriptions-item label="旅客">{{ bookingDetail.passengerName }}</el-descriptions-item>
        <el-descriptions-item label="证件">{{ bookingDetail.idNumber }}</el-descriptions-item>
        <el-descriptions-item label="总价">¥{{ bookingDetail.totalPrice }}</el-descriptions-item>
        <el-descriptions-item label="预订时间">{{ bookingDetail.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus"
import { ref, reactive, onMounted } from 'vue'
import { getBookingList, getBookingDetail } from '@/api/tickets'
import { ORDER_STATUS_MAP, CABIN_CLASS_MAP } from '@/utils/constants'
import { fmtDateTime } from '@/utils/format'

const loading = ref(false)
const bookingList = ref<any[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const searchForm = reactive({ pnr: '', passengerName: '', flightNo: '' })

const detailDialogVisible = ref(false)
const bookingDetail = ref<any>(null)

async function fetchList() {
  loading.value = true
  try {
    const data = await getBookingList({ ...searchForm, page: pagination.page, pageSize: pagination.pageSize }) as any
    bookingList.value = data.list || data || []
    pagination.total = data.pagination?.total || 0
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    bookingList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function resetSearch() {
  Object.assign(searchForm, { pnr: '', passengerName: '', flightNo: '' })
  handleSearch()
}

async function viewDetail(row: any) {
  try {
    bookingDetail.value = await getBookingDetail(row.pnr)
    detailDialogVisible.value = true
  } catch (err: any) {
    ElMessage.error(err?.message || '加载详情失败')
  }
}

onMounted(fetchList)
</script>

<style scoped>
/* .pagination-wrap 已收进全局样式 */
</style>
