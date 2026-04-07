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
              :rows="3"
            />
          </a-form-item>
          <a-form-item name="config" label="MCP配置">
            <div class="config-hint">
              <span class="hint-text">支持 STDIO 和 SSE 两种模式</span>
            </div>
            <div class="code-editor">
              <div class="line-numbers" ref="lineNumbersRef">
                <div class="line-number" v-for="line in lineCount" :key="line">{{ line }}</div>
              </div>
              <textarea
                :value="formData.config"
                @input="handleTextareaInput"
                @scroll="syncScroll"
                ref="textareaRef"
                class="code-textarea"
                placeholder='{
  "mcpServers": {
    "server-name": {
      "command": "npx",
      "args": ["-y", "mcp-server-example"]
    }
  }
}'
              ></textarea>
            </div>
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
import { ref, reactive, computed, watch } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { ToolOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { validateMcpConfig } from '@/api/mcpTool'
import type { McpTool, McpToolAddRequest, McpValidateResult } from '@/types'

const props = defineProps<{
  visible: boolean
  editingMcp?: McpTool | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'submit', data: McpToolAddRequest): void
}>()

const currentStep = ref(0)
const formRef = ref<FormInstance | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const lineNumbersRef = ref<HTMLElement | null>(null)
const validating = ref(false)
const submitting = ref(false)
const validateResult = ref<McpValidateResult | null>(null)

const formData = reactive({
  name: '',
  description: '',
  config: ''
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入工具名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }],
  config: [{ required: true, message: '请输入MCP配置', trigger: 'blur' }]
}

const lineCount = computed(() => {
  const lines = formData.config.split('\n').length
  return Math.max(lines, 10)
})

function handleTextareaInput(event: Event): void {
  const target = event.target as HTMLTextAreaElement
  formData.config = target.value
}

function syncScroll(): void {
  if (textareaRef.value && lineNumbersRef.value) {
    lineNumbersRef.value.scrollTop = textareaRef.value.scrollTop
  }
}

function getErrorMessage(result: McpValidateResult): string {
  const errorType = result.errorType
  const errorMsg = result.errorMessage || '未知错误'
  
  switch (errorType) {
    case 'CONFIG_INVALID':
      return `配置无效: ${errorMsg}`
    case 'CONNECTION_FAILED':
      return `连接失败: ${errorMsg}`
    case 'AUTH_FAILED':
      return `认证失败: ${errorMsg}`
    case 'TIMEOUT':
      return `连接超时: ${errorMsg}`
    default:
      return errorMsg
  }
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
  emit('update:visible', false)
}

function resetForm(): void {
  currentStep.value = 0
  validateResult.value = null
  formRef.value?.resetFields()
  Object.assign(formData, {
    name: '',
    description: '',
    config: ''
  })
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

.config-hint {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  
  .hint-text {
    font-size: 12px;
    color: #999;
  }
}

.code-editor {
  display: flex;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  overflow: hidden;
  background: #1e1e1e;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  min-height: 180px;
  max-height: 180px;

  .line-numbers {
    background: #252526;
    color: #858585;
    padding: 8px 0;
    text-align: right;
    user-select: none;
    min-width: 40px;
    overflow-y: hidden;
    border-right: 1px solid #3c3c3c;
    flex-shrink: 0;

    .line-number {
      padding: 0 12px;
      line-height: 22px;
      font-size: 13px;
    }
  }

  .code-textarea {
    flex: 1;
    background: #1e1e1e;
    color: #d4d4d4;
    border: none;
    outline: none;
    resize: none;
    padding: 8px 12px;
    font-family: inherit;
    font-size: 13px;
    line-height: 22px;
    min-height: 164px;
    max-height: 164px;

    &::placeholder {
      color: #6a6a6a;
    }

    &:focus {
      box-shadow: none;
    }
  }
}

.step-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
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
    color: #666;
    font-size: 13px;
  }
}

.server-info {
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;
  
  .info-label {
    color: #999;
  }
  
  .info-value {
    color: #333;
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
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 8px;
  margin-bottom: 12px;
  
  .error-icon {
    color: #ff4d4f;
    font-size: 20px;
    margin-right: 8px;
  }
  
  .error-title {
    font-weight: 600;
    color: #ff4d4f;
  }
}

.error-message {
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  color: #666;
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
    color: #666;
  }
}

.tools-list {
  .tool-items {
    max-height: 400px;
    overflow-y: auto;
  }
  
  .tool-item {
    padding: 10px 12px;
    background: #fafafa;
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
        color: #333;
        font-size: 13px;
      }
    }
    
    .tool-desc {
      font-size: 12px;
      color: #666;
      margin: 0;
      padding-left: 20px;
    }
  }
}
</style>
