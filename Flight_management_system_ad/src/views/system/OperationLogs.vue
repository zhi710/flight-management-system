<template>
  <div class="page-container">
    <div class="card-panel">
      <el-form :inline="true" :model="searchForm" class="search-bar">
        <el-form-item label="操作人">
          <el-input v-model="searchForm.operator" placeholder="操作人" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="模块">
          <el-input v-model="searchForm.module" placeholder="模块" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-input v-model="searchForm.action" placeholder="操作类型" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="searchForm.dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">操作日志</div>
      </div>

      <el-table scrollbar-always-on v-loading="loading" :data="logList" stripe>
        <el-table-column prop="logId" label="日志ID" width="130" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column prop="module" label="模块" width="90" />
        <el-table-column prop="action" label="操作" width="90" />
        <el-table-column prop="target" label="操作对象" width="120" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="120" />
        <el-table-column label="时间" width="140">
          <template #default="{ row }"><span class="tnum">{{ fmtDateTime(row.createdAt) }}</span></template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus"
import { ref, reactive, onMounted } from 'vue'
import { getOperationLogs } from '@/api/system'
import { fmtDateTime } from '@/utils/format'

const loading = ref(false)
const logList = ref<any[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const searchForm = reactive({ operator: '', module: '', action: '', dateRange: [] as string[] })

async function fetchList() {
  loading.value = true
  try {
    const params: any = {
      operator: searchForm.operator || undefined,
      module: searchForm.module || undefined,
      action: searchForm.action || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    }
    if (searchForm.dateRange?.length) {
      params.startDate = searchForm.dateRange[0]
      params.endDate = searchForm.dateRange[1]
    }
    const data = await getOperationLogs(params) as any
    logList.value = data.list || data || []
    pagination.total = data.pagination?.total || 0
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    logList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function resetSearch() {
  Object.assign(searchForm, { operator: '', module: '', action: '', dateRange: [] })
  handleSearch()
}

onMounted(fetchList)
</script>

<style scoped>
/* .pagination-wrap 已收进全局样式 */
</style>
