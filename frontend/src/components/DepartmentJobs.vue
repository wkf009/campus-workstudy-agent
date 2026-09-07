<template>
  <div class="dashboard">
    <h2>我的岗位</h2>
    <div style="margin-bottom:12px"><a-button size="small" @click="exportCsv">导出CSV</a-button></div>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="jobs.length === 0" class="card"><p>暂无岗位信息</p></div>
    <div v-else>
      <div class="list-toolbar">
        <a-select v-model:value="sort" style="width:150px" @change="onSortChange">
          <a-select-option value="latest">最新发布</a-select-option>
          <a-select-option value="salary_desc">薪资从高到低</a-select-option>
          <a-select-option value="salary_asc">薪资从低到高</a-select-option>
        </a-select>
      </div>
      <div v-for="job in sortedJobs" :key="job.id" class="card">
        <h3>{{ job.title }}</h3>
        <div class="info-grid">
          <span class="info-item"><strong>部门：</strong>{{ job.departmentName }}</span>
          <span class="info-item"><strong>薪资：</strong>¥{{ job.salary }}</span>
          <span class="info-item"><strong>招聘名额：</strong>{{ job.quota }}</span>
          <span class="info-item"><strong>状态：</strong><span :class="['status-tag', getStatusClass(job.status)]">{{ getStatusText(job.status) }}</span></span>
        </div>
        <p v-if="job.remark" class="info-remark"><strong>审批备注：</strong>{{ job.remark }}</p>

        <!-- 被打回（状态5）：突出显示 + 修改重提交 -->
        <div v-if="job.status === 5" class="sendback-box">
          <strong>⛔ 该岗位被管理员打回</strong>
          <p v-if="job.remark" class="sendback-reason">{{ job.remark }}</p>
          <a-button type="primary" size="small" @click="openRework(job)">✏️ 修改并重新提交</a-button>
        </div>

        <div class="actions">
          <a-popconfirm v-if="job.status===4" title="确认删除该岗位？" ok-text="确认删除" cancel-text="取消" @confirm="deleteJob(job.id)">
            <a-button danger>删除岗位</a-button>
          </a-popconfirm>
        </div>
      </div>
    </div>

    <!-- 修改被打回岗位 -->
    <a-modal v-model:open="reworkVisible" title="修改岗位并重新提交（将重新 AI 预审）" @ok="submitRework" @cancel="reworkVisible=false" okText="提交" cancelText="取消" :confirmLoading="reworkLoading">
      <a-form :model="reworkForm" layout="vertical">
        <a-form-item label="岗位标题"><a-input v-model:value="reworkForm.title" /></a-form-item>
        <a-form-item label="详细描述"><a-textarea v-model:value="reworkForm.description" :rows="3" /></a-form-item>
        <a-form-item label="任职要求"><a-textarea v-model:value="reworkForm.requirements" :rows="2" /></a-form-item>
        <a-form-item label="薪资（元/时）"><a-input v-model:value="reworkForm.salary" /></a-form-item>
        <a-form-item label="招聘名额"><a-input-number v-model:value="reworkForm.quota" :min="1" style="width:100%" /></a-form-item>
        <a-form-item label="工作时间"><a-input v-model:value="reworkForm.workTime" /></a-form-item>
        <a-form-item label="工作地点"><a-input v-model:value="reworkForm.location" /></a-form-item>
        <a-form-item label="联系人"><a-input v-model:value="reworkForm.contactPerson" /></a-form-item>
        <a-form-item label="联系电话"><a-input v-model:value="reworkForm.contactPhone" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import request, { downloadBlob } from '../utils/request.js'
import { message } from 'ant-design-vue'

