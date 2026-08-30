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

    const getStatusClass = (s) => {
      switch (s) { case 0: return 'status-pending'; case 1: return 'status-approved'; case 2: return 'status-rejected'; default: return 'status-pending' }
    }
    const getStatusText = (s) => {
      switch (s) { case 0: return '待处理'; case 1: return '已通过'; case 2: return '已拒绝'; case 3: return '已取消'; default: return '未知' }
    }

    onMounted(() => { fetch() })
    return { applications, loading, process, getStatusClass, getStatusText }
  }
}
</script>
