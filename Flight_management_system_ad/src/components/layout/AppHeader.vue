<template>
  <div class="app-header">
    <div class="header-left">
      <el-icon
        class="collapse-btn"
        :size="20"
        @click="appStore.toggleSidebar"
      >
        <Fold v-if="!appStore.sidebarCollapsed" />
        <Expand v-else />
      </el-icon>
    </div>

    <div class="header-right">
      <!-- 告警通知铃铛 -->
      <el-tooltip content="告警通知" placement="bottom">
        <el-badge :value="notificationStore.pendingCount" :hidden="notificationStore.pendingCount === 0" class="alert-badge">
          <el-icon class="header-action" :size="18" @click="router.push('/alerts')">
            <Bell />
          </el-icon>
        </el-badge>
      </el-tooltip>

      <!-- 全屏 -->
      <el-tooltip content="全屏" placement="bottom">
        <el-icon class="header-action" :size="18" @click="toggleFullscreen">
          <FullScreen />
        </el-icon>
      </el-tooltip>

      <!-- 用户信息 -->
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="32" :src="authStore.avatar || undefined" class="user-avatar">
            {{ authStore.name?.charAt(0) || 'A' }}
          </el-avatar>
          <span class="user-name">{{ authStore.name || authStore.username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>个人信息
            </el-dropdown-item>
            <el-dropdown-item command="password">
              <el-icon><Key /></el-icon>修改密码
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>

  <!-- 个人信息对话框 -->
  <el-dialog v-model="profileVisible" title="个人信息" width="560px" destroy-on-close @open="fetchProfile">
    <el-form v-loading="profileLoading" :model="profileForm" label-width="88px">
      <el-form-item label="用户名">
        <el-input v-model="profileForm.username" disabled />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="profileForm.name" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="profileForm.phone" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="profileForm.email" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="profileVisible = false">取消</el-button>
      <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
    </template>
  </el-dialog>

  <!-- 修改密码对话框 -->
  <el-dialog v-model="passwordVisible" title="修改密码" width="420px" destroy-on-close>
    <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="88px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="pwdForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="pwdForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordVisible = false">取消</el-button>
      <el-button type="primary" :loading="pwdSaving" @click="changePassword">确定</el-button>
    </template>
  </el-dialog>

  <!-- 退出确认对话框 -->
  <el-dialog v-model="logoutVisible" title="提示" width="420px" destroy-on-close :close-on-click-modal="false">
    <div class="logout-confirm">
      <el-icon :size="40" color="var(--color-warning)" style="display:block;margin:0 auto 12px"><WarningFilled /></el-icon>
      <p style="text-align:center;font-size:15px">确定退出登录？</p>
    </div>
    <template #footer>
      <div style="display:flex;justify-content:center;gap:12px">
        <el-button @click="logoutVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmLogout">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { getAdminProfile, updateAdminProfile, changeAdminPassword } from '@/api/system'

const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()

// ---- 个人信息 ----
const profileVisible = ref(false)
const profileLoading = ref(false)
const profileSaving = ref(false)
const profileForm = reactive({ username: '', name: '', phone: '', email: '' })

async function fetchProfile() {
  profileLoading.value = true
  try {
    const data = await getAdminProfile() as any
    profileForm.username = data.username || ''
    profileForm.name = data.name || ''
    profileForm.phone = data.phone || ''
    profileForm.email = data.email || ''
  } catch {} finally {
    profileLoading.value = false
  }
}

async function saveProfile() {
  profileSaving.value = true
  try {
    await updateAdminProfile({ name: profileForm.name, phone: profileForm.phone, email: profileForm.email })
    ElMessage.success('保存成功')
    profileVisible.value = false
  } catch {} finally {
    profileSaving.value = false
  }
}

// ---- 退出登录 ----
const logoutVisible = ref(false)

function confirmLogout() {
  authStore.logout()
  ElMessage.success('已退出登录')
  logoutVisible.value = false
  router.push('/login')
}

// ---- 修改密码 ----
const passwordVisible = ref(false)
const pwdSaving = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码不少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: (_rule: any, v: string, cb: any) => v === pwdForm.newPassword ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' },
  ],
}

async function changePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  pwdSaving.value = true
  try {
    await changeAdminPassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功')
    passwordVisible.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } catch {} finally {
    pwdSaving.value = false
  }
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    logoutVisible.value = true
  } else if (cmd === 'password') {
    passwordVisible.value = true
  } else if (cmd === 'profile') {
    profileVisible.value = true
  }
}
</script>

<style scoped lang="scss">

.app-header {
  height: $header-height;
  background: $bg-card;
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
}

.collapse-btn {
  cursor: pointer;
  color: $text-secondary;
  transition: color 0.2s;

  &:hover {
    color: $color-primary;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-action {
  cursor: pointer;
  color: $text-secondary;
  transition: color 0.2s;

  &:hover {
    color: $color-primary;
  }
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;

  &:hover {
    background: $bg-page;
  }
}

.user-avatar {
  background: $color-primary;
  color: var(--text-inverse);
  font-size: 14px;
}

.user-name {
  font-size: 14px;
  color: $text-primary;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-badge {
  display: flex;
  align-items: center;
}
</style>
