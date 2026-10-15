<template>
  <a-drawer
    :open="open"
    :title="drawerTitle"
    width="480"
    destroy-on-close
    @close="$emit('update:open', false)"
  >
    <!-- 详情模式：全字段只读展示 -->
    <a-descriptions v-if="mode === 'detail'" :column="1" bordered size="small">
      <a-descriptions-item label="ID">{{ record?.id }}</a-descriptions-item>
      <a-descriptions-item label="账号">{{ record?.userAccount }}</a-descriptions-item>
      <a-descriptions-item label="昵称">{{ record?.userName || '-' }}</a-descriptions-item>
      <a-descriptions-item label="角色">{{ record?.userRole === 'admin' ? '管理员' : '普通用户' }}</a-descriptions-item>
      <a-descriptions-item label="状态">
        <span class="adm-dot" :class="record?.status !== 0 ? 'is-success' : 'is-danger'">
          {{ record?.status === 0 ? '已停用' : '正常' }}
        </span>
      </a-descriptions-item>
      <a-descriptions-item label="头像">
        <a-avatar :src="record?.userAvatar" :size="32">{{ record?.userName?.charAt(0) || 'U' }}</a-avatar>
      </a-descriptions-item>
      <a-descriptions-item label="简介">{{ record?.userProfile || '-' }}</a-descriptions-item>
      <a-descriptions-item label="创建时间">{{ formatDateTime(record?.createTime) }}</a-descriptions-item>
    </a-descriptions>

    <!-- 表单模式：新增 / 编辑 -->
    <a-form v-else :model="form" layout="vertical">
      <a-form-item label="账号" required>
        <a-input v-model:value="form.userAccount" placeholder="登录账号（至少 4 位）" :maxlength="40" />
      </a-form-item>
      <a-form-item label="昵称">
        <a-input v-model:value="form.userName" placeholder="昵称" :maxlength="40" />
      </a-form-item>
      <a-form-item :label="isCreate ? '初始密码' : '重置密码'" :required="isCreate">
        <a-input-password
          v-model:value="form.userPassword"
          :placeholder="isCreate ? '至少 8 位' : '留空表示不修改'"
          :maxlength="64"
        />
      </a-form-item>
      <a-form-item label="角色">
        <a-radio-group v-model:value="form.userRole">
          <a-radio value="user">普通用户</a-radio>
          <a-radio value="admin">管理员</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="头像 URL">
        <a-input v-model:value="form.userAvatar" placeholder="https://..." :maxlength="500" />
      </a-form-item>
      <a-form-item label="简介">
        <a-textarea v-model:value="form.userProfile" :rows="3" :maxlength="500" />
      </a-form-item>
    </a-form>

    <template v-if="mode === 'form'" #footer>
      <a-space style="display: flex; justify-content: flex-end">
        <a-button @click="$emit('update:open', false)">取消</a-button>
        <a-button type="primary" :loading="submitting" @click="handleSubmit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import MioMessage from '@/utils/message'
import { formatDateTime } from '@/utils/format'
import { createUser, updateUser } from '@/api/adminUser'
import type { UserAdminVO } from '@/api/adminUser'

const props = defineProps<{
  open: boolean
  mode: 'detail' | 'form'
  /** null 表示新增 */
  record: UserAdminVO | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'saved'): void
}>()

const isCreate = computed(() => props.mode === 'form' && props.record === null)
const drawerTitle = computed(() =>
  props.mode === 'detail' ? '用户详情' : isCreate.value ? '新增用户' : '编辑用户'
)

const form = reactive({
  userAccount: '',
  userName: '',
  userPassword: '',
  userRole: 'user',
  userAvatar: '',
  userProfile: ''
})
const submitting = ref(false)

watch(
  () => props.open,
  open => {
    if (!open || props.mode !== 'form') return
    form.userAccount = props.record?.userAccount ?? ''
    form.userName = props.record?.userName ?? ''
    form.userPassword = ''
    form.userRole = props.record?.userRole ?? 'user'
    form.userAvatar = props.record?.userAvatar ?? ''
    form.userProfile = props.record?.userProfile ?? ''
  }
)

async function handleSubmit() {
  if (!form.userAccount.trim()) {
    MioMessage.warning('请填写账号')
    return
  }
  if (isCreate.value && form.userPassword.length < 8) {
    MioMessage.warning('初始密码至少 8 位')
    return
  }
  if (!isCreate.value && form.userPassword && form.userPassword.length < 8) {
    MioMessage.warning('密码至少 8 位')
    return
  }
  submitting.value = true
  try {
    if (isCreate.value) {
      await createUser({ ...form })
      MioMessage.success('用户已创建')
    } else {
      const payload: Record<string, unknown> = {
        id: props.record!.id,
        userAccount: form.userAccount,
        userName: form.userName,
        userRole: form.userRole,
        userAvatar: form.userAvatar,
        userProfile: form.userProfile
      }
      if (form.userPassword) payload.userPassword = form.userPassword
      await updateUser(payload as never)
      MioMessage.success('已保存')
    }
    emit('update:open', false)
    emit('saved')
  } catch {
    MioMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}
</script>