export default {
  name: 'DepartmentJobs',
  setup() {
    const jobs = ref([])
    const loading = ref(true)
    const reworkVisible = ref(false)
    const reworkLoading = ref(false)
    const reworkForm = ref({})
    const sort = ref('latest')

    // 前端排序：latest=最新发布在前；salary_desc/salary_asc=按薪资
    const sortedJobs = computed(() => {
      const list = [...jobs.value]
      if (sort.value === 'salary_asc') return list.sort((a, b) => (a.salary || 0) - (b.salary || 0))
      if (sort.value === 'salary_desc') return list.sort((a, b) => (b.salary || 0) - (a.salary || 0))
      return list.sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
    })
    const onSortChange = () => { /* sortedJobs computed 自动重排 */ }

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

    // 打开被打回岗位的编辑
    const openRework = (job) => {
      reworkForm.value = {
        id: job.id, title: job.title, description: job.description, requirements: job.requirements,
        salary: job.salary, quota: job.quota, workTime: job.workTime, location: job.location,
        contactPerson: job.contactPerson, contactPhone: job.contactPhone
      }
      reworkVisible.value = true
    }

    // 提交修改：状态 5 → 0 + 重新 AI 预审
    const submitRework = async () => {
      if (!reworkForm.value.title || !reworkForm.value.description) { message.warning('标题和描述必填'); return }
      reworkLoading.value = true
      try {
        await request.post('/department/jobs/rework', reworkForm.value, { timeout: 90000 })
        message.success('已重新提交，AI 正在预审...')
        reworkVisible.value = false
        fetchJobs()
      } catch (e) { console.error('重新提交失败', e) }
      finally { reworkLoading.value = false }
    }

    // 通过 axios 携带 token 导出，避免 window.open 无 Authorization 被 401 拦截
    const exportCsv = async () => {
      try {
        const res = await request.get('/export/jobs/csv', { responseType: 'blob' })
        downloadBlob(res.data, '岗位列表.csv')
      } catch (e) { message.error('导出失败') }
    }

    const getStatusClass = (s) => {
      switch (s) { case 0: return 'status-pending'; case 1: return 'status-published'; case 2: return 'status-approved'; case 3: return 'status-rejected'; case 4: return 'status-full'; case 5: return 'status-sendback'; default: return 'status-pending' }
    }
    const getStatusText = (s) => {
      switch (s) { case 0: return '待审批'; case 1: return '招聘中'; case 2: return '已结束'; case 3: return '已拒绝'; case 4: return '已招满'; case 5: return '已打回修改'; default: return '未知' }
    }

    onMounted(() => { fetchJobs() })
    return { jobs, sortedJobs, sort, onSortChange, loading, reworkVisible, reworkLoading, reworkForm, deleteJob, openRework, submitRework, exportCsv, getStatusClass, getStatusText }
  }
}
</script>

<style scoped>
.dashboard { padding: 20px; }
h2 { margin-bottom: 20px; color: #333; }
.card { background-color: #f9f9f9; padding: 20px; border-radius: 8px; border: 1px solid #e8e8e8; margin-bottom: 16px; transition: all 0.3s ease; }
.card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.card h3 { margin: 0 0 12px 0; color: #1890ff; font-size: 18px; }
.info-grid { display: flex; flex-wrap: wrap; gap: 8px 28px; margin-bottom: 8px; color: #666; }
.info-item { display: inline-flex; align-items: center; white-space: nowrap; line-height: 1.6; }
.info-remark { color: #666; line-height: 1.6; margin: 8px 0; }
.card p { margin: 8px 0; color: #666; line-height: 1.6; }
.status-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.status-pending { background: #fff7e6; color: #fa8c16; border: 1px solid #ffd591; }
.status-published { background: #e6f7ff; color: #1890ff; border: 1px solid #91d5ff; }
.status-approved { background: #f6ffed; color: #52c41a; border: 1px solid #b7eb8f; }
.status-rejected { background: #fff1f0; color: #ff4d4f; border: 1px solid #ffccc7; }
.status-full { background: #f9f0ff; color: #722ed1; border: 1px solid #d3adf7; }
.status-sendback { background: #fff7e6; color: #d46b08; border: 1px solid #ffd591; }
.sendback-box { margin-top: 10px; padding: 10px 14px; background: #fff7e6; border: 1px solid #ffd591; border-radius: 6px; }
.sendback-reason { color: #d46b08; margin: 6px 0 !important; }
.actions { margin-top: 12px; padding-top: 12px; border-top: 1px solid #e8e8e8; }
.list-toolbar { margin-bottom: 14px; }
.loading { text-align: center; padding: 40px; color: #999; font-size: 16px; }
</style>
