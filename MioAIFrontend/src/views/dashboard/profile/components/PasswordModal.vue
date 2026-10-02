<template>
  <a-modal
    :open="open"
    @update:open="emit('update:open', $event)"
    title="修改密码"
    :confirm-loading="passwordLoading"
    @ok="handlePasswordChange"
    ok-text="确认"
    cancel-text="取消"
  >
    <a-form
      ref="passwordFormRef"
      :model="passwordForm"
      :rules="passwordRules"
      layout="vertical"
    >
      <a-form-item name="oldPassword" label="原密码">
        <a-input-password v-model:value="passwordForm.oldPassword" placeholder="请输入原密码" />
      </a-form-item>
      <a-form-item name="newPassword" label="新密码">
        <a-input-password v-model:value="passwordForm.newPassword" placeholder="请输入新密码" />
      </a-form-item>
      <a-form-item name="confirmPassword" label="确认密码">
        <a-input-password v-model:value="passwordForm.confirmPassword" placeholder="请再次输入新密码" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { updatePassword } from '@/api/user'
import { useUserStore } from '@/store/user'
import type { PasswordUpdateRequest } from '@/types'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()

const userStore = useUserStore()
const passwordFormRef = ref<FormInstance | null>(null)
const passwordLoading = ref(false)

const passwordForm = reactive<PasswordUpdateRequest>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = async (_rule: Rule, value: string): Promise<void> => {
  if (!value) {
    return Promise.reject('请确认密码')
  }
  if (value !== passwordForm.newPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

const passwordRules: Record<string, Rule[]> = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为8-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

watch(() => props.open, (open) => {
  if (open) {
    passwordFormRef.value?.resetFields()
  }
})

async function handlePasswordChange(): Promise<void> {
  try {
    await passwordFormRef.value?.validate()
    passwordLoading.value = true
    await updatePassword(passwordForm)
    message.success('密码修改成功，请重新登录')
    emit('update:open', false)
    await userStore.logout()
    window.location.href = '/'
  } catch (e) {
    console.error(e)
  } finally {
    passwordLoading.value = false
  }
}
</script>
