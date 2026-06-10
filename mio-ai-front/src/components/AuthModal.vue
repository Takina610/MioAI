<template>
  <a-modal
    :open="visible"
    @update:open="$emit('update:visible', $event)"
    :footer="null"
    :width="400"
    :closable="true"
    :destroyOnClose="true"  
    class="auth-modal"
  >
    <div class="auth-container">
      <div class="form-header">
        <h2>{{ isLogin ? '登录' : '注册' }}</h2>
        <p>{{ isLogin ? '登录你的账户' : '创建新账户' }}</p>
      </div>

      <a-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit"
      >
        <a-form-item name="userAccount" label="用户名">
          <a-input
            :value="formData.userAccount"
            @update:value="formData.userAccount = $event"
            placeholder="请输入用户名"
            size="large"
          >
            <template #prefix>
              <UserOutlined />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item name="userPassword" label="密码">
          <a-input-password
            :value="formData.userPassword"
            @update:value="formData.userPassword = $event"
            placeholder="请输入密码"
            size="large"
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
          <div v-if="!isLogin" class="password-strength">
            <span class="strength-label">密码强度：</span>
            <a-progress
              :percent="passwordStrength.percent"
              :stroke-color="passwordStrength.color"
              :show-info="false"
              size="small"
            />
            <span v-if="formData.userPassword" class="strength-text" :style="{ color: passwordStrength.color }">
              {{ passwordStrength.text }}
            </span>
          </div>
        </a-form-item>

        <a-form-item v-if="!isLogin" name="checkPassword" label="确认密码">
          <a-input-password
            :value="formData.checkPassword"
            @update:value="formData.checkPassword = $event"
            placeholder="请再次输入密码"
            size="large"
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="loading"
          >
            {{ isLogin ? '登录' : '注册' }}
          </a-button>
        </a-form-item>
      </a-form>

      <div class="form-footer">
        <span v-if="isLogin">
          还没有账户？
          <a @click="toggleMode">立即注册</a>
        </span>
        <span v-else>
          已有账户？
          <a @click="toggleMode">立即登录</a>
        </span>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useUserStore } from '@/store/user'
import {
  UserOutlined,
  LockOutlined,
  IdcardOutlined
} from '@ant-design/icons-vue'

interface FormData {
  userAccount: string
  userPassword: string
  checkPassword: string
}

interface PasswordStrength {
  percent: number
  color: string
  text: string
}

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const userStore = useUserStore()

const loading = ref<boolean>(false)
const isLogin = ref<boolean>(true)
const formRef = ref<FormInstance | null>(null)
    
const formData = reactive<FormData>({
  userAccount: '',
  userPassword: '',
  checkPassword: ''
})

watch(
  () => props.visible,
  (newVisible) => {
    if (!newVisible) {
      resetForm()
    }
  }
)

function resetForm(): void {
  formRef.value?.resetFields()
  formData.userAccount = ''
  formData.userPassword = ''
  formData.checkPassword = ''
  isLogin.value = true
}

async function handleSubmit(): Promise<void> {
  loading.value = true
  try {
    if (isLogin.value) {
      await userStore.login({
        userAccount: formData.userAccount,
        userPassword: formData.userPassword
      })
      message.success('登录成功')
    } else {
      await userStore.register({
        userAccount: formData.userAccount,
        userPassword: formData.userPassword,
        checkPassword: formData.checkPassword
      })
      message.success('注册成功，请登录')
      resetForm()
      return
    }

    emit('update:visible', false)
    emit('success')
    formRef.value?.resetFields()
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const validateCheckPassword = async (_rule: Rule, value: string): Promise<void> => {
  if (!isLogin.value) {
    if (!value) {
      return Promise.reject('请确认密码')
    }
    if (value !== formData.userPassword) {
      return Promise.reject('两次输入的密码不一致')
    }
  }
  return Promise.resolve()
}

const rules: Record<string, Rule[]> = {
  userAccount: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为4-20个字符', trigger: 'blur' }
  ],
  userPassword: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为8-20个字符', trigger: 'blur' }
  ],
  checkPassword: [
    { validator: validateCheckPassword, trigger: 'blur' }
  ]
}

const passwordStrength = computed<PasswordStrength>(() => {
  const password = formData.userPassword
  if (!password) {
    return { percent: 0, color: '#d9d9d9', text: '' }
  }

  let score = 0

  if (password.length >= 8) score += 25
  if (password.length >= 12) score += 10
  if (/[a-z]/.test(password)) score += 15
  if (/[A-Z]/.test(password)) score += 15
  if (/[0-9]/.test(password)) score += 15
  if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) score += 20

  if (score <= 30) {
    return { percent: 25, color: '#ff4d4f', text: '弱' }
  } else if (score <= 50) {
    return { percent: 50, color: '#faad14', text: '一般' }
  } else if (score <= 70) {
    return { percent: 75, color: '#52c41a', text: '强' }
  } else {
    return { percent: 100, color: '#1890ff', text: '非常强' }
  }
})

function toggleMode(): void {
  isLogin.value = !isLogin.value
  formRef.value?.resetFields()
  formData.userAccount = ''
  formData.userPassword = ''
  formData.checkPassword = ''
}
</script>

<style lang="scss" scoped>
.auth-container {
  padding: 16px 0;

  .form-header {
    text-align: center;
    margin-bottom: 24px;

    h2 {
      font-size: 24px;
      font-weight: 600;
      color: $text-dark;
      margin-bottom: 8px;
    }

    p {
      font-size: 14px;
      color: #666;
    }
  }

  :deep(.ant-form-item-label > label) {
    color: $text-dark;
    font-weight: 500;
  }

  :deep(.ant-input-affix-wrapper),
  :deep(.ant-input) {
    border-radius: 8px;
  }

  .password-strength {
    margin-top: 8px;
    display: flex;
    align-items: center;
    gap: 8px;

    .strength-label {
      font-size: 12px;
      color: #666;
      white-space: nowrap;
    }

    :deep(.ant-progress) {
      flex: 1;
    }

    .strength-text {
      font-size: 12px;
      font-weight: 500;
      white-space: nowrap;
    }
  }

  :deep(.ant-btn-primary) {
    background: $primary-color;
    border-color: $primary-color;
    height: 44px;
    font-size: 15px;
    border-radius: 8px;

    &:hover {
      background: darken($primary-color, 10%);
      border-color: darken($primary-color, 10%);
    }
  }

  .form-footer {
    text-align: center;
    margin-top: 16px;
    color: #666;

    a {
      color: $primary-color;
      cursor: pointer;

      &:hover {
        text-decoration: underline;
      }
    }
  }
}
</style>
