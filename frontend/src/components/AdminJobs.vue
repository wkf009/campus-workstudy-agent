<template>
  <div class="dashboard">
    <h2>岗位管理（AI 审核工作台）</h2>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="jobs.length === 0" class="card"><p>暂无岗位信息</p></div>
    <div v-else>
      <div v-for="job in jobs" :key="job.id" class="card">
        <h3>{{ job.title }}</h3>
        <p><strong>部门：</strong>{{ job.departmentName }}</p>
        <p><strong>薪资：</strong>¥{{ job.salary }}</p>
        <p><strong>招聘名额：</strong>{{ job.quota }}</p>
        <p><strong>状态：</strong><span :class="['status-tag', getStatusClass(job.status)]">{{ getStatusText(job.status) }}</span></p>
        <p v-if="job.remark"><strong>审批备注：</strong>{{ job.remark }}</p>
        <div class="actions" v-if="job.status===0">
          <a-button type="primary" @click="audit(job.id, 1)">通过</a-button>
          <a-button danger @click="audit(job.id, 3)">拒绝</a-button>
          <a-button @click="generateReport(job.id)" :loading="generatingId === job.id">🤖 AI 预审</a-button>
        </div>

        <!-- AI 预审报告（Agent 2） -->
        <div v-if="reports[job.id]" class="ai-report">
          <div class="ai-report-header">
            <strong>🤖 AI 预审报告</strong>
            <a-tag :color="reportColor(reports[job.id].suggestion)">{{ suggestionText(reports[job.id].suggestion) }}</a-tag>
            <span class="ai-score">质量分 {{ reports[job.id].score }}</span>
          </div>
          <p v-if="reportList(reports[job.id].reasons).length"><strong>理由：</strong>{{ reportList(reports[job.id].reasons).join('；') }}</p>
          <p v-if="reportList(reports[job.id].suggestions).length"><strong>修改建议：</strong>{{ reportList(reports[job.id].suggestions).join('；') }}</p>
          <p v-if="reportList(reports[job.id].riskFlags).length" class="risk"><strong>风险：</strong>{{ reportList(reports[job.id].riskFlags).join('；') }}</p>
          <div class="actions" v-if="job.status===0">
            <a-button type="primary" size="small" @click="adopt(job.id)" :loading="adoptingId === job.id">✅ 采纳 AI 建议</a-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import request from '../utils/request.js'
import { message } from 'ant-design-vue'

export default {
  name: 'AdminJobs',
  setup() {
    const jobs = ref([])
    const loading = ref(true)
    const reports = ref({})        // jobId -> AuditReport
    const generatingId = ref(null)
    const adoptingId = ref(null)

    const fetch = async () => {
      try {
        const res = await request.get('/admin/jobs/all')
        jobs.value = res.data || []
      } catch (e) { message.error('获取岗位列表失败') }
      finally { loading.value = false }
    }

    const audit = async (jobId, result) => {
      try {
        await request.post('/admin/jobs/audit', { jobId, result, remark: result === 1 ? '审批通过' : '审批拒绝' })
        message.success(result === 1 ? '审批通过' : '已拒绝')
        fetch()
      } catch (e) { message.error('操作失败') }
    }

    // 生成 AI 预审报告
    const generateReport = async (jobId) => {
      generatingId.value = jobId
      try {
        // AI 预审含 LLM 调用，用长超时（默认 15s 不够）
        const res = await request.post(`/agent/audit/job/${jobId}/generate`, null, { timeout: 90000 })
        reports.value = { ...reports.value, [jobId]: res.data }
        message.success('AI 预审报告已生成')
      } catch (e) { console.error('AI 预审失败', e) }
      finally { generatingId.value = null }
    }

    // 一键采纳 AI 建议（HITL）
    const adopt = async (jobId) => {
      adoptingId.value = jobId
      try {
        const res = await request.post(`/agent/audit/job/${jobId}/adopt`)
        message.success(res.data?.message || '已采纳')
        fetch()
      } catch (e) { message.error('采纳失败') }
      finally { adoptingId.value = null }
    }

    // 后端存储的 JSON 数组字符串 → 数组
    const reportList = (s) => {
      if (!s) return []
      try { return JSON.parse(s) } catch (e) { return [] }
    }
    const suggestionText = (s) => ({ PASS: '建议通过', SUPPLEMENT: '需补充信息', REJECT: '建议拒绝' }[s] || s || '未知')
    const reportColor = (s) => ({ PASS: 'green', SUPPLEMENT: 'orange', REJECT: 'red' }[s] || 'default')

    const getStatusClass = (s) => {
      switch (s) { case 0: return 'status-pending'; case 1: return 'status-published'; case 2: return 'status-ended'; case 3: return 'status-rejected'; case 4: return 'status-full'; default: return 'status-pending' }
    }
    const getStatusText = (s) => {
      switch (s) { case 0: return '待审批'; case 1: return '招聘中'; case 2: return '已结束'; case 3: return '已拒绝'; case 4: return '已招满'; default: return '未知' }
    }

    onMounted(() => { fetch() })
    return { jobs, loading, reports, generatingId, adoptingId, audit, generateReport, adopt, reportList, suggestionText, reportColor, getStatusClass, getStatusText }
  }
}
</script>

<style scoped>
.dashboard { padding: 20px; }
h2 { margin-bottom: 20px; color: #333; }
.card { background-color: #f9f9f9; padding: 20px; border-radius: 8px; border: 1px solid #e8e8e8; margin-bottom: 16px; transition: all 0.3s ease; }
.card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.card h3 { margin: 0 0 12px 0; color: #1890ff; font-size: 18px; }
.card p { margin: 8px 0; color: #666; line-height: 1.6; }
.status-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.status-pending { background: #fff7e6; color: #fa8c16; border: 1px solid #ffd591; }
.status-published { background: #e6f7ff; color: #1890ff; border: 1px solid #91d5ff; }
.status-ended { background: #f6ffed; color: #52c41a; border: 1px solid #b7eb8f; }
.status-rejected { background: #fff1f0; color: #ff4d4f; border: 1px solid #ffccc7; }
.status-full { background: #f9f0ff; color: #722ed1; border: 1px solid #d3adf7; }
.actions { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
.ai-report { margin-top: 14px; padding: 14px 16px; background: linear-gradient(135deg, #f6ffed, #fafafa); border: 1px solid #b7eb8f; border-radius: 8px; }
.ai-report-header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.ai-score { color: #52c41a; font-weight: 600; }
.risk { color: #cf1322; }
.loading { text-align: center; padding: 40px; color: #999; font-size: 16px; }
</style>
