<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="card-panel">
      <el-form :inline="true" :model="searchForm" class="search-bar">
        <el-form-item label="日期">
          <el-date-picker v-model="searchForm.date" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item label="航线">
          <el-input v-model="searchForm.route" placeholder="如 PEK-SHA" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(v, k) in FLIGHT_STATUS_MAP" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="航班号/航司" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 操作栏 -->
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">航班计划</div>
        <div>
          <el-button type="primary" @click="showFlightDialog()"><el-icon><Plus /></el-icon> 新增航班</el-button>
          <el-button :disabled="!selectedIds.length" @click="handleBatch('CANCEL')"><el-icon><CircleClose /></el-icon> 批量取消</el-button>
        </div>
      </div>

      <el-table scrollbar-always-on
        v-loading="loading"
        :data="flightList"
        stripe
        @selection-change="handleSelectionChange"
        style="width: 100%"
      >
        <el-table-column type="selection" width="45" />
        <!--
          原先有「内部编号」列（110px）—— 航班主键，运营看航班号即可，属无效信息；
          又有「出发/到达航站楼」两列（各 80px）—— 航站楼只在值机场景有意义。
          三列合计 270px 是这张表超出容器的直接原因。航站楼已并入出发/到达列。

          第二轮把航班号与日期合成一列：运营读航班就是"哪个航班、哪天"，
          分开占 205px 不值，合并后这张表在 1280px（容器 986px）下也能放下。
        -->
        <el-table-column label="航班号 / 日期" width="110">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ row.flightNo }}</div>
              <div class="cell-stack__sub">{{ row.date }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="出发" min-width="126">
          <template #default="{ row }">
            <div class="cell-route" :title="row.route?.departureName">
              {{ shortAirport(row.route?.departureName) }} ({{ row.route?.departure }})
            </div>
            <div class="text-sub">
              <span class="tnum">{{ shortTime(row.schedule?.departureTime) }}</span>
              <template v-if="row.departureTerminal"> · {{ row.departureTerminal }}</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="到达" min-width="126">
          <template #default="{ row }">
            <div class="cell-route" :title="row.route?.arrivalName">
              {{ shortAirport(row.route?.arrivalName) }} ({{ row.route?.arrival }})
            </div>
            <div class="text-sub">
              <span class="tnum">{{ shortTime(row.schedule?.arrivalTime) }}</span>
              <template v-if="row.arrivalTerminal"> · {{ row.arrivalTerminal }}</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="机型" min-width="86">
          <template #default="{ row }">{{ row.aircraft?.type }}</template>
        </el-table-column>
        <el-table-column label="旅客(已订/可售)" min-width="112">
          <template #default="{ row }">
            <div>
              <span class="tnum">{{ row.passengers?.booked ?? 0 }} / {{ row.passengers?.capacity ?? 0 }}</span>
              <el-tooltip content="已值机人数" placement="top">
                <span v-if="(row.passengers?.checkedIn ?? 0) > 0" class="pax-checked-in">已值机 {{ row.passengers.checkedIn }}</span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="(FLIGHT_STATUS_MAP[row.status]?.type as any) || (row.isPast ? 'success' : 'info')" size="small">
              {{ FLIGHT_STATUS_MAP[row.status]?.label || (row.isPast ? '已起飞' : row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 4 个链接按钮（编辑/票价/不正常处理/删除）实测占 190px + 单元格内边距 24px ≈ 214px，
             给 220px 刚好一行放下；给 200px 会折行并把行高撑成 3 倍 -->
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="primary" link size="small" @click="showFlightDialog(row)">编辑</el-button>
              <el-button type="primary" link size="small" @click="showCabinDialog(row)">票价</el-button>
              <el-button type="warning" link size="small" @click="showIrregularDialog(row)">不正常处理</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </div>
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

    <!-- 新增/编辑航班对话框 -->
    <el-dialog
      v-model="flightDialogVisible"
      :title="editingFlight ? '编辑航班' : '新增航班'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="flightFormRef" :model="flightForm" :rules="flightRules" label-width="88px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="航班号" prop="flightNo">
              <el-input v-model="flightForm.flightNo" placeholder="如 CA1234" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="航班日期" prop="date">
              <el-date-picker v-model="flightForm.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="出发机场" prop="route.departure">
              <el-input v-model="flightForm.route.departure" placeholder="机场代码 如 PEK" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到达机场" prop="route.arrival">
              <el-input v-model="flightForm.route.arrival" placeholder="机场代码 如 SHA" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="出发时间" prop="schedule.departureTime">
              <el-time-picker v-model="flightForm.schedule.departureTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到达时间" prop="schedule.arrivalTime">
              <el-time-picker v-model="flightForm.schedule.arrivalTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="机型" prop="aircraft.type">
              <el-input v-model="flightForm.aircraft.type" placeholder="如 B738" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机号" prop="aircraft.registration">
              <el-input v-model="flightForm.aircraft.registration" placeholder="如 B-1234" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="航空公司" prop="airlineId">
              <el-select v-model="flightForm.airlineId" placeholder="选择航空公司" clearable style="width: 100%">
                <el-option v-for="a in airlines" :key="a.id" :label="`${a.code} - ${a.name}`" :value="a.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="航班类型" prop="flightType">
              <el-select v-model="flightForm.flightType" style="width: 100%">
                <el-option v-for="(v, k) in FLIGHT_TYPE_MAP" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="出发航站楼">
              <el-input v-model="flightForm.departureTerminal" placeholder="如 T1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到达航站楼">
              <el-input v-model="flightForm.arrivalTerminal" placeholder="如 T2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="值机开放(起飞前小时)">
              <el-input-number v-model="flightForm.checkinOpenHours" :min="0" :max="72" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="值机关闭(起飞前分钟)">
              <el-input-number v-model="flightForm.checkinCloseMinutes" :min="0" :max="240" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="flightForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="flightDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveFlight">确定</el-button>
      </template>
    </el-dialog>

    <!-- 不正常航班处理对话框 -->
    <el-dialog v-model="irregularDialogVisible" title="不正常航班处理" width="560px" destroy-on-close>
      <el-form ref="irregularFormRef" :model="irregularForm" :rules="irregularRules" label-width="88px">
        <el-form-item label="处理类型" prop="type">
          <el-radio-group v-model="irregularForm.type">
            <el-radio value="DELAY">延误</el-radio>
            <el-radio value="CANCEL">取消</el-radio>
            <el-radio value="DIVERSION">备降</el-radio>
            <el-radio value="RETURN">返航</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="irregularForm.reason" type="textarea" :rows="2" placeholder="请输入原因" />
        </el-form-item>
        <el-form-item v-if="irregularForm.type === 'DELAY'" label="新起飞时刻" prop="newTime">
          <el-date-picker v-model="irregularForm.newTime" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:00" placeholder="选填，不填则仅记录状态不计算延误时长" style="width: 100%" @change="calcDelayPreview" />
          <div v-if="delayPreview > 0" style="color:var(--color-warning);font-size:13px;margin-top:4px">
            ⏱ 预计延误 <strong>{{ delayPreview }}</strong> 分钟（{{ Math.floor(delayPreview / 60) }}小时{{ delayPreview % 60 }}分钟）
          </div>
        </el-form-item>
        <el-form-item label="通知旅客">
          <el-switch v-model="irregularForm.notifyPassengers" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="irregularDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleIrregular">确定</el-button>
      </template>
    </el-dialog>

    <!-- 舱位票价对话框（票价/税费来自标准票价表，只读） -->
    <el-dialog v-model="cabinDialogVisible" :title="`舱位座位 - ${cabinFlight?.flightNo}`" width="720px" destroy-on-close>
      <div class="cabin-fare-note">票价/税费来自该航司的标准票价表，如需修改请前往 <router-link to="/tickets/fares">票价管理</router-link></div>
      <el-table scrollbar-always-on :data="cabinList" v-loading="cabinLoading" stripe>
        <el-table-column prop="cabinName" label="舱位" width="90" />
        <el-table-column label="票价" width="110">
          <template #default="{ row }">¥{{ row.fare }}</template>
        </el-table-column>
        <el-table-column label="税费" width="80">
          <template #default="{ row }">¥{{ row.tax }}</template>
        </el-table-column>
        <el-table-column label="总价" width="100">
          <template #default="{ row }">¥{{ (row.fare || 0) + (row.tax || 0) }}</template>
        </el-table-column>
        <el-table-column label="总座位" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.totalSeats" :min="0" size="small" controls-position="right" style="width: 90px" />
          </template>
        </el-table-column>
        <el-table-column label="余座" width="90">
          <template #default="{ row }">
            <el-input-number v-model="row.availableSeats" :min="0" :max="row.totalSeats" size="small" controls-position="right" style="width: 90px" />
          </template>
        </el-table-column>
        <el-table-column label="行李" width="110">
          <template #default="{ row }">
            <el-input v-model="row.baggage" size="small" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="cabinDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="cabinSaving" @click="handleSaveCabins">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getFlightList, createFlight, updateFlight, deleteFlight, batchFlightAction, handleIrregularFlight, getFlightCabins, updateFlightCabin } from '@/api/flights'
import { getMasterData } from '@/api/system'
import { FLIGHT_STATUS_MAP, FLIGHT_TYPE_MAP } from '@/utils/constants'
import { shortAirport, shortTime } from '@/utils/format'

const loading = ref(false)
const submitting = ref(false)
const flightList = ref<any[]>([])
const selectedIds = ref<string[]>([])
const airlines = ref<any[]>([])

async function fetchAirlines() {
  try {
    const res = await getMasterData('airlines')
    airlines.value = Array.isArray(res) ? res : []
  } catch (err: any) {
    airlines.value = [];
    ElMessage.error(err?.message || '加载航司数据失败')
  }
}

const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const searchForm = reactive({ date: '', route: '', status: '', keyword: '' })

// ---- 航班表单 ----
const flightDialogVisible = ref(false)
const editingFlight = ref<any>(null)
const flightFormRef = ref<FormInstance>()

const defaultFlightForm = () => ({
  flightNo: '',
  date: '',
  route: { departure: '', arrival: '' },
  schedule: { departureTime: '', arrivalTime: '' },
  aircraft: { type: '', registration: '' },
  flightType: 'DOMESTIC',
  airlineId: null,
  departureTerminal: '',
  arrivalTerminal: '',
  checkinOpenHours: 24,
  checkinCloseMinutes: 30,
  remark: '',
})

const flightForm = reactive(defaultFlightForm())

const flightRules: FormRules = {
  flightNo: [{ required: true, message: '请输入航班号', trigger: 'blur' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  'route.departure': [{ required: true, message: '请输入出发机场', trigger: 'blur' }],
  'route.arrival': [{ required: true, message: '请输入到达机场', trigger: 'blur' }],
  'schedule.departureTime': [{ required: true, message: '请选择出发时间', trigger: 'change' }],
  'schedule.arrivalTime': [{ required: true, message: '请选择到达时间', trigger: 'change' }],
  'aircraft.type': [{ required: true, message: '请输入机型', trigger: 'blur' }],
  'aircraft.registration': [{ required: true, message: '请输入机号', trigger: 'blur' }],
  flightType: [{ required: true, message: '请选择航班类型', trigger: 'change' }],
  airlineId: [{ required: true, message: '请选择航空公司', trigger: 'change' }],
}

// ---- 不正常航班 ----
const irregularDialogVisible = ref(false)
const irregularFlightId = ref('')
const irregularFlightNo = ref('')
const irregularOriginalTime = ref('')  // 原始起飞时间，用于计算延误
const irregularFormRef = ref<FormInstance>()
const delayPreview = ref(0)
const irregularForm = reactive({
  type: 'DELAY' as string,
  reason: '',
  newTime: '',
  notifyPassengers: true,
})
const irregularRules: FormRules = {
  type: [{ required: true, message: '请选择处理类型', trigger: 'change' }],
  reason: [{ required: true, message: '请输入原因', trigger: 'blur' }],
  newTime: [{
    validator: (_rule: any, value: string, callback: any) => {
      if (irregularForm.type === 'DELAY' && !value) {
        callback(new Error('延误时必须选择新起飞时刻'))
      } else {
        callback()
      }
    },
    trigger: 'change',
  }],
}

function calcDelayPreview() {
  delayPreview.value = 0
  if (!irregularForm.newTime || !irregularOriginalTime.value) return
  const oldTime = new Date(irregularOriginalTime.value).getTime()
  const newTime = new Date(irregularForm.newTime).getTime()
  if (newTime > oldTime) {
    delayPreview.value = Math.round((newTime - oldTime) / 60000)
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: pagination.page, pageSize: pagination.pageSize }
    for (const [k, v] of Object.entries(searchForm)) {
      if (v !== '' && v != null) params[k] = v
    }
    const data = await getFlightList(params) as any
    flightList.value = data.list || []
    pagination.total = data.pagination?.total || 0
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    flightList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function resetSearch() {
  Object.assign(searchForm, { date: '', route: '', status: '', keyword: '' })
  handleSearch()
}

function handleSelectionChange(rows: any[]) {
  selectedIds.value = rows.map(r => r.flightId)
}

function showFlightDialog(row?: any) {
  editingFlight.value = row || null
  if (row) {
    Object.assign(flightForm, {
      flightNo: row.flightNo,
      date: row.date,
      route: { ...row.route },
      schedule: { ...row.schedule },
      aircraft: { ...row.aircraft },
      flightType: row.flightType || 'DOMESTIC',
      airlineId: row.airlineId || null,
      departureTerminal: row.departureTerminal || '',
      arrivalTerminal: row.arrivalTerminal || '',
      checkinOpenHours: row.checkinOpenHours ?? 24,
      checkinCloseMinutes: row.checkinCloseMinutes ?? 30,
      remark: row.remark || '',
    })
  } else {
    Object.assign(flightForm, defaultFlightForm())
  }
  flightDialogVisible.value = true
}

async function handleSaveFlight() {
  const valid = await flightFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingFlight.value) {
      await updateFlight(editingFlight.value.flightId, { ...flightForm })
      ElMessage.success('编辑成功')
    } else {
      await createFlight({ ...flightForm } as any)
      ElMessage.success('创建成功')
    }
    flightDialogVisible.value = false
    fetchList()
  } catch (err: any) {
    ElMessage.error(err?.message || '保存航班失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除航班 ${row.flightNo}？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteFlight(row.flightId)
    ElMessage.success('删除成功')
    fetchList()
  } catch (err: any) {
    ElMessage.error(err?.message || '删除航班失败')
  }
}

async function handleBatch(action: string) {
  try {
    await ElMessageBox.confirm(`确定批量${action === 'CANCEL' ? '取消' : '操作'}选中的航班？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await batchFlightAction({ flightIds: selectedIds.value, action })
    ElMessage.success('操作成功')
    fetchList()
  } catch (err: any) {
    ElMessage.error(err?.message || '批量操作失败')
  }
}

function showIrregularDialog(row: any) {
  irregularFlightId.value = row.flightId
  irregularFlightNo.value = row.flightNo || ''
  // 保存原始起飞时间用于计算延误分钟
  irregularOriginalTime.value = row.departureDateTime || (row.date + 'T' + (row.schedule?.departureTime || '00:00'))
  delayPreview.value = 0
  Object.assign(irregularForm, { type: 'DELAY', reason: '', newTime: '', notifyPassengers: true })
  irregularDialogVisible.value = true
}

async function handleIrregular() {
  const valid = await irregularFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: any = {
      type: irregularForm.type,
      reason: irregularForm.reason,
      notifyPassengers: irregularForm.notifyPassengers,
    }
    if (irregularForm.type === 'DELAY' && irregularForm.newTime) {
      payload.newSchedule = { departureTime: irregularForm.newTime }
      payload.arrangements = { delayMinutes: delayPreview.value }
    }
    await handleIrregularFlight(irregularFlightId.value, payload)
    // 如果延误时间被自动计算了，提示具体分钟数
    const msg = irregularForm.type === 'DELAY' && delayPreview.value > 0
      ? `已标记 ${irregularFlightNo.value} 延误 ${delayPreview.value} 分钟`
      : '处理成功'
    ElMessage.success(msg)
    irregularDialogVisible.value = false
    fetchList()
  } catch (err: any) {
    ElMessage.error(err?.message || '处理不正常航班失败')
  } finally {
    submitting.value = false
  }
}

// ---- 舱位票价 ----
const cabinDialogVisible = ref(false)
const cabinFlight = ref<any>(null)
const cabinList = ref<any[]>([])
const cabinLoading = ref(false)
const cabinSaving = ref(false)

async function showCabinDialog(row: any) {
  cabinFlight.value = row
  cabinDialogVisible.value = true
  cabinLoading.value = true
  try {
    cabinList.value = (await getFlightCabins(row.flightId)) as unknown as any[] || []
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '加载舱位失败')
    cabinList.value = []
  } finally {
    cabinLoading.value = false
  }
}

async function handleSaveCabins() {
  cabinSaving.value = true
  try {
    for (const cabin of cabinList.value) {
      await updateFlightCabin(cabinFlight.value.flightId, cabin.cabinId, {
        totalSeats: cabin.totalSeats,
        availableSeats: cabin.availableSeats,
        baggage: cabin.baggage,
      })
    }
    ElMessage.success('舱位座位已更新')
    cabinDialogVisible.value = false
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '保存失败')
  } finally {
    cabinSaving.value = false
  }
}

onMounted(() => { fetchList(); fetchAirlines() })
</script>

<style scoped lang="scss">
.text-secondary {
  font-size: 12px;
  color: var(--text-secondary);
}

/* 出发/到达列的机场名：主信息给足对比度，并强制单行 ——
   全名折行会把行高从 44px 撑到 86px，完整名称改用 title 悬浮查看 */
.cell-route {
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pagination-wrap {
  margin-top: var(--space-section);
}

.cabin-fare-note {
  background: $color-primary-bg;
  color: $color-primary;
  border: 1px solid var(--color-primary-border);
  border-radius: 6px;
  padding: 8px 14px;
  margin-bottom: 16px;
  font-size: 13px;
}

.cabin-fare-note a {
  color: $color-primary;
  font-weight: 600;
  text-decoration: underline;
}
.pax-checked-in {
  margin-left: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
