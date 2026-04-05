<template>
  <a-modal
    :open="visible"
    :width="720"
    :footer="null"
    :closable="false"
    :mask-closable="false"
    :destroyOnClose="true"
    class="kb-create-modal"
  >
    <div class="modal-header">
      <div class="header-left">
        <h3>创建知识库</h3>
      </div>
      <a-button type="text" class="close-btn" @click="handleCancel">
        <CloseOutlined />
      </a-button>
    </div>

    <a-steps :current="currentStep" class="create-steps" size="small">
      <a-step title="基本信息" />
      <a-step title="上传文件" />
      <a-step title="向量化处理" />
    </a-steps>

    <div class="step-content">
      <!-- Step 1: 基本信息 -->
      <div v-show="currentStep === 0" class="step-form">
        <a-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          layout="vertical"
        >
          <a-form-item name="name" label="知识库名称">
            <a-input
              v-model:value="formData.name"
              placeholder="请输入知识库名称"
              size="large"
              :maxlength="50"
              show-count
            />
          </a-form-item>

          <a-form-item name="description" label="知识库描述">
            <div class="desc-wrapper">
              <a-textarea
                v-model:value="formData.description"
                placeholder="请输入知识库描述，描述该知识库的用途和适用场景"
                :rows="4"
                :maxlength="500"
                show-count
              />
              <a-tooltip placement="bottomLeft" :overlay-inner-style="{ width: '500px', maxHeight: '450px', background: '#fff', color: '#333' }" :arrow="false" >
                <template #title>
                  <div>
                    <p><strong>知识库描述</strong></p>
                    <p><strong>【标题】</strong>专业猫科健康医疗知识库</p>
                    <p><strong>【描述】</strong>这是一个专业猫科健康医疗知识库。当用户咨询猫、猫咪或具体猫品种（英短、美短、布偶、暹罗等）的健康问题时使用。内容涵盖：猫科常见疾病（猫瘟、猫传腹、猫白血病、猫杯状病毒等传染病，猫癣、耳螨等皮肤病，泌尿系统疾病、口炎、肾衰竭、心肌病等）、品种特异性疾病（折耳猫骨骼病、波斯猫多囊肾等）、症状诊断（呕吐、尿闭、呼吸困难、流口水等）、治疗方案、用药指导（特别注意猫的代谢特殊性和禁用药物）、疫苗驱虫、营养饮食、日常护理、应激管理、急救知识（中毒、尿闭等）。查询触发：用户提及猫科并咨询疾病、症状、治疗、用药、护理、饮食等健康相关问题。</p>
                  </div>
                </template>
                <div class="example-btn">
                  <QuestionCircleOutlined />
                  <span class="example-text">示例</span>
                </div>
              </a-tooltip>
            </div>
          </a-form-item>
        </a-form>

        <div class="step-actions">
          <a-button @click="handleCancel">取消</a-button>
          <a-button type="primary" @click="handleNextStep" :loading="creatingKb">
            下一步
          </a-button>
        </div>
      </div>

      <!-- Step 2: 上传文件 -->
      <div v-show="currentStep === 1" class="step-upload">
        <div class="upload-area" @drop.prevent="handleDrop" @dragover.prevent>
          <a-upload-dragger
            :file-list="fileList"
            :before-upload="beforeUpload"
            :custom-request="customUpload"
            :multiple="true"
            accept=".pdf,.doc,.docx,.md,.txt,.ppt,.pptx"
            @remove="handleRemove"
          >
            <p class="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p class="ant-upload-hint">
              支持 PDF、DOC、DOCX、MD、TXT、PPT、PPTX 格式，单个文件不超过 50MB
            </p>
          </a-upload-dragger>
        </div>

        <div v-if="uploadedFiles.length > 0" class="uploaded-list">
          <h4>已上传文件 ({{ uploadedFiles.length }})</h4>
          <div class="file-items">
            <div v-for="file in uploadedFiles" :key="file.docId" class="file-item">
              <FileTextOutlined class="file-icon" />
              <div class="file-info">
                <span class="file-name">{{ file.fileName }}</span>
                <span class="file-size">{{ formatFileSize(file.fileSize) }}</span>
              </div>
              <CheckCircleFilled v-if="file.status === 'success'" class="status-success" />
              <CloseCircleFilled v-else-if="file.status === 'error'" class="status-error" />
              <LoadingOutlined v-else class="status-loading" />
            </div>
          </div>
        </div>

        <div class="step-actions">
          <a-button @click="prevStep">上一步</a-button>
          <a-button
            type="primary"
            @click="handleUploadAndNext"
            :loading="uploading"
            :disabled="uploadedFiles.length === 0"
          >
            开始向量化
          </a-button>
        </div>
      </div>

      <!-- Step 3: 向量化处理 -->
      <div v-show="currentStep === 2" class="step-vectorize">
        <div class="vectorize-status">
          <div class="status-icon" :class="{ 'is-processing': vectorizing, 'is-complete': vectorizeComplete, 'is-error': vectorizeError }">
            <LoadingOutlined v-if="vectorizing" spin />
                <CheckCircleFilled v-else-if="vectorizeComplete" />
                <CloseCircleFilled v-else-if="vectorizeError" />
          </div>

          <h3 class="status-title">{{ vectorizeStatusTitle }}</h3>
          <p class="status-message">{{ vectorizeStatusMessage }}</p>

          <a-progress
            v-if="vectorizing || vectorizeComplete"
            :percent="vectorizeProgress"
            :stroke-color="vectorizeError ? '#ff4d4f' : undefined"
            :status="vectorizeError ? 'exception' : (vectorizeComplete ? 'success' : 'active')"
          />

          <div v-if="currentFileName" class="current-file">
            <FileTextOutlined />
            <span>{{ currentFileName }}</span>
          </div>

          <div v-if="vectorizeComplete && !vectorizeError" class="result-summary">
            <a-descriptions :column="1" size="small" bordered>
              <a-descriptions-item label="总文件数">{{ vectorizeResult.totalFiles }}</a-descriptions-item>
              <a-descriptions-item label="成功处理">{{ vectorizeResult.completedFiles }}</a-descriptions-item>
            </a-descriptions>
          </div>

          <div v-if="vectorizeError" class="error-detail">
            <a-alert :message="errorMsg" type="error" show-icon />
          </div>
        </div>

        <div class="step-actions">
          <a-button v-if="!vectorizing" @click="handleCancelCreation">取消</a-button>
          <a-button v-if="vectorizeComplete && !vectorizeError" type="primary" @click="handleFinish">
            完成
          </a-button>
          <a-button v-if="vectorizeError" type="primary" @click="retryVectorize">
            重试
          </a-button>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  CloseOutlined,
  InboxOutlined,
  FileTextOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  QuestionCircleOutlined,
  LoadingOutlined
} from '@ant-design/icons-vue'
import { addKnowledgeBase, uploadKnowledgeFiles, cancelKnowledgeCreation, type UploadResult } from '@/api/knowledgeBase'

