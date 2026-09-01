<template>
  <div class="dashboard">
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="dashboard" tab="管理员首页">
        <div class="overview-cards">
          <div class="card"><h3>欢迎，{{ user.realName || user.username }}</h3><p>角色：超级管理员</p></div>
          <div class="card"><h3>用户统计</h3>
            <p><strong>学生：</strong>{{ stats.studentCount }}</p>
            <p><strong>企业导师：</strong>{{ stats.mentorCount }}</p>
            <p><strong>部门管理员：</strong>{{ stats.deptAdminCount }}</p>
            <p><strong>总用户：</strong>{{ stats.totalUsers }}</p>
          </div>
          <div class="card"><h3>岗位统计</h3>
            <p><strong>招聘中：</strong>{{ stats.publishedJobs }}</p>
            <p><strong>待审批：</strong>{{ stats.pendingJobs }}</p>
            <p><strong>总岗位：</strong>{{ stats.totalJobs }}</p>
          </div>
          <div class="card"><h3>系统操作</h3><a-button type="link" @click="activeTab='jobs'">管理岗位</a-button><a-button type="link" @click="activeTab='users'">管理用户</a-button></div>
        </div>
        <div class="charts-row">
          <div ref="chartRef" style="width:100%;height:300px"></div>
        </div>

        <!-- AI 运营分析（Agent：AnalystAgent） -->
        <div class="ai-analysis">
          <div class="ai-analysis-header">
            <h3>🤖 AI 运营分析</h3>
            <a-button size="small" @click="runAnalysis" :loading="analyzing">生成 AI 分析</a-button>
          </div>
          <div v-if="analysis && analysis.summary" class="analysis-content">
            <p class="analysis-summary">{{ analysis.summary }}</p>
            <p v-if="analysis.trends && analysis.trends.length"><strong>趋势：</strong>{{ analysis.trends.join('；') }}</p>
            <p v-if="analysis.anomalies && analysis.anomalies.length" class="analysis-anomaly"><strong>异常提示：</strong>{{ analysis.anomalies.join('；') }}</p>
            <p v-if="analysis.advice" class="analysis-advice"><strong>运营建议：</strong>{{ analysis.advice }}</p>
          </div>
          <div v-else-if="!analyzing" class="analysis-empty">点击"生成 AI 分析"，由大模型解读当前运营数据</div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="jobs" tab="岗位管理"><admin-jobs /></a-tab-pane>
      <a-tab-pane key="users" tab="用户管理"><admin-users /></a-tab-pane>
    </a-tabs>
  </div>
</template>

<script>
import { ref, onMounted, nextTick } from 'vue'
import AdminJobs from './AdminJobs.vue'
import AdminUsers from './AdminUsers.vue'
import request from '../utils/request.js'
import * as echarts from 'echarts'
import { message } from 'ant-design-vue'

export default {
  name: 'AdminDashboard',
  components: { AdminJobs, AdminUsers },
  setup() {
    const activeTab = ref('dashboard')
    const chartRef = ref(null)
    const user = ref({})
    const stats = ref({ totalUsers:0, studentCount:0, mentorCount:0, deptAdminCount:0, totalJobs:0, publishedJobs:0, pendingJobs:0 })

    onMounted(async () => {
      const userStr = localStorage.getItem('user')
      if (userStr) user.value = JSON.parse(userStr)
      try {
        const res = await request.get('/stats/overview')
        if (res.code === 200) stats.value = { ...stats.value, ...res.data }
      } catch (e) {}

      try {
        const appsRes = await request.get('/stats/applications-trend')
        const trendData = appsRes.data || []
        await nextTick()
        if (chartRef.value) {
          const chart = echarts.init(chartRef.value)
          chart.setOption({
            title: { text: '申请趋势统计', left: 'center' },
            tooltip: { trigger: 'axis' },
            xAxis: { type: 'category', data: trendData.map(d => d.date) },
            yAxis: { type: 'value' },
            series: [{ name: '申请量', type: 'line', data: trendData.map(d => d.count), smooth: true, areaStyle: {} }]
          })
        }
      } catch (e) {}
    })

    // AI 运营分析（AnalystAgent）
    const analysis = ref(null)
    const analyzing = ref(false)

    const runAnalysis = async () => {
      analyzing.value = true
      try {
        const res = await request.post('/agent/analyze')
        analysis.value = res.data || {}
      } catch (e) { message.error('AI 分析失败，请确认已配置通义千问 API Key') }
      finally { analyzing.value = false }
    }

    return { activeTab, chartRef, user, stats, analysis, analyzing, runAnalysis }
  }
}
</script>

<style scoped>
.overview-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-bottom: 30px; }
.charts-row { margin-top: 20px; }
.card { background-color: #f9f9f9; padding: 24px; border-radius: 8px; border: 1px solid #e8e8e8; transition: all 0.3s ease; }
.card:hover { transform: translateY(-4px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); background-color: #fff; }
.card h3 { margin-bottom: 16px; color: #333; font-size: 18px; font-weight: 600; }
.ai-analysis { margin-top: 24px; background: linear-gradient(135deg, #f6ffed, #fafafa); border: 1px solid #b7eb8f; border-radius: 8px; padding: 18px 20px; }
.ai-analysis-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.ai-analysis-header h3 { margin: 0; color: #333; font-size: 17px; }
.analysis-content p { margin: 8px 0; color: #555; line-height: 1.7; }
.analysis-summary { font-size: 15px; color: #333 !important; font-weight: 500; }
.analysis-anomaly { color: #cf1322 !important; }
.analysis-advice { color: #1890ff !important; }
.analysis-empty { color: #999; font-size: 14px; }
.card p { margin: 8px 0; color: #666; line-height: 1.5; }
</style>
