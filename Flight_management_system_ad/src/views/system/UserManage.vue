<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">用户管理</div>
        <el-button type="primary" @click="showUserDialog()"><el-icon><Plus /></el-icon> 新增用户</el-button>
      </div>

      <el-table scrollbar-always-on v-loading="loading" :data="userList" stripe>
        <el-table-column label="用户ID" width="120">
          <template #default="{ row }">
            <span class="text-mono tnum" :title="row.id">{{ shortId(row.id) }}</span>
          </template>
        </el-table-column>
        <!-- 用户名与姓名合成一列：两者都在回答"这是谁"，各占一列要 220px 不划算 -->
        <el-table-column label="用户名 / 姓名" width="120">
          <template #default="{ row }">
            <div class="cell-stack">
              <div class="cell-stack__main">{{ row.username }}</div>
              <div class="cell-stack__sub">{{ row.name }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="140" show-overflow-tooltip />
        <el-table-column prop="roleNames" label="角色" width="130">
          <template #default="{ row }">
            <el-tag v-for="(r, i) in (row.roleNames && row.roleNames.length ? row.roleNames : (row.roles || []))" :key="i" size="small" style="margin-right: 4px">{{ r }}</el-tag>
            <span v-if="!(row.roleNames && row.roleNames.length) && !(row.roles && row.roles.length)" style="color:var(--text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="175" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="primary" link size="small" @click="showUserDialog(row)">编辑</el-button>
              <el-button v-if="row.status === 1" type="warning" link size="small" @click="handleDisable(row)">禁用</el-button>
              <el-button v-else type="success" link size="small" @click="handleEnable(row)">启用</el-button>
              <el-button type="info" link size="small" @click="handleResetPwd(row)">重置密码</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 新增/编辑用户 -->
    <el-dialog v-model="userDialogVisible" :title="editingUser ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="88px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="!!editingUser" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="userForm.name" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="角色" prop="roles">
          <el-select v-model="userForm.roles" multiple style="width: 100%" placeholder="从角色权限中维护的角色中选择">
            <el-option v-for="r in roleList" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingUser" label="密码" prop="password">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- 禁用/启用/重置密码 确认对话框 -->
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
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getUserList, createUser, updateUser, disableUser, enableUser, resetPassword, getRoleList } from '@/api/system'
import { shortId } from '@/utils/format'

const loading = ref(false)
const userList = ref<any[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

/** 可分配角色（从 /admin/system/roles 动态加载） */
const roleList = ref<any[]>([])
async function fetchRoles() {
  try {
    roleList.value = (await getRoleList()) as any || []
  } catch {
    roleList.value = []
  }
}

const userDialogVisible = ref(false)
const editingUser = ref<any>(null)
const userFormRef = ref<FormInstance>()
const userForm = reactive({
  username: '', name: '', phone: '', email: '', roles: [] as string[], password: '',
})

const userRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  roles: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function fetchList() {
  loading.value = true
  try {
    const data = await getUserList({ page: pagination.page, pageSize: pagination.pageSize }) as any
    userList.value = data.list || data || []
    pagination.total = data.pagination?.total || 0
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
    userList.value = []
  } finally {
    loading.value = false
  }
}

function showUserDialog(row?: any) {
  editingUser.value = row || null
  if (row) {
    Object.assign(userForm, { username: row.username, name: row.name, phone: row.phone, email: row.email, roles: row.roles || [], password: '' })
  } else {
    Object.assign(userForm, { username: '', name: '', phone: '', email: '', roles: [], password: '' })
  }
  userDialogVisible.value = true
}

async function submitUser() {
  const valid = await userFormRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editingUser.value) {
      await updateUser(editingUser.value.id, { name: userForm.name, phone: userForm.phone, email: userForm.email, roles: userForm.roles })
      ElMessage.success('更新成功')
    } else {
      await createUser({ ...userForm })
      ElMessage.success('创建成功')
    }
    userDialogVisible.value = false
    fetchList()
  } catch (err: any) {
    ElMessage.error(err?.message || '保存用户失败')
  }
}

const actionVisible = ref(false)
const actionLoading = ref(false)
const actionMessage = ref('')
const actionUserId = ref('')
const actionType = ref<'disable' | 'enable' | 'resetPwd'>('disable')

function handleDisable(row: any) {
  actionUserId.value = row.id
  actionType.value = 'disable'
  actionMessage.value = `确定禁用用户 ${row.name}？`
  actionVisible.value = true
}

function handleEnable(row: any) {
  actionUserId.value = row.id
  actionType.value = 'enable'
  actionMessage.value = '确定启用该用户？'
  actionVisible.value = true
}

function handleResetPwd(row: any) {
  actionUserId.value = row.id
  actionType.value = 'resetPwd'
  actionMessage.value = `确定重置 ${row.name} 的密码？`
  actionVisible.value = true
}

async function confirmAction() {
  actionLoading.value = true
  try {
    const id = actionUserId.value
    if (actionType.value === 'disable') { await disableUser(id); ElMessage.success('已禁用') }
    else if (actionType.value === 'enable') { await enableUser(id); ElMessage.success('已启用') }
    else { await resetPassword(id); ElMessage.success('密码已重置') }
    actionVisible.value = false
    fetchList()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '操作失败')
  } finally { actionLoading.value = false }
}

onMounted(() => {
  fetchList()
  fetchRoles()
})
</script>

<style scoped>
/* .pagination-wrap 已收进全局样式 */
</style>