interface Props {
  visible: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance | null>(null)
const currentStep = ref(0)
const creatingKb = ref(false)
const uploading = ref(false)
const vectorizing = ref(false)
const vectorizeComplete = ref(false)
const vectorizeError = ref(false)
const kbId = ref<number | null>(null)

const formData = reactive({
  name: '',
  description: ''
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入知识库描述', trigger: 'blur' }]
}

const fileList = ref<any[]>([])
const uploadedFiles = ref<UploadResult[]>([])

const vectorizeProgress = ref(0)
const vectorizeStatusTitle = ref('准备向量化')
const vectorizeStatusMessage = ref('请稍候...')
const currentFileName = ref('')
const errorMsg = ref('')

const vectorizeResult = reactive({
  totalFiles: 0,
  completedFiles: 0
})

let eventSource: EventSource | null = null

function handleCancel(): void {
  if (kbId.value) {
    cancelKnowledgeCreation(kbId.value).catch(() => {})
  }
  resetState()
  emit('update:visible', false)
}

function resetState(): void {
  currentStep.value = 0
  creatingKb.value = false
  uploading.value = false
  vectorizing.value = false
  vectorizeComplete.value = false
  vectorizeError.value = false
  kbId.value = null
  fileList.value = []
  uploadedFiles.value = []
  vectorizeProgress.value = 0
  vectorizeStatusTitle.value = '准备向量化'
  vectorizeStatusMessage.value = '请稍候...'
  currentFileName.value = ''
  errorMsg.value = ''
  vectorizeResult.totalFiles = 0
  vectorizeResult.completedFiles = 0
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  formData.name = ''
  formData.description = ''
}

async function handleNextStep(): Promise<void> {
  try {
    await formRef.value?.validate()
    creatingKb.value = true
    const id = await addKnowledgeBase({ name: formData.name, description: formData.description })
    kbId.value = id
    currentStep.value = 1
  } catch (e) {
    console.error(e)
  } finally {
    creatingKb.value = false
  }
}

function prevStep(): void {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

function beforeUpload(file: any): boolean {
  const allowedTypes = ['pdf', 'doc', 'docx', 'md', 'txt', 'ppt', 'pptx']
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!allowedTypes.includes(extension || '')) {
    message.error(`不支持的文件格式: ${extension}`)
    return false
  }
  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    message.error('文件大小不能超过 50MB')
    return false
  }
  return true
}

function customUpload(options: any): void {
  const { file, onSuccess, onError } = options
  setTimeout(() => {
    onSuccess({ url: 'temp' }, file)
  }, 100)
}

function handleRemove(file: any): void {
  const index = fileList.value.findIndex(f => f.uid === file.uid)
  if (index > -1) {
    fileList.value.splice(index, 1)
  }
}

function handleDrop(e: DragEvent): void {
  // Handled by upload-dragger
}

async function handleUploadAndNext(): Promise<void> {
  if (!kbId.value || uploadedFiles.value.length === 0) return

  uploading.value = true
  try {
    const filesToUpload: File[] = []
    fileList.value.forEach((f: any) => {
      if (f.originFileObj) {
        filesToUpload.push(f.originFileObj)
      }
    })

    const results = await uploadKnowledgeFiles(kbId.value, filesToUpload)
    uploadedFiles.value = results

    const hasError = results.some(r => r.status === 'error')
    if (hasError) {
      message.warning('部分文件上传失败，请查看详情')
    }

    currentStep.value = 2
    startVectorization()
  } catch (e) {
    console.error(e)
    message.error('上传失败')
  } finally {
    uploading.value = false
  }
}

function startVectorization(): void {
  if (!kbId.value) return

  vectorizing.value = true
  vectorizeComplete.value = false
  vectorizeError.value = false
  errorMsg.value = ''

  const token = localStorage.getItem('token') || ''
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  const url = `${baseUrl}/knowledge-bases/create/vectorize/${kbId.value}`

  eventSource = new EventSource(url)

  eventSource.onmessage = (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data)
      handleVectorizeEvent(data)
    } catch {
      console.error('解析向量数据失败:', event.data)
    }
  }

  eventSource.onerror = () => {
    vectorizing.value = false
    vectorizeError.value = true
    errorMsg.value = '连接中断，请重试'
    eventSource?.close()
    eventSource = null
  }
}

