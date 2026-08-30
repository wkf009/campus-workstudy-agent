<template>
  <div class="dashboard">
    <h2>岗位管理</h2>
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

    const getStatusClass = (s) => {
      switch (s) { case 0: return 'status-pending'; case 1: return 'status-published'; case 2: return 'status-ended'; case 3: return 'status-rejected'; case 4: return 'status-full'; default: return 'status-pending' }
    }
    const getStatusText = (s) => {
      switch (s) { case 0: return '待审批'; case 1: return '招聘中'; case 2: return '已结束'; case 3: return '已拒绝'; case 4: return '已招满'; default: return '未知' }
    }

    onMounted(() => { fetch() })
    return { jobs, loading, audit, getStatusClass, getStatusText }
  }
}
</script>
