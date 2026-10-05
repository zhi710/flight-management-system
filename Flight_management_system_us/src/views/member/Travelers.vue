<template>
  <div class="travelers-page page-container">
    <div class="page-header">
      <h2 class="page-title">{{ $t('member.travelers') }}</h2>
      <el-button type="primary" @click="showAddDialog = true">
        <el-icon><Plus /></el-icon> {{ $t('member.addTraveler') }}
      </el-button>
    </div>

    <div v-loading="loading">
      <div v-if="travelers.length === 0" class="empty">
        <el-empty :description="$t('member.noTravelers') ">
          <el-button type="primary" @click="showAddDialog = true">{{ $t('member.addFirstTraveler')  }}</el-button>
        </el-empty>
      </div>

      <div v-for="t in travelers" :key="t.id" class="traveler-card card-shadow">
        <div class="traveler-info">
          <div class="name-row">
            <span class="name">{{ t.name }}</span>
            <el-tag size="small">{{ genderText(t.gender) }}</el-tag>
            <el-tag v-if="t.passengerType" size="small" type="info">{{ paxTypeText(t.passengerType) }}</el-tag>
          </div>
          <div class="detail-row">
            <span>{{ idTypeText(t.idType) }}: {{ t.idNumber }}</span>
            <span v-if="t.phone">{{ $t('member.phone') }}: {{ t.phone }}</span>
            <span v-if="t.frequentFlyerNo">
              {{ $t('order.frequentFlyerNo') }}: {{ t.frequentFlyerNo }}
            </span>
          </div>
        </div>
        <div class="traveler-actions">
          <el-button text type="primary" @click="editTraveler(t)">{{ $t('common.edit') }}</el-button>
          <el-button text type="danger" @click="handleDelete(t.id)">{{ $t('common.delete') }}</el-button>
        </div>
      </div>
    </div>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="showAddDialog"
      :title="editingId ? $t('member.editTraveler') : $t('member.addTraveler')"
      width="500px"
    >
      <el-form :model="form" label-width="80px">
        <el-form-item :label="$t('member.name')" required>
          <el-input v-model="form.name" :placeholder="$t('order.namePlaceholder') " />
        </el-form-item>
        <el-form-item :label="$t('member.gender')" required>
          <el-radio-group v-model="form.gender">
            <el-radio value="MALE">{{ $t('member.male') }}</el-radio>
            <el-radio value="FEMALE">{{ $t('member.female') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('member.idType')" required>
          <el-select v-model="form.idType" style="width: 100%">
            <el-option :label="$t('member.idCard')" value="ID_CARD" />
            <el-option :label="$t('member.passport')" value="PASSPORT" />
            <el-option :label="$t('member.hmPassport')" value="HM_PASSPORT" />
            <el-option :label="$t('member.taiwanPassport')" value="TAIWAN_PASSPORT" />
            <el-option :label="$t('member.other')" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('order.idNumber')" required>
          <el-input v-model="form.idNumber" :placeholder="$t('order.idNumberPlaceholder')" />
          <div class="ffp-help">{{ $t(idNumberRule(form.idType)) }}</div>
        </el-form-item>
        <el-form-item :label="$t('member.travelerType')">
          <el-select v-model="form.passengerType" style="width: 100%">
            <el-option :label="$t('member.adult')" value="ADULT" />
            <el-option :label="$t('member.child')" value="CHILD" />
            <el-option :label="$t('member.infant')" value="INFANT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('member.phone')">
          <el-input v-model="form.phone" :placeholder="$t('help.required') " />
        </el-form-item>
        <el-form-item :label="$t('member.email')">
          <el-input v-model="form.email" :placeholder="$t('help.required') " />
        </el-form-item>
        <el-form-item :label="$t('order.frequentFlyerNo')">
          <el-input v-model="form.frequentFlyerNo" disabled :placeholder="$t('order.ffpNoPlaceholderAuto')" />
          <div class="ffp-help">{{ $t('member.ffpNoHelp') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTravelers, addTraveler, updateTraveler, deleteTraveler } from '@/api/member'
import { checkIdNumber, idNumberRule } from '@/utils/idNumber'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const showAddDialog = ref(false)
const editingId = ref('')
const travelers = ref<any[]>([])

const form = reactive({
  name: '',
  gender: 'MALE',
  idType: 'ID_CARD',
  idNumber: '',
  passengerType: 'ADULT',
  phone: '',
  email: '',
  frequentFlyerNo: '',
})

function genderText(g: string) {
  return g === 'MALE' ? t('member.male') : t('member.female')
}

function paxTypeText(pt: string) {
  const map: Record<string, string> = {
    ADULT: t('member.adult'),
    CHILD: t('member.child'),
    INFANT: t('member.infant'),
  }
  return map[pt] || pt
}

function idTypeText(it: string) {
  const map: Record<string, string> = {
    ID_CARD: t('member.idCard'),
    PASSPORT: t('member.passport'),
    HM_PASSPORT: t('member.hmPassport'),
    TAIWAN_PASSPORT: t('member.taiwanPassport'),
    OTHER: t('member.other'),
  }
  return map[it] || it
}

function editTraveler(t: any) {
  editingId.value = t.id
  Object.assign(form, {
    name: t.name,
    gender: t.gender,
    idType: t.idType,
    idNumber: t.idNumber,
    passengerType: t.passengerType ,
    phone: t.phone ,
    email: t.email ,
    frequentFlyerNo: t.frequentFlyerNo ,
  })
  showAddDialog.value = true
}

function resetForm() {
  editingId.value = ''
  Object.assign(form, {
    name: '',
    gender: 'MALE',
    idType: 'ID_CARD',
    idNumber: '',
    passengerType: 'ADULT',
    phone: '',
    email: '',
    frequentFlyerNo: '',
  })
}

async function handleSave() {
  if (!form.name) {
    ElMessage.warning(t('member.nameIdRequired') )
    return
  }
  // 证件号格式在前端先挡一道（后端仍会再校验一次，这里只为即时反馈）
  const docError = checkIdNumber(form.idType, form.idNumber)
  if (docError) {
    ElMessage.warning(t(docError) )
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateTraveler(editingId.value, { ...form })
      ElMessage.success(t('member.updateSuccess'))
    } else {
      await addTraveler({ ...form })
      ElMessage.success(t('member.addSuccess'))
    }
    showAddDialog.value = false
    resetForm()
    await loadTravelers()
  } catch {
    // error handled
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm(t('member.confirmDelete'), t('common.delete'), { type: 'warning' })
    await deleteTraveler(id)
    ElMessage.success(t('member.deleteSuccess'))
    await loadTravelers()
  } catch {
    // cancelled
  }
}

async function loadTravelers() {
  loading.value = true
  try {
    const res = await getTravelers()
    travelers.value = res.data
  } catch {
    // error handled
  } finally {
    loading.value = false
  }
}

onMounted(loadTravelers)
</script>

<style scoped>
.travelers-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
}

.traveler-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.detail-row {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--text-secondary);
}

.empty {
  padding: 60px 0;
}

/* 常旅客号说明：格式与用途都不是自明的，放在输入框下方一行 */
.ffp-help {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
  margin-top: 4px;
}
</style>
