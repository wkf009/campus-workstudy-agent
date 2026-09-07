<template>
  <div>
    <div class="nav">
      <ul>
        <li><router-link to="/department/dashboard">首页</router-link></li>
        <li><router-link to="/department/jobs">我的岗位</router-link></li>
        <li><router-link to="/department/applications">申请管理</router-link></li>
        <li><router-link to="/department/submit-job">提交新岗位</router-link></li>
        <li><a href="#" @click="logout">退出登录</a></li>
      </ul>
    </div>
    <router-view />
  </div>
</template>

<script>
import { useRouter } from 'vue-router'
export default { name: 'Department', setup() { const router = useRouter(); const logout = () => { sessionStorage.removeItem('user'); sessionStorage.removeItem('token'); router.push('/') }; return { logout } } }
</script>
