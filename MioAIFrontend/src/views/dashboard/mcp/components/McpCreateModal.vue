<template>
  <a-modal
    :open="visible"
    @update:open="$emit('update:visible', $event)"
    :title="editingMcp ? '编辑MCP工具' : '添加MCP工具'"
    :footer="null"
    width="700px"
    :maskClosable="false"
    @cancel="handleCancel"
  >
    <a-steps :current="currentStep" class="steps-container">
      <a-step title="基本信息" description="输入MCP配置" />
      <a-step title="校验配置" description="验证可用性" />
    </a-steps>

    <div class="step-content">
      <template v-if="currentStep === 0">
        <a-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          layout="vertical"
          class="form-container"
        >
          <a-form-item name="name" label="工具名称">
            <a-input v-model:value="formData.name" placeholder="请输入工具名称" />
          </a-form-item>
          <a-form-item name="description" label="描述">
            <a-textarea
              v-model:value="formData.description"
              placeholder="请输入描述"
              :maxlength="500"
              :rows="3"
            />
          </a-form-item>
          <a-form-item name="config" label="MCP配置">
            <McpCodeEditor
              v-model="formData.config"
              :min-height="220"
              :placeholder="configPlaceholder"
            />
          </a-form-item>
        </a-form>
        <div class="step-actions">
          <a-button @click="handleCancel">取消</a-button>
          <a-button type="primary" @click="handleNext" :loading="validating">
            下一步
          </a-button>
        </div>
      </template>

      <template v-else-if="currentStep === 1">
        <div class="validate-result">
          <template v-if="validateResult">
            <template v-if="validateResult.success">
              <div class="success-header">
                <CheckCircleOutlined class="success-icon" />
                <span class="success-title">校验成功</span>
                <span class="success-count">发现 {{ validateResult.tools?.length || 0 }} 个工具</span>
              </div>
              <div class="server-info" v-if="validateResult.serverInfo">
                <span class="info-label">服务器：</span>
                <span class="info-value">{{ validateResult.serverInfo.name || '-' }}</span>
                <span class="info-divider">|</span>
                <span class="info-label">版本：</span>
                <span class="info-value">{{ validateResult.serverInfo.version || '-' }}</span>
              </div>
              <div class="warning-list" v-if="validateResult.warnings?.length">
                <div class="warning-item" v-for="w in validateResult.warnings" :key="w">{{ w }}</div>
              </div>
              <div class="tools-list" v-if="validateResult.tools && validateResult.tools.length > 0">
                <div class="tool-items">
                  <div class="tool-item" v-for="tool in validateResult.tools" :key="tool.name">
                    <div class="tool-header">
                      <ToolOutlined class="tool-icon" />
                      <span class="tool-name">{{ tool.name }}</span>
                    </div>
                    <p class="tool-desc">{{ tool.description || '暂无描述' }}</p>
                  </div>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="error-header">
                <CloseCircleOutlined class="error-icon" />
                <span class="error-title">校验失败</span>
              </div>
              <div class="error-message">{{ getErrorMessage(validateResult) }}</div>
            </template>
          </template>
          <template v-else>
            <div class="validating">
              <a-spin size="large" />
              <p>正在校验配置...</p>
            </div>
          </template>
        </div>
        <div class="step-actions">
          <a-button @click="handlePrev">上一步</a-button>
          <a-button 
            v-if="validateResult?.success" 
            type="primary" 
            @click="handleSubmit" 
            :loading="submitting"
          >
            {{ editingMcp ? '保存' : '创建' }}
          </a-button>
          <a-button v-else type="primary" @click="handleRevalidate" :loading="validating">
            重新校验
          </a-button>
        </div>
      </template>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { ToolOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { validateMcpConfig } from '@/api/mcpTool'
import { formatValidateError } from '@/utils/mcpTool'
import McpCodeEditor from '@/views/dashboard/mcp/components/McpCodeEditor.vue'
import type { McpTool, McpToolAddRequest, McpValidateResult } from '@/types'

const props = defineProps<{
  visible: boolean
  editingMcp?: McpTool | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'submit', data: McpToolAddRequest): void
}>()

const validating = ref(false)
const submitting = ref(false)
const currentStep = ref(0)
const formRef = ref<FormInstance | null>(null)
const validateResult = ref<McpValidateResult | null>(null)

const formData = reactive({
  name: '',
  description: '',
  config: ''
})

const configPlaceholder = `{
  "mcpServers": {
    "server-name": {
      "command": "npx",
      "args": ["-y", "mcp-server-example"]
    }
  }
}`

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入工具名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }],
  config: [{ required: true, message: '请输入MCP配置', trigger: 'blur' }]
}

