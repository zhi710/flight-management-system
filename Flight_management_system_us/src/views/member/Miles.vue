<template>
  <div class="miles-page page-container">
    <h2 class="page-title">{{ $t('member.miles') }}</h2>

    <div v-if="loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else>
      <!-- Miles overview -->
      <div class="miles-overview card-shadow">
        <div class="overview-main">
          <div class="balance">
            <span class="label">{{ $t('member.milesBalance') }}</span>
            <span class="value">{{ milesData.balance?.toLocaleString() }}</span>
          </div>
          <div v-if="milesData.expiringMiles" class="expiring">
            <el-icon><WarningFilled /></el-icon>
            <span>{{ $t('member.expiringSoon') }}: {{ milesData.expiringMiles }} {{ $t('member.miles') }} {{ milesData.expiringDate }}</span>
          </div>
        </div>

        <!-- Level progress -->
        <div v-if="milesData.levelProgress" class="level-progress">
          <div class="level-info">
            <span>{{ $t('member.currentLevel') }}: <el-tag size="small">{{ milesData.levelProgress.current }}</el-tag></span>
            <span>{{ $t('member.nextLevel') }}: <el-tag size="small" type="info">{{ milesData.levelProgress.next }}</el-tag></span>
          </div>
          <el-progress
            :percentage="milesData.levelProgress.progress"
            :stroke-width="12"
            color="var(--color-primary)"
          />
          <div class="progress-text">
            <template v-if="remainingMiles > 0">
              {{ $t('member.requiredMiles') }}: {{ remainingMiles.toLocaleString() }} {{ $t('member.miles') }}
            </template>
            <template v-else>{{ $t('member.maxLevelReached') }}</template>
          </div>
        </div>
      </div>

      <!-- Miles records -->
      <div class="records-section card-shadow">
        <h3>{{ $t('member.milesDetail') }}</h3>
        <el-table :data="milesData.records" style="width: 100%">
          <el-table-column prop="date" :label="$t('member.date')" width="120" />
          <el-table-column :label="$t('member.type')" width="100">
            <template #default="{ row }">
              <el-tag :type="row.type === 'EARN' ? 'success' : 'warning'" size="small">
                {{ row.type === 'EARN' ? $t('member.earn') : $t('member.redeem') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" :label="$t('member.description')" />
          <el-table-column :label="$t('member.miles')" width="120" align="right">
            <template #default="{ row }">
              <span :class="['miles-value', row.type === 'EARN' ? 'earn' : 'use']">
                {{ row.type === 'EARN' ? '+' : '-' }}{{ Math.abs(row.miles) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMiles } from '@/api/member'

const loading = ref(true)
const milesData = ref<any>({})

/** 距下一等级还需的里程；已是最高等级（后端 returned required = 0）时为 0 */
const remainingMiles = computed(() => {
  const lp = milesData.value.levelProgress
  if (!lp) return 0
  return Math.max(0, (lp.required || 0) - (milesData.value.balance || 0))
})

onMounted(async () => {
  try {
    const res = await getMiles()
    milesData.value = res.data
  } catch {
    ElMessage.error('加载里程信息失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.miles-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.miles-overview {
  margin-bottom: 24px;
}

.overview-main {
  margin-bottom: 24px;
}

.balance .label {
  font-size: 14px;
  color: var(--text-secondary);
  display: block;
  margin-bottom: 4px;
}

.balance .value {
  font-size: 42px;
  font-weight: 700;
  color: var(--color-primary);
}

.expiring {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-warning);
}

.level-progress {
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

.level-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}

.progress-text {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.records-section h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.miles-value.earn {
  color: var(--color-success);
  font-weight: 600;
}

.miles-value.use {
  color: var(--color-danger);
  font-weight: 600;
}
</style>
