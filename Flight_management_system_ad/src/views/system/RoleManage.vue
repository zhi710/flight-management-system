<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">角色权限</div>
        <el-button type="primary" @click="showRoleDialog()"><el-icon><Plus /></el-icon> 新增角色</el-button>
      </div>

      <el-table scrollbar-always-on v-loading="loading" :data="roleList" stripe>
        <el-table-column prop="roleId" label="角色ID" width="110" show-overflow-tooltip />
        <el-table-column prop="name" label="角色名称" width="130" />
        <el-table-column prop="code" label="角色代码" width="130" />
        <el-table-column label="权限" min-width="240">
          <template #default="{ row }">
            <el-tag v-for="p in row.permissions" :key="p" size="small" style="margin: 2px 4px 2px 0">{{ p }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showRoleDialog(row)">编辑</el-button>
            <el-tooltip :content="isBuiltin(row.code) ? '系统内置角色，不可删除' : '删除角色'" placement="top">
              <span>
                <el-button type="danger" link size="small" :disabled="isBuiltin(row.code)" @click="handleDelete(row)">删除</el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑角色 -->
    <el-dialog v-model="roleDialogVisible" :title="editingRole ? '编辑角色' : '新增角色'" width="560px" destroy-on-close>
      <el-form :model="roleForm" label-width="88px">
        <el-form-item label="角色名称">
          <el-input v-model="roleForm.name" />
        </el-form-item>
        <el-form-item label="角色代码">
          <el-input v-model="roleForm.code" :disabled="!!editingRole" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="权限">
          <div style="width: 100%">
            <p v-if="editingRole?.code === 'SUPER_ADMIN'" style="margin:0 0 6px;font-size:12px;color:var(--text-secondary)">
              超级管理员固定拥有全部权限（防误操作锁死系统），不可修改
            </p>
            <el-checkbox-group
              v-model="roleForm.permissions"
              :disabled="editingRole?.code === 'SUPER_ADMIN'"
            >
              <el-checkbox
                v-for="p in permissionList"
                :key="p.code"
                :value="p.code"
                class="perm-checkbox"
              >{{ p.name }}（{{ p.code }}）</el-checkbox>
            </el-checkbox-group>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRole">确定</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认对话框 -->
    <el-dialog v-model="deleteVisible" title="提示" width="420px" destroy-on-close :close-on-click-modal="false">
      <div style="text-align:center">
        <el-icon :size="44" color="var(--color-warning)"><WarningFilled /></el-icon>
        <p style="font-size:15px;margin:12px 0 4px">确定删除角色  <strong>{{ deleteName }}</strong>？</p>
      </div>
      <template #footer>
        <div style="display:flex;justify-content:center;gap:12px">
          <el-button @click="deleteVisible = false">取消</el-button>
          <el-button type="danger" :loading="deleteLoading" @click="confirmDeleteRole">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoleList, createRole, updateRole, deleteRole, getPermissionList } from '@/api/system'

const loading = ref(false)
const roleList = ref<any[]>([])

const roleDialogVisible = ref(false)
const editingRole = ref<any>(null)
const roleForm = reactive({ name: '', code: '', description: '', permissions: [] as string[] })

/** 可选权限（从 /admin/system/permissions 动态加载，字典唯一事实源在 sys_permission） */
const permissionList = ref<any[]>([])
async function fetchPermissions() {
  try {
    permissionList.value = (await getPermissionList()) as any || []
  } catch {
    permissionList.value = []
  }
}

async function fetchList() {
  loading.value = true
  try {
    const data = await getRoleList() as any
    roleList.value = data.list || data || []
  } catch (err: any) {
      ElMessage.error(err?.message || '操作失败')
    roleList.value = []
  } finally {
    loading.value = false
  }
}

function showRoleDialog(row?: any) {
  editingRole.value = row || null
  if (row) {
    Object.assign(roleForm, { name: row.name, code: row.code, description: row.description, permissions: [...(row.permissions || [])] })
  } else {
    Object.assign(roleForm, { name: '', code: '', description: '', permissions: [] })
  }
  roleDialogVisible.value = true
}

async function submitRole() {
  try {
    if (editingRole.value) {
      await updateRole(editingRole.value.roleId, { ...roleForm })
      ElMessage.success('更新成功')
    } else {
      await createRole({ ...roleForm })
      ElMessage.success('创建成功')
    }
    roleDialogVisible.value = false
    fetchList()
  } catch (err: any) {
    ElMessage.error(err?.message || '保存角色失败')
  }
}

const deleteVisible = ref(false)
const deleteLoading = ref(false)
const deleteName = ref('')
const deleteRoleId = ref('')

/** 系统内置角色禁止删除（与后端 PermissionBootstrap 内置名单一致，防管理员被锁死） */
const BUILTIN_CODES = ['SUPER_ADMIN', 'DISPATCHER', 'CHECKIN_STAFF', 'CUSTOMER_SERVICE']
function isBuiltin(code: string) {
  return BUILTIN_CODES.includes(code)
}

async function handleDelete(row: any) {
  deleteName.value = row.name
  deleteRoleId.value = row.roleId
  deleteVisible.value = true
}

async function confirmDeleteRole() {
  deleteLoading.value = true
  try {
    await deleteRole(deleteRoleId.value)
    ElMessage.success('删除成功')
    deleteVisible.value = false
    fetchList()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '删除角色失败')
  } finally {
    deleteLoading.value = false
  }
}

onMounted(() => {
  fetchList()
  fetchPermissions()
})
</script>

<style scoped>
.perm-checkbox {
  width: 240px;
  margin-right: 0;
  margin-bottom: 8px;
}
</style>
