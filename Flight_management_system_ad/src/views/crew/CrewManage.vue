<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">机组人员</div>
        <el-button type="primary" @click="showDialog()"><el-icon><Plus /></el-icon> 新增人员</el-button>
      </div>

      <el-form :inline="true" class="search-bar">
        <el-form-item label="部门">
          <el-select v-model="query.department" clearable placeholder="全部部门" style="width: 140px" @change="fetchCrews">
            <el-option v-for="d in DEPARTMENTS" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="工号 / 姓名"
            clearable
            style="width: 180px"
            @keyup.enter="fetchCrews"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchCrews"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel">
      <el-table scrollbar-always-on :data="pagedCrews" stripe v-loading="loading">
        <el-table-column prop="crewId" label="机组编号" width="120" />
        <!-- 姓名与部门合成一列：名单是按"人 + 所属部门"读的，拆成两列反而要来回对 -->
        <el-table-column label="姓名 / 部门" width="110">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main">{{ row.name }}</div>
              <div class="cell-stack__sub">{{ row.department || '—' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="性别" width="70">
          <template #default="{ row }">{{ GENDER_LABEL[row.gender] || row.gender || '—' }}</template>
        </el-table-column>
        <el-table-column label="手机号" width="110">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="140">
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(CREW_STATUS_MAP[row.status]?.type as any) || 'info'" size="small">
              {{ CREW_STATUS_MAP[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可任角色" min-width="120">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles || []" :key="r" size="small" style="margin-right: 4px">
              {{ ROLE_LABEL[r] || r }}
            </el-tag>
            <span v-if="!row.roles?.length" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="资质数" width="70">
          <template #default="{ row }">{{ row.qualCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
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
          :total="crewList.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="pagination.page = 1"
        />
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑机组人员' : '新增机组人员'"
      width="560px"
      destroy-on-close
    >
      <el-form :model="form" label-width="88px">
        <el-form-item label="机组编号" required>
          <el-input v-model="form.crewId" placeholder="如 C20260101001" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" placeholder="如 张伟" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="MALE">男</el-radio>
            <el-radio value="FEMALE">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.department" filterable allow-create style="width: 100%">
            <el-option v-for="d in DEPARTMENTS" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option
              v-for="(v, k) in CREW_STATUS_MAP"
              :key="k"
              :label="v.label"
              :value="k"
            />
          </el-select>
          <div class="form-tip">人工改状态会清除状态机记录的到期时间/原因；新增人员默认为「待命」。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCrewList, createCrew, updateCrew, deleteCrew } from '@/api/crew'
import { CREW_STATUS_MAP } from '@/utils/constants'

/** 部门候选（可手输新增，如「空保部」） */
const DEPARTMENTS = ['飞行部', '客舱部', '空保部', '机务部']

const ROLE_LABEL: Record<string, string> = {
  CAPTAIN: '机长',
  FO: '副驾驶',
  FA: '乘务员',
}

/** 库中性别存 MALE / FEMALE */
const GENDER_LABEL: Record<string, string> = {
  MALE: '男',
  FEMALE: '女',
}

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const crewList = ref<any[]>([])
const query = reactive({ department: '', keyword: '' })
const pagination = reactive({ page: 1, pageSize: 20 })

const form = reactive({
  id: '',
  crewId: '',
  name: '',
  gender: 'MALE',
  department: '飞行部',
  phone: '',
  email: '',
  status: 'STANDBY',
})

/** 机组名单数据量不大，直接前端分页 */
const pagedCrews = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return crewList.value.slice(start, start + pagination.pageSize)
})

async function fetchCrews() {
  loading.value = true
  try {
    const data = await getCrewList({
      department: query.department || undefined,
      keyword: query.keyword.trim() || undefined,
    }) as any
    crewList.value = data || []
    // 过滤后当前页可能越界，回到第一页
    if ((pagination.page - 1) * pagination.pageSize >= crewList.value.length) {
      pagination.page = 1
    }
  } catch {
    crewList.value = []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.department = ''
  query.keyword = ''
  pagination.page = 1
  fetchCrews()
}

function showDialog(row?: any) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      crewId: row.crewId,
      name: row.name,
      gender: row.gender || 'MALE',
      department: row.department || '飞行部',
      phone: row.phone || '',
      email: row.email || '',
      status: row.status || 'STANDBY',
    })
  } else {
    Object.assign(form, {
      id: '',
      crewId: '',
      name: '',
      gender: 'MALE',
      department: '飞行部',
      phone: '',
      email: '',
      status: 'STANDBY',
    })
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.crewId.trim()) {
    ElMessage.warning('请填写机组编号')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  saving.value = true
  try {
    const payload = {
      crewId: form.crewId.trim(),
      name: form.name.trim(),
      gender: form.gender,
      department: form.department,
      phone: form.phone.trim(),
      email: form.email.trim(),
    }
    if (form.id) {
      await updateCrew(form.id, { ...payload, status: form.status })
      ElMessage.success('已保存')
    } else {
      await createCrew(payload)
      ElMessage.success('已新增机组人员')
    }
    dialogVisible.value = false
    fetchCrews()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除机组人员「${row.name}（${row.crewId}）」？其名下资质将一并移除。`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteCrew(row.id)
    ElMessage.success('已删除')
    fetchCrews()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '删除失败')
  }
}

onMounted(fetchCrews)
</script>

<style scoped>
.form-tip {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-top: 4px;
}

.muted {
  color: var(--text-placeholder);
}
</style>
