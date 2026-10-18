<template>
  <a-modal
    :open="visible"
    :width="720"
    :footer="null"
    :closable="false"
    :mask-closable="false"
    :keyboard="false"
    :destroyOnClose="true"
    class="kb-create-modal"
  >
    <div class="modal-header">
      <div class="header-left">
        <h3>{{ modalTitle }}</h3>
      </div>
      <!-- 向量化进行中禁止关闭，防止误触中断/取消流程 -->
      <a-button v-if="!vectorizing" type="text" class="close-btn" @click="handleCancel">
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
              <a-tooltip placement="bottomLeft" :overlay-inner-style="{ width: '500px', maxHeight: '450px', background: '#fff', color: '#141413' }" :arrow="false" >
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

          <a-form-item label="分块设置">
            <div class="chunk-config">
              <div class="preset-row">
                <button
                  v-for="preset in chunkPresets"
                  :key="preset.key"
                  type="button"
                  class="preset-chip"
                  :class="{ active: chunkPreset === preset.key }"
                  @click="applyChunkPreset(preset.key)"
                >
                  <span class="preset-name">{{ preset.name }}</span>
                  <span class="preset-hint">{{ preset.hint }}</span>
                </button>
              </div>
              <div class="slider-row">
                <span class="slider-label">分块大小</span>
                <a-slider
                  v-model:value="formData.chunkSize"
                  class="slider-track"
                  :min="200"
                  :max="2048"
                  :step="50"
                  :tooltip-open="false"
                />
                <a-input-number
                  v-model:value="formData.chunkSize"
                  class="slider-number"
                  :min="200"
                  :max="2048"
                  :step="50"
                  size="small"
                />
              </div>
              <div class="slider-row">
                <span class="slider-label">分块重叠</span>
                <a-slider
                  v-model:value="formData.chunkOverlap"
                  class="slider-track"
                  :min="0"
                  :max="maxChunkOverlap"
                  :step="10"
                  :tooltip-open="false"
                />
                <a-input-number
                  v-model:value="formData.chunkOverlap"
                  class="slider-number"
                  :min="0"
                  :max="maxChunkOverlap"
                  :step="10"
                  size="small"
                />
              </div>
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
        <div class="upload-area" @dragover.prevent>
          <a-upload-dragger
            v-model:file-list="fileList"
            :before-upload="beforeUpload"
            :multiple="true"
            accept=".pdf,.doc,.docx,.md,.txt"
            :show-remove-button="!uploading"
            @remove="handleRemove"
          >
            <p class="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p class="ant-upload-hint">
              支持 PDF、DOC、DOCX、MD、TXT 格式，单个文件不超过 50MB
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
          <a-button @click="skipFirstStep ? handleCancel() : prevStep()">{{ skipFirstStep ? '取消' : '上一步' }}</a-button>
          <a-button
            type="primary"
            @click="handleUploadAndNext"
            :loading="uploading"
            :disabled="fileList.length === 0"
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
            :stroke-color="vectorizeError ? '#c0453a' : undefined"
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
          <!-- 向量化进行中不提供任何关闭入口；失败后关闭=保留知识库，可稍后重新向量化 -->
          <template v-if="!vectorizing">
            <a-button v-if="vectorizeError" @click="handleCancel">关闭</a-button>
            <a-button v-if="vectorizeComplete && !vectorizeError" type="primary" @click="handleFinish">
              完成
            </a-button>
            <a-button v-if="vectorizeError" type="primary" @click="retryVectorize">
              重试
            </a-button>
          </template>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onUnmounted, computed, nextTick } from 'vue'
import { message, Upload } from 'ant-design-vue'
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
import { addKnowledgeBase, updateKnowledgeBase, uploadKnowledgeFiles, cancelKnowledgeCreation, vectorizeKnowledgeFiles, type UploadResult } from '@/api/knowledgeBase'

