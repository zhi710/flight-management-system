<template>
  <div class="not-found">
    <div class="nf-visual" aria-hidden="true">
      <!-- 用飞机尾迹串起 404，呼应品牌图形，避免纯数字的廉价感 -->
      <el-icon class="nf-plane"><Promotion /></el-icon>
      <span class="nf-code">404</span>
      <span class="nf-trail"></span>
    </div>

    <h1 class="nf-title">{{ $t('error.notFoundTitle') }}</h1>
    <p class="nf-desc">{{ $t('error.notFoundDesc') }}</p>

    <div class="nf-actions">
      <el-button type="primary" @click="goHome">{{ $t('error.backHome') }}</el-button>
      <el-button @click="goBack">{{ $t('error.backPrev') }}</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/home')
}

function goBack() {
  // 直接输错地址进来时没有上一页，此时退回首页而不是卡在原地
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/home')
  }
}
</script>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  min-height: 56vh;
  padding: var(--space-10) var(--space-5);
}

.nf-visual {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}

.nf-plane {
  font-size: 30px;
  color: var(--color-primary);
  /* 轻微仰角，像正在爬升 */
  transform: rotate(-12deg);
}

.nf-code {
  font-size: 64px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.04em;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

/* 虚线尾迹：把"迷路的航班"这个意象说清楚 */
.nf-trail {
  width: 56px;
  height: 2px;
  border-radius: var(--radius-full);
  background: repeating-linear-gradient(
    90deg,
    var(--color-primary-border) 0 6px,
    transparent 6px 12px
  );
}

.nf-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--text-primary);
  margin-bottom: var(--space-3);
}

.nf-desc {
  max-width: 420px;
  font-size: var(--font-size-base);
  line-height: var(--line-height-relaxed);
  color: var(--text-secondary);
  margin-bottom: var(--space-6);
}

.nf-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

/* 窄屏：按钮改为竖排并撑满，避免两个按钮挤成一行后文字换行 */
@media (max-width: 480px) {
  .nf-code {
    font-size: 48px;
  }

  .nf-actions {
    flex-direction: column;
    align-self: stretch;
  }

  .nf-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
