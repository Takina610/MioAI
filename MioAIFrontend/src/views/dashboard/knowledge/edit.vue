<template>
  <div class="dash-page">
    <PageHeader
      back-label="知识库管理"
      back-to="/dashboard/knowledge"
      :title="knowledgeBase?.name || '知识库详情'"
    >
      <template #actions>
        <a-button @click="goHitTest">
          <AimOutlined /> 命中测试
        </a-button>
        <a-button type="primary" :loading="saving" :disabled="!dirty" @click="handleSave">
          保存更改
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content">
      <a-spin :spinning="loading">
        <div class="edit-layout">
          <div class="dash-section docs-section">
            <div class="docs-header">
              <h3 class="section-title">
                文档
                <span v-if="pagination.total" class="doc-count">{{ pagination.total }}</span>
              </h3>
                <div class="docs-actions">
                  <a-button
                    v-if="hasRetryableDocs"
                    size="small"
                    @click="handleRevectorize(false)"
                  >
                    <ReloadOutlined /> 重新向量化
                  </a-button>
                  <a-button type="primary" size="small" @click="uploadModalVisible = true">
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

          <div class="settings-grid">
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
              <h3 class="section-title">
                分块设置
                <span v-if="chunkDirty" class="dirty-chip">未保存</span>
              </h3>

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

              <div class="slider-field">
                <div class="slider-head">
                  <span class="slider-label">分块大小</span>
                  <a-input-number
                    v-model:value="formData.chunkSize"
                    :min="200"
                    :max="2048"
                    :step="50"
                    size="small"
                  />
                </div>
                <a-slider
                  v-model:value="formData.chunkSize"
                  :min="200"
                  :max="2048"
                  :step="50"
                  :tooltip-open="false"
                />
              </div>

              <div class="slider-field">
                <div class="slider-head">
                  <span class="slider-label">分块重叠</span>
                  <a-input-number
                    v-model:value="formData.chunkOverlap"
                    :min="0"
                    :max="maxChunkOverlap"
                    :step="10"
                    size="small"
                  />
                </div>
                <a-slider
                  v-model:value="formData.chunkOverlap"
                  :min="0"
                  :max="maxChunkOverlap"
                  :step="10"
                  :tooltip-open="false"
                />
              </div>

              <a-popconfirm
                title="将按当前分块设置重建全部文档，耗时较长，确定继续？"
                ok-text="重建"
                cancel-text="取消"
                :disabled="chunkDirty"
                @confirm="handleRevectorize(true)"
              >
                <a-button danger block :disabled="chunkDirty" class="rebuild-btn">
                  <ReloadOutlined /> 按当前设置全量重建
                </a-button>
              </a-popconfirm>
            </div>
          </div>
        </div>
      </a-spin>
    </div>

    <KnowledgeBaseModal
      v-model:visible="uploadModalVisible"
      :kb-id="kbId"
      :skip-first-step="true"
      @success="refresh"
    />

    <!-- 重建/重解析：直接落在向量化进度步骤，进行中禁关不可回退 -->
    <KnowledgeBaseModal
      v-model:visible="vectorModalVisible"
      :kb-id="kbId"
      :skip-first-step="true"
      :resume-vectorize="true"
      :force-vectorize="vectorModalForce"
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
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import {
  getKnowledgeBaseById,
  updateKnowledgeBase,
  deleteDocument,
  revectorizeDocument,
  type Document
} from '@/api/knowledgeBase'
import { formatFileSize } from '@/utils/format'
import type { KnowledgeBase } from '@/types'
import { PlusOutlined, ReloadOutlined, AimOutlined } from '@ant-design/icons-vue'
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

const { docLoading, documentList, pagination, fetchDocuments, handleTableChange } = useKbDocuments()

const formData = reactive({
  name: '',
  description: '',
  isPublic: 0,
  chunkSize: 800,
  chunkOverlap: 100
})

/** 已落库快照：与 formData 比对得出脏状态，门控保存按钮 */
const savedSnapshot = ref('')
const savedChunkSize = ref(800)
const savedChunkOverlap = ref(100)

const dirty = computed(() => JSON.stringify(snapshotOf()) !== savedSnapshot.value)

