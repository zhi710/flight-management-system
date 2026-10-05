<template>
  <div class="help-page page-container">
    <h2 class="page-title">{{ $t('help.title') }}</h2>

    <!-- Search -->
    <div class="search-area">
      <el-input
        v-model="keyword"
        :placeholder="$t('help.searchPlaceholder')"
        size="large"
        clearable
        @keyup.enter="loadFaq"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- Category tabs -->
    <el-tabs v-model="activeCategory" @tab-change="loadFaq">
      <el-tab-pane :label="$t('help.all')" name="" />
      <el-tab-pane :label="$t('help.booking')" name="BOOKING" />
      <el-tab-pane :label="$t('help.checkin')" name="CHECKIN" />
      <el-tab-pane :label="$t('help.refund')" name="REFUND" />
      <el-tab-pane :label="$t('help.baggage')" name="BAGGAGE" />
      <el-tab-pane :label="$t('help.other')" name="OTHER" />
    </el-tabs>

    <!-- FAQ list -->
    <div class="faq-list">
      <el-collapse v-model="activeFaq">
        <el-collapse-item
          v-for="item in faqList"
          :key="item.id"
          :title="item.question"
          :name="item.id"
        >
          <div class="faq-answer">{{ item.answer }}</div>
        </el-collapse-item>
      </el-collapse>
      <el-empty v-if="faqList.length === 0 && !loading" :description="$t('common.noData')" />
    </div>

    <!-- Quick links -->
    <div class="quick-links">
      <h3>{{ $t('help.quickLinks') }}</h3>
      <div class="links-grid">
        <div class="link-card card-shadow" @click="activeCategory = 'REFUND'; loadFaq()">
          <el-icon :size="32" color="var(--color-primary)"><RefreshRight /></el-icon>
          <span>{{ $t('help.refundPolicy') }}</span>
        </div>
        <div class="link-card card-shadow" @click="activeCategory = 'BAGGAGE'; loadFaq()">
          <el-icon :size="32" color="var(--color-warning)"><Box /></el-icon>
          <span>{{ $t('help.baggageRules') }}</span>
        </div>
        <div class="link-card card-shadow" @click="activeCategory = 'CHECKIN'; loadFaq()">
          <el-icon :size="32" color="var(--color-success)"><Ticket /></el-icon>
          <span>{{ $t('help.checkinGuide') }}</span>
        </div>
        <div class="link-card card-shadow" @click="showFeedback = true">
          <el-icon :size="32" color="var(--text-secondary)"><ChatDotRound /></el-icon>
          <span>{{ $t('help.feedback') }}</span>
        </div>
      </div>
    </div>

    <!-- Feedback Dialog -->
    <el-dialog v-model="showFeedback" :title="$t('help.feedback')" width="500px">
      <el-form :model="feedbackForm" label-width="80px">
        <el-form-item :label="$t('help.feedbackType')">
          <el-radio-group v-model="feedbackForm.type">
            <el-radio value="COMPLAINT">{{ $t('help.complaint') }}</el-radio>
            <el-radio value="SUGGESTION">{{ $t('help.suggestion') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('help.relatedOrder')">
          <el-input v-model="feedbackForm.orderId" :placeholder="$t('help.required') " />
        </el-form-item>
        <el-form-item :label="$t('help.content')" required>
          <el-input v-model="feedbackForm.content" type="textarea" :rows="4" :placeholder="$t('help.contentPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('help.contactPhone')">
          <el-input v-model="feedbackForm.contactPhone" :placeholder="$t('help.required') " />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFeedback = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="feedbackLoading" @click="handleFeedback">{{ $t('help.submit') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getFaqList, submitFeedback } from '@/api/help'

const { t } = useI18n()
const loading = ref(false)
const keyword = ref('')
const activeCategory = ref('')
const activeFaq = ref<string[]>([])
const faqList = ref<any[]>([])
const showFeedback = ref(false)
const feedbackLoading = ref(false)

const feedbackForm = reactive({
  type: 'SUGGESTION' as 'COMPLAINT' | 'SUGGESTION',
  orderId: '',
  content: '',
  contactPhone: '',
})

async function loadFaq() {
  loading.value = true
  try {
    const res = await getFaqList({
      category: activeCategory.value || undefined,
      keyword: keyword.value || undefined,
    })
    faqList.value = res.data
  } catch {
    // error handled
  } finally {
    loading.value = false
  }
}

async function handleFeedback() {
  if (!feedbackForm.content) {
    ElMessage.warning(t('help.contentRequired') )
    return
  }
  feedbackLoading.value = true
  try {
    await submitFeedback({
      type: feedbackForm.type,
      orderId: feedbackForm.orderId || undefined,
      content: feedbackForm.content,
      contactPhone: feedbackForm.contactPhone || undefined,
    })
    ElMessage.success(t('help.submitSuccess'))
    showFeedback.value = false
    feedbackForm.content = ''
    feedbackForm.orderId = ''
    feedbackForm.contactPhone = ''
  } catch {
    // error handled
  } finally {
    feedbackLoading.value = false
  }
}

onMounted(loadFaq)
</script>

<style scoped>
.help-page {
  padding-top: 24px;
  padding-bottom: 48px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.search-area {
  max-width: 500px;
  margin-bottom: 24px;
}

.faq-list {
  margin-bottom: 32px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px 24px;
}

.faq-list :deep(.el-collapse-item__header) {
  font-size: 15px;
  font-weight: 500;
  padding: 14px 0;
}

.faq-list :deep(.el-collapse-item__content) {
  padding-bottom: 16px;
}

.faq-answer {
  font-size: 14px;
  color: var(--text-regular);
  line-height: 1.8;
  padding: 8px 0;
}

.quick-links h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.links-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.link-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 28px 16px;
  cursor: pointer;
  transition: all 0.25s;
  text-align: center;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.link-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-3px);
}

.link-card span {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}
</style>
