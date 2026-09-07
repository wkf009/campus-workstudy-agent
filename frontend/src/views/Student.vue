<template>
  <div>
    <div class="nav">
      <ul>
        <li><router-link to="/student/dashboard">首页</router-link></li>
        <li><router-link to="/student/jobs">岗位列表</router-link></li>
        <li><router-link to="/student/applications">我的申请</router-link></li>
        <li><a href="#" @click="logout">退出登录</a></li>
      </ul>
    </div>
    <router-view />
    <!-- AI 求职助手（Agent 1 对话形态） -->
    <chat-assistant />
  </div>
</template>

<script>
import { useRouter } from 'vue-router'
import ChatAssistant from '../components/ChatAssistant.vue'
export default {
  name: 'Student',
  components: { ChatAssistant },
  setup() {
    const router = useRouter()
    const logout = () => { sessionStorage.removeItem('user'); sessionStorage.removeItem('token'); router.push('/') }
    return { logout }
  }
}
</script>
