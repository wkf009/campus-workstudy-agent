<template>
  <div class="dashboard">
    <h2>我的申请</h2>
    <div style="margin-bottom:12px"><a-button size="small" @click="exportCsv">导出CSV</a-button></div>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="applications.length === 0" class="card"><p>暂无申请记录</p></div>
    <div v-else>
      <div v-for="app in applications" :key="app.id" class="card">
        <h3>{{ app.jobTitle || '岗位' + app.jobId }}</h3>
        <p><strong>申请状态：</strong><span :class="['status-tag', getStatusClass(app.status)]">{{ getStatusText(app.status) }}</span></p>
        <p><strong>申请时间：</strong>{{ app.applyTime || '未知' }}</p>
        <p v-if="app.auditRemark"><strong>备注：</strong>{{ app.auditRemark }}</p>

        <!-- AI 替代推荐（场景 2：被拒后撮合） -->
        <div v-if="app.status === 2" class="ai-rematch">
          <a-button size="small" type="dashed" @click="showRematch(app)" :loading="rematchLoadingId === app.id">🤖 查看 AI 替代推荐</a-button>
          <div v-if="rematchList[app.id] && rematchList[app.id].length" class="rematch-list">
            <div v-for="rec in rematchList[app.id]" :key="rec.jobId" class="rematch-item">
              <span class="rematch-title">{{ rec.title }}（¥{{ rec.salary }}/时 · {{ rec.reason }}）</span>
              <a-button size="small" type="primary" @click="transfer(app, rec.jobId)">一键转投</a-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import request, { downloadBlob } from '../utils/request.js'
import { message } from 'ant-design-vue'

export default {
  name: 'StudentApplications',
  setup() {
    const applications = ref([])
    const loading = ref(true)
    const rematchList = ref({})       // applicationId -> 替代推荐列表
    const rematchLoadingId = ref(null)

    const fetchApplications = async () => {
      try {
        const user = JSON.parse(sessionStorage.getItem('user'))
        const res = await request.get(`/student/applications/${user.id}`)
        applications.value = res.data || res
      } catch (e) { message.error('获取申请记录失败') }
      finally { loading.value = false }
    }

    // 通过 axios 携带 token 导出，避免 window.open 无 Authorization 被 401 拦截
    const exportCsv = async () => {
      const user = JSON.parse(sessionStorage.getItem('user'))
      try {
        const res = await request.get(`/export/applications/csv?userId=${user.id}`, { responseType: 'blob' })
        downloadBlob(res.data, '申请记录.csv')
      } catch (e) { message.error('导出失败') }
    }

    // 查看被拒后的 AI 替代推荐（场景 2 撮合结果）
    const showRematch = async (app) => {
      rematchLoadingId.value = app.id
      try {
        const res = await request.get(`/agent/rematch/${app.id}`)
        rematchList.value = { ...rematchList.value, [app.id]: res.data || [] }
      } catch (e) { message.error('获取 AI 推荐失败') }
      finally { rematchLoadingId.value = null }
    }

    // 一键转投替代岗位
    const transfer = async (app, jobId) => {
      try {
        await request.post('/student/applications', { jobId, resumeUrl: '', coverLetter: `由 AI 撮合转投（原申请 #${app.id}）` })
        message.success('已转投成功！')
        fetchApplications()
      } catch (e) { message.error('转投失败，可能已申请过该岗位') }
    }

    const getStatusClass = (status) => {
      switch (status) {
        case 0: return 'status-pending'
        case 1: return 'status-approved'
        case 2: return 'status-rejected'
        default: return 'status-pending'
      }
    }

    const getStatusText = (status) => {
      switch (status) {
        case 0: return '待处理'
        case 1: return '已通过'
        case 2: return '已拒绝'
        case 3: return '已取消'
        default: return '未知状态'
      }
    }

    onMounted(() => { fetchApplications() })
    return { applications, loading, rematchList, rematchLoadingId, showRematch, transfer, exportCsv, getStatusClass, getStatusText }
  }
}
</script>

<style scoped>
.dashboard { padding: 20px; }
h2 { margin-bottom: 20px; color: #333; }
.card { background-color: #f9f9f9; padding: 20px; border-radius: 8px; border: 1px solid #e8e8e8; margin-bottom: 16px; }
.card h3 { margin: 0 0 12px 0; color: #1890ff; font-size: 18px; }
.card p { margin: 8px 0; color: #666; line-height: 1.6; }
.status-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.status-pending { background: #fff7e6; color: #fa8c16; border: 1px solid #ffd591; }
.status-approved { background: #f6ffed; color: #52c41a; border: 1px solid #b7eb8f; }
.status-rejected { background: #fff1f0; color: #ff4d4f; border: 1px solid #ffccc7; }
.ai-rematch { margin-top: 12px; padding: 10px 12px; background: #fffbe6; border: 1px solid #ffe58f; border-radius: 6px; }
.rematch-list { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.rematch-item { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 6px 10px; background: #fff; border-radius: 4px; }
.rematch-title { font-size: 13px; color: #333; flex: 1; }
.loading { text-align: center; padding: 40px; color: #999; font-size: 16px; }
</style>
