<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">基础数据管理</div>
      </div>

      <el-tabs v-model="activeType" @tab-change="fetchData">
        <el-tab-pane label="机场管理" name="airports" />
        <el-tab-pane label="航线管理" name="routes" />
        <el-tab-pane label="机型管理" name="aircraft-types" />
        <el-tab-pane label="机队管理" name="fleet" />
        <el-tab-pane label="部门管理" name="departments" />
      </el-tabs>

      <div style="margin-bottom: 16px">
        <el-button type="primary" @click="showDialog()"><el-icon><Plus /></el-icon> 新增</el-button>
      </div>

      <el-table scrollbar-always-on :data="dataList" stripe v-loading="loading">
        <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" :min-width="col.width || 120" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="editingItem ? '编辑' : '新增'" width="560px" destroy-on-close>
      <el-form :model="formData" label-width="88px">
        <el-form-item v-for="field in formFields" :key="field.prop" :label="field.label">
          <el-input v-model="formData[field.prop]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitData">确定</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认对话框 -->
    <el-dialog v-model="deleteVisible" title="提示" width="420px" destroy-on-close :close-on-click-modal="false">
      <div style="text-align:center">
        <el-icon :size="44" color="var(--color-warning)"><WarningFilled /></el-icon>
        <p style="font-size:15px;margin:12px 0 4px">确定删除该数据？</p>
      </div>
      <template #footer>
        <div style="display:flex;justify-content:center;gap:12px">
          <el-button @click="deleteVisible = false">取消</el-button>
          <el-button type="danger" :loading="deleteLoading" @click="confirmDelete">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMasterData, createMasterData, updateMasterData, deleteMasterData } from '@/api/system'

const loading = ref(false)
const activeType = ref('airports')
const dataList = ref<any[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const dialogVisible = ref(false)
const editingItem = ref<any>(null)
const formData = reactive<Record<string, string>>({})

// 不同类型的列定义
const typeIdFields: Record<string, string> = {
  airports: 'id',
  routes: 'id',
  'aircraft-types': 'id',
  fleet: 'id',
  departments: 'id',
}

const typeColumns: Record<string, { prop: string; label: string; width?: number }[]> = {
  airports: [
    { prop: 'code', label: '代码', width: 80 },
    { prop: 'name', label: '名称' },
    { prop: 'city', label: '城市', width: 100 },
    { prop: 'country', label: '国家', width: 80 },
  ],
  routes: [
    { prop: 'departure', label: '出发', width: 80 },
    { prop: 'arrival', label: '到达', width: 80 },
    { prop: 'distance', label: '距离(km)', width: 100 },
  ],
  'aircraft-types': [
    { prop: 'code', label: '代码', width: 80 },
    { prop: 'name', label: '名称' },
    { prop: 'manufacturer', label: '制造商', width: 120 },
    { prop: 'seats', label: '座位数', width: 80 },
  ],
  fleet: [
    { prop: 'registration', label: '注册号', width: 100 },
    { prop: 'aircraftTypeId', label: '机型ID', width: 80 },
    { prop: 'airlineId', label: '航司ID', width: 80 },
    { prop: 'status', label: '状态', width: 80 },
  ],
  departments: [
    { prop: 'id', label: 'ID', width: 80 },
    { prop: 'name', label: '名称' },
    { prop: 'description', label: '描述' },
  ],
}

const columns = computed(() => typeColumns[activeType.value] || [])
const formFields = computed(() => columns.value.map(c => ({ prop: c.prop, label: c.label })))

async function fetchData() {
  loading.value = true
  try {
    const data = await getMasterData(activeType.value, { page: pagination.page, pageSize: pagination.pageSize }) as any
    dataList.value = data.list || data || []
    pagination.total = data.pagination?.total || 0
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
    dataList.value = []
  } finally {
    loading.value = false
  }
}

function showDialog(row?: any) {
  editingItem.value = row || null
  // 清空表单
  Object.keys(formData).forEach(k => delete formData[k])
  if (row) {
    columns.value.forEach(c => { formData[c.prop] = row[c.prop] || '' })
  }
  dialogVisible.value = true
}

function getIdField(type: string): string {
  return typeIdFields[type] || 'code'
}

function getRowId(row: any): string {
  const field = getIdField(activeType.value)
  return row[field] || row.id || ''
}

async function submitData() {
  try {
    const payload = { ...formData }
    if (editingItem.value) {
      await updateMasterData(activeType.value, getRowId(editingItem.value), payload)
      ElMessage.success('更新成功')
    } else {
      await createMasterData(activeType.value, payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (err: any) {
    ElMessage.error(err?.message || '保存数据失败')
  }
}

const deleteVisible = ref(false)
const deleteLoading = ref(false)
const deleteRow = ref<any>(null)

function handleDelete(row: any) {
  deleteRow.value = row
  deleteVisible.value = true
}

async function confirmDelete() {
  deleteLoading.value = true
  try {
    await deleteMasterData(activeType.value, getRowId(deleteRow.value))
    ElMessage.success('删除成功')
    deleteVisible.value = false
    fetchData()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '删除数据失败')
  } finally {
    deleteLoading.value = false
  }
}


onMounted(fetchData)
</script>

<style scoped>
/* .pagination-wrap 已收进全局样式 */
</style>