function handleVectorizeEvent(data: any): void {
  const type = data.type
  const eventData = data.data

  switch (type) {
    case 'progress':
      if (typeof eventData === 'object' && eventData !== null) {
        const total = eventData.total || 1
        const current = eventData.current || 0
        vectorizeProgress.value = Math.round((current / total) * 100)

        if (eventData.message) {
          vectorizeStatusMessage.value = eventData.message
        }

        if (eventData.fileName) {
          currentFileName.value = eventData.fileName
        }

        if (current > 0 && total > 0) {
          vectorizeStatusTitle.value = `正在处理 (${current}/${total})`
        }
      }
      break

    case 'error':
      vectorizing.value = false
      vectorizeError.value = true
      if (typeof eventData === 'object' && eventData !== null) {
        errorMsg.value = eventData.message || '处理失败'
      } else {
        errorMsg.value = String(eventData)
      }
      break

    case 'done':
      vectorizing.value = false
      vectorizeComplete.value = true
      if (typeof eventData === 'object' && eventData !== null) {
        vectorizeResult.totalFiles = eventData.totalFiles || 0
        vectorizeResult.completedFiles = eventData.completedFiles || 0
        vectorizeStatusMessage.value = eventData.message || '向量化完成'
      }
      vectorizeStatusTitle.value = '向量化完成'
      vectorizeProgress.value = 100
      break
  }
}

