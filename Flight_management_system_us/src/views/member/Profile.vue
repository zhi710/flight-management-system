<template>
  <div class="member-page page-container">
    <h2 class="page-title">{{ $t('member.title') }}</h2>

    <!-- Profile card -->
    <div class="profile-card card-shadow">
      <div class="profile-header">
        <el-upload
          class="avatar-uploader"
          action="#"
          :show-file-list="false"
          :auto-upload="false"
          :disabled="uploading"
          @change="handleAvatarChange"
        >
          <el-avatar :size="80" :src="getAvatarUrl(profile.avatar)" icon="UserFilled" />
          <div class="avatar-overlay">
            <el-icon v-if="!uploading"><Camera /></el-icon>
            <el-icon v-else class="is-loading"><Loading /></el-icon>
          </div>
        </el-upload>
        <div class="profile-info">
          <h3>{{ profile.name || $t('header.passenger') }}</h3>
          <div class="member-badge">
            <el-tag :type="levelTagType(profile.memberLevel)" effect="dark">
              {{ levelText(profile.memberLevel) }}
            </el-tag>
            <span class="member-no">{{ $t('member.memberNo') }}: {{ profile.memberNo }}</span>
          </div>
          <div class="miles-display">
            <router-link to="/home/member/miles">
              <span class="miles-num">{{ profile.miles || 0 }}</span>
              <span class="miles-label">{{ $t('member.miles') }} ></span>
            </router-link>
          </div>
        </div>
      </div>
    </div>

    <!-- Quick actions -->
    <div class="quick-actions">
      <div class="action-card card-shadow" @click="$router.push('/home/orders')">
        <el-icon :size="28" color="var(--color-primary)"><List /></el-icon>
        <span>{{ $t('member.myOrders') }}</span>
      </div>
      <div class="action-card card-shadow" @click="$router.push('/home/member/miles')">
        <el-icon :size="28" color="var(--color-warning)"><Medal /></el-icon>
        <span>{{ $t('member.myMiles') }}</span>
      </div>
      <div class="action-card card-shadow" @click="$router.push('/home/member/travelers')">
        <el-icon :size="28" color="var(--color-success)"><UserFilled /></el-icon>
        <span>{{ $t('member.myTravelers') }}</span>
      </div>
      <div class="action-card card-shadow" @click="$router.push('/home/help')">
        <el-icon :size="28" color="var(--text-secondary)"><QuestionFilled /></el-icon>
        <span>{{ $t('member.helpCenter') }}</span>
      </div>
    </div>

    <!-- 实名认证：购票的前置条件 -->
    <div class="realname-section card-shadow">
      <div class="realname-header">
        <h3>{{ $t('member.realname') }}</h3>
        <el-tag v-if="profile.realnameVerified" type="success" effect="dark">
          {{ $t('member.realnameVerified') }}
        </el-tag>
        <el-tag v-else type="warning" effect="dark">
          {{ $t('member.realnameUnverified') }}
        </el-tag>
      </div>

      <el-alert
        v-if="!profile.realnameVerified"
        type="warning"
        :closable="false"
        show-icon
        class="realname-alert"
      >
        {{ $t('member.realnameRequiredTip') }}
      </el-alert>

      <!-- 已认证：展示档案 -->
      <template v-if="profile.realnameVerified && !editingRealname">
        <el-descriptions :column="1" border class="realname-desc">
          <el-descriptions-item :label="$t('member.name')">{{ profile.name }}</el-descriptions-item>
          <el-descriptions-item :label="$t('member.idType')">{{ idTypeText(profile.idType) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('member.idNumber')">{{ profile.idNumber }}</el-descriptions-item>
          <el-descriptions-item :label="$t('member.realnameTime')">{{ formatTime(profile.realnameTime) }}</el-descriptions-item>
        </el-descriptions>
        <div class="realname-actions">
          <el-button @click="startEditRealname">{{ $t('member.modifyRealname') }}</el-button>
          <span class="realname-note">{{ $t('member.realnameLockedTip') }}</span>
        </div>
      </template>

      <!-- 未认证 / 修改中：表单 -->
      <el-form
        v-else
        :model="realnameForm"
        label-width="90px"
        style="max-width: 500px"
      >
        <el-form-item :label="$t('member.name')" required>
          <el-input v-model="realnameForm.name" :placeholder="$t('member.realNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('member.idType')" required>
          <el-select v-model="realnameForm.idType" style="width: 100%">
            <el-option :label="$t('member.idCard')" value="ID_CARD" />
            <el-option :label="$t('member.passport')" value="PASSPORT" />
            <el-option :label="$t('member.hmPassport')" value="HM_PASSPORT" />
            <el-option :label="$t('member.taiwanPassport')" value="TAIWAN_PASSPORT" />
            <el-option :label="$t('member.other')" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('member.idNumber')" required>
          <el-input
            v-model="realnameForm.idNumber"
            :placeholder="$t('member.idNumber')"
            @blur="onRealnameBlur"
          />
          <div class="field-hint">{{ $t(idNumberRule(realnameForm.idType)) }}</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="realnameSaving" @click="handleSubmitRealname">
            {{ $t('member.submitRealname') }}
          </el-button>
          <el-button v-if="profile.realnameVerified" @click="cancelEditRealname">
            {{ $t('common.cancel') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Edit form -->
    <div class="edit-section card-shadow">
      <h3>{{ $t('member.profile') }}</h3>
      <el-form :model="form" label-width="80px" style="max-width: 500px">
        <el-form-item :label="$t('member.name')">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="$t('member.gender')">
          <el-radio-group v-model="form.gender">
            <el-radio value="MALE">{{ $t('member.male') }}</el-radio>
            <el-radio value="FEMALE">{{ $t('member.female') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('member.birthday')">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('member.email')">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item :label="$t('member.phone')">
          <el-input :model-value="profile.phone" disabled />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('member.save') }}</el-button>
          <el-button @click="openPwdDialog">{{ $t('member.changePassword') }}</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="showPwdDialog" :title="$t('member.changePassword')" width="420px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item :label="$t('member.oldPassword')" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password :placeholder="$t('member.passwordPlaceholderOld')" />
        </el-form-item>
        <el-form-item :label="$t('member.newPassword')" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password :placeholder="$t('member.passwordPlaceholderNew')" />
        </el-form-item>
        <el-form-item :label="$t('member.confirmNewPassword')" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password :placeholder="$t('member.passwordPlaceholderConfirm')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPwdDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="handleChangePwd">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type UploadFile } from 'element-plus'
import { Camera, Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { updateProfile, uploadFile, changePassword, submitRealname } from '@/api/member'
import { getAvatarUrl } from '@/utils/image'
import { checkIdNumber, idNumberRule } from '@/utils/idNumber'

const userStore = useUserStore()
const { t } = useI18n()

const saving = ref(false)
const uploading = ref(false)
const profile = reactive({
  name: '',
  gender: '',
  birthday: '',
  email: '',
  phone: '',
  avatar: '',
  memberLevel: '',
  memberNo: '',
  miles: 0,
  idType: '',
  idNumber: '',
  realnameVerified: false,
  realnameTime: '',
})

const form = reactive({
  name: '',
  gender: '',
  birthday: '',
  email: '',
})

const showPwdDialog = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function levelText(level: string) {
  const map: Record<string, string> = {
    SILVER: t('member.silver'),
    GOLD: t('member.gold'),
    PLATINUM: t('member.platinum'),
    DIAMOND: t('member.diamond'),
  }
  return map[level] || t('member.normal')
}

function levelTagType(level: string) {
  const map: Record<string, string> = {
    SILVER: 'info',
    GOLD: 'warning',
    PLATINUM: '',
    DIAMOND: 'danger',
  }
  return map[level] 
}

async function handleAvatarChange(file: UploadFile) {
  if (!file.raw) return

  profile.avatar = URL.createObjectURL(file.raw)

  uploading.value = true
  try {
    const res: any = await uploadFile(file.raw)
    if (res.code === 200 && res.data?.path) {
      profile.avatar = res.data.path
      await updateProfile({ avatar: res.data.path })
      await userStore.fetchProfile()
      ElMessage.success(t('member.avatarUploadSuccess'))
    }
  } catch {
    ElMessage.error(t('member.avatarUploadFailed'))
  } finally {
    uploading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateProfile({
      name: form.name,
      gender: form.gender,
      birthday: form.birthday,
      email: form.email,
      avatar: profile.avatar,
    })
    ElMessage.success(t('member.saveSuccess'))
    await userStore.fetchProfile()
  } catch {
    // error handled
  } finally {
    saving.value = false
  }
}

// ---------------- 实名认证 ----------------
// 实名是账号与「人」的绑定，也是购票的前置条件：未实名不允许下单，
// 且订单中必须有一位乘客与这里的姓名 + 证件号完全一致。
const editingRealname = ref(false)
const realnameSaving = ref(false)
const realnameForm = reactive({
  name: '',
  idType: 'ID_CARD',
  idNumber: '',
})

function idTypeText(type: string) {
  const map: Record<string, string> = {
    ID_CARD: t('member.idCard'),
    PASSPORT: t('member.passport'),
    HM_PASSPORT: t('member.hmPassport'),
    TAIWAN_PASSPORT: t('member.taiwanPassport'),
    OTHER: t('member.other'),
  }
  return map[type] || type || ''
}

function formatTime(value: string) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

/** 证件号失焦即时提示，别等提交才被驳回 */
function onRealnameBlur() {
  if (!realnameForm.idNumber) return
  const err = checkIdNumber(realnameForm.idType, realnameForm.idNumber)
  if (err) ElMessage.warning(t(err))
}

function startEditRealname() {
  realnameForm.name = profile.name
  realnameForm.idType = profile.idType || 'ID_CARD'
  realnameForm.idNumber = profile.idNumber || ''
  editingRealname.value = true
}

function cancelEditRealname() {
  editingRealname.value = false
}

async function handleSubmitRealname() {
  const name = realnameForm.name.trim()
  if (!name) {
    ElMessage.warning(t('member.realnameNameRequired'))
    return
  }
  if (!realnameForm.idNumber.trim()) {
    ElMessage.warning(t('validation.idNumberEmpty'))
    return
  }
  const docError = checkIdNumber(realnameForm.idType, realnameForm.idNumber)
  if (docError) {
    ElMessage.warning(t(docError))
    return
  }

  realnameSaving.value = true
  try {
    await submitRealname({
      name,
      idType: realnameForm.idType,
      idNumber: realnameForm.idNumber.trim(),
    })
    ElMessage.success(t('member.realnameSuccess'))
    editingRealname.value = false
    const data = await userStore.fetchProfile()
    if (data) Object.assign(profile, data)
  } catch {
    // error handled
  } finally {
    realnameSaving.value = false
  }
}

function openPwdDialog() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  showPwdDialog.value = true
}

async function handleChangePwd() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword || !pwdForm.confirmPassword) {
    ElMessage.warning(t('member.passwordRequired'))
    return
  }
  if (pwdForm.newPassword.length < 8 || pwdForm.newPassword.length > 20) {
    ElMessage.warning(t('member.passwordLengthError'))
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning(t('member.passwordMismatchError'))
    return
  }
  pwdLoading.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success(t('member.passwordChanged'))
    showPwdDialog.value = false
  } catch {
    // error handled
  } finally {
    pwdLoading.value = false
  }
}

onMounted(async () => {
  const data = await userStore.fetchProfile()
  if (data) {
    Object.assign(profile, data)
    form.name = data.name 
    form.gender = data.gender 
    form.birthday = data.birthday 
    form.email = data.email 
    // 未实名时表单预填当前资料，少敲一次键盘
    if (!data.realnameVerified) {
      realnameForm.name = data.name || ''
    }
  }
})
</script>

<style scoped>
.member-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.profile-card {
  margin-bottom: 24px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
}

.avatar-uploader {
  position: relative;
  cursor: pointer;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 50%;
  opacity: 0;
  transition: opacity 0.2s;
  color: var(--text-inverse);
}

.avatar-uploader:hover .avatar-overlay {
  opacity: 1;
}

.profile-info h3 {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.member-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.member-no {
  font-size: 13px;
  color: var(--text-secondary);
}

.miles-display a {
  text-decoration: none;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.miles-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
}

.miles-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.action-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.25s;
  text-align: center;
}

.action-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.action-card span {
  font-size: 14px;
  color: var(--text-primary);
}

.edit-section h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.realname-section {
  padding: 20px;
  margin-bottom: 24px;
}

.realname-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.realname-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.realname-alert {
  margin-bottom: 16px;
}

.realname-desc {
  max-width: 560px;
}

.realname-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.realname-note {
  font-size: 12px;
  color: var(--text-secondary);
}

.field-hint {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-top: 4px;
}
</style>
