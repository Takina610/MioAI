<template>
  <div class="dash-page profile-page">
    <PageHeader title="个人中心" description="管理您的个人信息" />

    <div class="dash-page-content profile-content">
      <a-row :gutter="24">
        <a-col :span="6">
          <a-card class="avatar-card" :bordered="false">
            <div class="avatar-section">
              <div class="avatar-preview" @click="cropperRef?.pick()">
                <a-avatar :size="100" :src="userStore.userAvatar">
                  {{ userStore.userName?.charAt(0)?.toUpperCase() }}
                </a-avatar>
                <div class="avatar-overlay">
                  <EditOutlined />
                  <span>更换头像</span>
                </div>
              </div>
              <div class="user-basic">
                <div class="user-name">{{ userStore.userName }}</div>
                <div class="user-role">{{ userStore.userInfo?.userRole === 'admin' ? '管理员' : '普通用户' }}</div>
              </div>
            </div>
          </a-card>
        </a-col>

        <a-col :span="18">
          <a-card class="info-card" :bordered="false">
            <template #title>
              <span>基本信息</span>
            </template>
            <a-form
              ref="formRef"
              :model="formData"
              :rules="rules"
              @finish="handleUpdate"
            >
              <div class="form-grid">
                <div class="form-item">
                  <span class="label">昵称</span>
                  <a-input v-model:value="formData.userName" placeholder="请输入昵称" />
                </div>
                <div class="form-item">
                  <span class="label">账号</span>
                  <a-input v-model:value="formData.userAccount" disabled />
                </div>
                <div class="form-item">
                  <span class="label">角色</span>
                  <a-input :value="formData.userRole === 'admin' ? '管理员' : '普通用户'" disabled />
                </div>
                <div class="form-item">
                  <span class="label">简介</span>
                  <a-input v-model:value="formData.userProfile" placeholder="请输入个人简介" />
                </div>
              </div>
              <div class="form-actions">
                <a-button type="primary" html-type="submit" :loading="loading">
                  保存修改
                </a-button>
              </div>
            </a-form>
          </a-card>
        </a-col>
      </a-row>

      <a-card class="setting-card" :bordered="false">
        <template #title>
          <span>安全设置</span>
        </template>
        <div class="setting-item">
          <div class="item-info">
            <span class="label">登录密码</span>
          </div>
          <a-button @click="passwordModalVisible = true">修改密码</a-button>
        </div>
      </a-card>

      <a-card class="setting-card" :bordered="false">
        <template #title>
          <span>模型设置</span>
        </template>
        <div class="setting-item">
          <div class="item-info">
            <span class="label">AI 模型提供方</span>
          </div>
          <ProviderSwitch />
        </div>
      </a-card>
    </div>

    <PasswordModal v-model:open="passwordModalVisible" />
    <AvatarCropper ref="cropperRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useUserStore } from '@/store/user'
import { updateUser } from '@/api/user'
import type { UpdateUserRequest, UserVO } from '@/types'
import { EditOutlined } from '@ant-design/icons-vue'
import PageHeader from '../components/PageHeader.vue'
import PasswordModal from './components/PasswordModal.vue'
import AvatarCropper from './components/AvatarCropper.vue'
import ProviderSwitch from './components/ProviderSwitch.vue'

interface FormData {
  id: number | null
  userName: string
  userAccount: string
  userRole: string
  userProfile: string
}

const userStore = useUserStore()
const loading = ref(false)
const passwordModalVisible = ref(false)
const formRef = ref<FormInstance | null>(null)
const cropperRef = ref<InstanceType<typeof AvatarCropper> | null>(null)

const formData = reactive<FormData>({
  id: null,
  userName: '',
  userAccount: '',
  userRole: '',
  userProfile: ''
})

const rules: Record<string, Rule[]> = {
  userName: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

async function handleUpdate(): Promise<void> {
  loading.value = true
  try {
    await updateUser(formData as UpdateUserRequest)
    message.success('更新成功')
    if (userStore.userInfo) {
      userStore.setUserInfo({ ...userStore.userInfo, ...formData } as UserVO)
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (userStore.userInfo) {
    const info: UserVO = userStore.userInfo
    Object.assign(formData, {
      id: info.id,
      userName: info.userName,
      userAccount: info.userAccount,
      userRole: info.userRole,
      userProfile: info.userProfile || ''
    })
  }
})
</script>

<style lang="scss" scoped>
.profile-page {
  .profile-content {
    max-width: 1000px;
    margin: 0 auto;
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 20px;

    .avatar-card,
    .info-card,
    .setting-card {
      border-radius: 12px;
      background: #fff;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);

      :deep(.ant-card-head) {
        min-height: 48px;
        padding: 0 20px;
        border-bottom: 1px solid #ece9de;

        .ant-card-head-title {
          font-weight: 600;
          font-size: 16px;
          padding: 12px 0;
        }
      }

      :deep(.ant-card-body) {
        padding: 20px;
      }
    }

    .avatar-card {
      height: 100%;

      .avatar-section {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 16px;

        .avatar-preview {
          position: relative;
          cursor: pointer;
          border-radius: 50%;
          overflow: hidden;

          .avatar-overlay {
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.5);
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            color: white;
            opacity: 0;
            transition: opacity 0.3s;

            .anticon {
              font-size: 20px;
              margin-bottom: 4px;
            }

            span {
              font-size: 12px;
            }
          }

          &:hover .avatar-overlay {
            opacity: 1;
          }
        }

        .user-basic {
          text-align: center;

          .user-name {
            font-size: 16px;
            font-weight: 600;
            color: #141413;
            margin-bottom: 4px;
          }

          .user-role {
            font-size: 13px;
            color: #6e6b62;
          }
        }
      }
    }

    .info-card {
      .form-grid {
        width: 100%;
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 20px;

        .form-item {
          display: flex;
          align-items: center;
          gap: 12px;

          .label {
            font-size: 14px;
            color: #6e6b62;
            min-width: 50px;
            flex-shrink: 0;
          }

          :deep(.ant-input) {
            flex: 1;
          }
        }
      }

      .form-actions {
        width: 100%;
        display: block;
        margin-top: 20px;
        padding-top: 16px;
        border-top: 1px solid #ece9de;
        clear: both;
      }
    }

    .setting-card {
      flex-shrink: 0;

      .setting-item {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .item-info {
          display: flex;
          align-items: center;
          gap: 16px;

          .label {
            font-size: 15px;
            font-weight: 500;
            color: $text-dark;
          }
        }
      }
    }
  }
}
</style>
