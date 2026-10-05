<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">机组资质</div>
        <div>
          <el-input v-model="query.department" placeholder="部门" clearable style="width: 120px" />
          <el-input v-model="query.keyword" placeholder="机组编号/姓名" clearable style="width: 200px; margin-left: 12px">
            <template #append>
              <el-button @click="fetchCrews"><el-icon><Search /></el-icon></el-button>
            </template>
          </el-input>
          <el-button style="margin-left: 12px" @click="resetQuery">重置</el-button>
        </div>
      </div>
      <el-alert type="info" :closable="false" show-icon style="margin-top: 12px">
        <template #title>每人名下的每张证照即为一条资质。状态（有效/临期/过期）由系统按「到期日 vs 今天」自动判定，到期超过30天可续期，资质过期将影响排班与停飞判定。</template>
      </el-alert>
    </div>

    <!-- 机组名单 -->
    <div class="card-panel">
      <el-table scrollbar-always-on :data="crewList" stripe v-loading="loading">
        <el-table-column prop="crewId" label="机组编号" width="130" />
        <el-table-column prop="name" label="姓名" width="95" />
        <el-table-column prop="department" label="部门" width="95" />
        <el-table-column prop="status" label="当前状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(CREW_STATUS_MAP[row.status]?.type as any) || 'info'" size="small">
              {{ CREW_STATUS_MAP[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资质数" width="100">
          <template #default="{ row }">
            <el-tag :type="row.qualCount > 0 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.qualCount }} 项
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openManage(row)">
              <el-icon><Postcard /></el-icon>&nbsp;资质维护
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 资质维护对话框 -->
    <el-dialog v-model="manageVisible" :title="`资质维护 · ${current?.name || ''}（${current?.crewId || ''}）`" width="900px" destroy-on-close>
      <div class="manage-toolbar">
        <span class="manage-sub">{{ current?.department || '' }} · 共 {{ qualList.length }} 项资质</span>
        <el-button type="primary" size="small" @click="openQualForm()"><el-icon><Plus /></el-icon> 新增资质</el-button>
      </div>
      <el-table scrollbar-always-on :data="qualList" stripe>
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ QUAL_TYPE_MAP[row.type] || row.type || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="资质名称" min-width="120" />
        <el-table-column label="适配机型" width="120">
          <template #default="{ row }">
            <template v-if="aircraftTags(row.name).length">
              <el-tag
                v-for="c in aircraftTags(row.name)"
                :key="c"
                size="small"
                effect="plain"
                style="margin-right: 4px"
              >{{ c }}</el-tag>
            </template>
            <span v-else style="color: var(--text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="number" label="编号" width="115">
          <template #default="{ row }">{{ row.number || '—' }}</template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="100">
          <template #default="{ row }">{{ QUAL_LEVEL_MAP[row.level] || row.level || '—' }}</template>
        </el-table-column>
        <el-table-column prop="issueDate" label="发证日" width="105">
          <template #default="{ row }">{{ row.issueDate || '—' }}</template>
        </el-table-column>
        <el-table-column prop="expireDate" label="到期日" width="105">
          <template #default="{ row }">{{ row.expireDate || '长期' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(QUAL_STATUS_MAP[row.status]?.type as any) || 'info'" size="small">
              {{ QUAL_STATUS_MAP[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openQualForm(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="removeQual(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="manageVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增 / 编辑资质 -->
    <el-dialog v-model="qualFormVisible" :title="qualForm.id ? '编辑资质' : '新增资质'" width="560px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        <template #title>「状态」由系统按到期日自动判定，无需填写。到期日留空 = 长期有效。</template>
      </el-alert>
      <el-form :model="qualForm" label-width="88px">
        <el-form-item label="类型" required>
          <el-select v-model="qualForm.type" style="width: 100%">
            <el-option v-for="(v, k) in QUAL_TYPE_MAP" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="资质名称" required>
          <el-input v-model="qualForm.name" placeholder="如：B738机长资质 / 乘务员执照" />
        </el-form-item>
        <el-form-item label="证书编号">
          <el-input v-model="qualForm.number" placeholder="如：AC20150001" />
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="qualForm.level" clearable placeholder="不限定可不选" style="width: 100%">
            <el-option label="机长 (CAPTAIN)" value="CAPTAIN" />
            <el-option label="副驾驶 (FO)" value="FO" />
            <el-option label="乘务长 (SENIOR)" value="SENIOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="发证日">
          <el-date-picker v-model="qualForm.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="到期日">
          <el-date-picker v-model="qualForm.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="留空=长期有效" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div v-if="qualForm.expireDate" class="preview-line">
        保存后状态预览：
        <el-tag size="small" :type="(QUAL_STATUS_MAP[previewStatus]?.type as any) || 'info'">{{ QUAL_STATUS_MAP[previewStatus]?.label }}</el-tag>
      </div>
      <template #footer>
        <el-button @click="qualFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveQual">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCrewList, getCrewQualifications, addCrewQualification, updateCrewQualification, deleteCrewQualification } from '@/api/crew'
import { CREW_STATUS_MAP } from '@/utils/constants'

const loading = ref(false)
const crewList = ref<any[]>([])
const query = reactive({ department: '', keyword: '' })

// ===== 资质类型 / 等级 / 状态 字典 =====
const QUAL_TYPE_MAP: Record<string, string> = {
  AIRCRAFT: '机型资质',
  LICENSE: '执照/合格证',
  MEDICAL: '体检合格证',
  OTHER: '其他',
}
const QUAL_LEVEL_MAP: Record<string, string> = {
  CAPTAIN: '机长',
  FO: '副驾驶',
  SENIOR: '乘务长',
}
const QUAL_STATUS_MAP: Record<string, { label: string; type: string }> = {
  VALID: { label: '有效', type: 'success' },
  EXPIRING_SOON: { label: '临期(30天内)', type: 'warning' },
  EXPIRED: { label: '已过期', type: 'danger' },
}

/** 从资质名称中识别机型码（如 "B738机长资质" → ["B738"]），便于直观看出适配机型 */
function aircraftTags(name?: string | null): string[] {
  if (!name) return []
  const tokens = name.match(/[A-Za-z]{1,3}\d{3,4}/g) || []
  return Array.from(new Set(tokens.map(t => t.toUpperCase())))
}

// ===== 机组名单 =====
async function fetchCrews() {
  loading.value = true
  try {
    crewList.value = await getCrewList({
      department: query.department || undefined,
      keyword: query.keyword || undefined,
    }) as any || []
  } catch { crewList.value = [] } finally {
    loading.value = false
  }
}
function resetQuery() {
  query.department = ''
  query.keyword = ''
  fetchCrews()
}

// ===== 资质维护 =====
const manageVisible = ref(false)
const current = ref<any>(null)
const qualList = ref<any[]>([])

async function openManage(row: any) {
  current.value = row
  manageVisible.value = true
  await loadQuals(row.id)
}

async function loadQuals(crewId: string) {
  try {
    const data = await getCrewQualifications(crewId) as any || {}
    current.value = { ...(current.value || {}), name: data.name, department: data.department }
    qualList.value = data.qualifications || []
  } catch {
    qualList.value = []
  }
}

// ===== 新增 / 编辑 =====
const qualFormVisible = ref(false)
const saving = ref(false)
const qualForm = reactive<any>({ id: null, type: 'LICENSE', name: '', number: '', level: '', issueDate: '', expireDate: '' })

const previewStatus = computed(() => {
  if (!qualForm.expireDate) return 'VALID'
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const exp = new Date(qualForm.expireDate + 'T00:00:00')
  if (exp.getTime() < today.getTime()) return 'EXPIRED'
  const soon = new Date(today.getTime() + 29 * 86400000)
  if (exp.getTime() <= soon.getTime()) return 'EXPIRING_SOON'
  return 'VALID'
})

function openQualForm(row?: any) {
  if (row) {
    Object.assign(qualForm, {
      id: row.id, type: row.type, name: row.name, number: row.number,
      level: row.level || '', issueDate: row.issueDate || '', expireDate: row.expireDate || '',
    })
  } else {
    Object.assign(qualForm, { id: null, type: 'LICENSE', name: '', number: '', level: '', issueDate: '', expireDate: '' })
  }
  qualFormVisible.value = true
}

async function saveQual() {
  if (!qualForm.name) {
    ElMessage.warning('请填写资质名称')
    return
  }
  saving.value = true
  const crewId = String(current.value.id)
  const payload = {
    type: qualForm.type,
    name: qualForm.name,
    number: qualForm.number || null,
    level: qualForm.level || null,
    issueDate: qualForm.issueDate || null,
    expireDate: qualForm.expireDate || null,
  }
  try {
    if (qualForm.id) {
      await updateCrewQualification(crewId, String(qualForm.id), payload)
      ElMessage.success('资质已更新')
    } else {
      await addCrewQualification(crewId, payload)
      ElMessage.success('资质已添加')
    }
    qualFormVisible.value = false
    await loadQuals(crewId)
    fetchCrews() // 刷新列表（资质数 / 停飞联动后的状态）
  } catch { /* 错误提示已由拦截器处理 */ } finally {
    saving.value = false
  }
}

async function removeQual(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除资质「${row.name}」？删除后如无有效资质，该机组可能被自动停飞。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteCrewQualification(String(current.value.id), String(row.id))
    ElMessage.success('已删除')
    await loadQuals(String(current.value.id))
    fetchCrews()
  } catch { /* 拦截器已提示 */ }
}

onMounted(fetchCrews)
</script>

<style scoped>
.manage-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.manage-sub {
  font-size: 13px;
  color: var(--text-secondary);
}
.preview-line {
  font-size: 13px;
  color: var(--text-regular);
  margin-top: 4px;
}
</style>
