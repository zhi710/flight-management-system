<template>
  <div class="login-page">
    <!-- 表单侧：桌面在左 -->
    <div class="login-panel">
      <div class="login-card">
        <div class="login-header">
          <div class="login-logo">
            <el-icon :size="34" color="var(--color-primary)"><Promotion /></el-icon>
          </div>
          <h1 class="login-title">SkyOps</h1>
          <p class="login-subtitle">航班管理系统 · 管理端</p>
        </div>

        <div v-if="loginError" class="login-error" role="alert">
          <el-icon><CircleCloseFilled /></el-icon>
          <span>{{ loginError }}</span>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              :prefix-icon="Lock"
              autocomplete="current-password"
            />
          </el-form-item>

          <el-form-item v-if="showMfa" label="动态验证码" prop="mfaCode">
            <el-input
              v-model="form.mfaCode"
              :prefix-icon="Key"
              maxlength="6"
              autocomplete="one-time-code"
              placeholder="6 位动态码"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >登录</el-button>
          </el-form-item>
        </el-form>

        <!--
          管理端不提供自助重置密码（改密码要走内部审批），但必须给出「该怎么办」，
          否则用户只会看到一个没有出口的登录框。这里用纯文本而非链接：
          它不跳转到任何页面，做成链接反而会变成一个点了没反应的死链。
        -->
        <p class="login-hint">忘记密码或账号被锁定？请联系系统管理员</p>

        <div class="login-footer">© 2026 SkyOps 航班管理系统</div>
      </div>
    </div>

    <!-- 照片侧：桌面在右，移动端用 order 提到顶部当横幅 -->
    <div class="login-media" aria-hidden="true"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Key, CircleCloseFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const showMfa = ref(false)
const loginError = ref('')

const form = reactive({
  username: '',
  password: '',
  mfaCode: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于6位', trigger: 'blur' },
  ],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  loginError.value = ''
  try {
    await authStore.login({
      username: form.username,
      password: form.password,
      mfaCode: form.mfaCode || undefined,
    })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (err: any) {
    if (err?.message?.includes('MFA') || err?.code === 'MFA_REQUIRED') {
      showMfa.value = true
    } else {
      loginError.value = err?.message || '登录失败，请检查用户名和密码'
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
/**
 * 与前台登录页共用一套布局语言：
 *   桌面 → 左表单 | 右照片
 *   移动 → 上照片横幅 / 下表单
 * 卡片圆角、阴影、控件尺寸都取设计令牌，两个系统改一处即同步。
 */
.login-page {
  min-height: 100vh;
  display: flex;
  background: var(--bg-page);
}

/* ---------- 表单侧 ---------- */
.login-panel {
  flex: 0 0 46%;
  min-width: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 40px 36px 28px;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  background: $color-primary-bg;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: $text-primary;
  /* 原为 letter-spacing: 4px —— 中英混排下字距被拉散，且末字后也会留空隙 */
}

.login-subtitle {
  font-size: 13px;
  color: $text-secondary;
  margin-top: 6px;
}

/* 登录失败提示：与前台旅客端登录页保持一致 */
.login-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--color-danger-soft);
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-base);
  color: $color-danger;
  font-size: 14px;
  line-height: 1.5;
}

.login-btn {
  width: 100%;
  /* 与卡片内输入框同高（--el-component-size-large 只管输入框，
     EP 的按钮高度走自己硬编码的尺寸表，必须在这里显式钉住，
     否则紧凑档下会出现"输入框 44px、按钮 32px"的高低错位）。 */
  height: var(--auth-control-height);
  font-size: 15px;
  border-radius: var(--radius-control);
  /* 原为 letter-spacing: 8px —— 中文按钮会整体左偏 4px，观感也松散 */
}

.login-hint {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border-color-lighter);
  font-size: 12px;
  line-height: 1.6;
  color: $text-secondary;
  text-align: center;
}

.login-footer {
  text-align: center;
  margin-top: 14px;
  font-size: 12px;
  color: $text-placeholder;
}

/* ---------- 照片侧 ---------- */
.login-media {
  flex: 1;
  min-width: 0;
  background: url('/auth-photo.webp') center 42% / cover no-repeat;
}

/* 表单标签在 top 位置时略微收紧 */
:deep(.el-form-item) {
  margin-bottom: 18px;
}

:deep(.el-form-item__label) {
  font-size: 13px;
  color: $text-regular;
  padding-bottom: 4px;
  line-height: 1.4;
}

/* ---------- 移动端：翻转成上下结构 ---------- */
@media (max-width: 880px) {
  .login-page {
    flex-direction: column;
  }

  .login-media {
    order: -1;
    flex: none;
    height: 168px;
    background-position: center 32%;
  }

  .login-panel {
    flex: 1;
    min-width: 0;
    align-items: flex-start;
    padding: 24px 16px 40px;
  }

  /* 原为 width: 420px，在 390 屏上被压缩后左右贴边、毫无留白 */
  .login-card {
    max-width: 440px;
    margin: 0 auto;
    padding: 28px 22px;
  }

  .login-header {
    margin-bottom: 22px;
  }
}
</style>
