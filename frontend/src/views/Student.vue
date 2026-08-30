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
  </div>
</template>

<script>
import { useRouter } from 'vue-router'
export default {
  name: 'Student',
  setup() {
    const router = useRouter()
    const logout = () => { localStorage.removeItem('user'); localStorage.removeItem('token'); router.push('/') }
    return { logout }
  }
}
</script>
