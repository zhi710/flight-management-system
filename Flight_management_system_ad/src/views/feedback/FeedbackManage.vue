<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">投诉建议</div>
      </div>

      <el-table scrollbar-always-on v-loading="loading" :data="list" stripe>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 'COMPLAINT' ? 'danger' : 'success'" size="small">
              {{ row.type === 'COMPLAINT' ? '投诉' : '建议' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="100">
          <template #default="{ row }">{{ row.userName || row.userPhone || '—' }}</template>
        </el-table-column>
        <el-table-column label="联系方式" width="115">
          <template #default="{ row }">{{ row.contactPhone || row.userPhone || '—' }}</template>
        </el-table-column>
        <el-table-column prop="content" label="反馈内容" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'REPLIED' ? 'success' : 'info'" size="small">
              {{ row.status === 'REPLIED' ? '已回复' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reply" label="回复内容" min-width="150" show-overflow-tooltip />
        <el-table-column label="提交时间" width="140">
          <template #default="{ row }"><span class="tnum">{{ fmtDateTime(row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openReply(row)">
              {{ row.status === 'REPLIED' ? '修改回复' : '回复' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 回复对话框 -->
    <el-dialog
      v-model="replyVisible"
      :title="replyRow?.status === 'REPLIED' ? '修改回复' : '回复反馈'"
      width="560px"
      destroy-on-close
    >
      <div class="fb-content-preview">{{ replyRow?.content }}</div>
      <el-form label-width="88px">
        <el-form-item label="回复">
          <el-input v-model="replyText" type="textarea" :rows="4" placeholder="请输入回复内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReply">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFeedbackList, replyFeedback } from '@/api/feedback'
import { fmtDateTime } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])

const replyVisible = ref(false)
const replyRow = ref<any>(null)
const replyText = ref('')

async function fetchList() {
  loading.value = true
  try {
    list.value = (await getFeedbackList()) as any
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '加载失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function openReply(row: any) {
  replyRow.value = row
  replyText.value = row.reply || ''
  replyVisible.value = true
}

async function submitReply() {
  if (!replyText.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  saving.value = true
  try {
    await replyFeedback(replyRow.value.id, replyText.value.trim())
    ElMessage.success('已回复')
    replyVisible.value = false
    fetchList()
  } catch (err: any) {
    if (!err?.handled) ElMessage.error(err?.message || '回复失败')
  } finally {
    saving.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.fb-content-preview {
  padding: 12px;
  margin-bottom: 16px;
  background: var(--bg-page);
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.6;
}
</style>
