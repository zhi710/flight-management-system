<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">机组排班</div>
        <div>
          <el-button type="primary" @click="showCreateDialog"><el-icon><Plus /></el-icon> 新建排班</el-button>
          <el-button type="success" @click="showAutoDialog"><el-icon><MagicStick /></el-icon> 自动排班</el-button>
        </div>
      </div>

      <el-form :inline="true" class="search-bar">
        <el-form-item label="日期范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="department" placeholder="部门" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="机型资质">
          <el-input v-model="qualification" placeholder="机型" clearable style="width: 100px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchSchedule"><el-icon><Search /></el-icon> 查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel">
      <el-table scrollbar-always-on :data="scheduleList" stripe v-loading="loading">
        <el-table-column prop="crewId" label="机组ID" width="130" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="role" label="角色" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="flightNo" label="航班号" width="100" />
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column prop="route" label="航线" width="140" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="(CREW_STATUS_MAP[row.status]?.type as any) || 'info'" size="small">
              {{ CREW_STATUS_MAP[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="handleDeleteSchedule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchSchedule"
          @current-change="fetchSchedule"
        />
      </div>
    </div>

    <!-- 新建排班 -->
    <el-dialog v-model="createDialogVisible" title="新建排班" width="560px" destroy-on-close>
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        <template #title>每个航班至少需配齐3人（机长+副驾驶+乘务员各1人）才算完整排班</template>
      </el-alert>
      <el-form :model="createForm" label-width="88px">
        <el-divider content-position="left">本次排班</el-divider>
        <el-form-item label="机组人员">
          <el-select v-model="createForm.crewId" filterable remote clearable
            :remote-method="searchCrews"
            :loading="crewLoading"
            placeholder="搜索 工号 / 姓名"
            style="width: 100%"
            @change="onCrewChange"
            @visible-change="(v: boolean) => { if (v) searchCrews('') }"
          >
            <el-option v-for="c in crewOptions" :key="c.value" :label="c.label" :value="c.value">
              <span>{{ c.label }}</span>
              <span class="opt-role">{{ c.rolesText }}</span>
            </el-option>
          </el-select>
          <div v-if="selectedCrew" class="crew-tip">
            <el-tag
              v-for="r in selectedCrew.roles || []"
              :key="r"
              size="small"
              style="margin-right:4px"
            >{{ ROLE_LABEL[r] || r }}</el-tag>
            <span v-if="!(selectedCrew.roles || []).length" class="crew-tip-warn">
              该机组名下暂无有效资质，无法按机长/副驾驶/乘务员排班
            </span>
          </div>
        </el-form-item>
        <el-form-item label="航班号">
          <el-select v-model="createForm.flightId" filterable
            :loading="flightSearchLoading"
            placeholder="搜索航班号"
            style="width: 100%"
            @focus="searchFlightsForCreate('')"
            @visible-change="(v: boolean) => { if (v) searchFlightsForCreate('') }"
          >
            <el-option v-for="f in createFlightOptions" :key="f.value"
              :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option
              v-for="r in roleOptions"
              :key="r.value"
              :label="r.label"
              :value="r.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="航班日期">
          <el-date-picker v-model="createForm.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-divider content-position="left">下次任务（选填）</el-divider>
        <el-form-item label="下次航班">
          <el-select v-model="createForm.nextFlightId" filterable clearable
            :loading="flightSearchLoading"
            placeholder="留空则系统自动从排班推导"
            style="width: 100%"
            @focus="searchFlightsForCreate('')"
          >
            <el-option v-for="f in createFlightOptions" :key="f.value"
              :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="下次日期">
          <el-date-picker v-model="createForm.nextDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="下次角色">
          <el-select v-model="createForm.nextRole" clearable style="width: 100%">
            <el-option label="机长 (CAPTAIN)" value="CAPTAIN" />
            <el-option label="副驾驶 (FO)" value="FO" />
            <el-option label="乘务员 (FA)" value="FA" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 自动排班 -->
    <el-dialog v-model="autoDialogVisible" title="自动排班" width="560px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px">
        <template #title>系统将自动查找日期范围内未排班的航班，为其分配空闲机组（机长+副驾驶+乘务员各1人）</template>
      </el-alert>
      <el-form :model="autoForm" label-width="88px">
        <el-form-item label="日期范围">
          <el-date-picker v-model="autoForm.dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="排班规则">
          <el-checkbox v-model="autoForm.skipWeekends">跳过周末</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="autoDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="autoScheduling" @click="submitAutoSchedule">开始自动排班</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCrewSchedule, createCrewSchedule, autoSchedule, deleteCrewSchedule, getCrewList } from '@/api/crew'
import { getFlightList } from '@/api/flights'
import { CREW_STATUS_MAP } from '@/utils/constants'

const ROLE_LABEL: Record<string, string> = {
  CAPTAIN: '机长',
  FO: '副驾驶',
  FA: '乘务员',
}

const ALL_ROLES = [
  { value: 'CAPTAIN', label: '机长 (CAPTAIN)' },
  { value: 'FO', label: '副驾驶 (FO)' },
  { value: 'FA', label: '乘务员 (FA)' },
]

const loading = ref(false)
const scheduleList = ref<any[]>([])
const dateRange = ref<string[]>([])
const department = ref('')
const qualification = ref('')
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const createDialogVisible = ref(false)
const createSaving = ref(false)
const flightSearchLoading = ref(false)
const createFlightOptions = ref<any[]>([])
const createForm = reactive({
  crewId: '', flightId: '', role: 'CAPTAIN', date: '',
  nextFlightId: '', nextDate: '', nextRole: '',
})

// ===== 机组人员选择（手动排班必须指定到具体的人，而不是只选角色） =====
const crewLoading = ref(false)
const crewOptions = ref<any[]>([])
const selectedCrew = computed(() => crewOptions.value.find((c) => c.value === createForm.crewId) || null)

/** 角色候选项：优先按所选人员的可任角色限定；未选人时给出全部角色 */
const roleOptions = computed(() => {
  const roles: string[] = selectedCrew.value?.roles || []
  if (!roles.length) return ALL_ROLES
  return ALL_ROLES.filter((r) => roles.includes(r.value))
})

async function searchCrews(query: string) {
  crewLoading.value = true
  try {
    const data = await getCrewList({ keyword: query?.trim() || undefined }) as any
    crewOptions.value = (data || []).map((c: any) => ({
      value: c.id,
      label: `${c.crewId} · ${c.name} · ${c.department || '—'} · ${CREW_STATUS_MAP[c.status]?.label || c.status}`,
      rolesText: (c.roles || []).map((r: string) => ROLE_LABEL[r] || r).join('/'),
      roles: c.roles || [],
      status: c.status,
    }))
  } catch {
    crewOptions.value = []
  } finally {
    crewLoading.value = false
  }
}

/** 换人后：若当前角色不在该人的可任角色内，自动切到第一个可任角色 */
function onCrewChange() {
  const roles: string[] = selectedCrew.value?.roles || []
  if (roles.length && !roles.includes(createForm.role)) {
    createForm.role = roles[0]!
  }
}

const autoDialogVisible = ref(false)
const autoScheduling = ref(false)
const autoForm = reactive({ dateRange: [] as string[], skipWeekends: false })

async function fetchSchedule() {
  if (!dateRange.value?.length) return
  loading.value = true
  try {
    const data = await getCrewSchedule({
      startDate: dateRange.value[0]!,
      endDate: dateRange.value[1]!,
      department: department.value || undefined,
      qualification: qualification.value || undefined,
    }) as any
    scheduleList.value = data.list || data || []
    pagination.total = data.pagination?.total || 0
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    scheduleList.value = []
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  Object.assign(createForm, {
    crewId: '', flightId: '', role: 'CAPTAIN', date: pageDate(),
    nextFlightId: '', nextDate: '', nextRole: '',
  })
  searchCrews('') // 预加载机组候选，避免下拉为空
  createDialogVisible.value = true
}

function pageDate(): string {
  return dateRange.value?.[0] || new Date().toISOString().substring(0, 10)
}

async function searchFlightsForCreate(query: string) {
  flightSearchLoading.value = true
  try {
    const data = await getFlightList({ page: 1, pageSize: 200 }) as any
    const rawList = data?.list || []
    createFlightOptions.value = rawList
      .filter((f: any) => f.status === 'SCHEDULED')
      .map((f: any) => ({
        label: `${f.flightNo}  ${f.route?.departure || ''}→${f.route?.arrival || ''}  ${f.date || ''}  ${f.schedule?.departureTime || ''}`,
        value: f.flightNo,
      }))
      .filter((f: any) => !query || f.value.toUpperCase().includes(query.toUpperCase()))
  } catch {
    createFlightOptions.value = []
  } finally {
    flightSearchLoading.value = false
  }
}

async function submitCreate() {
  if (!createForm.crewId) {
    ElMessage.warning('请选择机组人员')
    return
  }
  if (!createForm.flightId) {
    ElMessage.warning('请选择航班')
    return
  }
  createSaving.value = true
  try {
    const payload: any = {
      crewId: createForm.crewId,
      flightId: createForm.flightId,
      role: createForm.role,
      date: createForm.date,
    }
    // 如果有下次任务，一起传
    if (createForm.nextFlightId && createForm.nextDate) {
      payload.nextFlightId = createForm.nextFlightId
      payload.nextDate = createForm.nextDate
      payload.nextRole = createForm.nextRole || createForm.role
    }
    await createCrewSchedule(payload)
    const msg = createForm.nextFlightId
      ? `已创建本次排班+下次任务（${createForm.nextFlightId} ${createForm.nextDate}）`
      : '创建成功'
    ElMessage.success(msg)
    createDialogVisible.value = false
    fetchSchedule()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '创建排班失败')
  } finally {
    createSaving.value = false
  }
}

function showAutoDialog() {
  autoForm.dateRange = dateRange.value || []
  autoForm.skipWeekends = false
  autoDialogVisible.value = true
}

async function submitAutoSchedule() {
  if (!autoForm.dateRange?.length) {
    ElMessage.warning('请选择日期范围')
    return
  }
  autoScheduling.value = true
  try {
    const result = await autoSchedule({
      startDate: autoForm.dateRange[0]!,
      endDate: autoForm.dateRange[1]!,
      skipWeekends: autoForm.skipWeekends,
    }) as any
    const summary = result as any
    const skipped = (summary?.totalFlights || 0) - (summary?.assignedFlights || 0)
    let msg = `自动排班完成：${summary?.assignedFlights || 0} 个航班×3人 = ${summary?.assignedCrew || 0} 名机组`
    if (skipped > 0) msg += `；${skipped} 个航班因机组不足被跳过`
    ElMessage.success(msg)
    autoDialogVisible.value = false
    fetchSchedule()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '自动排班失败')
  } finally {
    autoScheduling.value = false
  }
}

async function handleDeleteSchedule(row: any) {
  try {
    await ElMessageBox.confirm('确定删除此排班？', '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteCrewSchedule(row.scheduleId)
    ElMessage.success('删除成功')
    fetchSchedule()
  } catch (err: any) {
    ElMessage.error(err?.message || '删除排班失败')
  }
}

onMounted(() => {
  const now = new Date()
  const start = now.toISOString().substring(0, 10)
  const end = new Date(now.getTime() + 6 * 86400000).toISOString().substring(0, 10)
  dateRange.value = [start, end]
  fetchSchedule()
})
</script>

<style scoped>
/* 下拉项右侧显示该机组可任角色 */
.opt-role {
  float: right;
  color: var(--text-secondary);
  font-size: 12px;
  margin-left: 16px;
}

.crew-tip {
  margin-top: 6px;
  line-height: 1.6;
}

.crew-tip-warn {
  font-size: 12px;
  color: var(--color-warning);
}
</style>
