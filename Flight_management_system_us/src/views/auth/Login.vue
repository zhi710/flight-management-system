<template>
  <AuthShell :title="$t('auth.welcomeBack')" :subtitle="$t('auth.loginDesc')">
    <!--
      登录方式切换用 el-segmented 而不是 <span @click>：
      后者没有 role / tabindex，键盘用户按 Tab 跳不过来，等于无法切换登录方式。
      el-segmented 内部基于 radio，天然可聚焦、可用方向键切换。
    -->
    <el-segmented v-model="mode" :options="modeOptions" size="large" block class="auth-mode" />

    <!-- 登录错误提示 -->
    <div v-if="loginError" class="login-error" role="alert">
      <el-icon><CircleCloseFilled /></el-icon>
      <span>{{ loginError }}</span>
    </div>

    <!-- 密码登录 -->
    <el-form
      v-if="mode === 'password'"
      ref="pwdFormRef"
      :model="pwdForm"
      :rules="pwdRules"
      label-position="top"
      @submit.prevent="handlePwdLogin"
    >
      <el-form-item :label="$t('auth.phone')" prop="phone">
        <el-input
          v-model="pwdForm.phone"
          size="large"
          :prefix-icon="Iphone"
          maxlength="11"
          autocomplete="username"
        />
      </el-form-item>
      <el-form-item :label="$t('auth.password')" prop="password">
        <el-input
          v-model="pwdForm.password"
          type="password"
          size="large"
          :prefix-icon="Lock"
          show-password
          autocomplete="current-password"
        />
      </el-form-item>
      <el-form-item v-if="showCaptcha" :label="$t('auth.captcha')" prop="captcha">
        <el-input
          v-model="pwdForm.captcha"
          size="large"
          :prefix-icon="Key"
          autocomplete="off"
        />
      </el-form-item>

      <div class="auth-extras">
        <el-checkbox v-model="rememberMe">{{ $t('auth.rememberMe') }}</el-checkbox>
        <button type="button" class="auth-link" @click="handleForgot">
          {{ $t('auth.forgotPassword') }}
        </button>
      </div>

      <el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          style="width: 100%"
          @click="handlePwdLogin"
        >{{ $t('auth.login') }}</el-button>
      </el-form-item>
    </el-form>

    <!-- 验证码登录 -->
    <el-form
      v-else
      ref="smsFormRef"
      :model="smsForm"
      :rules="smsRules"
      label-position="top"
      @submit.prevent="handleSmsLogin"
    >
      <el-form-item :label="$t('auth.phone')" prop="phone">
        <el-input
          v-model="smsForm.phone"
          size="large"
          :prefix-icon="Iphone"
          maxlength="11"
          autocomplete="username"
        />
      </el-form-item>
      <el-form-item :label="$t('auth.smsCode')" prop="smsCode">
        <div class="sms-row">
          <el-input
            v-model="smsForm.smsCode"
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
      <el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          style="width: 100%"
          @click="handleSmsLogin"
        >{{ $t('auth.login') }}</el-button>
      </el-form-item>
    </el-form>

    <div class="third-party">
      <el-divider>{{ $t('auth.otherLogin') }}</el-divider>
      <!--
        用品牌色 + 品牌图形，并带上文字名：原先拿「对话气泡 / 钱包」两个通用图标
        冒充微信支付宝，用户根本认不出是哪个渠道。
        页面上也不再有「暂未开放」的常驻说明 —— 未完成的功能不必占着位置，
        点的时候提示即可（见 handleOAuth）。
      -->
      <div class="oauth-btns">
        <button type="button" class="oauth-btn" @click="handleOAuth('wechat')">
          <svg class="oauth-icon" viewBox="0 0 24 24" aria-hidden="true">
            <ellipse cx="9.4" cy="8.6" rx="7.4" ry="6.4" fill="#07C160" />
            <circle cx="6.9" cy="7.1" r="1.05" fill="#fff" />
            <circle cx="11.8" cy="7.1" r="1.05" fill="#fff" />
            <ellipse cx="16.6" cy="15.2" rx="5.9" ry="5.3" fill="#07C160" />
            <circle cx="14.7" cy="14" r="0.92" fill="#fff" />
            <circle cx="18.5" cy="14" r="0.92" fill="#fff" />
          </svg>
          <span>{{ $t('auth.wechat') }}</span>
        </button>
        <button type="button" class="oauth-btn" @click="handleOAuth('alipay')">
          <svg class="oauth-icon" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="10" fill="#1677FF" />
            <path
              d="M7.2 8.4h9.6M12 5.6v6.2M7.6 13.2c2.4 3 6.2 4.1 9.4 3.9M16.4 10.9c-1.6 2.7-4.4 4.8-7.8 5.9"
              fill="none" stroke="#fff" stroke-width="1.5" stroke-linecap="round"
            />
          </svg>
          <span>{{ $t('auth.alipay') }}</span>
        </button>
      </div>
    </div>

    <div class="auth-footer">
      <span>{{ $t('auth.noAccount') }}</span>
      <router-link to="/register">{{ $t('auth.registerNow') }}</router-link>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Iphone, Lock, Key, CircleCloseFilled } from '@element-plus/icons-vue'
import AuthShell from './AuthShell.vue'
import { useUserStore } from '@/stores/user'
import { sendSmsCode } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()

const loginError = ref('')
const mode = ref<'password' | 'sms'>('password')
const loading = ref(false)
const showCaptcha = ref(false)
const countdown = ref(0)

