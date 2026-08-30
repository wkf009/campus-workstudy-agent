<template>
  <div class="dashboard">
    <h2>申请管理</h2>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="applications.length === 0" class="card"><p>暂无申请记录</p></div>
    <div v-else>
      <div v-for="app in applications" :key="app.id" class="card">
        <h3>{{ app.jobTitle || '岗位' + app.jobId }}</h3>
        <p><strong>申请人：</strong>{{ app.studentName || '用户' + app.userId }}</p>
        <p><strong>申请状态：</strong><span :class="['status-tag', getStatusClass(app.status)]">{{ getStatusText(app.status) }}</span></p>
        <p><strong>申请时间：</strong>{{ app.applyTime || '未知' }}</p>
        <p v-if="app.auditRemark"><strong>审核备注：</strong>{{ app.auditRemark }}</p>
        <div class="actions" v-if="app.status === 0">
          <a-button type="primary" @click="process(app.id, 1)">录用</a-button>
          <a-button danger @click="process(app.id, 2)">拒绝</a-button>
          <a-button @click="evaluateMatch(app.id)" :loading="evaluatingId === app.id">🤖 AI 匹配度</a-button>
        </div>

        <!-- AI 匹配度评估（Agent 2） -->
        <div v-if="matches[app.id]" class="ai-match">
          <div class="ai-match-header">
            <strong>🤖 AI 匹配度</strong>
            <a-progress :percent="Number(matches[app.id].score || 0)" :stroke-color="matchColor(matches[app.id].score)" style="width:180px" />
          </div>
          <p v-if="matchList(matches[app.id].reasons).length"><strong>匹配点：</strong>{{ matchList(matches[app.id].reasons).join('；') }}</p>
          <p v-if="matchList(matches[app.id].suggestions).length"><strong>差距点：</strong>{{ matchList(matches[app.id].suggestions).join('；') }}</p>
          <p v-if="matchList(matches[app.id].riskFlags).length"><strong>面试建议：</strong>{{ matchList(matches[app.id].riskFlags).join('；') }}</p>
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
  name: 'DepartmentApplications',
  setup() {
    const applications = ref([])
    const loading = ref(true)
    const matches = ref({})        // applicationId -> AuditReport
    const evaluatingId = ref(null)

    const fetch = async () => {
      try {
        const res = await request.get('/department/applications')
        applications.value = res.data || (res.applications ? res.applications : [])
      } catch (e) { message.error('获取申请记录失败') }
      finally { loading.value = false }
    }

    const process = async (applicationId, result) => {
      try {
        await request.post('/department/applications/process', { applicationId, result, remark: result === 1 ? '审批通过' : '审批拒绝' })
        message.success(result === 1 ? '录用成功' : '已拒绝')
        fetch()
      } catch (e) { message.error('操作失败') }
    }

    // AI 匹配度评估（Agent 2 辅助决策）
    const evaluateMatch = async (applicationId) => {
      evaluatingId.value = applicationId
      try {
        const res = await request.post(`/agent/audit/match/${applicationId}`)
        matches.value = { ...matches.value, [applicationId]: res.data }
        message.success('AI 匹配度评估完成')
      } catch (e) { message.error('评估失败，请确认已配置通义千问 API Key') }
      finally { evaluatingId.value = null }
    }

    const matchList = (s) => {
      if (!s) return []
      try { return JSON.parse(s) } catch (e) { return [] }
    }
    const matchColor = (score) => (score >= 80 ? '#52c41a' : score >= 60 ? '#faad14' : '#ff4d4f')

    const getStatusClass = (s) => {
      switch (s) { case 0: return 'status-pending'; case 1: return 'status-approved'; case 2: return 'status-rejected'; default: return 'status-pending' }
    }
    const getStatusText = (s) => {
      switch (s) { case 0: return '待处理'; case 1: return '已通过'; case 2: return '已拒绝'; case 3: return '已取消'; default: return '未知' }
    }

    onMounted(() => { fetch() })
    return { applications, loading, matches, evaluatingId, process, evaluateMatch, matchList, matchColor, getStatusClass, getStatusText }
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
.actions { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
.status-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.status-pending { background: #fff7e6; color: #fa8c16; border: 1px solid #ffd591; }
.status-approved { background: #f6ffed; color: #52c41a; border: 1px solid #b7eb8f; }
.status-rejected { background: #fff1f0; color: #ff4d4f; border: 1px solid #ffccc7; }
.ai-match { margin-top: 14px; padding: 14px 16px; background: linear-gradient(135deg, #e6f7ff, #fafafa); border: 1px solid #91d5ff; border-radius: 8px; }
.ai-match-header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.loading { text-align: center; padding: 40px; color: #999; font-size: 16px; }
</style>
