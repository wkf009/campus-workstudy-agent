<template>
  <div class="dashboard">
    <div class="dashboard-header"><h2>部门管理</h2><a-button type="primary" @click="openDepartmentEdit">编辑部门信息</a-button></div>
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="dashboard" tab="部门概览">
        <div class="overview-cards">
          <div class="card"><h3>当前部门</h3><p><strong>部门名称：</strong>{{ departmentInfo.name || '未设置' }}</p><p><strong>负责人：</strong>{{ departmentInfo.manager || '未设置' }}</p><p><strong>联系电话：</strong>{{ departmentInfo.phone || '未设置' }}</p></div>
          <div class="card"><h3>岗位管理</h3><p>已发布岗位：{{ stats.jobCount }}</p><p>待审批岗位：{{ stats.pendingJobCount }}</p><a-button type="link" @click="activeTab='jobs'">管理岗位</a-button></div>
          <div class="card"><h3>申请管理</h3><p>收到申请：{{ stats.applicationCount }}</p><p>待处理申请：{{ stats.pendingApplicationCount }}</p><a-button type="link" @click="activeTab='applications'">管理申请</a-button></div>
          <div class="card"><h3>学生管理</h3><p>部门学生：{{ stats.studentCount }}</p><p>待审批学生：{{ pendingStudentCount }}</p><a-button type="link" @click="activeTab='students'">管理学生</a-button></div>
        </div>
        <!-- ECharts 图表 -->
        <div class="charts-row">
          <div ref="chartRef1" style="width:100%;height:300px"></div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="jobs" tab="岗位管理"><department-jobs /></a-tab-pane>
      <a-tab-pane key="submit-job" tab="提交新岗位"><submit-job /></a-tab-pane>
      <a-tab-pane key="applications" tab="申请管理"><department-applications /></a-tab-pane>
      <a-tab-pane key="students" tab="学生审批">
        <div v-if="loadingStudents" class="loading">加载中...</div>
        <div v-else-if="pendingStudents.length === 0" class="card"><p>暂无待审批学生</p></div>
        <div v-else>
          <div v-for="student in pendingStudents" :key="student.id" class="card">
            <h4>{{ student.realName || student.username }}</h4>
            <p><strong>用户名：</strong>{{ student.username }}</p>
            <p><strong>联系电话：</strong>{{ student.phone || '未设置' }}</p>
            <p><strong>邮箱：</strong>{{ student.email || '未设置' }}</p>
            <p><strong>学号：</strong>{{ student.studentNo || '未设置' }}</p>
            <p><strong>专业：</strong>{{ student.major || '未设置' }}</p>
            <div class="actions">
              <a-button type="primary" @click="approveStudent(student.id,1)">通过</a-button>
              <a-button danger @click="approveStudent(student.id,2)">拒绝</a-button>
            </div>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="editDepartmentVisible" title="编辑部门信息" @ok="handleDepartmentUpdate" @cancel="editDepartmentVisible=false" okText="保存" cancelText="取消">
      <a-form :model="departmentForm" layout="vertical">
        <a-form-item label="部门名称"><a-input v-model:value="departmentForm.name" /></a-form-item>
        <a-form-item label="负责人"><a-input v-model:value="departmentForm.manager" /></a-form-item>
        <a-form-item label="联系电话"><a-input v-model:value="departmentForm.phone" /></a-form-item>
        <a-form-item label="部门描述"><a-textarea v-model:value="departmentForm.description" rows="3" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
import { ref, onMounted, nextTick } from 'vue'
import DepartmentJobs from './DepartmentJobs.vue'
import DepartmentApplications from './DepartmentApplications.vue'
import SubmitJob from './SubmitJob.vue'
import request from '../utils/request.js'
import { message } from 'ant-design-vue'
import * as echarts from 'echarts'

