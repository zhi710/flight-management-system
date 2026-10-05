<template>
  <div class="breadcrumb-wrap" v-if="breadcrumbs.length > 0">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
        {{ item.title }}
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const breadcrumbs = computed(() => {
  const items: { title: string; path: string }[] = []
  const meta = route.meta as any

  if (meta?.parent) {
    items.push({ title: meta.parent, path: '' })
  }
  if (meta?.title && route.path !== '/dashboard') {
    items.push({ title: meta.title, path: route.path })
  }

  return items
})
</script>

<style scoped lang="scss">

.breadcrumb-wrap {
  padding: 16px 20px 0;
}
</style>
