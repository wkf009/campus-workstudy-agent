<template>
  <div class="dashboard">
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="dashboard" tab="学生首页">
        <div class="overview-cards">
          <div class="card"><h3>个人信息</h3>
            <p><strong>姓名：</strong>{{ user.realName || user.username }}</p>
            <p><strong>学号：</strong>{{ user.studentNo || '未设置' }}</p>
            <p><strong>专业：</strong>{{ user.major || '未设置' }}</p>
            <p><strong>所属部门：</strong>{{ getDeptName() }}</p>
            <div class="actions">
              <a-button type="primary" size="small" @click="openEditProfile">编辑信息</a-button>
              <a-button danger size="small" @click="confirmDeleteAccount">注销账号</a-button>
            </div>
          </div>
          <div class="card"><h3>岗位浏览</h3><p>查看并申请勤工俭学岗位</p><a-button type="link" @click="goToJobs">浏览岗位</a-button></div>
          <div class="card"><h3>我的申请</h3><p>查看申请状态和结果</p><a-button type="link" @click="goToApps">查看申请</a-button></div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="jobs" tab="岗位浏览"><student-jobs /></a-tab-pane>
      <a-tab-pane key="applications" tab="我的申请"><student-applications /></a-tab-pane>
    </a-tabs>
    <a-modal v-model:open="editProfileVisible" title="编辑个人信息" @ok="handleProfileUpdate" @cancel="editProfileVisible = false" okText="保存" cancelText="取消">
      <a-form :model="profileForm" layout="vertical">
        <a-form-item label="真实姓名"><a-input v-model:value="profileForm.realName" /></a-form-item>
        <a-form-item label="联系电话"><a-input v-model:value="profileForm.phone" /></a-form-item>
        <a-form-item label="邮箱"><a-input v-model:value="profileForm.email" /></a-form-item>
        <a-form-item label="学号"><a-input v-model:value="profileForm.studentNo" /></a-form-item>
        <a-form-item label="专业"><a-input v-model:value="profileForm.major" /></a-form-item>
        <a-form-item label="新密码（留空表示不修改）"><a-input-password v-model:value="profileForm.password" placeholder="请输入新密码" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import StudentJobs from './StudentJobs.vue'
import StudentApplications from './StudentApplications.vue'
import request from '../utils/request.js'
import { message, Modal } from 'ant-design-vue'

export default {
  name: 'StudentDashboard',
  components: { StudentJobs, StudentApplications },
  setup() {
    const router = useRouter()
    const activeTab = ref('dashboard')
    const user = ref({})
    const editProfileVisible = ref(false)
    const profileForm = ref({})

    onMounted(() => {
      const userStr = localStorage.getItem('user')
      if (userStr) user.value = JSON.parse(userStr)
    })

    const goToJobs = () => router.push('/student/jobs')
    const goToApps = () => router.push('/student/applications')

    const getDeptName = () => {
      return user.value.departmentName || '未设置'
    }

    const openEditProfile = () => {
      profileForm.value = { ...user.value, password: '' }
      editProfileVisible.value = true
    }

    const handleProfileUpdate = async () => {
      try {
        const res = await request.put(`/admin/users/${user.value.id}`, profileForm.value)
        if (res.code === 200) {
          message.success('个人信息更新成功')
          editProfileVisible.value = false
          if (res.data) { localStorage.setItem('user', JSON.stringify(res.data)); user.value = res.data }
        }
      } catch (e) { message.error('更新失败') }
    }

    const confirmDeleteAccount = () => {
      Modal.confirm({
        title: '确认注销账号', content: '确定要注销账号吗？此操作不可恢复！', okText: '确认注销', okType: 'danger', cancelText: '取消',
        onOk: async () => {
          try {
            await request.delete(`/admin/users/${user.value.id}`)
            message.success('账号已注销')
            localStorage.removeItem('user'); localStorage.removeItem('token')
            router.push('/')
          } catch (e) { message.error('注销失败') }
        }
      })
    }

    return { activeTab, user, editProfileVisible, profileForm, openEditProfile, handleProfileUpdate, confirmDeleteAccount, goToJobs, goToApps, getDeptName }
  }
}
</script>

<style scoped>
.overview-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-bottom: 30px; }
.card { background-color: #f9f9f9; padding: 24px; border-radius: 8px; border: 1px solid #e8e8e8; transition: all 0.3s ease; }
.card:hover { transform: translateY(-4px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); background-color: #fff; }
.card h3 { margin-bottom: 16px; color: #333; font-size: 18px; font-weight: 600; }
.card p { margin: 8px 0; color: #666; line-height: 1.5; }
.actions { margin-top: 16px; display: flex; gap: 8px; }
</style>