watch(() => props.visible, (val) => {
  if (val) {
    if (props.editingMcp) {
      Object.assign(formData, {
        name: props.editingMcp.name,
        description: props.editingMcp.description || '',
        config: props.editingMcp.config || ''
      })
    } else {
      resetForm()
    }
  }
})

function getErrorMessage(result: McpValidateResult): string {
  return formatValidateError(result.errorType, result.errorMessage)
}

async function handleNext(): Promise<void> {
  try {
    await formRef.value?.validate()
    validating.value = true
    validateResult.value = null
    currentStep.value = 1
    
    const result = await validateMcpConfig({ config: formData.config })
    validateResult.value = result
  } catch (e) {
    currentStep.value = 0
  } finally {
    validating.value = false
  }
}

function handlePrev(): void {
  validating.value = false
  currentStep.value = 0
}

async function handleRevalidate(): Promise<void> {
  validating.value = true
  validateResult.value = null
  
  try {
    const result = await validateMcpConfig({ config: formData.config })
    validateResult.value = result
  } catch (e) {
    message.error('校验失败，请重试')
  } finally {
    validating.value = false
  }
}

async function handleSubmit(): Promise<void> {
  if (!validateResult.value?.success) {
    message.error('请先完成配置校验')
    return
  }
  
  submitting.value = true
  try {
    const data: McpToolAddRequest = {
      name: formData.name,
      description: formData.description,
      config: formData.config,
      toolInfo: JSON.stringify(validateResult.value.tools || [])
    }
    emit('submit', data)
  } finally {
    submitting.value = false
  }
}

function handleCancel(): void {
  validating.value = false
  submitting.value = false
  emit('update:visible', false)
}

function resetForm(): void {
  currentStep.value = 0
  validateResult.value = null
  validating.value = false
  submitting.value = false
  formRef.value?.resetFields()
  Object.assign(formData, {
    name: '',
    description: '',
    config: ''
  })
}
</script>

<style lang="scss" scoped>
.steps-container {
  margin-bottom: 20px;
}

.step-content {
  max-height: 600px;
  overflow-y: auto;
}

.form-container {
  margin-top: 16px;
}

.step-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ece9de;
}

.validate-result {
  padding: 8px 0;
}

.success-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  margin-bottom: 12px;
  
  .success-icon {
    color: #52c41a;
    font-size: 20px;
    margin-right: 8px;
  }
  
  .success-title {
    font-weight: 600;
    color: #52c41a;
    margin-right: 12px;
  }
  
  .success-count {
    color: #6e6b62;
    font-size: 13px;
  }
}

.server-info {
  padding: 8px 12px;
  background: #f5f3ec;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;
  
  .info-label {
    color: #8c8a82;
  }
  
  .info-value {
    color: #141413;
    font-weight: 500;
  }
  
  .info-divider {
    margin: 0 12px;
    color: #e8e8e8;
  }
}

.error-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f9ece9;
  border: 1px solid #f0d9d6;
  border-radius: 8px;
  margin-bottom: 12px;
  
  .error-icon {
    color: #c0453a;
    font-size: 20px;
    margin-right: 8px;
  }
  
  .error-title {
    font-weight: 600;
    color: #c0453a;
  }
}

.error-message {
  padding: 12px;
  background: #f5f3ec;
  border-radius: 6px;
  color: #6e6b62;
  font-size: 13px;
}

.validating {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 150px;
  
  p {
    margin-top: 12px;
    color: #6e6b62;
  }
}

.warning-list {
  padding: 8px 12px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 6px;
  margin-bottom: 12px;

  .warning-item {
    color: #ad6800;
    font-size: 12px;
    line-height: 20px;
  }
}

.tools-list {
  .tool-items {
    max-height: 400px;
    overflow-y: auto;
  }
  
  .tool-item {
    padding: 10px 12px;
    background: #f5f3ec;
    border-radius: 6px;
    margin-bottom: 6px;
    
    &:last-child {
      margin-bottom: 0;
    }
    
    .tool-header {
      display: flex;
      align-items: center;
      margin-bottom: 2px;
      
      .tool-icon {
        color: $primary-color;
        margin-right: 6px;
        font-size: 12px;
      }
      
      .tool-name {
        font-weight: 500;
        color: #141413;
        font-size: 13px;
      }
    }
    
    .tool-desc {
      font-size: 12px;
      color: #6e6b62;
      margin: 0;
      padding-left: 20px;
    }
  }
}
</style>
