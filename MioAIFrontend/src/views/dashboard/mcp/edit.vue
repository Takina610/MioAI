<template>
  <div class="dash-page">
    <PageHeader
      back-label="MCP管理"
      back-to="/dashboard/mcp"
      :title="mcpDetail?.name || 'MCP详情'"
    >
      <template #actions>
        <a-button
          v-if="!loading && !error"
          type="primary"
          :loading="saving"
          :disabled="!canSave"
          @click="handleSave"
        >
          保存更改
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content">
      <div v-if="loading" class="loading-container">
        <a-spin size="large" />
      </div>

      <template v-else>
        <a-row :gutter="24">
          <a-col :span="14">
            <a-card title="基本信息" class="info-card">
              <a-form layout="vertical">
                <a-form-item label="工具名称">
                  <a-input v-model:value="formData.name" @change="formChanged = true" />
                </a-form-item>
                <a-form-item label="描述">
                  <a-textarea v-model:value="formData.description" :rows="3" @change="formChanged = true" />
                </a-form-item>
                <a-form-item label="可见性">
                  <a-radio-group v-model:value="formData.isPublic" @change="formChanged = true">
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
                  <McpCodeEditor v-model="formData.config" :min-lines="9" @update:model-value="onConfigInput" />
                  <div class="config-actions">
                    <a-button
                      type="link"
                      class="validate-btn"
                      :loading="validating"
                      :disabled="!formData.config"
                      @click="handleValidate"
                    >
                      <SyncOutlined /> {{ validating ? '校验中...' : '校验配置' }}
                    </a-button>
                    <a-button
                      type="link"
                      class="format-btn"
                      :disabled="!formData.config"
                      @click="formatConfig"
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

              <McpToolList :tools="toolInfos" empty-text="暂无工具信息，请校验配置" />
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
      </template>
    </div>

    <McpValidateModal v-model:open="validateModalVisible" :result="validateResult" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { SyncOutlined, ExclamationCircleOutlined, FormatPainterOutlined } from '@ant-design/icons-vue'
import { getMcpToolById, updateMcpTool, validateMcpConfig } from '@/api/mcpTool'
import { useUserStore } from '@/store/user'
import { parseMcpTools } from '@/utils/mcpTool'
import type { McpTool, McpValidateResult } from '@/types'
import dayjs from 'dayjs'
import PageHeader from '../components/PageHeader.vue'
import McpCodeEditor from './components/McpCodeEditor.vue'
import McpToolList from './components/McpToolList.vue'
import McpValidateModal from './components/McpValidateModal.vue'

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

const formData = reactive({
  name: '',
  description: '',
  config: '',
  isPublic: 0
})

const toolInfos = computed(() => parseMcpTools(mcpDetail.value?.toolInfo))

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
      formattedConfig = JSON.stringify(JSON.parse(formattedConfig), null, 2)
    } catch {
      // 配置非合法 JSON 时原样展示
    }

    Object.assign(formData, {
      name: data.name,
      description: data.description || '',
      config: formattedConfig,
      isPublic: data.isPublic || 0
    })
    originalConfig.value = formattedConfig
  } catch (e) {
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

function onConfigInput(value: string): void {
  configChanged.value = value !== originalConfig.value
  if (configChanged.value) {
    validateResult.value = null
  }
  formChanged.value = true
}

function formatConfig(): void {
  try {
    formData.config = JSON.stringify(JSON.parse(formData.config), null, 2)
    formChanged.value = true
    onConfigInput(formData.config)
  } catch {
    message.error('JSON格式无效，无法格式化')
  }
}

onMounted(() => {
  fetchMcpDetail()
})
</script>

<style lang="scss" scoped>
.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

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

.config-actions {
  display: flex;
  gap: 16px;
  margin-top: 8px;
}

.validate-btn,
.format-btn {
  padding: 0;
}

.meta-card {
  .meta-item {
    display: flex;
    justify-content: space-between;
    padding: 8px 0;
    border-bottom: 1px solid #ece9de;

    &:last-child {
      border-bottom: none;
    }

    .meta-label {
      color: #6e6b62;
    }

    .meta-value {
      color: #141413;
    }
  }
}
</style>
