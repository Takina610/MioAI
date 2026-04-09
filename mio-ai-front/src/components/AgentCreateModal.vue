<template>
  <a-modal
    :open="visible"
    @update:open="handleModalClose"
    :title="mode === 'create' ? '创建智能体' : '编辑智能体'"
    :confirm-loading="submitLoading"
    @ok="handleSubmit"
    @cancel="handleCancel"
    :mask-closable="false"
    wrap-class-name="agent-avatar-upload"
    width="400px"
    :ok-text="mode === 'create' ? '创建' : '保存'"
    cancel-text="取消"
  >
    <a-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      layout="vertical"
    >
      <a-form-item name="name" label="名称">
        <a-input :value="formData.name" @update:value="formData.name = $event" placeholder="请输入智能体名称" />
      </a-form-item>
      <a-form-item name="description" label="描述">
        <a-textarea
          :value="formData.description"
          @update:value="formData.description = $event"
          placeholder="请输入描述"
          :rows="3"
        />
      </a-form-item>
      <a-form-item name="avatar" label="应用头像">
        <div class="avatar-section">
          <div class="avatar-preview" @click="triggerFileSelect">
            <a-avatar :size="60" :src="avatarUrl || defaultAvatarUrl" shape="square" />
            <div class="avatar-overlay">
              <EditOutlined />
            </div>
          </div>
        </div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { EditOutlined } from '@ant-design/icons-vue'
import { addAgent, uploadAgentAvatar, deleteTempAvatar, updateAgent, getAgentById } from '@/api/agent'
import type { AgentAddRequest, AgentUpdateRequest } from '@/types'

interface FormData {
  name: string
  description: string
  avatar?: string
}

const props = defineProps<{
  visible: boolean
  mode?: 'create' | 'edit'
  agentId?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'success', agentId?: number | void): void
}>()

const defaultAvatarUrl = 'https://cdn.tak1na.cn/custom_agent.png'
const submitLoading = ref(false)
const loading = ref(false)
const formRef = ref<FormInstance | null>(null)
const avatarUrl = ref<string>('')
const originalAvatarUrl = ref<string>('')

const formData = reactive<FormData>({
  name: '',
  description: ''
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

watch(() => props.visible, async (newVal) => {
  if (!newVal) {
    resetForm()
  } else if (props.mode === 'edit' && props.agentId) {
    await fetchAgentData()
  }
})

async function fetchAgentData(): Promise<void> {
  if (!props.agentId) return
  
  try {
    loading.value = true
    const agent = await getAgentById(props.agentId)
    formData.name = agent.name || ''
    formData.description = agent.description || ''
    formData.avatar = agent.avatar || ''
    avatarUrl.value = agent.avatar || ''
    originalAvatarUrl.value = agent.avatar || ''
  } catch (e) {
    console.error(e)
    message.error('获取智能体信息失败')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  formRef.value?.resetFields()
  Object.assign(formData, {
    name: '',
    description: '',
    avatar: ''
  })
  avatarUrl.value = ''
  originalAvatarUrl.value = ''
}

function triggerFileSelect(): void {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = handleFileChange
  input.click()
}

async function handleFileChange(event: Event): Promise<void> {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]

  if (!file) return

  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片文件')
    return
  }

  const isLt20M = file.size / 1024 / 1024 < 20
  if (!isLt20M) {
    message.error('图片大小不能超过 20MB')
    return
  }

  try {
    submitLoading.value = true
    const url = await uploadAgentAvatar(file)
    avatarUrl.value = url
  } catch (e) {
    console.error(e)
    message.error('上传头像失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleCancel(): Promise<void> {
  if (avatarUrl.value && avatarUrl.value !== originalAvatarUrl.value && avatarUrl.value.includes('temp_')) {
    try {
      await deleteTempAvatar(avatarUrl.value)
    } catch (e) {
      console.error('删除临时头像失败', e)
    }
  }
  avatarUrl.value = ''
  emit('update:visible', false)
}

function handleModalClose(visible: boolean): void {
  if (!visible) {
    handleCancel()
  }
}

async function handleSubmit(): Promise<void> {
  try {
    await formRef.value?.validate()
    submitLoading.value = true

    const finalAvatar = avatarUrl.value || defaultAvatarUrl

    if (props.mode === 'edit' && props.agentId) {
      const data: AgentUpdateRequest = {
        id: props.agentId,
        name: formData.name,
        description: formData.description,
        avatar: finalAvatar
      }
      await updateAgent(data)
      avatarUrl.value = ''
      emit('update:visible', false)
      emit('success', props.agentId)
      message.success('更新成功')
    } else {
      const agentId = await addAgent({ ...formData, avatar: finalAvatar } as AgentAddRequest)
      avatarUrl.value = ''
      emit('update:visible', false)
      emit('success', agentId)
    }
    resetForm()
  } catch (e) {
    console.error(e)
    if (props.mode === 'edit') {
      message.error('更新失败')
    }
  } finally {
    submitLoading.value = false
  }
}
</script>

<style lang="scss">
.agent-avatar-upload {
  .avatar-section {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
    margin-bottom: 24px;
  }

  .avatar-preview {
    position: relative;
    cursor: pointer;
    border-radius: 10px;
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
      font-size: 16px;
    }

    &:hover .avatar-overlay {
      opacity: 1;
    }
  }
}
</style>
