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

    const fetchApplications = async () => {
      try {
        const user = JSON.parse(localStorage.getItem('user'))
        const res = await request.get(`/student/applications/${user.id}`)
        applications.value = res.data || res
      } catch (e) { message.error('获取申请记录失败') }
      finally { loading.value = false }
    }

    // 通过 axios 携带 token 导出，避免 window.open 无 Authorization 被 401 拦截
    const exportCsv = async () => {
      const user = JSON.parse(localStorage.getItem('user'))
      try {
        const res = await request.get(`/export/applications/csv?userId=${user.id}`, { responseType: 'blob' })
        downloadBlob(res.data, '申请记录.csv')
      } catch (e) { message.error('导出失败') }
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
    return { applications, loading, exportCsv, getStatusClass, getStatusText }
  }
}
</script>
