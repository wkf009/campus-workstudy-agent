<template>
  <div class="dashboard">
    <h2>用户管理</h2>
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="all" tab="所有用户">
        <div v-if="loading" class="loading">加载中...</div>
        <div v-else-if="users.length === 0" class="card"><p>暂无用户信息</p></div>
        <div v-else>
          <div v-for="roleId in [0,1,2,3]" :key="roleId" class="role-section">
            <div class="role-header" @click="toggleRole(roleId)"><h3>{{ getRoleText(roleId) }}</h3><span>{{ expandedRoles[roleId] ? '▼' : '▶' }}</span></div>
            <div v-if="expandedRoles[roleId]" class="role-content">
              <div v-for="u in getUsersByRole(roleId)" :key="u.id" class="card">
                <h4>{{ u.realName || u.username }}</h4>
                <p><strong>用户名：</strong>{{ u.username }}</p>
                <p><strong>电话：</strong>{{ u.phone || '未设置' }}</p>
                <p><strong>邮箱：</strong>{{ u.email || '未设置' }}</p>
                <p><strong>状态：</strong>{{ getUserStatusText(u.status) }}</p>
                <div class="actions">
                  <a-button type="primary" @click="openEdit(u)">编辑</a-button>
                  <a-button danger @click="confirmDelete(u)">删除</a-button>
                </div>
              </div>
              <div v-if="getUsersByRole(roleId).length===0" class="empty-role"><p>暂无用户</p></div>
            </div>
          </div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="pending" tab="待审批用户">
        <div v-if="loadingPending" class="loading">加载中...</div>
        <div v-else-if="pendingUsers.length===0" class="card"><p>暂无待审批用户</p></div>
        <div v-else>
          <div v-for="u in pendingUsers" :key="u.id" class="card">
            <h4>{{ u.realName || u.username }}</h4>
            <p><strong>用户名：</strong>{{ u.username }}</p>
            <p><strong>角色：</strong>{{ getRoleText(u.role) }}</p>
            <p><strong>电话：</strong>{{ u.phone || '未设置' }}</p>
            <p><strong>邮箱：</strong>{{ u.email || '未设置' }}</p>
            <div class="actions">
              <a-button type="primary" @click="approveUser(u.id,1)">通过</a-button>
              <a-button danger @click="approveUser(u.id,2)">拒绝</a-button>
            </div>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="editModalVisible" title="编辑用户" @ok="handleEdit" @cancel="editModalVisible=false" okText="保存" cancelText="取消" width="600px">
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="用户名"><a-input v-model:value="editForm.username" disabled /></a-form-item>
        <a-form-item label="真实姓名"><a-input v-model:value="editForm.realName" /></a-form-item>
        <a-form-item label="联系电话"><a-input v-model:value="editForm.phone" /></a-form-item>
        <a-form-item label="邮箱"><a-input v-model:value="editForm.email" /></a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="editForm.role">
            <a-select-option :value="0">学生</a-select-option><a-select-option :value="1">企业导师</a-select-option><a-select-option :value="2">部门管理员</a-select-option><a-select-option :value="3">超级管理员</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="所属部门"><a-select v-model:value="editForm.departmentId" placeholder="请选择"><a-select-option v-for="d in deptList" :key="d.id" :value="d.id">{{ d.name }}</a-select-option></a-select></a-form-item>
        <template v-if="editForm.role===0">
          <a-form-item label="学号"><a-input v-model:value="editForm.studentNo" /></a-form-item>
          <a-form-item label="专业"><a-input v-model:value="editForm.major" /></a-form-item>
        </template>
        <a-form-item label="状态"><a-select v-model:value="editForm.status"><a-select-option :value="0">待审批</a-select-option><a-select-option :value="1">正常</a-select-option><a-select-option :value="2">拒绝</a-select-option></a-select></a-form-item>
        <a-form-item label="新密码（留空不修改）"><a-input-password v-model:value="editForm.password" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
import { ref, onMounted, watch } from 'vue'
import request from '../utils/request.js'
import { message, Modal } from 'ant-design-vue'

