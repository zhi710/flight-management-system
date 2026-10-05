<template>
  <el-config-provider :locale="epLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
// Root component — all layout is handled by router
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getElementPlusLocale } from '@/locales'

const { locale } = useI18n()

/**
 * Element Plus 自身组件（日期选择器、分页、确认框按钮…）的文案。
 * 走 el-config-provider 而不是 app.use 的初始 locale，切换语言时才能即时生效。
 */
const epLocale = computed(() => getElementPlusLocale(locale.value))

/** 同步 <html lang>：浏览器据此选字形、屏幕阅读器据此切发音 */
watch(
  locale,
  (v) => {
    document.documentElement.setAttribute('lang', v)
  },
  { immediate: true },
)
</script>
