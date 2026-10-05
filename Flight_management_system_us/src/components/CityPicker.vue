<template>
  <el-select
    :model-value="modelValue"
    filterable
    clearable
    :size="size"
    :placeholder="placeholder"
    class="city-picker"
    style="width: 100%"
    @update:model-value="onChange"
  >
    <template #prefix><el-icon><Location /></el-icon></template>

    <!-- 城市分组：先给「全部机场」城市级选项，再列该城市的各个机场 -->
    <el-option-group v-for="g in groups" :key="g.city" :label="g.city">
      <el-option :label="`${g.city}（全部机场）`" :value="g.city" />
      <el-option
        v-for="a in g.airports"
        :key="a.code"
        :label="`${a.city} · ${a.name} (${a.code})`"
        :value="a.code"
      />
    </el-option-group>
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAirports } from '@/api/flight'

withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  size?: 'large' | 'default' | 'small'
}>(), {
  placeholder: '城市 / 机场',
  size: 'default',
})

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

interface AirportItem { code: string; name: string; city: string }
interface CityGroup { city: string; airports: AirportItem[] }

const groups = ref<CityGroup[]>([])

/** 机场列表在首页、搜索页共用，进程内只请求一次 */
let cache: AirportItem[] | null = null
async function loadAirports(): Promise<AirportItem[]> {
  if (cache) return cache
  const res = (await getAirports()) as any
  cache = (res?.data || []) as AirportItem[]
  return cache
}

function onChange(value: unknown) {
  emit('update:modelValue', (value as string) || '')
}

onMounted(async () => {
  try {
    const list = await loadAirports()
    const map = new Map<string, AirportItem[]>()
    list.forEach((a) => {
      if (!a.city) return
      if (!map.has(a.city)) map.set(a.city, [])
      map.get(a.city)!.push(a)
    })
    groups.value = [...map.entries()].map(([city, airports]) => ({ city, airports }))
  } catch {
    // 加载失败时降级为「无候选」，用户仍可手输城市名或三字码（后端已支持解析）
  }
})
</script>
