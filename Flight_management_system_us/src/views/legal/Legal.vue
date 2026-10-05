<template>
  <AuthShell :title="title">
    <div class="legal-body">
      <p v-for="(para, i) in paragraphs" :key="i">{{ para }}</p>
    </div>

    <el-button size="large" style="width: 100%" @click="handleBack">
      {{ $t('common.back') }}
    </el-button>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AuthShell from '@/views/auth/AuthShell.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

/** /terms 与 /privacy 共用这一份版式，靠路由 meta.doc 区分内容 */
const isPrivacy = computed(() => route.meta.doc === 'privacy')

const title = computed(() => (isPrivacy.value ? t('auth.privacy') : t('auth.terms')))
const paragraphs = computed(() =>
  t(isPrivacy.value ? 'auth.privacyBody' : 'auth.termsBody')
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
)

function handleBack() {
  // 直接输地址进来的场景没有上一页，回登录页兜底
  if (window.history.length > 1) router.back()
  else router.push('/login')
}
</script>

<style scoped>
.legal-body {
  margin-bottom: 24px;
}

.legal-body p {
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-regular);
  margin-bottom: 12px;
}

.legal-body p:last-child {
  margin-bottom: 0;
}
</style>
