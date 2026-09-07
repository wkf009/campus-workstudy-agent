<template>
  <div id="app">
    <AiProgressBar />
    <router-view v-if="$route.name !== 'Home'">
      <template #default>
        <div class="container">
          <h1>勤工俭学系统</h1>
          <router-view />
        </div>
      </template>
    </router-view>
    <router-view v-else />
  </div>
</template>

<script>
import AiProgressBar from './components/AiProgressBar.vue'
export default {
  name: 'App',
  components: { AiProgressBar }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Microsoft YaHei', Arial, sans-serif;
  background-color: #f0f2f5;
  color: #333;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

h1 {
  text-align: center;
  margin-bottom: 40px;
  color: #1890ff;
  font-size: 28px;
  font-weight: bold;
}

.nav {
  background-color: #1890ff;
  color: white;
  padding: 16px 0;
  margin-bottom: 40px;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.3);
}

.nav ul {
  list-style: none;
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
}

.nav li {
  margin: 0 16px;
}

.nav a {
  color: white;
  text-decoration: none;
  font-size: 16px;
  font-weight: 500;
  padding: 8px 16px;
  border-radius: 4px;
  transition: all 0.3s ease;
}

.nav a:hover {
  background-color: rgba(255, 255, 255, 0.2);
}

.dashboard {
  background-color: white;
  padding: 40px;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.dashboard h2 {
  margin-bottom: 30px;
  color: #333;
  font-size: 24px;
  font-weight: bold;
  border-bottom: 2px solid #f0f0f0;
  padding-bottom: 12px;
}

.card {
  background-color: #f9f9f9;
  padding: 24px;
  border-radius: 8px;
  margin-bottom: 24px;
  border: 1px solid #e8e8e8;
  transition: all 0.3s ease;
}

.card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.card h3 {
  margin-bottom: 16px;
  color: #333;
  font-size: 18px;
  font-weight: 600;
}

.card p {
  margin-bottom: 12px;
  color: #666;
  line-height: 1.6;
}

.loading {
  text-align: center;
  padding: 60px 20px;
  color: #666;
  font-size: 16px;
}

.loading::after {
  content: '';
  display: inline-block;
  width: 20px;
  height: 20px;
  margin-left: 10px;
  border: 2px solid #f3f3f3;
  border-top: 2px solid #1890ff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.status-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  margin-right: 8px;
}

.status-pending {
  background-color: #faad14;
}

.status-approved {
  background-color: #52c41a;
}

.status-rejected {
  background-color: #ff4d4f;
}

.status-published {
  background-color: #1890ff;
}

.actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .container {
    padding: 10px;
  }
  
  .dashboard {
    padding: 20px;
  }
  
  .nav ul {
    flex-direction: column;
    align-items: center;
  }
  
  .nav li {
    margin: 8px 0;
  }
}
</style>