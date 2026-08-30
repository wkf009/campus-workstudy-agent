<template>
  <div class="submit-job-form">
    <h2>提交新岗位</h2>
    <a-form :model="formState" @finish="handleSubmit" @finishFailed="handleFinishFailed">
      <a-form-item label="岗位标题：" name="title" :rules="[{required:true,message:'请输入岗位标题'}]"><a-input v-model:value="formState.title" placeholder="请输入岗位标题" /></a-form-item>
      <a-form-item label="详细描述：" name="description" :rules="[{required:true,message:'请输入岗位详细描述'}]"><a-textarea v-model:value="formState.description" placeholder="请输入岗位详细描述" rows="4" /></a-form-item>
      <a-form-item label="任职要求：" name="requirements"><a-textarea v-model:value="formState.requirements" placeholder="请输入任职要求" rows="3" /></a-form-item>
      <a-form-item label="薪资：" name="salary" :rules="[{required:true,message:'请输入薪资'}]"><a-input v-model:value="formState.salary" placeholder="请输入薪资" /></a-form-item>
      <a-form-item label="工作地点：" name="location"><a-input v-model:value="formState.location" placeholder="请输入工作地点" /></a-form-item>
      <a-form-item label="招聘名额：" name="quota" :rules="[{required:true,message:'请输入招聘名额'}]"><a-input-number v-model:value="formState.quota" :min="1" placeholder="请输入招聘名额" /></a-form-item>
      <a-form-item label="工作时间：" name="workTime"><a-input v-model:value="formState.workTime" placeholder="请输入工作时间" /></a-form-item>
      <a-form-item label="联系人：" name="contactPerson"><a-input v-model:value="formState.contactPerson" placeholder="请输入联系人" /></a-form-item>
      <a-form-item label="联系电话：" name="contactPhone"><a-input v-model:value="formState.contactPhone" placeholder="请输入联系电话" /></a-form-item>
      <a-form-item label="部门ID：" name="departmentId"><a-input v-model:value="formState.departmentId" disabled /></a-form-item>
      <a-form-item label="部门名称：" name="departmentName"><a-input v-model:value="formState.departmentName" placeholder="请输入部门名称" /></a-form-item>
      <a-form-item><a-button type="primary" html-type="submit" block>提交</a-button></a-form-item>
    </a-form>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import request from '../utils/request.js'
import { message } from 'ant-design-vue'

export default {
  name: 'SubmitJob',
  setup() {
    const formState = ref({ title:'', description:'', requirements:'', salary:'', location:'', quota:1, workTime:'', contactPerson:'', contactPhone:'', departmentId:'', departmentName:'' })

    const handleSubmit = async (values) => {
      try {
        await request.post('/department/jobs', { ...values })
        message.success('岗位提交成功，等待管理员审批')
        formState.value = { title:'', description:'', requirements:'', salary:'', location:'', quota:1, workTime:'', contactPerson:'', contactPhone:'', departmentId:formState.value.departmentId, departmentName:formState.value.departmentName }
      } catch (e) { message.error('提交岗位失败') }
    }

    const handleFinishFailed = (e) => { console.log('验证失败:', e) }

    onMounted(async () => {
      const user = JSON.parse(localStorage.getItem('user'))
      formState.value.departmentId = user.departmentId
      if (user.departmentId) {
        try {
          const res = await request.get(`/departments/${user.departmentId}`)
          const d = res.data || {}
          formState.value.departmentName = d.name || ''
        } catch (e) {}
      }
    })

    return { formState, handleSubmit, handleFinishFailed }
  }
}
</script>

<style scoped>
.submit-job-form :deep(.ant-form) { width:100%; }
.submit-job-form :deep(.ant-form-item) { display:block!important; margin-bottom:20px!important; }
.submit-job-form :deep(.ant-form-item-label) { display:inline-block!important; text-align:right!important; width:100px!important; padding-right:12px!important; line-height:32px!important; height:32px!important; vertical-align:top!important; }
.submit-job-form :deep(.ant-form-item-control) { display:inline-block!important; width:calc(100% - 112px)!important; vertical-align:top!important; }
.submit-job-form :deep(.ant-input),.submit-job-form :deep(.ant-input-number) { width:100%!important; height:32px!important; border-radius:4px!important; font-size:14px!important; }
.submit-job-form :deep(.ant-textarea) { border-radius:4px!important; font-size:14px!important; }
.submit-job-form :deep(.ant-btn) { height:40px!important; font-size:16px!important; width:100%!important; border-radius:4px!important; }
.submit-job-form :deep(.ant-form-item:last-child) { margin-left:112px!important; }
</style>
