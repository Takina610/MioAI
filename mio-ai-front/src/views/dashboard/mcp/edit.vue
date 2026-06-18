<template>
  <div class="mcp-detail">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="back-btn" @click="goBack">
            <h2>MCP管理</h2>
          </div>
          <h2> / {{ mcpDetail?.name || 'MCP详情' }}</h2>
        </div>
        <div class="header-right" v-if="!loading && !error">
          <a-button type="primary" @click="handleSave" :loading="saving" :disabled="!canSave">
            保存更改
          </a-button>
        </div>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content">
      <template v-if="loading">
        <div class="loading-container">
          <a-spin size="large" />
        </div>
      </template>

      <template v-else>
        <div class="detail-content">
          <a-row :gutter="24">
            <a-col :span="14">
              <a-card title="基本信息" class="info-card">
                <a-form layout="vertical">
                  <a-form-item label="工具名称">
                    <a-input v-model:value="formData.name" @change="handleFormChange" />
                  </a-form-item>
                  <a-form-item label="描述">
                    <a-textarea v-model:value="formData.description" :rows="3" @change="handleFormChange" />
                  </a-form-item>
                  <a-form-item label="可见性">
                    <a-radio-group v-model:value="formData.isPublic" @change="handleFormChange">
                      <a-radio :value="0">私有</a-radio>
                      <a-radio :value="1">公开</a-radio>
                    </a-radio-group>
                  </a-form-item>
                  <a-form-item label="MCP配置">
                    <div class="config-header">
                      <span v-if="configChanged" class="config-changed-hint">
                        <ExclamationCircleOutlined /> 配置已修改，需要重新校验
                      </span>
                    </div>
                    <div class="code-editor">
                      <div class="line-numbers" ref="lineNumbersRef">
                        <div class="line-number" v-for="line in lineCount" :key="line">{{ line }}</div>
                      </div>
                      <textarea
                        :value="formData.config"
                        @input="handleConfigInput"
                        @scroll="syncScroll"
                        ref="textareaRef"
                        class="code-textarea"
                      ></textarea>
                    </div>
                    <div class="config-actions">
                      <a-button 
                        type="link" 
                        class="validate-btn"
                        @click="handleValidate"
                        :loading="validating"
                        :disabled="!formData.config"
                      >
                        <SyncOutlined /> {{ validating ? '校验中...' : '校验配置' }}
                      </a-button>
                      <a-button 
                        type="link" 
                        class="format-btn"
                        @click="formatConfig"
                        :disabled="!formData.config"
                      >
                        <FormatPainterOutlined /> 格式化
                      </a-button>
                    </div>
                  </a-form-item>
                </a-form>
              </a-card>
            </a-col>

            <a-col :span="10">
              <a-card title="工具列表" class="tools-card">
                <template #extra>
                  <a-tag :color="mcpDetail?.status === 1 ? 'green' : 'default'">
                    {{ mcpDetail?.status === 1 ? '启用' : '禁用' }}
                  </a-tag>
                </template>
                
                <template v-if="toolInfos.length > 0">
                  <div class="tool-list">
                    <div class="tool-item" v-for="tool in toolInfos" :key="tool.name">
                      <div class="tool-header">
                        <ToolOutlined class="tool-icon" />
                        <span class="tool-name">{{ tool.name }}</span>
                      </div>
                      <p class="tool-desc">{{ tool.description || '暂无描述' }}</p>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <a-empty description="暂无工具信息，请校验配置" />
                </template>
              </a-card>

              <a-card title="元信息" class="meta-card">
                <div class="meta-item">
                  <span class="meta-label">创建时间</span>
                  <span class="meta-value">{{ formatTime(mcpDetail?.createTime) }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">更新时间</span>
                  <span class="meta-value">{{ formatTime(mcpDetail?.updateTime) }}</span>
                </div>
              </a-card>
            </a-col>
          </a-row>
        </div>
      </template>
    </div>

    <a-modal
      v-model:open="validateModalVisible"
      title="校验结果"
      :footer="null"
      width="500px"
    >
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
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ToolOutlined, SyncOutlined, ExclamationCircleOutlined, FormatPainterOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { getMcpToolById, updateMcpTool, validateMcpConfig } from '@/api/mcpTool'
import { useUserStore } from '@/store/user'
import type { McpTool, McpToolInfo, McpValidateResult } from '@/types'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(true)
const saving = ref(false)
const validating = ref(false)
const validateModalVisible = ref(false)
const configChanged = ref(false)
const formChanged = ref(false)
const originalConfig = ref('')

const error = ref<string | null>(null)
const mcpDetail = ref<McpTool | null>(null)
const validateResult = ref<McpValidateResult | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const lineNumbersRef = ref<HTMLElement | null>(null)

const formData = reactive({
  name: '',
  description: '',
  config: '',
  isPublic: 0
})


const lineCount = computed(() => {
  const lines = formData.config.split('\n').length
  return Math.max(lines, 9)
})

const toolInfos = computed<McpToolInfo[]>(() => {
  if (!mcpDetail.value?.toolInfo) return []
  try {
    return JSON.parse(mcpDetail.value.toolInfo)
  } catch {
    return []
  }
})

const canSave = computed(() => {
  if (!formChanged.value) return false
  if (configChanged.value && !validateResult.value?.success) return false
  return true
})

async function fetchMcpDetail(): Promise<void> {
  const id = route.params.id as string
  if (!id) {
    router.push('/404')
    return
  }

  try {
    const data = await getMcpToolById(Number(id))

    if (!data) {
      router.push('/404')
      return
    }

    if (!userStore.isLoggedIn || userStore.userInfo?.id !== data.userId) {
      router.push('/403')
      return
    }
    
    mcpDetail.value = data
    
    let formattedConfig = data.config || ''
    try {
      const parsed = JSON.parse(formattedConfig)
      formattedConfig = JSON.stringify(parsed, null, 2)
    } catch {
    }
    
    Object.assign(formData, {
      name: data.name,
      description: data.description || '',
      config: formattedConfig,
      isPublic: data.isPublic || 0
    })
    originalConfig.value = formattedConfig
  } catch (e: any) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function handleValidate(): Promise<void> {
  validating.value = true
  try {
    const result = await validateMcpConfig({ config: formData.config })
    validateResult.value = result
    validateModalVisible.value = true
  } catch (e) {
    console.error(e)
    message.error('校验失败，请重试')
  } finally {
    validating.value = false
  }
}

async function handleSave(): Promise<void> {
  if (!mcpDetail.value) return
  
  if (configChanged.value && !validateResult.value?.success) {
    message.warning('配置已修改，请先校验配置')
    return
  }
  
  saving.value = true
  try {
    await updateMcpTool({
      id: mcpDetail.value.id,
      name: formData.name,
      description: formData.description,
      config: formData.config,
      toolInfo: mcpDetail.value.toolInfo,
      isPublic: formData.isPublic
    })
    message.success('保存成功')
    formChanged.value = false
    originalConfig.value = formData.config
    await fetchMcpDetail()
  } catch (e) {
    console.error(e)
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

function syncScroll(): void {
  if (textareaRef.value && lineNumbersRef.value) {
    lineNumbersRef.value.scrollTop = textareaRef.value.scrollTop
  }
}

function handleFormChange(): void {
  formChanged.value = true
}

function handleConfigInput(event: Event): void {
  const target = event.target as HTMLTextAreaElement
  formData.config = target.value
  if (formData.config !== originalConfig.value) {
    configChanged.value = true
    validateResult.value = null
  } else {
    configChanged.value = false
  }
  formChanged.value = true
}

function formatConfig(): void {
  try {
    const parsed = JSON.parse(formData.config)
    formData.config = JSON.stringify(parsed, null, 2)
    formChanged.value = true
    if (formData.config !== originalConfig.value) {
      configChanged.value = true
      validateResult.value = null
    }
  } catch {
    message.error('JSON格式无效，无法格式化')
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

function goBack(): void {
  router.push('/dashboard/mcp')
}

onMounted(() => {
  fetchMcpDetail()
})
</script>

<style lang="scss" scoped>
.mcp-detail {
  height: 100%;
  
  .page-header {
    .header-content {
      padding: 16px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        display: flex;
        align-items: center;
        
        .back-btn {
          &:hover {
            cursor: pointer;
          }
          h2 {
            color: #5f6368;
            font-weight: 400;
            margin-right: 8px;
          }
        }
        
        h2 {
          font-size: 24px;
          font-weight: 600;
          color: #202124;
        }
      }

      .header-right {
        :deep(.ant-btn-primary) {
          background: $primary-color;
          border-color: $primary-color;

          &:hover {
            background: darken($primary-color, 10%);
            border-color: darken($primary-color, 10%);
          }
          
          &:disabled {
            background: #d9d9d9;
            border-color: #d9d9d9;
          }
        }
      }
    }

    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .page-content {
    max-height: 765px;
    overflow-y: auto;
    padding: 24px;
    -ms-overflow-style: none; /* IE 和旧版 Edge 隐藏滚动条 */
    scrollbar-width: none; /* Firefox 隐藏滚动条 */
  }
  
  .loading-container {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 400px;
  }
  
  .detail-content {
    .info-card,
    .tools-card,
    .meta-card {
      margin-bottom: 16px;
      
      :deep(.ant-card-head-title) {
        font-weight: 600;
      }
      
      :deep(.ant-card-body) {
        max-height: 650px;
        overflow-y: auto;
      }
    }
  }
  
  .config-header {
    margin-bottom: 8px;
    min-height: 22px;
    
    .config-changed-hint {
      color: #faad14;
      font-size: 12px;
      
      .anticon {
        margin-right: 4px;
      }
    }
  }
  
  .code-editor {
    display: flex;
    border: 1px solid #d9d9d9;
    border-radius: 6px;
    overflow: hidden;
    background: #1e1e1e;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    // min-height: 160px;
    // max-height: 200px;

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
      // min-height: 144px;
      // max-height: 144px;

      &::placeholder {
        color: #6a6a6a;
      }

      &:focus {
        box-shadow: none;
      }
    }
  }
  
  .config-actions {
    display: flex;
    gap: 16px;
    margin-top: 8px;
  }
  
  .validate-btn,
  .format-btn {
    padding: 0;
  }
  
  .tool-list {
    max-height: 280px;
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
  
  .meta-card {
    .meta-item {
      display: flex;
      justify-content: space-between;
      padding: 8px 0;
      border-bottom: 1px solid #f0f0f0;
      
      &:last-child {
        border-bottom: none;
      }
      
      .meta-label {
        color: #666;
      }
      
      .meta-value {
        color: #333;
      }
    }
  }
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