function snapshotOf() {
  return {
    name: formData.name,
    description: formData.description,
    isPublic: formData.isPublic,
    chunkSize: formData.chunkSize,
    chunkOverlap: formData.chunkOverlap
  }
}

function goHitTest(): void {
  router.push(`/dashboard/knowledge/similaritySearch/${kbId.value}`)
}

type ChunkPresetKey = 'fine' | 'balanced' | 'coarse' | 'custom'

const chunkPresets: { key: Exclude<ChunkPresetKey, 'custom'>; name: string; hint: string; chunkSize: number; chunkOverlap: number }[] = [
  { key: 'fine', name: '精细', hint: '片段短，检索更准', chunkSize: 400, chunkOverlap: 80 },
  { key: 'balanced', name: '均衡', hint: '适合大多数文档', chunkSize: 800, chunkOverlap: 100 },
  { key: 'coarse', name: '粗粒度', hint: '上下文更完整', chunkSize: 1200, chunkOverlap: 150 }
]

const chunkPreset = ref<ChunkPresetKey>('balanced')

const maxChunkOverlap = computed(() => Math.min(500, Math.floor(formData.chunkSize / 2)))

const chunkDirty = computed(() =>
  formData.chunkSize !== savedChunkSize.value ||
  formData.chunkOverlap !== savedChunkOverlap.value
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
    savedSnapshot.value = JSON.stringify(snapshotOf())
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

/** 向量化弹窗（进度步骤）开关；force 决定 SSE 是否全量重建 */
const vectorModalVisible = ref(false)
const vectorModalForce = ref(false)
const vectorTaskActive = ref(false)
const hasRetryableDocs = computed(() =>
  documentList.value.some((d) => d.status === 1 || d.status === 3)
)

watch(vectorModalVisible, (val) => {
  if (!val) {
    vectorTaskActive.value = false
    window.removeEventListener('beforeunload', handleBeforeUnload)
  }
})

/** 弹窗进行中禁止误触丢进度：浏览器关闭/刷新走原生确认 */
function handleBeforeUnload(e: BeforeUnloadEvent): void {
  e.preventDefault()
  e.returnValue = ''
}

function handleRevectorize(force: boolean): void {
  vectorModalForce.value = force
  vectorTaskActive.value = true
  window.addEventListener('beforeunload', handleBeforeUnload)
  vectorModalVisible.value = true
}

/** 向量化进行中拦截路由离开：任务在后台继续，回管理页可恢复进度 */
onBeforeRouteLeave((to) => {
  if (!vectorTaskActive.value) {
    return true
  }
  Modal.confirm({
    title: '向量化正在进行',
    content: '任务会在后台继续，回到知识库管理页可恢复进度。',
    okText: '离开',
    cancelText: '留下',
    onOk: () => {
      vectorTaskActive.value = false
      router.push(to)
    }
  })
  return false
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onMounted(() => {
  refresh()
})
</script>

<style lang="scss" scoped>
.edit-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 20px;
  align-items: start;
}

.docs-section {
  .docs-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 0;
      padding-bottom: 0;
      border-bottom: none;
    }

    .doc-count {
      font-size: 12px;
      font-weight: 500;
      color: $text-muted;
      background: rgba($primary-color, 0.08);
      border-radius: 10px;
      padding: 1px 8px;
      font-variant-numeric: tabular-nums;
    }

    .docs-actions {
      display: flex;
      gap: 8px;
    }
  }
}

.chunk-section {
  .dirty-chip {
    font-size: 11px;
    font-weight: 500;
    color: #8c6d2f;
    background: $background-light;
    border-radius: 4px;
    padding: 1px 7px;
    margin-left: 8px;
    vertical-align: 2px;
  }

  .preset-row {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    margin-bottom: 18px;
  }

  .preset-chip {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    padding: 10px 4px;
    border: 1px solid #ece9de;
    border-radius: 10px;
    background: transparent;
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
      color: $text-muted;
    }
  }

  .slider-field {
    margin-bottom: 8px;

    .slider-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2px;

      .slider-label {
        font-size: 13px;
        color: $text-secondary;
      }

      .ant-input-number-sm {
        width: 76px;
      }
    }

    :deep(.ant-slider) {
      margin: 4px 0 10px;
    }
  }

  .rebuild-btn {
    margin-top: 8px;
    border-radius: 10px;
  }
}
</style>
