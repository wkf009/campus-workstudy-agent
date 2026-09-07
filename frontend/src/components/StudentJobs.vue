<template>
  <div class="dashboard">
    <h2>岗位列表</h2>
    <div class="search-bar">
      <a-input-search v-model:value="keyword" placeholder="搜索岗位..." @search="onSearch" style="max-width:400px" />
      <a-select v-model:value="filterDept" placeholder="筛选部门" allowClear style="width:160px;margin-left:8px" @change="onSearch">
        <a-select-option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</a-select-option>
      </a-select>
    </div>
    <!-- AI 自然语言搜索（Agent：QueryAgent） -->
    <div class="ai-search-bar">
      <a-input-search
        v-model:value="aiQuery"
        placeholder="🤖 AI 搜索：试试'晚上能做的兼职'、'图书馆附近的工作'"
        @search="aiSearch"
        :loading="aiSearching"
        style="max-width:520px"
      />
    </div>
    <p v-if="aiSummary" class="ai-summary">💡 AI 理解你的需求：{{ aiSummary }}</p>
    <p class="tip">排序：本部门岗位优先，薪资从高到低（服务端分页）</p>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="jobs.length === 0" class="card"><p>暂无岗位信息</p></div>
    <div v-else>
      <div v-for="job in jobs" :key="job.id" class="card">
        <div class="card-header">
          <h3>{{ job.title }}</h3>
          <a-tag v-if="job.remark === '1'" color="blue">本部门推荐</a-tag>
        </div>
        <p><strong>部门：</strong>{{ job.departmentName }}</p>
        <p><strong>薪资：</strong>¥{{ job.salary }} /小时</p>
        <p><strong>招聘名额：</strong>{{ job.quota }}</p>
        <p><strong>工作地点：</strong>{{ job.location || '未设置' }}</p>
        <p><strong>工作时间：</strong>{{ job.workTime || '未设置' }}</p>
        <p><strong>联系人：</strong>{{ job.contactPerson || '未设置' }}</p>
        <p><strong>岗位描述：</strong>{{ job.description }}</p>
        <p><strong>任职要求：</strong>{{ job.requirements || '无' }}</p>
        <div class="actions">
          <a-button type="primary" :disabled="job.status === 4" @click="applyJob(job.id)">{{ job.status === 4 ? '已招满' : '申请' }}</a-button>
        </div>
      </div>
      <a-pagination
        v-model:current="page"
        :total="total"
        :page-size="pageSize"
        :show-total="t => `共 ${t} 条`"
        @change="onPageChange"
        style="text-align:center;margin-top:16px"
      />
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import request from '../utils/request.js'
import { message } from 'ant-design-vue'

export default {
  name: 'StudentJobs',
  setup() {
    const jobs = ref([])
    const loading = ref(true)
    const keyword = ref('')
    const filterDept = ref(null)
    const departments = ref([])
    const page = ref(1)
    const pageSize = ref(10)
    const total = ref(0)

    // AI 自然语言搜索（QueryAgent）
    const aiQuery = ref('')
    const aiSearching = ref(false)
    const aiSummary = ref('')

    const aiSearch = async () => {
      if (!aiQuery.value.trim()) return
      aiSearching.value = true
      aiSummary.value = ''
      try {
        const res = await request.post('/agent/search', { query: aiQuery.value }, { timeout: 90000 })
        const d = res.data || {}
        aiSummary.value = d.summary || ''
        jobs.value = d.jobs || []
        total.value = jobs.value.length
        page.value = 1
        message.success('AI 已为你找到 ' + jobs.value.length + ' 个岗位')
      } catch (e) { console.error('AI 搜索失败', e) }
      finally { aiSearching.value = false }
    }

    // 搜索/筛选/排序均在服务端完成（SQL + PageHelper 分页），前端只负责参数与渲染
    const fetchJobs = async () => {
      loading.value = true
      try {
        const params = { page: page.value, pageSize: pageSize.value }
        if (keyword.value && keyword.value.trim()) params.keyword = keyword.value.trim()
        if (filterDept.value) params.departmentId = filterDept.value
        const res = await request.get('/student/jobs', { params })
        const data = res.data || {}
        jobs.value = data.records || []
        total.value = data.total || 0
      } catch (e) {
        console.error('获取岗位失败:', e)
        message.error('获取岗位列表失败')
      } finally { loading.value = false }
    }

    const onSearch = () => { page.value = 1; fetchJobs() }
    const onPageChange = (p) => { page.value = p; fetchJobs() }

    const fetchDepartments = async () => {
      try {
        const res = await request.get('/departments')
        departments.value = res.data || []
      } catch (e) {}
    }

    const applyJob = async (jobId) => {
      try {
        await request.post('/student/applications', { jobId, resumeUrl: '', coverLetter: '' })
        message.success('申请成功！')
      } catch (e) { message.error('申请失败') }
    }

    onMounted(() => { fetchJobs(); fetchDepartments() })

    return { jobs, loading, keyword, filterDept, departments, page, pageSize, total, aiQuery, aiSearching, aiSummary, aiSearch, onSearch, onPageChange, applyJob }
  }
}
</script>

<style scoped>
.dashboard h2 { margin-bottom: 8px; }
.search-bar { display: flex; align-items: center; margin-bottom: 8px; }
.ai-search-bar { margin-bottom: 6px; }
.ai-summary { color: #1890ff; font-size: 14px; margin-bottom: 4px; }
.tip { color: #888; font-size: 14px; margin-bottom: 20px; }
.card { background-color: #f9f9f9; padding: 24px; border-radius: 8px; border: 1px solid #e8e8e8; margin-bottom: 20px; transition: all 0.3s ease; }
.card:hover { transform: translateY(-4px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); background-color: #fff; }
.card-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.card h3 { margin-bottom: 0; color: #333; font-size: 18px; font-weight: 600; }
.card p { margin: 8px 0; color: #666; line-height: 1.5; }
.actions { margin-top: 16px; display: flex; gap: 8px; }
.loading { text-align: center; padding: 60px 20px; color: #666; font-size: 16px; }
</style>
