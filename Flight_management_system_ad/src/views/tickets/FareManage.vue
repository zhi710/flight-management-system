<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">票价管理</div>
        <div>
          <el-select v-model="filterAirline" placeholder="按航司筛选" clearable style="width: 180px; margin-right: 12px">
            <el-option v-for="a in airlines" :key="a.id" :label="`${a.code} - ${a.name}`" :value="a.id" />
          </el-select>
          <el-button type="primary" @click="showFareDialog()"><el-icon><Plus /></el-icon> 新增标准票价</el-button>
        </div>
      </div>

      <el-table scrollbar-always-on v-loading="loading" :data="fareList" stripe>
        <el-table-column label="航司" width="120">
          <template #default="{ row }">{{ row.airlineName }}</template>
        </el-table-column>
        <el-table-column label="舱位等级" width="100">
          <template #default="{ row }">{{ CABIN_CLASS_MAP[row.cabinClass] || row.cabinClass }}</template>
        </el-table-column>
        <el-table-column prop="fare" label="票价" width="100">
          <template #default="{ row }">¥{{ row.fare }}</template>
        </el-table-column>
        <el-table-column prop="tax" label="税费" width="80">
          <template #default="{ row }">¥{{ row.tax }}</template>
        </el-table-column>
        <el-table-column prop="baggage" label="行李额度" width="90" />
        <el-table-column prop="refundRule" label="退票规则" min-width="180" />
        <el-table-column prop="changeRule" label="改签规则" min-width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showFareDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="fareDialogVisible" :title="editingFare ? '编辑标准票价' : '新增标准票价'" width="560px" destroy-on-close>
      <el-form ref="fareFormRef" :model="fareForm" :rules="fareRules" label-width="88px">
        <el-form-item label="航司" prop="airlineId">
          <el-select v-model="fareForm.airlineId" placeholder="选择航司" :disabled="!!editingFare" style="width: 100%">
            <el-option v-for="a in airlines" :key="a.id" :label="`${a.code} - ${a.name}`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="舱位等级" prop="cabinClass">
          <el-select v-model="fareForm.cabinClass" :disabled="!!editingFare" style="width: 100%">
            <el-option v-for="(v, k) in CABIN_CLASS_MAP" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="票价" prop="fare">
              <el-input-number v-model="fareForm.fare" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税费" prop="tax">
              <el-input-number v-model="fareForm.tax" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="行李额度">
          <el-input v-model="fareForm.baggage" placeholder="如 20KG" />
        </el-form-item>
        <el-form-item label="退票规则">
          <el-input v-model="fareForm.refundRule" placeholder="如 起飞前24小时免费退改" />
        </el-form-item>
        <el-form-item label="改签规则">
          <el-input v-model="fareForm.changeRule" placeholder="如 起飞前24小时免费改签" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fareDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitFare">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getFareList, createFare, updateFare, deleteFare } from '@/api/fares'
import { getMasterData } from '@/api/system'
import { CABIN_CLASS_MAP } from '@/utils/constants'

const loading = ref(false)
const saving = ref(false)
const fareList = ref<any[]>([])
const airlines = ref<any[]>([])
const filterAirline = ref<number | null>(null)

const fareDialogVisible = ref(false)
const editingFare = ref<any>(null)
const fareFormRef = ref<FormInstance>()
const defaultFareForm = () => ({
  airlineId: null,
  cabinClass: 'ECONOMY',
  fare: 0,
  tax: 0,
  baggage: '20KG',
  refundRule: '起飞前24小时免费退改',
  changeRule: '起飞前24小时免费改签',
})
const fareForm = reactive(defaultFareForm())
const fareRules: FormRules = {
  airlineId: [{ required: true, message: '请选择航司', trigger: 'change' }],
  cabinClass: [{ required: true, message: '请选择舱位', trigger: 'change' }],
  fare: [{ required: true, message: '请输入票价', trigger: 'blur' }],
  tax: [{ required: true, message: '请输入税费', trigger: 'blur' }],
}

watch(filterAirline, () => fetchList())

async function fetchList() {
  loading.value = true
  try {
    const data = await getFareList(filterAirline.value ? { airlineId: filterAirline.value } : undefined) as any
    fareList.value = Array.isArray(data) ? data : data.list || []
  } catch (err: any) {
    showToast(err?.message || '操作失败', 'error')
    fareList.value = []
  } finally {
    loading.value = false
  }
}

async function fetchAirlines() {
  try {
    const res = await getMasterData('airlines')
    airlines.value = Array.isArray(res) ? res : []
  } catch { airlines.value = [] }
}

function showFareDialog(row?: any) {
  editingFare.value = row || null
  if (row) {
    Object.assign(fareForm, {
      airlineId: row.airlineId,
      cabinClass: row.cabinClass,
      fare: row.fare,
      tax: row.tax,
      baggage: row.baggage || '20KG',
      refundRule: row.refundRule || '起飞前24小时免费退改',
      changeRule: row.changeRule || '起飞前24小时免费改签',
    })
  } else {
    Object.assign(fareForm, defaultFareForm())
  }
  fareDialogVisible.value = true
}

async function submitFare() {
  const valid = await fareFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingFare.value) {
      await updateFare(editingFare.value.id, fareForm as any)
      showToast('更新成功', 'success')
    } else {
      await createFare(fareForm as any)
      showToast('创建成功', 'success')
    }
    fareDialogVisible.value = false
    fetchList()
  } catch (err: any) {
    showToast(err?.message || '操作失败', 'error')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除 ${row.airlineName} ${CABIN_CLASS_MAP[row.cabinClass] || row.cabinClass} 的票价？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteFare(row.id)
    showToast('删除成功', 'success')
    fetchList()
  } catch (err: any) {
    showToast(err?.message || '删除失败', 'error')
  }
}

function showToast(msg: string, type: 'success' | 'error') {
  const el = document.createElement('div')
  el.className = `toast toast-${type}`
  el.textContent = msg
  Object.assign(el.style, {
    position: 'fixed',
    top: '20px',
    left: '50%',
    transform: 'translateX(-50%)',
    padding: '12px 24px',
    borderRadius: '8px',
    zIndex: '9999',
    fontSize: '14px',
    fontWeight: '500',
    transition: 'opacity 0.3s',
    backgroundColor: type === 'success' ? 'var(--color-success)' : 'var(--color-danger)',
    color: 'var(--text-inverse)',
    boxShadow: 'var(--shadow-md)',
  })
  document.body.appendChild(el)
  setTimeout(() => { el.style.opacity = '0'; setTimeout(() => el.remove(), 300) }, 2500)
}

onMounted(() => { fetchAirlines(); fetchList() })
</script>

<style scoped>
/* .pagination-wrap 已收进全局样式 */
</style>
