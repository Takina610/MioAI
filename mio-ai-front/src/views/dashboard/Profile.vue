<template>
  <div class="profile-page">
    <div class="profile-header">
      <div class="avatar-section">
        <a-avatar :size="80" :src="userStore.userAvatar">
          {{ userStore.userName?.charAt(0)?.toUpperCase() }}
        </a-avatar>
        <div class="user-info">
          <h2>{{ userStore.userName }}</h2>
          <p>{{ userStore.userInfo?.userAccount }}</p>
        </div>
      </div>
    </div>

    <div class="profile-content">
      <a-card title="基本信息" class="info-card">
        <a-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          layout="vertical"
          @finish="handleUpdate"
        >
          <a-row :gutter="24">
            <a-col :span="12">
              <a-form-item name="userName" label="昵称">
                <a-input v-model="formData.userName" placeholder="请输入昵称" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item name="userAccount" label="账号">
                <a-input v-model="formData.userAccount" disabled />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item name="userRole" label="角色">
                <a-input :value="formData.userRole === 'admin' ? '管理员' : '普通用户'" disabled />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item name="userProfile" label="个人简介">
                <a-input v-model="formData.userProfile" placeholder="请输入个人简介" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item>
            <a-button type="primary" html-type="submit" :loading="loading">
              保存修改
            </a-button>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card title="安全设置" class="security-card">
        <div class="security-item">
          <div class="item-info">
            <h4>登录密码</h4>
            <p>定期更换密码可以提高账号安全性</p>
          </div>
          <a-button @click="showPasswordModal">修改密码</a-button>
        </div>
      </a-card>
    </div>

    <a-modal
      v-model="passwordModalVisible"
      title="修改密码"
      :confirm-loading="passwordLoading"
      @ok="handlePasswordChange"
    >
      <a-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        layout="vertical"
      >
        <a-form-item name="oldPassword" label="原密码">
          <a-input-password v-model="passwordForm.oldPassword" placeholder="请输入原密码" />
        </a-form-item>
        <a-form-item name="newPassword" label="新密码">
          <a-input-password v-model="passwordForm.newPassword" placeholder="请输入新密码" />
        </a-form-item>
        <a-form-item name="confirmPassword" label="确认密码">
          <a-input-password v-model="passwordForm.confirmPassword" placeholder="请再次输入新密码" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import { updateUser } from '@/api/user'

const userStore = useUserStore()
const formRef = ref(null)
const passwordFormRef = ref(null)
const loading = ref(false)
const passwordLoading = ref(false)
const passwordModalVisible = ref(false)

const formData = reactive({
  id: null,
  userName: '',
  userAccount: '',
  userRole: '',
  userProfile: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const rules = {
  userName: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

const validateConfirmPassword = async (rule, value) => {
  if (!value) {
    return Promise.reject('请确认密码')
  }
  if (value !== passwordForm.newPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为8-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

onMounted(() => {
  if (userStore.userInfo) {
    Object.assign(formData, {
      id: userStore.userInfo.id,
      userName: userStore.userInfo.userName,
      userAccount: userStore.userInfo.userAccount,
      userRole: userStore.userInfo.userRole,
      userProfile: userStore.userInfo.userProfile
    })
  }
})

async function handleUpdate() {
  loading.value = true
  try {
    await updateUser(formData)
    message.success('更新成功')
    userStore.setUserInfo({ ...userStore.userInfo, ...formData })
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showPasswordModal() {
  passwordModalVisible.value = true
  passwordFormRef.value?.resetFields()
}

async function handlePasswordChange() {
  try {
    await passwordFormRef.value?.validate()
    passwordLoading.value = true
    message.success('密码修改成功，请重新登录')
    passwordModalVisible.value = false
    await userStore.logout()
    window.location.href = '/login'
  } catch (e) {
    console.error(e)
  } finally {
    passwordLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  max-width: 800px;
  margin: 0 auto;

  .profile-header {
    background: #fff;
    border-radius: 12px;
    padding: 32px;
    margin-bottom: 24px;

    .avatar-section {
      display: flex;
      align-items: center;
      gap: 24px;

      .user-info {
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
    }
  }

  .profile-content {
    .info-card,
    .security-card {
      margin-bottom: 24px;
      border-radius: 12px;

      :deep(.ant-card-head-title) {
        font-weight: 600;
      }
    }

    :deep(.ant-btn-primary) {
      background: $primary-color;
      border-color: $primary-color;

      &:hover {
        background: #238b92;
        border-color: #238b92;
      }
    }

    .security-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 0;
      border-bottom: 1px solid #f0f0f0;

      &:last-child {
        border-bottom: none;
      }

      .item-info {
        h4 {
          font-size: 15px;
          font-weight: 500;
          color: $text-dark;
          margin-bottom: 4px;
        }

        p {
          font-size: 13px;
          color: #666;
        }
      }
    }
  }
}
</style>
