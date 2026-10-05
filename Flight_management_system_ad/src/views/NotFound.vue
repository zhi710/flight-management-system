<template>
  <div class="not-found">
    <div class="nf-visual" aria-hidden="true">
      <el-icon class="nf-plane"><Promotion /></el-icon>
      <span class="nf-code">404</span>
      <span class="nf-trail"></span>
    </div>

    <h1 class="nf-title">页面不存在</h1>
    <p class="nf-desc">该地址可能已失效，或您没有把它输入完整。左侧导航仍可使用。</p>

    <div class="nf-actions">
      <el-button type="primary" @click="goDashboard">返回监控大屏</el-button>
      <el-button @click="goBack">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goDashboard() {
  router.push('/dashboard')
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/dashboard')
  }
}
</script>

<style scoped lang="scss">
/* 与前台 404 用同一套视觉语言（同一品牌、同一图形隐喻），
   只在密度上跟随后台的 compact 档 */
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  min-height: 56vh;
  padding: var(--space-10) var(--space-card);
}

.nf-visual {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.nf-plane {
  font-size: 26px;
  color: var(--color-primary);
  transform: rotate(-12deg);
}

.nf-code {
  font-size: 56px;
  font-weight: var(--font-weight-bold);
  line-height: 1;
  letter-spacing: 0.04em;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.nf-trail {
  width: 48px;
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
  margin-bottom: var(--space-2);
}

.nf-desc {
  max-width: 420px;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  color: var(--text-secondary);
  margin-bottom: var(--space-5);
}

.nf-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}
</style>
