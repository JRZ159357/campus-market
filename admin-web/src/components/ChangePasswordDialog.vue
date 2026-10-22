<!-- 文件路径: admin-web/src/components/ChangePasswordDialog.vue -->
<template>
  <el-dialog
    v-model="visible"
    title="修改密码"
    width="460px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="handleClosed"
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="tip"
      title="修改成功后请使用新密码重新登录"
    />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model.trim="form.oldPassword" type="password" show-password placeholder="请输入原密码" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model.trim="form.newPassword" type="password" show-password placeholder="6-20 位，建议字母与数字组合" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model.trim="form.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

// 双向绑定：父组件用 v-model 控制显隐
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const formRef = ref()
const submitting = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const handleClosed = () => {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  formRef.value?.clearValidate()
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    visible.value = false
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.tip {
  margin-bottom: 16px;
}
</style>
