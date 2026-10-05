<template>
  <AuthShell :title="$t('auth.createAccount')" :subtitle="$t('auth.registerDesc')">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleRegister"
    >
      <el-form-item :label="$t('auth.phone')" prop="phone">
        <el-input
          v-model="form.phone"
          size="large"
          :prefix-icon="Iphone"
          maxlength="11"
          autocomplete="tel"
        />
      </el-form-item>

      <el-form-item :label="$t('auth.smsCode')" prop="smsCode">
        <div class="sms-row">
          <el-input
            v-model="form.smsCode"
            size="large"
            :prefix-icon="Key"
            maxlength="6"
            autocomplete="one-time-code"
          />
          <el-button size="large" :disabled="countdown > 0" @click="handleSendSms">
            {{ countdown > 0 ? $t('auth.resendAfter', { seconds: countdown }) : $t('auth.getSmsCode') }}
          </el-button>
        </div>
      </el-form-item>

      <!--
        密码放在验证码之后、姓名之前：
        原先顺序是 手机号 → 验证码 → 姓名 → 密码 → 确认密码，
        姓名夹在中间会打断「设置登录凭据」这条连续动作。
      -->
      <el-form-item :label="$t('auth.password')" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          size="large"
          :prefix-icon="Lock"
          show-password
          autocomplete="new-password"
          :placeholder="$t('auth.passwordPlaceholder')"
        />
      </el-form-item>

      <el-form-item :label="$t('auth.confirmPassword')" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          size="large"
          :prefix-icon="Lock"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>

      <el-form-item :label="$t('auth.name')" prop="name">
        <el-input
          v-model="form.name"
          size="large"
          :prefix-icon="User"
          autocomplete="name"
          :placeholder="$t('auth.nameOptional')"
        />
      </el-form-item>

      <el-form-item class="agree-item">
        <!--
          协议链接指向真实页面（原先 href="javascript:void(0)"，点了没反应，
          而用户被要求「我已阅读并同意」—— 看不到内容却要同意，是合规硬伤）。
          新标签打开，不打断注册流程。
        -->
        <el-checkbox v-model="agreeTerms" :class="{ 'is-error': agreeError }">
          <span>{{ $t('auth.agreeTerms') }}</span>
          <router-link to="/terms" target="_blank" @click.stop>{{ $t('auth.terms') }}</router-link>
          <span>{{ $t('auth.and') }}</span>
          <router-link to="/privacy" target="_blank" @click.stop>{{ $t('auth.privacy') }}</router-link>
        </el-checkbox>
      </el-form-item>

      <el-form-item>
        <!--
          按钮不再用 disabled：禁用态只会让用户困惑「为什么点不了」。
          改为允许点击，未勾选时给出明确提示并高亮勾选框。
        -->
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          style="width: 100%"
          @click="handleRegister"
        >{{ $t('auth.register') }}</el-button>
      </el-form-item>
    </el-form>

    <div class="auth-footer">
      <span>{{ $t('auth.hasAccount') }}</span>
      <router-link to="/login">{{ $t('auth.loginNow') }}</router-link>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Iphone, Lock, Key, User } from '@element-plus/icons-vue'
import AuthShell from './AuthShell.vue'
import { useUserStore } from '@/stores/user'
import { sendSmsCode } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

const formRef = ref<FormInstance>()
const loading = ref(false)
const countdown = ref(0)
const agreeTerms = ref(false)
const agreeError = ref(false)

const form = reactive({
  phone: '',
  smsCode: '',
  password: '',
  confirmPassword: '',
  name: '',
})

const validateConfirmPwd = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error(t('auth.passwordMismatch')))
  } else {
    callback()
  }
}

/**
 * 密码强度校验。
 * 原先前端只校验 8–20 位长度，但 placeholder 写着「含字母和数字」、
 * 后端也按这个规则拒 —— 用户填纯数字会在提交时才被后端打回，前后端规则不一致。
 * 这里补齐，让提示文案与真实规则对齐。
 */
const validatePassword = (_rule: any, value: string, callback: any) => {
  if (!value) return callback()
  if (value.length < 8 || value.length > 20) {
    return callback(new Error(t('auth.passwordInvalid')))
  }
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return callback(new Error(t('auth.passwordInvalid')))
  }
  callback()
}

const rules: FormRules = {
  phone: [
    { required: true, message: t('auth.phoneRequired'), trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: t('auth.phoneInvalid'), trigger: 'blur' },
  ],
  smsCode: [
    { required: true, message: t('auth.smsCodeRequired'), trigger: 'blur' },
    { pattern: /^\d{6}$/, message: t('auth.smsCodeInvalid'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: t('auth.passwordRequired'), trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: t('auth.confirmPasswordRequired'), trigger: 'blur' },
    { validator: validateConfirmPwd, trigger: 'blur' },
  ],
}

watch(agreeTerms, (v) => {
  if (v) agreeError.value = false
})

async function handleSendSms() {
  if (!form.phone || !/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning(t('auth.phoneInvalid'))
    return
  }
  try {
    await sendSmsCode({ phone: form.phone, type: 'register' })
    ElMessage.success(t('auth.smsCodeSent'))
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch {
    ElMessage.error(t('auth.smsCodeSendFailed'))
  }
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (!agreeTerms.value) {
    agreeError.value = true
    ElMessage.warning(t('auth.agreeTermsRequired'))
    return
  }

  loading.value = true
  try {
    await userStore.register({
      phone: form.phone,
      smsCode: form.smsCode,
      password: form.password,
      name: form.name || undefined,
    })
    ElMessage.success(t('auth.registerSuccess'))
    router.push('/home')
  } catch (err: any) {
    ElMessage.error(err?.message || t('auth.registerFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.sms-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.sms-row :deep(.el-input) {
  flex: 1;
  min-width: 0;
}

.sms-row :deep(.el-button) {
  flex: none;
}

.agree-item {
  margin-bottom: 12px;
}

.agree-item :deep(.el-checkbox) {
  height: auto;
  align-items: flex-start;
  white-space: normal;
}

.agree-item :deep(.el-checkbox__input) {
  margin-top: 2px;
}

.agree-item :deep(.el-checkbox__label) {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: normal;
}

/* 未勾选就提交时，用红色边框指出问题所在（而不是默默禁用按钮） */
.agree-item :deep(.is-error .el-checkbox__inner) {
  border-color: var(--color-danger);
}

.agree-item a {
  color: var(--color-primary);
  margin: 0 3px;
}

.auth-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: var(--text-secondary);
}

.auth-footer a {
  color: var(--color-primary);
  font-weight: 500;
  margin-left: 4px;
}

:deep(.el-form-item) {
  margin-bottom: 18px;
}

:deep(.el-form-item__label) {
  font-size: 13px;
  color: var(--text-regular);
  padding-bottom: 4px;
  line-height: 1.4;
}
</style>
