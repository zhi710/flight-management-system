<template>
  <div class="page-container">
    <div class="card-panel">
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-bar">
        <el-form-item label="手机号">
          <el-input v-model="searchForm.phone" placeholder="手机号" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="searchForm.name" placeholder="姓名" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="会员等级">
          <el-select v-model="searchForm.memberLevel" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(v, k) in MEMBER_LEVEL_MAP" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">前台用户</div>
      </div>

      <el-table scrollbar-always-on v-loading="loading" :data="memberList" stripe>
        <!-- 会员号 + 用户ID 合成一列：两者都是标识，且用户ID 是 20 位雪花号，
             单独占一列要 180px 却没人能一眼读完，改为截短显示 + 悬浮看全 -->
        <el-table-column label="会员号 / 用户ID" width="140">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main tnum">{{ row.memberNo || '—' }}</div>
              <div class="cell-stack__sub tnum" :title="row.userId || row.id">{{ shortId(row.userId || row.id) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="姓名 / 手机号" width="120">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main">{{ row.name || '—' }}</div>
              <div class="cell-stack__sub tnum">{{ row.phone || '—' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="会员等级" width="95">
          <template #default="{ row }">
            <el-tag
              v-if="MEMBER_LEVEL_MAP[row.memberLevel]"
              :style="{
                background: MEMBER_LEVEL_MAP[row.memberLevel]?.bg,
                color: MEMBER_LEVEL_MAP[row.memberLevel]?.fg,
                border: 'none',
              }"
              size="small"
            >
              {{ MEMBER_LEVEL_MAP[row.memberLevel]?.label }}
            </el-tag>
            <span v-else>{{ row.memberLevel || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="miles" label="里程" width="70">
          <template #default="{ row }"><span class="tnum">{{ thousands(row.miles) }}</span></template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="75">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="135">
          <template #default="{ row }"><span class="tnum">{{ fmtDateTime(row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="primary" link size="small" @click="showEditDialog(row)">编辑</el-button>
              <el-button v-if="row.status === 1" type="warning" link size="small" @click="openAction(row, 'disable')">禁用</el-button>
              <el-button v-else type="success" link size="small" @click="openAction(row, 'enable')">启用</el-button>
              <el-button type="info" link size="small" @click="openAction(row, 'resetPwd')">重置密码</el-button>
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
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editVisible" title="编辑前台用户" width="420px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" label-width="88px">
        <el-form-item label="姓名">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" />
        </el-form-item>
        <el-form-item label="会员等级">
          <el-select v-model="editForm.memberLevel" style="width: 100%">
            <el-option v-for="(v, k) in MEMBER_LEVEL_MAP" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 禁用/启用/重置密码确认 -->
    <el-dialog v-model="actionVisible" title="提示" width="420px" destroy-on-close :close-on-click-modal="false">
      <div style="text-align:center">
        <el-icon :size="44" color="var(--color-warning)"><WarningFilled /></el-icon>
        <p style="font-size:15px;margin:12px 0 4px">{{ actionMessage }}</p>
      </div>
      <template #footer>
        <div style="display:flex;justify-content:center;gap:12px">
          <el-button @click="actionVisible = false">取消</el-button>
          <el-button type="primary" :loading="actionLoading" @click="confirmAction">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { getMemberList, updateMember, disableMember, enableMember, resetMemberPassword } from '@/api/members'
import { MEMBER_LEVEL_MAP } from '@/utils/constants'
import { fmtDateTime, shortId, thousands } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const memberList = ref<any[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const searchForm = reactive({ phone: '', name: '', memberLevel: '', status: undefined as number | undefined })

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: pagination.page, pageSize: pagination.pageSize }
    for (const [k, v] of Object.entries(searchForm)) {
      if (v !== '' && v != null) params[k] = v
    }
    const data = await getMemberList(params) as any
    memberList.value = data.list || []
    pagination.total = data.pagination?.total || 0
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
    memberList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function resetSearch() {
  Object.assign(searchForm, { phone: '', name: '', memberLevel: '', status: undefined })
  handleSearch()
}

// ---- 编辑 ----
const editVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editingId = ref('')
const editForm = reactive({ name: '', email: '', memberLevel: 'NORMAL' })

function showEditDialog(row: any) {
  editingId.value = row.id
  Object.assign(editForm, { name: row.name || '', email: row.email || '', memberLevel: row.memberLevel || 'NORMAL' })
  editVisible.value = true
}

async function submitEdit() {
  saving.value = true
  try {
    await updateMember(editingId.value, { ...editForm })
    ElMessage.success('更新成功')
    editVisible.value = false
    fetchList()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 禁用/启用/重置密码 ----
const actionVisible = ref(false)
const actionLoading = ref(false)
const actionMessage = ref('')
const actionUserId = ref('')
const actionType = ref<'disable' | 'enable' | 'resetPwd'>('disable')

function openAction(row: any, type: 'disable' | 'enable' | 'resetPwd') {
  actionUserId.value = row.id
  actionType.value = type
  if (type === 'disable') actionMessage.value = `确定禁用用户 ${row.name}？禁用后该用户将无法登录。`
  else if (type === 'enable') actionMessage.value = `确定启用用户 ${row.name}？`
  else actionMessage.value = `确定重置 ${row.name} 的密码为 Reset@123？`
  actionVisible.value = true
}

async function confirmAction() {
  actionLoading.value = true
  try {
    const id = actionUserId.value
    if (actionType.value === 'disable') { await disableMember(id); ElMessage.success('已禁用') }
    else if (actionType.value === 'enable') { await enableMember(id); ElMessage.success('已启用') }
    else { await resetMemberPassword(id); ElMessage.success('密码已重置为 Reset@123') }
    actionVisible.value = false
    fetchList()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '操作失败')
  } finally {
    actionLoading.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
/* .pagination-wrap 已收进全局样式 */
</style>
