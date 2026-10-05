<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">系统配置</div>
        <el-button type="primary" :loading="saving" @click="handleSave"><el-icon><Check /></el-icon> 保存配置</el-button>
      </div>
    </div>

    <div class="card-panel" v-loading="loading">
      <el-form :model="configForm" label-width="160px" style="max-width: 700px">
        <h3 class="section-title">值机配置</h3>
        <el-form-item label="值机开放时间(小时)">
          <el-input-number v-model="configForm.checkinOpenHours" :min="1" :max="72" />
        </el-form-item>
        <el-form-item label="登机口关闭时间(分钟)">
          <el-input-number v-model="configForm.boardingCloseMinutes" :min="5" :max="60" />
        </el-form-item>

        <h3 class="section-title">售票配置</h3>
        <el-form-item label="停售截止时间(分钟)">
          <el-input-number v-model="configForm.saleCloseMinutes" :min="0" :max="240" />
        </el-form-item>

        <h3 class="section-title">机组状态时长</h3>
        <el-form-item label="航班到达后休息时长(分钟)">
          <el-input-number v-model="configForm.crewRestMinutes" :min="0" :max="10080" />
          <span class="form-tip">默认 720（12 小时），休息到期后按本月飞行时长自动转「待命」或「停飞」</span>
        </el-form-item>
        <el-form-item label="超限停飞时长(分钟)">
          <el-input-number v-model="configForm.crewGroundedMinutes" :min="0" :max="10080" />
          <span class="form-tip">默认 1440（24 小时），停飞到期后自动转「培训」</span>
        </el-form-item>
        <el-form-item label="停飞后培训时长(分钟)">
          <el-input-number v-model="configForm.crewTrainingMinutes" :min="0" :max="10080" />
          <span class="form-tip">默认 1440（24 小时），培训到期后自动恢复「待命」</span>
        </el-form-item>

        <h3 class="section-title">退改规则</h3>
        <el-form-item label="起飞前4小时退票费率">
          <el-input-number v-model="configForm.defaultRefundRule.before4h" :min="0" :max="1" :step="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="起飞前2小时退票费率">
          <el-input-number v-model="configForm.defaultRefundRule.before2h" :min="0" :max="1" :step="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="起飞后退票费率">
          <el-input-number v-model="configForm.defaultRefundRule.after4h" :min="0" :max="1" :step="0.01" :precision="2" />
        </el-form-item>

        <h3 class="section-title">通知模板</h3>
        <el-form-item label="短信-延误通知">
          <el-input v-model="configForm.notificationTemplates.SMS_DELAY" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="邮件-电子行程单">
          <el-input v-model="configForm.notificationTemplates.EMAIL_ETICKET" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSystemConfig, updateSystemConfig } from '@/api/system'

const loading = ref(false)
const saving = ref(false)

const configForm = reactive({
  checkinOpenHours: 24,
  boardingCloseMinutes: 15,
  saleCloseMinutes: 45,
  crewRestMinutes: 720,
  crewGroundedMinutes: 1440,
  crewTrainingMinutes: 1440,
  defaultRefundRule: {
    before4h: 0.05,
    before2h: 0.1,
    after4h: 0,
  },
  notificationTemplates: {
    SMS_DELAY: '',
    EMAIL_ETICKET: '',
  },
})

async function fetchConfig() {
  loading.value = true
  try {
    const data = await getSystemConfig() as any
    if (data) {
      configForm.checkinOpenHours = data.checkinOpenHours ?? 24
      configForm.boardingCloseMinutes = data.boardingCloseMinutes ?? 15
      configForm.saleCloseMinutes = Number(data.saleCloseMinutes) || 45
      configForm.crewRestMinutes = Number(data.crewRestMinutes) || 720
      configForm.crewGroundedMinutes = Number(data.crewGroundedMinutes) || 1440
      configForm.crewTrainingMinutes = Number(data.crewTrainingMinutes) || 1440
      if (data.defaultRefundRule) {
        Object.assign(configForm.defaultRefundRule, data.defaultRefundRule)
      }
      if (data.notificationTemplates) {
        Object.assign(configForm.notificationTemplates, data.notificationTemplates)
      }
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '加载配置失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateSystemConfig({ ...configForm })
    ElMessage.success('配置保存成功')
  } catch (err: any) {
    ElMessage.error(err?.message || '保存配置失败')
  } finally {
    saving.value = false
  }
}

onMounted(fetchConfig)
</script>

<style scoped>
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 24px 0 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color-light);
}
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
