<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-left">
        <div class="brand">
          <span class="logo-text" @click="goHome">MioAI</span>
        </div>
        <div class="welcome">
          <h1>欢迎使用 MioAI</h1>
          <p>构建你的AI智能体，连接知识库与工具</p>
        </div>
        <div class="features">
          <div class="feature-item">
            <CheckCircleOutlined />
            <span>智能体快速创建</span>
          </div>
          <div class="feature-item">
            <CheckCircleOutlined />
            <span>知识库管理</span>
          </div>
          <div class="feature-item">
            <CheckCircleOutlined />
            <span>MCP工具集成</span>
          </div>
        </div>
      </div>

      <div class="login-right">
        <div class="form-container">
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
                v-model="formData.userAccount"
                placeholder="请输入用户名"
                size="large"
              >
                <template #prefix>
                  <UserOutlined />
                </template>
              </a-input>
            </a-form-item>

            <a-form-item v-if="!isLogin" name="userName" label="昵称">
              <a-input
                v-model="formData.userName"
                placeholder="请输入昵称"
                size="large"
              >
                <template #prefix>
                  <IdcardOutlined />
                </template>
              </a-input>
            </a-form-item>

            <a-form-item name="userPassword" label="密码">
              <a-input-password
                v-model="formData.userPassword"
                placeholder="请输入密码"
                size="large"
              >
                <template #prefix>
                  <LockOutlined />
                </template>
              </a-input-password>
            </a-form-item>

            <a-form-item v-if="!isLogin" name="checkPassword" label="确认密码">
              <a-input-password
                v-model="formData.checkPassword"
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
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import {
  UserOutlined,
  LockOutlined,
  IdcardOutlined,
  CheckCircleOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const isLogin = ref(true)

const formData = reactive({
  userAccount: '',
  userName: '',
  userPassword: '',
  checkPassword: ''
})

const validateCheckPassword = async (rule, value) => {
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

const rules = {
  userAccount: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为4-20个字符', trigger: 'blur' }
  ],
  userName: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  userPassword: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为8-20个字符', trigger: 'blur' }
  ],
  checkPassword: [
    { validator: validateCheckPassword, trigger: 'blur' }
  ]
}

function toggleMode() {
  isLogin.value = !isLogin.value
  formRef.value?.resetFields()
}

function goHome() {
  router.push('/')
}

async function handleSubmit() {
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
        userName: formData.userName,
        userPassword: formData.userPassword,
        checkPassword: formData.checkPassword
      })
      message.success('注册成功，请登录')
      isLogin.value = true
      formRef.value?.resetFields()
      return
    }

    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: #fff;
}

.login-container {
  display: flex;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  min-height: 100vh;

  @media (max-width: 768px) {
    flex-direction: column;
  }
}

.login-left {
  flex: 1;
  background: $secondary-color;
  padding: 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: #fff;

  @media (max-width: 768px) {
    padding: 40px 24px;
  }

  .brand {
    margin-bottom: 60px;

    .logo-text {
      font-size: 32px;
      font-weight: 700;
      color: $primary-color;
      cursor: pointer;
    }
  }

  .welcome {
    margin-bottom: 40px;

    h1 {
      font-size: 36px;
      font-weight: 600;
      margin-bottom: 16px;
    }

    p {
      font-size: 16px;
      opacity: 0.8;
    }
  }

  .features {
    display: flex;
    flex-direction: column;
    gap: 16px;

    .feature-item {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 15px;

      .anticon {
        color: $primary-color;
        font-size: 18px;
      }
    }
  }
}

.login-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;

  @media (max-width: 768px) {
    padding: 40px 24px;
  }
}

.form-container {
  width: 100%;
  max-width: 400px;

  .form-header {
    text-align: center;
    margin-bottom: 32px;

    h2 {
      font-size: 28px;
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

  :deep(.ant-btn-primary) {
    background: $primary-color;
    border-color: $primary-color;
    height: 48px;
    font-size: 16px;
    border-radius: 8px;

    &:hover {
      background: darken($primary-color, 10%);
      border-color: darken($primary-color, 10%);
    }
  }

  .form-footer {
    text-align: center;
    margin-top: 24px;
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