function retryVectorize(): void {
  vectorizeError.value = false
  errorMsg.value = ''
  startVectorization()
}

function handleFinish(): void {
  emit('update:visible', false)
  emit('success')
  resetState()
}

async function handleCancelCreation(): Promise<void> {
  if (kbId.value) {
    await cancelKnowledgeCreation(kbId.value)
  }
  handleCancel()
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

watch(() => props.visible, (newVal) => {
  if (!newVal) {
    resetState()
  }
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})
</script>

<style lang="scss" scoped>
.kb-create-modal {
  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;

    h3 {
      font-size: 18px;
      font-weight: 600;
      color: $text-dark;
      margin: 0;
    }

    .close-btn {
      font-size: 16px;
      color: #999;

      &:hover {
        color: $text-dark;
      }
    }
  }

  .create-steps {
    margin-bottom: 32px;
  }

  .step-content {
    min-height: 320px;
  }

  .step-form {
    .desc-wrapper {
      position: relative;

      .example-btn {
        position: absolute;
        top: -28px;
        right: 0;
        color: $primary-color;
        cursor: pointer;
        font-size: 13px;

        &:hover {
          color: darken($primary-color, 10%);
        }

        .example-text {
          margin-left: 4px;
        }
      }
    }
  }

  .step-upload {
    .upload-area {
      margin-bottom: 20px;
    }

    .uploaded-list {
      h4 {
        font-size: 14px;
        font-weight: 500;
        color: $text-dark;
        margin-bottom: 12px;
      }

      .file-items {
        max-height: 180px;
        overflow-y: auto;
        border: 1px solid #f0f0f0;
        border-radius: 8px;
        padding: 8px;
      }

      .file-item {
        display: flex;
        align-items: center;
        padding: 10px 12px;
        border-radius: 6px;
        background: #fafafa;
        margin-bottom: 8px;

        &:last-child {
          margin-bottom: 0;
        }

        .file-icon {
          font-size: 18px;
          color: $primary-color;
          margin-right: 12px;
        }

        .file-info {
          flex: 1;
          min-width: 0;

          .file-name {
            display: block;
            font-size: 13px;
            color: $text-dark;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .file-size {
            font-size: 12px;
            color: #999;
          }
        }

        .status-success {
          color: #52c41a;
          font-size: 16px;
        }

        .status-error {
          color: #ff4d4f;
          font-size: 16px;
        }

        .status-loading {
          color: $primary-color;
          font-size: 16px;
        }
      }
    }
  }

  .step-vectorize {
    .vectorize-status {
      text-align: center;
      padding: 40px 20px;

      .status-icon {
        width: 64px;
        height: 64px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        margin: 0 auto 24px;
        font-size: 28px;
        background: #f5f5f5;
        color: #999;

        &.is-processing {
          background: rgba($primary-color, 0.1);
          color: $primary-color;
        }

        &.is-complete {
          background: rgba(#52c41a, 0.1);
          color: #52c41a;
        }

        &.is-error {
          background: rgba(#ff4d4f, 0.1);
          color: #ff4d4f;
        }
      }

      .status-title {
        font-size: 18px;
        font-weight: 600;
        color: $text-dark;
        margin-bottom: 8px;
      }

      .status-message {
        font-size: 14px;
        color: #666;
        margin-bottom: 24px;
      }

      .current-file {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 8px 16px;
        background: #f5f5f5;
        border-radius: 20px;
        font-size: 13px;
        color: #666;
        margin-bottom: 24px;

        .anticon {
          color: $primary-color;
        }
      }

      .result-summary {
        max-width: 300px;
        margin: 24px auto 0;
      }

      .error-detail {
        max-width: 400px;
        margin: 24px auto 0;
      }
    }
  }

  .step-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 32px;
    padding-top: 20px;
    border-top: 1px solid #f0f0f0;

    :deep(.ant-btn-primary) {
      background: $primary-color;
      border-color: $primary-color;

      &:hover {
        background: darken($primary-color, 10%);
        border-color: darken($primary-color, 10%);
      }
    }
  }
}
</style>