export default {
  name: 'AdminUsers',
  setup() {
    const users = ref([])
    const pendingUsers = ref([])
    const loading = ref(true)
    const loadingPending = ref(true)
    const activeTab = ref('all')
    const expandedRoles = ref({0:true,1:true,2:true,3:true})
    const editModalVisible = ref(false)
    const editForm = ref({})
    const deptList = ref([])

    const fetchDepts = async () => { try { const res = await request.get('/departments'); deptList.value = res.data || [] } catch (e) {} }
    const fetchUsers = async () => {
      try { const res = await request.get('/admin/users'); users.value = res.data || res } catch (e) { message.error('获取用户列表失败') } finally { loading.value = false }
    }
    const fetchPending = async () => {
      try { const res = await request.get('/admin/users/pending'); pendingUsers.value = res.data || [] } catch (e) {} finally { loadingPending.value = false }
    }
    const toggleRole = (r) => { expandedRoles.value[r] = !expandedRoles.value[r] }
    const getUsersByRole = (r) => users.value.filter(u => u.role === r)
    const getRoleText = (r) => { switch(r) { case 0: return '学生'; case 1: return '企业导师'; case 2: return '部门管理员'; case 3: return '超级管理员'; default: return '未知' } }
    const getUserStatusText = (s) => { switch(s) { case 0: return '待审批'; case 1: return '正常'; case 2: return '拒绝'; default: return '未知' } }
    const openEdit = (u) => { editForm.value = { ...u, password: '' }; editModalVisible.value = true }
    const handleEdit = async () => {
      try {
        await request.put(`/admin/users/${editForm.value.id}`, editForm.value)
        message.success('更新成功')
        editModalVisible.value = false
        fetchUsers()
      } catch (e) { message.error('更新失败') }
    }
    const confirmDelete = (u) => {
      Modal.confirm({
        title: '确认删除', content: `确定要删除用户"${u.realName || u.username}"吗？`, okText: '确认删除', okType: 'danger', cancelText: '取消',
        onOk: async () => { try { await request.delete(`/admin/users/${u.id}`); message.success('删除成功'); fetchUsers() } catch (e) { message.error('删除失败') } }
      })
    }
    const approveUser = async (userId, status) => {
      try { await request.post('/admin/users/approve', { userId, status }); message.success(status===1?'审批通过':'审批拒绝'); fetchPending(); fetchUsers() } catch (e) { message.error('审批失败') }
    }
    watch(activeTab, (t) => { if (t === 'pending') fetchPending() })
    onMounted(() => { fetchUsers(); fetchDepts() })

    return { users, pendingUsers, loading, loadingPending, activeTab, expandedRoles, editModalVisible, editForm, deptList, toggleRole, getUsersByRole, getRoleText, getUserStatusText, openEdit, handleEdit, confirmDelete, approveUser }
  }
}
</script>

<style scoped>
.role-section { margin-bottom:20px; border:1px solid #e8e8e8; border-radius:4px; overflow:hidden; }
.role-header { background:#f5f5f5; padding:12px 16px; cursor:pointer; display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid #e8e8e8; }
.role-header:hover { background:#f0f0f0; }
.role-header h3 { margin:0; font-size:16px; font-weight:600; color:#333; }
.role-content { padding:16px; background:#fff; }
.empty-role { text-align:center; padding:20px; color:#999; font-size:14px; }
.card { margin-bottom:12px; padding:16px; border:1px solid #e8e8e8; border-radius:4px; background:#fafafa; transition:all 0.3s ease; }
.card:hover { box-shadow:0 2px 8px rgba(0,0,0,0.1); background:#fff; }
.card h4 { margin-top:0; margin-bottom:12px; color:#333; font-size:14px; font-weight:600; }
.card p { margin:6px 0; font-size:13px; color:#666; }
.actions { margin-top:16px; display:flex; gap:8px; }
.loading { text-align:center; padding:40px; color:#999; font-size:16px; }
</style>
