<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">自定义报表</div>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon> 新建报表
        </el-button>
      </div>

      <el-table scrollbar-always-on v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column label="报表名称" min-width="180">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main">{{ row.name }}</div>
              <div class="cell-stack__sub">{{ row.creator ? `创建人 ${row.creator}` : '—' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="统计维度" width="100">
          <template #default="{ row }">{{ row.dimensionLabel || row.dimension }}</template>
        </el-table-column>
        <el-table-column label="时间范围" width="200">
          <template #default="{ row }">
            <span class="tnum">{{ row.startDate }} ~ {{ row.endDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据行数" width="90">
          <template #default="{ row }">
            <span class="tnum">{{ row.rowCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近生成" min-width="150">
          <template #default="{ row }">
            <span v-if="row.lastGeneratedAt" class="tnum">{{ fmtDateTime(row.lastGeneratedAt) }}</span>
            <span v-else class="text-secondary">从未生成</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="primary" link size="small" @click="openResult(row)">查看结果</el-button>
              <el-button type="primary" link size="small" :loading="generatingId === row.id" @click="handleGenerate(row)">重新生成</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建报表 -->
    <el-dialog v-model="createVisible" title="新建报表" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="报表名称" prop="name">
          <el-input v-model="form.name" placeholder="如 九月航线收入统计" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="统计维度" prop="dimension">
          <el-select v-model="form.dimension" style="width: 100%">
            <el-option v-for="(label, value) in DIMENSIONS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围" prop="range">
          <el-date-picker
            v-model="form.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item>
          <div class="form-tip">
            统计口径：所选时间范围内状态为已成交（已支付 / 已出票 / 已值机 / 已完成）的订单；
            指标为订单数、旅客数与收入金额。创建后立即生成一次结果。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 报表结果 -->
    <el-dialog v-model="resultVisible" :title="result?.name || '报表结果'" width="720px" destroy-on-close>
      <div class="result-head">
        <div class="result-stat">
          <span class="result-stat__label">订单数</span>
          <span class="result-stat__value tnum">{{ summary.orders }}</span>
        </div>
        <div class="result-stat">
          <span class="result-stat__label">旅客数</span>
          <span class="result-stat__value tnum">{{ summary.passengers }}</span>
        </div>
        <div class="result-stat">
          <span class="result-stat__label">收入合计</span>
          <span class="result-stat__value tnum">¥{{ summary.revenue }}</span>
        </div>
        <div class="result-stat">
          <span class="result-stat__label">统计范围</span>
          <span class="result-range tnum">{{ result?.startDate }} ~ {{ result?.endDate }}</span>
        </div>
      </div>

      <el-table v-if="resultRows.length" scrollbar-always-on :data="resultRows" size="small" stripe max-height="380">
        <el-table-column :label="groupColumnLabel" min-width="140">
          <template #default="{ row }">
            <span class="cell-stack__main">{{ displayGroup(row.group) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orders" label="订单数" width="90" align="right" />
        <el-table-column prop="passengers" label="旅客数" width="90" align="right" />
        <el-table-column label="收入" width="130" align="right">
          <template #default="{ row }">
            <span class="tnum">¥{{ row.revenue }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty">
        <p class="empty-tip">该时间范围内没有成交订单，没有可统计的数据</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getCustomReportList, createCustomReport, generateReport } from '@/api/reports'
import { CABIN_CLASS_MAP } from '@/utils/constants'
import { fmtDateTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const DIMENSIONS: Record<string, string> = {
  DATE: '按日期',
  ROUTE: '按航线',
  AIRLINE: '按航司',
  CABIN: '按舱位',
}

const authStore = useAuthStore()

const loading = ref(false)
const list = ref<any[]>([])
const generatingId = ref('')

async function fetchList() {
  loading.value = true
  try {
    list.value = (await getCustomReportList()) as unknown as any[] || []
  } catch (err: any) {
    ElMessage.error(err?.message || '加载报表列表失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

// ---- 新建 ----
const createVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()

function defaultRange(): string[] {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 30)
  const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return [fmt(start), fmt(end)]
}

const form = reactive({
  name: '',
  dimension: 'DATE',
  range: defaultRange() as string[],
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入报表名称', trigger: 'blur' }],
  dimension: [{ required: true, message: '请选择统计维度', trigger: 'change' }],
  range: [{ required: true, message: '请选择时间范围', trigger: 'change' }],
}

function openCreate() {
  form.name = ''
  form.dimension = 'DATE'
  form.range = defaultRange()
  createVisible.value = true
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await createCustomReport({
      name: form.name,
      dimension: form.dimension,
      startDate: form.range[0],
      endDate: form.range[1],
      // 后台登录态里存的是管理员用户名，作为创建人记录
      creator: authStore.username || null,
    })
    ElMessage.success('报表已创建并完成首次生成')
    createVisible.value = false
    await fetchList()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '创建报表失败')
  } finally {
    creating.value = false
  }
}

// ---- 生成 ----
async function handleGenerate(row: any) {
  generatingId.value = row.id
  try {
    await generateReport(row.id)
    ElMessage.success('已重新生成')
    await fetchList()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '生成失败')
  } finally {
    generatingId.value = ''
  }
}

// ---- 结果 ----
const resultVisible = ref(false)
const result = ref<any>(null)
const resultRows = computed<any[]>(() => result.value?.rows || [])

const summary = computed(() => {
  const rows = resultRows.value
  return {
    orders: rows.reduce((n, r) => n + (Number(r.orders) || 0), 0),
    passengers: rows.reduce((n, r) => n + (Number(r.passengers) || 0), 0),
    revenue: rows.reduce((n, r) => n + (Number(r.revenue) || 0), 0).toFixed(2),
  }
})

const groupColumnLabel = computed(() => {
  const dim = result.value?.dimension
  return dim === 'ROUTE' ? '航线' : dim === 'AIRLINE' ? '航司' : dim === 'CABIN' ? '舱位' : '日期'
})

function displayGroup(group: string) {
  const dim = result.value?.dimension
  if (dim === 'CABIN') return CABIN_CLASS_MAP[group] || group
  return group || '—'
}

function openResult(row: any) {
  result.value = row
  resultVisible.value = true
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.result-head {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.result-stat {
  background: var(--bg-muted);
  border-radius: var(--radius-base);
  padding: 10px 12px;
}

.result-stat__label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.result-stat__value {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.result-range {
  font-size: 13px;
  color: var(--text-primary);
}

.empty {
  padding: 32px 0;
  text-align: center;
}

.empty-tip {
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
