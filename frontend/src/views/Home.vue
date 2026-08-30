<template>
  <div class="home-container">
    <div class="home-content">
      <h1>勤工俭学系统</h1>
      <p>欢迎使用勤工俭学系统，为学生提供更多实践机会</p>
      <div class="auth-container">
        <div class="auth-tabs">
          <div
            class="auth-tab"
            :class="{ active: activeTab === 'login' }"
            @click="activeTab = 'login'"
          >登录</div>
          <div
            class="auth-tab"
            :class="{ active: activeTab === 'register' }"
            @click="activeTab = 'register'"
          >注册</div>
        </div>
        <div class="auth-forms">
          <div v-if="activeTab === 'login'" class="login-form">
            <a-form :model="loginForm" @finish="handleLoginSubmit" @finishFailed="handleFinishFailed">
              <a-form-item label="用户名：" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
                <a-input v-model:value="loginForm.username" placeholder="请输入用户名" />
              </a-form-item>
              <a-form-item label="密码：" name="password" :rules="[{ required: true, message: '请输入密码' }]">
                <a-input-password v-model:value="loginForm.password" placeholder="请输入密码" />
              </a-form-item>
              <a-form-item>
                <a-button type="primary" html-type="submit" block>登录</a-button>
              </a-form-item>
            </a-form>
            <div v-if="loginError" class="error-message">{{ loginError }}</div>
          </div>
          <div v-else class="register-form">
            <a-form :model="registerForm" @finish="handleRegisterSubmit" @finishFailed="handleFinishFailed">
              <a-form-item label="注册角色：" name="role" :rules="[{ required: true, message: '请选择注册角色' }]">
                <a-select v-model:value="registerForm.role" placeholder="请选择注册角色">
                  <a-select-option value="0">学生</a-select-option>
                  <a-select-option value="1">企业导师</a-select-option>
                  <a-select-option value="2">部门管理员</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="用户名：" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
                <a-input v-model:value="registerForm.username" placeholder="请输入用户名" />
              </a-form-item>
              <a-form-item label="密码：" name="password" :rules="[{ required: true, message: '请输入密码' }]">
                <a-input-password v-model:value="registerForm.password" placeholder="请输入密码" />
              </a-form-item>
              <a-form-item label="真实姓名：" name="realName" :rules="[{ required: true, message: '请输入真实姓名' }]">
                <a-input v-model:value="registerForm.realName" placeholder="请输入真实姓名" />
              </a-form-item>
              <a-form-item label="联系电话：" name="phone" :rules="[{ required: true, message: '请输入联系电话' }]">
                <a-input v-model:value="registerForm.phone" placeholder="请输入联系电话" />
              </a-form-item>
              <a-form-item label="邮箱：" name="email" :rules="[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '请输入正确的邮箱格式' }]">
                <a-input v-model:value="registerForm.email" placeholder="请输入邮箱" />
              </a-form-item>
              <div v-if="registerForm.role === '0'">
                <a-form-item label="学号：" name="studentNo" :rules="[{ required: true, message: '请输入学号' }]">
                  <a-input v-model:value="registerForm.studentNo" placeholder="请输入学号" />
                </a-form-item>
                <a-form-item label="专业：" name="major" :rules="[{ required: true, message: '请输入专业' }]">
                  <a-input v-model:value="registerForm.major" placeholder="请输入专业" />
                </a-form-item>
                <a-form-item label="所属部门：" name="departmentId" :rules="[{ required: true, message: '请选择所属部门' }]">
                  <a-select v-model:value="registerForm.departmentId" placeholder="请选择所属部门">
                    <a-select-option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</a-select-option>
                  </a-select>
                </a-form-item>
              </div>
              <div v-if="registerForm.role === '2'">
                <a-form-item label="所属部门：" name="departmentId" :rules="[{ required: true, message: '请选择所属部门' }]">
                  <a-select v-model:value="registerForm.departmentId" placeholder="请选择所属部门">
                    <a-select-option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</a-select-option>
                  </a-select>
                </a-form-item>
              </div>
              <a-form-item>
                <a-button type="primary" html-type="submit" block>注册</a-button>
              </a-form-item>
            </a-form>
            <div v-if="registerError" class="error-message">{{ registerError }}</div>
            <div v-if="registerSuccess" class="success-message">{{ registerSuccess }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request.js'

export default {
  name: 'Home',
  setup() {
    const router = useRouter()
    const activeTab = ref('login')
    const loginForm = ref({ username: '', password: '' })
    const loginError = ref('')
    const registerForm = ref({ role: '', username: '', password: '', realName: '', phone: '', email: '', studentNo: '', major: '', departmentId: '' })
    const registerError = ref('')
    const registerSuccess = ref('')
    const departments = ref([])

    const fetchDepartments = async () => {
      try {
        const res = await request.get('/departments')
        departments.value = res.data || []
      } catch (e) {
        console.error('获取部门列表失败:', e)
      }
    }

    const handleLoginSubmit = async (values) => {
      try {
        const res = await request.post('/auth/login', { username: values.username, password: values.password })
        const data = res.data || res
        if (data.token) {
          localStorage.setItem('user', JSON.stringify(data.user))
          localStorage.setItem('token', data.token)
          const user = data.user
          if (user.role === 0) router.push('/student/dashboard')
          else if (user.role === 2 || user.role === 1) router.push('/department/dashboard')
          else if (user.role === 3) router.push('/admin/dashboard')
        } else {
          loginError.value = res.message || '登录失败'
        }
      } catch (e) {
        loginError.value = '登录失败，请检查后端服务是否运行'
      }
    }

    const handleRegisterSubmit = async (values) => {
      try {
        await request.post('/auth/register', { ...values, role: parseInt(values.role), status: 0 })
        registerSuccess.value = '注册成功，等待管理员审批'
        registerError.value = ''
        registerForm.value = { role: '', username: '', password: '', realName: '', phone: '', email: '', studentNo: '', major: '', departmentId: '' }
        setTimeout(() => { activeTab.value = 'login'; registerSuccess.value = '' }, 3000)
      } catch (e) {
        registerError.value = '注册失败，请检查后端服务是否运行'
        registerSuccess.value = ''
      }
    }

    const handleFinishFailed = (errorInfo) => {
      console.log('表单验证失败:', errorInfo)
    }

    onMounted(() => { fetchDepartments() })

    return { activeTab, loginForm, loginError, registerForm, registerError, registerSuccess, departments, handleLoginSubmit, handleRegisterSubmit, handleFinishFailed }
  }
}
</script>

<style scoped>
.home-container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; }
.home-content { text-align: center; max-width: 500px; width: 100%; }
.home-content h1 { color: white; font-size: 36px; margin-bottom: 16px; text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3); }
.home-content p { color: rgba(255, 255, 255, 0.9); font-size: 18px; margin-bottom: 40px; }
.auth-container { background-color: white; border-radius: 12px; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2); overflow: hidden; }
.auth-tabs { display: flex; border-bottom: 1px solid #f0f0f0; }
.auth-tab { flex: 1; padding: 16px; text-align: center; cursor: pointer; font-size: 16px; font-weight: 500; color: #666; transition: all 0.3s ease; }
.auth-tab:hover { color: #1890ff; }
.auth-tab.active { color: #1890ff; border-bottom: 2px solid #1890ff; }
.auth-forms { padding: 30px; }
.error-message { color: #ff4d4f; margin-top: 12px; font-size: 14px; text-align: center; }
.success-message { color: #52c41a; margin-top: 12px; font-size: 14px; text-align: center; }
@media (max-width: 768px) { .home-content h1 { font-size: 28px; } .home-content p { font-size: 16px; } .auth-forms { padding: 20px; } }
</style>
