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
    wrap-class-name="agent-create-modal"
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
          mood-cycle
          eye-color="#ffffff"
        />
      </div>

      <BotIconDesigner :config="config" eye-color="#ffffff" @change="config = $event" />

      <div class="form-section">
        <h3 class="section-title">名称</h3>
        <a-input v-model:value="formData.name" :maxlength="100" placeholder="给智能体起个名字，留空则用「未命名智能体」" />
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
          :disabled="submitLoading"
          :loading="submitLoading"
          @click="handleCreate"
        >
          立即开始
        </a-button>
      </div>
    </div>
  </a-modal>

  <!-- 编辑：与创建同款图标设计器（形体/颜色）+ 名称/描述；头像已由图标取代 -->
  <a-modal
    v-else
    :open="visible"
    @update:open="handleModalClose"
    title="编辑智能体"
    :mask-closable="false"
    width="620px"
    :confirm-loading="submitLoading"
    @ok="handleSubmit"
    @cancel="handleCancel"
    ok-text="保存"
    cancel-text="取消"
    wrap-class-name="agent-create-modal"
  >
    <div class="creator">
      <div class="creator-preview">
        <BotIcon
          :shape="config.shape"
          :fill="config.fill"
          state="idle"
          :size="92"
          follow
          mood-cycle
          eye-color="#ffffff"
        />
      </div>

      <BotIconDesigner :config="config" eye-color="#ffffff" @change="config = $event" />

      <a-form ref="formRef" :model="formData" :rules="rules" layout="vertical" class="form-section">
        <a-form-item name="name" label="名称" class="compact-item">
          <a-input
            :value="formData.name"
            @update:value="formData.name = $event"
            placeholder="请输入智能体名称"
          />
        </a-form-item>
        <a-form-item name="description" label="描述" class="compact-item">
          <a-textarea
            :value="formData.description"
            @update:value="formData.description = $event"
            placeholder="请输入描述"
            :maxlength="500"
            :rows="2"
          />
        </a-form-item>
      </a-form>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { addAgent, updateAgent, getAgentById } from '@/api/agent'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import BotIconDesigner from '@/components/bot-icon/BotIconDesigner.vue'
import { DEFAULT_BOT_ICON, parseBotIcon, serializeBotIcon } from '@/components/bot-icon/types'
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

const submitLoading = ref(false)
const formRef = ref<FormInstance | null>(null)

const formData = reactive<FormData>({
  name: '',
  description: ''
})

/** 图标配置（创建/编辑共用同一设计器） */
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
    const agent = await getAgentById(props.agentId)
    formData.name = agent.name || ''
    formData.description = agent.description || ''
    formData.avatar = agent.avatar || ''
    config.value = parseBotIcon(agent.icon) ?? { ...DEFAULT_BOT_ICON }
  } catch (e) {
    console.error(e)
    message.error('获取智能体信息失败')
  }
}

function handleModalClose(visible: boolean): void {
  if (!visible) {
    handleCancel()
  }
}

async function handleCancel(): Promise<void> {
  emit('update:visible', false)
}

/** 创建成功：转一圈撒花后关闭，由父级跳转编辑页。名称留空时用默认名 */
const DEFAULT_NAME = '未命名智能体'

async function handleCreate(): Promise<void> {
  if (submitLoading.value) return
  try {
    submitLoading.value = true
    const agentId = await addAgent({
      name: formData.name.trim() || DEFAULT_NAME,
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
    if (props.agentId) {
      // 头像保持不动（存量数据兼容），身份展示已由图标接管
      const data: AgentUpdateRequest = {
        id: props.agentId,
        name: formData.name.trim(),
        description: formData.description.trim() || undefined,
        avatar: formData.avatar,
        icon: serializeBotIcon(config.value)
      }
      await updateAgent(data)
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
  config.value = { ...DEFAULT_BOT_ICON }
  previewState.value = 'idle'
}
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

    .compact-item {
      margin-bottom: 12px;
    }
  }

  .creator-footer {
    display: flex;
    justify-content: center;
    padding-top: 34px;

    .start-btn {
      min-width: 200px;
      height: 44px;
      border-radius: 22px;
      font-size: 15px;
      font-weight: 600;
    }
  }
}
</style>
