<template>
  <div class="dashboard">
    <h2>我的岗位</h2>
    <div style="margin-bottom:12px"><a-button size="small" @click="exportCsv">导出CSV</a-button></div>
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
        <div class="actions">
          <a-popconfirm v-if="job.status===4" title="确认删除该岗位？" ok-text="确认删除" cancel-text="取消" @confirm="deleteJob(job.id)">
            <a-button danger>删除岗位</a-button>
          </a-popconfirm>
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
  name: 'DepartmentJobs',
  setup() {
    const jobs = ref([])
    const loading = ref(true)

    const fetchJobs = async () => {
      try {
        const res = await request.get('/admin/jobs/all')
        jobs.value = res.data || []
      } catch (e) { message.error('获取岗位列表失败') }
      finally { loading.value = false }
    }

    const deleteJob = async (jobId) => {
      try {
        await request.delete(`/department/jobs/${jobId}`)
        message.success('岗位已删除')
        fetchJobs()
      } catch (e) { message.error('删除失败') }
    }

    // 通过 axios 携带 token 导出，避免 window.open 无 Authorization 被 401 拦截
    const exportCsv = async () => {
      try {
        const res = await request.get('/export/jobs/csv', { responseType: 'blob' })
        downloadBlob(res.data, '岗位列表.csv')
      } catch (e) { message.error('导出失败') }
    }

    const getStatusClass = (s) => {
      switch (s) { case 0: return 'status-pending'; case 1: return 'status-published'; case 2: return 'status-approved'; case 3: return 'status-rejected'; case 4: return 'status-full'; default: return 'status-pending' }
    }
    const getStatusText = (s) => {
      switch (s) { case 0: return '待审批'; case 1: return '招聘中'; case 2: return '已结束'; case 3: return '已拒绝'; case 4: return '已招满'; default: return '未知' }
    }

    onMounted(() => { fetchJobs() })
    return { jobs, loading, deleteJob, exportCsv, getStatusClass, getStatusText }
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
.status-approved { background: #f6ffed; color: #52c41a; border: 1px solid #b7eb8f; }
.status-rejected { background: #fff1f0; color: #ff4d4f; border: 1px solid #ffccc7; }
.status-full { background: #f9f0ff; color: #722ed1; border: 1px solid #d3adf7; }
.actions { margin-top: 12px; padding-top: 12px; border-top: 1px solid #e8e8e8; }
.loading { text-align: center; padding: 40px; color: #999; font-size: 16px; }
</style>
