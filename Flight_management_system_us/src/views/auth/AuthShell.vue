<template>
  <div class="auth-shell">
    <!-- 表单侧：桌面在左，移动端占满 -->
    <div class="auth-shell__panel">
      <div class="auth-card">
        <div class="auth-header">
          <div class="auth-logo">
            <el-icon :size="30" color="var(--color-primary)"><Promotion /></el-icon>
            <span>SkyTrip</span>
          </div>
          <h2>{{ title }}</h2>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>

        <slot />
      </div>
    </div>

    <!--
      照片侧：桌面在右，移动端用 order 提到顶部变成横幅。
      aria-hidden —— 纯装饰，读屏不必念。
    -->
    <div class="auth-shell__media" aria-hidden="true"></div>
  </div>
</template>

<script setup lang="ts">
import { Promotion } from '@element-plus/icons-vue'

defineProps<{
  /** 主标题，如「欢迎回来」 */
  title: string
  /** 标题下的一行说明 */
  subtitle?: string
}>()
</script>

<style scoped>
/**
 * 登录/注册共用的外壳。
 *
 * 布局只有一套，靠 flex-direction 在断点处翻转：
 *   桌面  → 左表单 | 右照片
 *   移动  → 上照片横幅 / 下表单
 *
 * 照片因此从「必须承担全屏氛围」降级为「装饰性侧栏」，
 * 原先「一张竖图铺满横屏、只用到中间 35% 高度」的裁切问题自然消失。
 * 详见 design-system/README.md 第 9 节。
 */
.auth-shell {
  min-height: 100vh;
  display: flex;
  background: var(--bg-page);
}

/* ---------- 表单侧 ---------- */
.auth-shell__panel {
  flex: 0 0 46%;
  min-width: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 40px 36px;
}

.auth-header {
  text-align: center;
  margin-bottom: 28px;
}

.auth-logo {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: 14px;
}

.auth-header h2 {
  font-size: var(--font-size-xl, 20px);
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.auth-header p {
  font-size: 14px;
  color: var(--text-secondary);
}

/* ---------- 照片侧 ---------- */
.auth-shell__media {
  flex: 1;
  min-width: 0;
  background: url('/auth-photo.webp') center 42% / cover no-repeat;
}

/* ---------- 移动端：翻转成上下结构 ---------- */
@media (max-width: 880px) {
  .auth-shell {
    flex-direction: column;
  }

  .auth-shell__media {
    order: -1; /* DOM 里在后，这里提到顶部当横幅 */
    flex: none;
    height: 168px;
    background-position: center 32%;
  }

  .auth-shell__panel {
    flex: 1;
    min-width: 0;
    align-items: flex-start;
    padding: 24px 16px 40px;
  }

  .auth-card {
    padding: 28px 22px;
    max-width: 440px;
    margin: 0 auto;
  }

  .auth-header {
    margin-bottom: 22px;
  }
}
</style>