export default {
  name: 'DepartmentDashboard',
  components: { DepartmentJobs, DepartmentApplications, SubmitJob },
  setup() {
    const activeTab = ref('dashboard')
    const chartRef1 = ref(null)
    const stats = ref({ jobCount: 0, pendingJobCount: 0, applicationCount: 0, pendingApplicationCount: 0, studentCount: 0 })
    const pendingStudents = ref([])
    const pendingStudentCount = ref(0)
    const loadingStudents = ref(false)
    const editDepartmentVisible = ref(false)
    const departmentForm = ref({})
    const departmentInfo = ref({})

    const fetchStats = async () => {
      try {
        const user = JSON.parse(localStorage.getItem('user'))
        if (user && user.departmentId) {
          const res = await request.get(`/departments/${user.departmentId}`)
          if (res.code === 200 && res.data) {
            const d = res.data
            departmentInfo.value = { id: d.id, name: d.name, description: d.description || '', manager: d.managerName || user.realName, phone: d.phone || user.phone || '未设置' }
          }
          try {
            const statRes = await request.get('/stats/overview')
            if (statRes.code === 200 && statRes.data) {
              stats.value = { ...stats.value, ...statRes.data }
            }
          } catch (e) {}
        }
      } catch (e) { console.error('获取数据失败:', e) }
    }

    const initChart = async () => {
      await nextTick()
      if (chartRef1.value) {
        const chart = echarts.init(chartRef1.value)
        try {
          const jobsRes = await request.get('/stats/jobs-by-department')
          const appsRes = await request.get('/stats/applications-trend')
          const deptData = jobsRes.data || []
          const trendData = appsRes.data || []
          chart.setOption({
            title: { text: '申请趋势', left: 'center' },
            tooltip: { trigger: 'axis' },
            xAxis: { type: 'category', data: trendData.map(d => d.date) },
            yAxis: { type: 'value' },
            series: [{ name: '申请数', type: 'line', data: trendData.map(d => d.count), smooth: true }]
          })
        } catch (e) {
          chart.setOption({ title: { text: '暂无数据' } })
        }
      }
    }

    const fetchPendingStudents = async () => {
      try {
        loadingStudents.value = true
        const user = JSON.parse(localStorage.getItem('user'))
        const res = await request.get(`/department/users/pending?departmentId=${user.departmentId}`)
        pendingStudents.value = res.data || []
        pendingStudentCount.value = (res.data || []).length
      } catch (e) { message.error('获取待审批学生失败') }
      finally { loadingStudents.value = false }
    }

    const approveStudent = async (studentId, status) => {
      try {
        const user = JSON.parse(localStorage.getItem('user'))
        await request.post('/department/users/approve', { userId: studentId, status, departmentId: user.departmentId })
        message.success(status === 1 ? '审批通过' : '审批拒绝')
        fetchPendingStudents()
      } catch (e) { message.error('审批失败') }
    }

    const openDepartmentEdit = () => { departmentForm.value = { ...departmentInfo.value }; editDepartmentVisible.value = true }

    const handleDepartmentUpdate = async () => {
      try {
        const id = departmentForm.value.id
        await request.put(`/departments/${id}`, { name: departmentForm.value.name, description: departmentForm.value.description })
        await request.put(`/departments/${id}/manager`, { managerName: departmentForm.value.manager, phone: departmentForm.value.phone })
        message.success('部门信息已更新')
        editDepartmentVisible.value = false
        fetchStats()
      } catch (e) { message.error('更新失败') }
    }

    onMounted(() => { fetchStats(); fetchPendingStudents(); initChart() })
    return { activeTab, chartRef1, stats, pendingStudents, pendingStudentCount, loadingStudents, editDepartmentVisible, departmentForm, departmentInfo, approveStudent, openDepartmentEdit, handleDepartmentUpdate }
  }
}
</script>

<style scoped>
.dashboard-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.dashboard-header h2 { margin: 0; }
.overview-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-bottom: 30px; }
.charts-row { margin-top: 20px; }
.card { background-color: #f9f9f9; padding: 24px; border-radius: 8px; border: 1px solid #e8e8e8; transition: all 0.3s ease; }
.card:hover { transform: translateY(-4px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); background-color: #fff; }
.card h3 { margin-bottom: 16px; color: #333; font-size: 18px; font-weight: 600; }
.card p { margin: 8px 0; color: #666; line-height: 1.5; }
.actions { margin-top: 16px; display: flex; gap: 8px; }
.loading { text-align: center; padding: 60px 20px; color: #666; font-size: 16px; }
</style>
