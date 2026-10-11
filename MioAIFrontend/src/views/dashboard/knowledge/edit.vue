<template>
  <div class="dash-page">
    <PageHeader
      back-label="知识库管理"
      back-to="/dashboard/knowledge"
      :title="knowledgeBase?.name || '知识库详情'"
    >
      <template #actions>
        <a-button type="primary" :loading="saving" @click="handleSave">
          保存更改
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content">
      <a-spin :spinning="loading">
        <a-tabs v-model:active-key="activeTab" class="kb-edit-tabs">
          <a-tab-pane key="documents" tab="文档管理">
            <div class="dash-section document-section">
              <div class="section-header">
                <div class="section-actions">
                  <a-button
                    v-if="hasRetryableDocs"
                    :loading="vectorizing"
                    @click="handleRevectorize(false)"
                  >
                    <ReloadOutlined /> 重新向量化
                  </a-button>
                  <a-button type="primary" @click="uploadModalVisible = true">
                    <PlusOutlined /> 上传文档
                  </a-button>
                </div>
              </div>

              <DocumentTable
                :documents="documentList"
                :loading="docLoading"
                :pagination="pagination"
                show-actions
                @change="handleTableChange"
                @preview="handlePreview"
                @chunks="handleViewChunks"
                @revectorize="handleDocRevectorize"
                @delete="handleDeleteDocument"
              />
            </div>
          </a-tab-pane>

          <a-tab-pane key="settings" tab="知识库设置">
            <KnowledgeInfoForm
              v-model:name="formData.name"
              v-model:description="formData.description"
              v-model:is-public="formData.isPublic"
              mode="edit"
              :author="authorName"
              :document-count="knowledgeBase?.documentCount || 0"
              :storage-size="formatFileSize(knowledgeBase?.storageSize || 0)"
            />

            <div class="dash-section chunk-section">
              <div class="section-header">
                <h3 class="section-title">分块设置</h3>
                <a-tag v-if="chunkDirty" color="warning">已修改，保存并重新向量化后生效</a-tag>
              </div>

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
                  :tooltip-visible="false"
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
                  :tooltip-visible="false"
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
              <p class="field-note">单位为 token；重叠用于避免句子在块边界被切断。分块越小检索越精细，越大上下文越完整。</p>

              <div class="rebuild-row">
                <div class="rebuild-info">
                  <span class="info-label">向量模型</span>
                  <span class="info-value">mistral-embed · 1024 维</span>
                </div>
                <a-popconfirm
                  title="将按当前分块设置重建全部文档的向量索引，耗时较长，确定继续？"
                  ok-text="重建"
                  cancel-text="取消"
                  :disabled="!chunkDirty && !hasRetryableDocs"
                  @confirm="handleRevectorize(true)"
                >
                  <a-button danger :disabled="!chunkDirty && !hasRetryableDocs" :loading="vectorizing">
                    <ReloadOutlined /> 按当前设置全量重建
                  </a-button>
                </a-popconfirm>
              </div>
              <p v-if="chunkDirty" class="field-note warn">分块设置已修改：请先保存更改，再执行全量重建使新设置对已有文档生效。</p>
            </div>
          </a-tab-pane>
        </a-tabs>
      </a-spin>
    </div>

    <KnowledgeBaseModal
      v-model:visible="uploadModalVisible"
      :kb-id="kbId"
      :skip-first-step="true"
      @success="refresh"
    />

    <FilePreviewDrawer
      v-model:visible="previewVisible"
      :file-name="previewFileName"
      :file-url="previewFileUrl"
      :document-id="previewDocumentId"
    />

    <ChunkListDrawer
      v-model:visible="chunksVisible"
      :document-id="chunkDocumentId"
      :file-name="chunkFileName"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import {
  getKnowledgeBaseById,
  updateKnowledgeBase,
  deleteDocument,
  revectorizeDocument,
  vectorizeKnowledgeFiles,
  type Document
} from '@/api/knowledgeBase'
import { formatFileSize } from '@/utils/format'
import type { KnowledgeBase } from '@/types'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import KnowledgeBaseModal from '@/components/KnowledgeBaseModal.vue'
import FilePreviewDrawer from '@/components/FilePreviewDrawer.vue'
import PageHeader from '../components/PageHeader.vue'
import KnowledgeInfoForm from './components/KnowledgeInfoForm.vue'
import DocumentTable from './components/DocumentTable.vue'
import ChunkListDrawer from './components/ChunkListDrawer.vue'
import { useKbDocuments } from './composables/useKbDocuments'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const uploadModalVisible = ref(false)
const previewVisible = ref(false)
const previewFileName = ref('')
const previewFileUrl = ref('')
const previewDocumentId = ref(0)
const knowledgeBase = ref<KnowledgeBase | null>(null)
const activeTab = ref('documents')

const { docLoading, documentList, pagination, fetchDocuments, handleTableChange } = useKbDocuments()

const formData = reactive({
  name: '',
  description: '',
  isPublic: 0,
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
const savedChunkSize = ref(800)
const savedChunkOverlap = ref(100)

const maxChunkOverlap = computed(() => Math.min(500, Math.floor(formData.chunkSize / 2)))

const chunkDirty = computed(() =>
  formData.chunkSize !== savedChunkSize.value || formData.chunkOverlap !== savedChunkOverlap.value
)

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
  const cap = Math.min(500, Math.floor(size / 2))
  if (formData.chunkOverlap > cap) {
    formData.chunkOverlap = cap
  }
  chunkPreset.value = matchChunkPreset(size, formData.chunkOverlap)
})

