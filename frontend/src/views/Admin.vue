<template>
  <div>
    <div class="nav">
      <ul>
        <li><router-link to="/admin/dashboard">首页</router-link></li>
        <li><router-link to="/admin/jobs">岗位管理</router-link></li>
        <li><router-link to="/admin/users">用户管理</router-link></li>
        <li><a href="#" @click="logout">退出登录</a></li>
      </ul>
    </div>
    <router-view />
  </div>
</template>

<script>
import { useRouter } from 'vue-router'
export default { name: 'Admin', setup() { const router = useRouter(); const logout = () => { localStorage.removeItem('user'); localStorage.removeItem('token'); router.push('/') }; return { logout } } }
</script>