interface Props {
  visible: boolean
  kbId?: number
  skipFirstStep?: boolean
  /** 打开后直接进入向量化步骤并连接进度（用于页面刷新后的任务恢复） */
  resumeVectorize?: boolean
  /** 连接向量化 SSE 时携带 force，重建场景用（需配合 resumeVectorize） */
  forceVectorize?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  kbId: undefined,
  skipFirstStep: false,
  resumeVectorize: false,
  forceVectorize: false
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()


const creatingKb = ref(false)
const uploading = ref(false)
const vectorizing = ref(false)
const vectorizeComplete = ref(false)
const vectorizeError = ref(false)
const reconnecting = ref(false)

const vectorizeProgress = ref(0)
const currentStep = ref(0)
const currentFileName = ref('')
const errorMsg = ref('')
const vectorizeStatusMessage = ref('请稍候...')
const vectorizeStatusTitle = ref('准备向量化')

const formRef = ref<FormInstance | null>(null)
const kbId = ref<number | null>(null)
const fileList = ref<any[]>([])
const uploadedFiles = ref<UploadResult[]>([])

const originalFormData = reactive({
  name: '',
  description: ''
})

const formData = reactive({
  name: '',
  description: '',
  chunkSize: 800,
  chunkOverlap: 100
})

type ChunkPresetKey = 'fine' | 'balanced' | 'coarse' | 'custom'

const chunkPresets: { key: Exclude<ChunkPresetKey, 'custom'>; name: string; hint: string; chunkSize: number; chunkOverlap: number }[] = [
  { key: 'fine', name: '精细', hint: '片段短，检索更准', chunkSize: 400, chunkOverlap: 80 },
  { key: 'balanced', name: '均衡', hint: '适合大多数文档', chunkSize: 800, chunkOverlap: 100 },
  { key: 'coarse', name: '粗粒度', hint: '上下文更完整', chunkSize: 1200, chunkOverlap: 150 }
]

const chunkPreset = ref<ChunkPresetKey>('balanced')

const maxChunkOverlap = computed(() => Math.min(500, Math.floor(formData.chunkSize / 2)))

function matchChunkPreset(size: number, overlap: number): ChunkPresetKey {
  const hit = chunkPresets.find(p => p.chunkSize === size && p.chunkOverlap === overlap)
  return hit ? hit.key : 'custom'
}

function applyChunkPreset(key: Exclude<ChunkPresetKey, 'custom'>): void {
  const preset = chunkPresets.find(p => p.key === key)
  if (!preset) return
  formData.chunkSize = preset.chunkSize
  formData.chunkOverlap = preset.chunkOverlap
  chunkPreset.value = key
}

watch(() => formData.chunkSize, (size) => {
  // 重叠不能超过块大小的一半（后端同样钳制），块变小时收敛重叠
  const cap = Math.min(500, Math.floor(size / 2))
  if (formData.chunkOverlap > cap) {
    formData.chunkOverlap = cap
  }
  chunkPreset.value = matchChunkPreset(size, formData.chunkOverlap)
})

watch(() => formData.chunkOverlap, (overlap) => {
  chunkPreset.value = matchChunkPreset(formData.chunkSize, overlap)
})

let eventSource: EventSource | null = null

const skipFirstStep = computed(() => props.skipFirstStep)

const modalTitle = computed(() => {
  if (!props.skipFirstStep) return '创建知识库'
  return props.resumeVectorize ? '向量化进度' : '上传文档'
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入知识库描述', trigger: 'blur' }]
}

const vectorizeResult = reactive({
  totalFiles: 0,
  completedFiles: 0
})

watch(() => props.visible, (newVal) => {
  if (!newVal) {
    resetState()
  } else if (props.skipFirstStep && props.kbId) {
    kbId.value = props.kbId
    if (props.resumeVectorize) {
      // 恢复在途向量化：直接进入第三步接管进度
      currentStep.value = 2
      nextTick(() => startVectorization())
    } else {
      currentStep.value = 1
    }
  }
})


function handleCancel(): void {
  // 关闭 SSE 连接
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  // 只有"新建流程的前两步"离开才会级联删除已创建的库与已上传文件；
  // 补传模式（skipFirstStep）永远不删库；第三步失败后关闭=保留知识库待重试
  if (kbId.value && !skipFirstStep.value && currentStep.value < 2) {
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
  reconnecting.value = false
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
  formData.chunkSize = 800
  formData.chunkOverlap = 100
  chunkPreset.value = 'balanced'
  originalFormData.name = ''
  originalFormData.description = ''
}

async function handleNextStep(): Promise<void> {
  try {
    await formRef.value?.validate()
    
    if (kbId.value) {
      const nameChanged = formData.name !== originalFormData.name
      const descChanged = formData.description !== originalFormData.description
      
      if (nameChanged || descChanged) {
        creatingKb.value = true
        await updateKnowledgeBase({
          id: kbId.value,
          name: formData.name,
          description: formData.description
        })
        originalFormData.name = formData.name
        originalFormData.description = formData.description
        creatingKb.value = false
      }
      currentStep.value = 1
      return
    }
    
    creatingKb.value = true
    const id = await addKnowledgeBase({
      name: formData.name,
      description: formData.description,
      chunkSize: formData.chunkSize,
      chunkOverlap: formData.chunkOverlap
    })
    kbId.value = id
    originalFormData.name = formData.name
    originalFormData.description = formData.description
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
  const allowedTypes = ['pdf', 'doc', 'docx', 'md', 'txt']
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!allowedTypes.includes(extension || '')) {
    message.error(`不支持的文件格式: ${extension}`)
    return Upload.LIST_IGNORE as any
  }
  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    message.error('文件大小不能超过 50MB')
    return Upload.LIST_IGNORE as any
  }
  return false
}

function handleRemove(file: any): void {
  const index = fileList.value.findIndex(f => f.uid === file.uid)
  if (index > -1) {
    fileList.value.splice(index, 1)
  }
}

async function handleUploadAndNext(): Promise<void> {
  if (!kbId.value || fileList.value.length === 0) return

  uploading.value = true
  try {
    const filesToUpload: File[] = []
    fileList.value.forEach((f: any) => {
      if (f.originFileObj) {
        filesToUpload.push(f.originFileObj)
      }
    })

    if (filesToUpload.length === 0) {
      message.warning('请选择要上传的文件')
      return
    }

    const results = await uploadKnowledgeFiles(kbId.value, filesToUpload)
    uploadedFiles.value = results

    const successCount = results.filter(r => r.status === 'success').length
    const errorCount = results.filter(r => r.status === 'error').length

    if (errorCount > 0 && successCount === 0) {
      message.error('所有文件上传失败')
      return
    }

    if (errorCount > 0) {
      message.warning(`成功上传 ${successCount} 个文件，${errorCount} 个文件失败`)
    } else {
      message.success(`成功上传 ${successCount} 个文件`)
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
  reconnecting.value = false
  errorMsg.value = ''
  vectorizeStatusTitle.value = '准备向量化'
  vectorizeStatusMessage.value = '向量化处理可能需要较长时间，请耐心等待...'

  const token = localStorage.getItem('token') || ''
  eventSource = vectorizeKnowledgeFiles(kbId.value, token, props.forceVectorize)

  eventSource.onmessage = (event: MessageEvent) => {
    reconnecting.value = false
    try {
      const data = JSON.parse(event.data)
      handleVectorizeEvent(data)
    } catch {
      console.error('解析向量数据失败:', event.data)
    }
  }

  eventSource.onerror = () => {
    // 收到过终态事件后连接被服务端关闭属正常收尾；否则是网络波动——
    // EventSource 会自动重连，后端任务继续在跑，重连后以观察者模式接管进度
    if (vectorizeComplete.value || vectorizeError.value) {
      eventSource?.close()
      eventSource = null
      return
    }
    reconnecting.value = true
    vectorizeStatusTitle.value = '连接中断'
    vectorizeStatusMessage.value = '网络波动，正在自动重连，向量化仍在后台进行...'
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
        
        // 如果是自动清理的错误，延迟后自动关闭
        if (eventData.message && eventData.message.includes('已自动清理')) {
          vectorizeStatusMessage.value = '处理失败，已自动清理'
          setTimeout(() => {
            emit('update:visible', false)
            emit('success') // 刷新列表
            resetState()
          }, 2000)
        }
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
        
        // 检查是否有失败的文件
        const failedFiles = eventData.failedFiles || 0
        const totalFiles = eventData.totalFiles || 0
        
        if (failedFiles > 0) {
          vectorizeError.value = true
          if (totalFiles === 1 && failedFiles === 1) {
            errorMsg.value = '文件处理失败，请检查文件格式或内容是否正确'
            vectorizeStatusMessage.value = '处理失败'
          } else {
            errorMsg.value = `${failedFiles}/${totalFiles} 个文件处理失败，请检查文件格式或内容`
            vectorizeStatusMessage.value = eventData.message || '向量化完成'
          }
        } else {
          vectorizeStatusMessage.value = eventData.message || '向量化完成'
        }
      }
      vectorizeStatusTitle.value = '向量化完成'
      vectorizeProgress.value = 100
      break
      
    case 'cancelled':
      vectorizing.value = false
      vectorizeComplete.value = true
      vectorizeStatusTitle.value = '已取消'
      vectorizeStatusMessage.value = '向量化已取消'
      break
  }
}

function retryVectorize(): void {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  vectorizeError.value = false
  errorMsg.value = ''
  startVectorization()
}

function handleFinish(): void {
  emit('update:visible', false)
  emit('success')
  resetState()
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

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
      color: #8c8a82;

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

    .chunk-config {
      .preset-row {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 10px;
        margin-bottom: 14px;
      }

      .preset-chip {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 2px;
        padding: 10px 8px;
        border: 1px solid #ece9de;
        border-radius: 10px;
        background: #faf9f5;
        cursor: pointer;
        transition: all 0.15s ease;

        &:hover {
          border-color: rgba($primary-color, 0.5);
        }

        &.active {
          border-color: $primary-color;
          background: rgba($primary-color, 0.06);

          .preset-name {
            color: $primary-color;
          }
        }

        .preset-name {
          font-size: 13px;
          font-weight: 600;
          color: $text-dark;
        }

        .preset-hint {
          font-size: 11px;
          color: #8c8a82;
        }
      }

      .slider-row {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 6px;

        .slider-label {
          flex-shrink: 0;
          width: 60px;
          font-size: 13px;
          color: $text-secondary;
        }

        .slider-track {
          flex: 1;
          margin: 0;
        }

        .slider-number {
          flex-shrink: 0;
          width: 84px;
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
        border: 1px solid #ece9de;
        border-radius: 8px;
        padding: 8px;
      }

      .file-item {
        display: flex;
        align-items: center;
        padding: 10px 12px;
        border-radius: 6px;
        background: #f5f3ec;
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
            color: #8c8a82;
          }
        }

        .status-success {
          color: #52c41a;
          font-size: 16px;
        }

        .status-error {
          color: #c0453a;
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
        background: #f0ede4;
        color: #8c8a82;

        &.is-processing {
          background: rgba($primary-color, 0.1);
          color: $primary-color;
        }

        &.is-complete {
          background: rgba(#52c41a, 0.1);
          color: #52c41a;
        }

        &.is-error {
          background: rgba(#c0453a, 0.1);
          color: #c0453a;
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
        color: #6e6b62;
        margin-bottom: 24px;
      }

      .current-file {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 8px 16px;
        background: #f0ede4;
        border-radius: 20px;
        font-size: 13px;
        color: #6e6b62;
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
    border-top: 1px solid #ece9de;

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