watch(() => formData.chunkOverlap, (overlap) => {
  chunkPreset.value = matchChunkPreset(formData.chunkSize, overlap)
})

const kbId = computed(() => Number(route.params.id))

const authorName = computed(() => userStore.userInfo?.userName || '未知用户')

async function fetchKnowledgeBase(): Promise<void> {
  loading.value = true
  try {
    const res = await getKnowledgeBaseById(kbId.value)
    if (!res) {
      router.push('/404')
      return
    }

    if (!userStore.isLoggedIn || userStore.userInfo?.id !== res.userId) {
      router.push('/403')
      return
    }
    knowledgeBase.value = res
    formData.name = res.name || ''
    formData.description = res.description || ''
    formData.isPublic = res.isPublic ?? 0
    formData.chunkSize = res.chunkSize ?? 800
    formData.chunkOverlap = res.chunkOverlap ?? 100
    savedChunkSize.value = formData.chunkSize
    savedChunkOverlap.value = formData.chunkOverlap
    chunkPreset.value = matchChunkPreset(formData.chunkSize, formData.chunkOverlap)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function handleSave(): Promise<void> {
  if (!formData.name.trim()) {
    message.warning('请输入知识库名称')
    return
  }
  saving.value = true
  try {
    await updateKnowledgeBase({
      id: kbId.value,
      name: formData.name,
      description: formData.description,
      isPublic: formData.isPublic,
      chunkSize: formData.chunkSize,
      chunkOverlap: formData.chunkOverlap
    })
    message.success('保存成功')
    fetchKnowledgeBase()
  } catch (e) {
    console.error(e)
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDeleteDocument(docId: number): Promise<void> {
  try {
    await deleteDocument(docId)
    message.success('删除成功')
    refresh()
  } catch (e) {
    console.error(e)
    message.error('删除失败')
  }
}

function refresh(): void {
  fetchKnowledgeBase()
  fetchDocuments({ kbId: kbId.value, userId: userStore.userInfo?.id })
}

function handlePreview(record: Document): void {
  if (!record.filePath) {
    message.warning('文件路径不存在')
    return
  }
  previewFileName.value = record.fileName
  previewFileUrl.value = record.filePath
  previewDocumentId.value = record.id
  previewVisible.value = true
}

const chunksVisible = ref(false)
const chunkDocumentId = ref(0)
const chunkFileName = ref('')

function handleViewChunks(record: Document): void {
  chunkDocumentId.value = record.id
  chunkFileName.value = record.fileName
  chunksVisible.value = true
}

async function handleDocRevectorize(record: Document): Promise<void> {
  try {
    await revectorizeDocument(record.id)
    message.success(`「${record.fileName}」已加入解析队列`)
    handleRevectorize(false)
  } catch (e) {
    console.error(e)
    message.error('操作失败')
  }
}

const vectorizing = ref(false)
const reconnNotified = ref(false)
let eventSource: EventSource | null = null
const hasRetryableDocs = computed(() =>
  documentList.value.some((d) => d.status === 1 || d.status === 3)
)

function closeVectorizeStream(): void {
  eventSource?.close()
  eventSource = null
  vectorizing.value = false
  reconnNotified.value = false
}

function handleRevectorize(force: boolean): void {
  if (vectorizing.value) {
    return
  }
  vectorizing.value = true
  reconnNotified.value = false
  const token = localStorage.getItem('token') || ''
  eventSource = vectorizeKnowledgeFiles(kbId.value, token, force)
  eventSource.onmessage = (event) => {
    reconnNotified.value = false
    try {
      const payload = JSON.parse(event.data)
      if (payload.type === 'done') {
        message.success(payload.data?.message || '向量化完成')
        closeVectorizeStream()
        refresh()
      } else if (payload.type === 'error') {
        message.error(payload.data?.message || '向量化失败，文件已保留，可重试')
        closeVectorizeStream()
        refresh()
      } else if (payload.type === 'cancelled') {
        closeVectorizeStream()
        refresh()
      }
    } catch {
      // 忽略无法解析的行
    }
  }
  eventSource.onerror = () => {
    // 网络波动：EventSource 自动重连，后端任务继续在跑并以观察者模式接管；
    // 不关闭连接，等待终态事件
    if (!reconnNotified.value) {
      reconnNotified.value = true
      message.info('网络波动，正在自动重连向量化进度...')
    }
  }
}

onBeforeUnmount(() => {
  closeVectorizeStream()
})

onMounted(() => {
  refresh()
})
</script>

<style lang="scss" scoped>
.kb-edit-tabs {
  :deep(.ant-tabs-tab) {
    font-size: 15px;
  }
}

.document-section,
.chunk-section {
  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding-bottom: 12px;
    border-bottom: 1px solid #ece9de;

    .section-title {
      margin-bottom: 0;
      padding-bottom: 0;
      border-bottom: none;
    }

    .section-actions {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
      flex: 1;
    }
  }
}

.chunk-section {
  .preset-row {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
    max-width: 520px;
    margin-bottom: 16px;
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
    max-width: 520px;
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

  .field-note {
    margin: 8px 0 0;
    font-size: 12px;
    color: #8c8a82;
    max-width: 520px;

    &.warn {
      color: #b8860b;
    }
  }

  .rebuild-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    max-width: 520px;
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px dashed #ece9de;

    .rebuild-info {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .info-label {
        font-size: 12px;
        color: #8c8a82;
      }

      .info-value {
        font-size: 13px;
        color: $text-dark;
      }
    }
  }
}
</style>
