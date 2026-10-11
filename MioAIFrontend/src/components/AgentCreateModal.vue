<template>
  <!-- 创建：Grok 风格机器人图标设计流程（预览→颜色→形体→名称→描述→立即开始） -->
  <a-modal
    v-if="mode === 'create'"
    :open="visible"
    @update:open="handleModalClose"
    title="创建智能体"
    :mask-closable="false"
    width="620px"
    :footer="null"
  >
    <div class="creator">
      <div class="creator-preview">
        <BotIcon
          ref="previewRef"
          :shape="config.shape"
          :fill="config.fill"
          :state="previewState"
          :size="92"
          follow
          eye-color="#ffffff"
        />
      </div>

      <BotIconDesigner :config="config" eye-color="#ffffff" @change="config = $event" />

      <div class="form-section">
        <h3 class="section-title">名称</h3>
        <a-input v-model:value="formData.name" :maxlength="100" placeholder="给智能体起个名字" />
      </div>
      <div class="form-section">
        <h3 class="section-title">描述</h3>
        <a-textarea v-model:value="formData.description" :maxlength="500" :rows="2" placeholder="它擅长什么、用于什么场景" />
      </div>

      <div class="creator-footer">
        <a-button
          type="primary"
          size="large"
          class="start-btn"
          :disabled="!formData.name.trim() || submitLoading"
          :loading="submitLoading"
          @click="handleCreate"
        >
          立即开始
        </a-button>
      </div>
    </div>
  </a-modal>

  <!-- 编辑：沿用原名称/描述/头像表单 -->
  <a-modal
    v-else
    :open="visible"
    @update:open="handleModalClose"
    title="编辑智能体"
    :confirm-loading="submitLoading"
    @ok="handleSubmit"
    @cancel="handleCancel"
    :mask-closable="false"
    wrap-class-name="agent-avatar-upload"
    width="400px"
    ok-text="保存"
    cancel-text="取消"
  >
    <a-form ref="formRef" :model="formData" :rules="rules" layout="vertical">
      <a-form-item name="name" label="名称">
        <a-input
          :value="formData.name"
          @update:value="formData.name = $event"
          placeholder="请输入智能体名称"
        />
      </a-form-item>
      <a-form-item name="description" label="描述">
        <a-textarea
          :value="formData.description"
          @update:value="formData.description = $event"
          placeholder="请输入描述"
          :maxlength="500"
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
import { reactive, ref, watch, onBeforeUnmount } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { EditOutlined } from '@ant-design/icons-vue'
import { addAgent, uploadAgentAvatar, deleteTempAvatar, updateAgent, getAgentById } from '@/api/agent'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import BotIconDesigner from '@/components/bot-icon/BotIconDesigner.vue'
import { DEFAULT_BOT_ICON, serializeBotIcon } from '@/components/bot-icon/types'
import type { BotIconConfig } from '@/components/bot-icon/types'
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

const isCreate = props.mode !== 'edit'
const formData = reactive<FormData>({
  name: '',
  description: ''
})

/** 创建流程的图标配置（颜色/形体设计器） */
const config = ref<BotIconConfig>({ ...DEFAULT_BOT_ICON })
const previewState = ref('idle')
const previewRef = ref<InstanceType<typeof BotIcon> | null>(null)

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

    // 删除之前的临时头像
    if (avatarUrl.value && avatarUrl.value.includes('temp_')) {
      try {
        await deleteTempAvatar(avatarUrl.value)
      } catch (e) {
        console.error('删除之前的临时头像失败', e)
      }
    }

    const url = await uploadAgentAvatar(file)
    avatarUrl.value = url
  } catch (e) {
    console.error(e)
    message.error('上传头像失败')
  } finally {
    submitLoading.value = false
  }
}

function handleModalClose(visible: boolean): void {
  if (!visible) {
    handleCancel()
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

/** 创建成功：转一圈撒花后关闭，由父级跳转编辑页 */
async function handleCreate(): Promise<void> {
  if (!formData.name.trim() || submitLoading.value) return
  try {
    submitLoading.value = true
    const agentId = await addAgent({
      name: formData.name.trim(),
      description: formData.description.trim() || undefined,
      icon: serializeBotIcon(config.value)
    } as AgentAddRequest)
    previewState.value = 'celebrate'
    previewRef.value?.spinOnce(2)
    previewRef.value?.burstOnce()
    message.success('创建成功')
    window.setTimeout(() => {
      emit('update:visible', false)
      emit('success', agentId)
      resetForm()
    }, 1000)
  } catch (e) {
    console.error(e)
    message.error('创建失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmit(): Promise<void> {
  try {
    await formRef.value?.validate()
    submitLoading.value = true

    const finalAvatar = avatarUrl.value || defaultAvatarUrl

    if (props.agentId) {
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
    }
    resetForm()
  } catch (e) {
    console.error(e)
    message.error('更新失败')
  } finally {
    submitLoading.value = false
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
  if (isCreate) {
    config.value = { ...DEFAULT_BOT_ICON }
    previewState.value = 'idle'
  }
}

onBeforeUnmount(() => {
  if (avatarUrl.value && avatarUrl.value !== originalAvatarUrl.value && avatarUrl.value.includes('temp_')) {
    deleteTempAvatar(avatarUrl.value).catch(e => console.error('删除临时头像失败', e))
  }
})
</script>

<style lang="scss">
.agent-create-modal {
  .creator {
    display: flex;
    flex-direction: column;
  }

  .creator-preview {
    display: flex;
    justify-content: center;
    padding: 4px 0 18px;
  }

  .form-section {
    margin-top: 16px;

    .section-title {
      font-size: 13px;
      font-weight: 600;
      color: $text-dark;
      margin: 0 0 8px;
    }
  }

  .creator-footer {
    display: flex;
    justify-content: center;
    padding-top: 20px;

    .start-btn {
      min-width: 200px;
      height: 44px;
      border-radius: 22px;
      font-size: 15px;
      font-weight: 600;
    }
  }
}

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