const modeOptions = computed(() => [
  { label: t('auth.loginByPassword'), value: 'password' },
  { label: t('auth.loginBySms'), value: 'sms' },
])

/** 「记住我」只存手机号，不存密码 —— 密码留在浏览器密码管理器里更安全。 */
const REMEMBER_KEY = 'skytrip_remembered_phone'
const rememberMe = ref(false)

const pwdFormRef = ref<FormInstance>()
const smsFormRef = ref<FormInstance>()

const pwdForm = reactive({
  phone: '',
  password: '',
  captcha: '',
})

const smsForm = reactive({
  phone: '',
  smsCode: '',
})

const pwdRules: FormRules = {
  phone: [
    { required: true, message: t('auth.phoneRequired'), trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: t('auth.phoneInvalid'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: t('auth.passwordRequired'), trigger: 'blur' },
    { min: 8, max: 20, message: t('auth.passwordInvalid'), trigger: 'blur' },
  ],
}

const smsRules: FormRules = {
  phone: [
    { required: true, message: t('auth.phoneRequired'), trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: t('auth.phoneInvalid'), trigger: 'blur' },
  ],
  smsCode: [
    { required: true, message: t('auth.smsCodeRequired'), trigger: 'blur' },
    { pattern: /^\d{6}$/, message: t('auth.smsCodeInvalid'), trigger: 'blur' },
  ],
}

onMounted(() => {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    pwdForm.phone = saved
    rememberMe.value = true
  }
})

function syncRememberedPhone() {
  if (rememberMe.value) localStorage.setItem(REMEMBER_KEY, pwdForm.phone)
  else localStorage.removeItem(REMEMBER_KEY)
}

async function handlePwdLogin() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(pwdForm.phone, pwdForm.password, pwdForm.captcha || undefined)
    syncRememberedPhone()
    ElMessage.success(t('auth.loginSuccess'))
    const redirect = (route.query.redirect as string) || '/home'
    router.push(redirect)
  } catch (err: any) {
    if (err?.message?.includes('验证码') || err?.message?.includes('captcha')) {
      showCaptcha.value = true
    } else {
      loginError.value = err?.message || t('auth.loginFailedPwd')
    }
  } finally {
    loading.value = false
  }
}

async function handleSmsLogin() {
  const valid = await smsFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.loginBySms(smsForm.phone, smsForm.smsCode)
    ElMessage.success(t('auth.loginSuccess'))
    const redirect = (route.query.redirect as string) || '/home'
    router.push(redirect)
  } catch (err: any) {
    loginError.value = err?.message || t('auth.loginFailedSms')
  } finally {
    loading.value = false
  }
}

async function handleSendSms() {
  if (!smsForm.phone || !/^1[3-9]\d{9}$/.test(smsForm.phone)) {
    ElMessage.warning(t('auth.phoneInvalid'))
    return
  }
  try {
    await sendSmsCode({ phone: smsForm.phone, type: 'login' })
    ElMessage.success(t('auth.smsCodeSent'))
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch {
    // error handled
  }
}

/**
 * 忘记密码：系统已提供「验证码登录」通道，登入后可在个人中心改密码，
 * 所以这里给出可执行的路径，而不是一个点了没反应的死链。
 */
function handleForgot() {
  ElMessageBox.alert(t('auth.forgotPasswordHint'), t('auth.forgotPassword'), {
    confirmButtonText: t('common.ok'),
    showClose: false,
  }).catch(() => {})
  mode.value = 'sms'
}

/**
 * 第三方快捷登录（微信 / 支付宝）。
 *
 * 后端 /auth/oauth/{provider} 目前是占位实现（只返回提示文案，不含授权跳转地址）。
 * 前端原来的写法是「请求成功 → 取 data.url 跳转」，取不到 url 就什么都不做，
 * 表现为「点了没反应」的假死体验。这里改为：点击即给出明确的「开发中」提示。
 *
 * 后续接入真实 OAuth 时，把这里替换为：拿到授权地址后 window.location.href = 授权地址。
 */
function handleOAuth(provider: 'wechat' | 'alipay') {
  const brand = provider === 'wechat' ? t('auth.wechat') : t('auth.alipay')
  ElMessage.warning({
    message: t('auth.oauthDeveloping', { provider: brand }),
    duration: 2500,
    showClose: true,
  })
}
</script>

<style scoped>
.auth-mode {
  margin-bottom: 20px;
}

.login-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--color-danger-soft);
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-base);
  color: var(--color-danger);
  font-size: 14px;
  line-height: 1.5;
}

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

/* 记住我 / 忘记密码：同一行两端对齐 */
.auth-extras {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -6px 0 16px;
}

.auth-link {
  border: none;
  background: none;
  padding: 4px 0;
  font-size: 13px;
  color: var(--color-primary);
  cursor: pointer;
}

.auth-link:hover {
  color: var(--color-primary-hover);
  text-decoration: underline;
}

.third-party {
  margin-top: 20px;
}

.third-party :deep(.el-divider__text) {
  font-size: 13px;
  color: var(--text-secondary);
}

.oauth-btns {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.oauth-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: var(--auth-control-height);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background: var(--bg-card);
  color: var(--text-regular);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.18s, color 0.18s;
}

.oauth-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.oauth-icon {
  width: 18px;
  height: 18px;
  flex: none;
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

/* 表单项在 top 标签下的下间距，比 EP 默认更紧凑一点 */
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
