<template>
  <div class="knowledge-detail">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="back-btn" @click="goBack">
            <h2>知识库管理</h2>
          </div>
          <h2> / {{ knowledgeBase?.name || '知识库详情' }}</h2>
        </div>
        <div class="header-right">
          <a-button type="primary" :loading="saving" @click="handleSave">
            保存更改
          </a-button>
        </div>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content">
      <a-spin :spinning="loading">
        <div class="detail-container">
          <div class="info-section">
            <h3 class="section-title">基本信息</h3>
            <a-form layout="vertical" class="info-form">
              <a-row :gutter="24">
                <a-col :span="12">
                  <a-form-item label="作者">
                    <a-input :value="authorName" disabled />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="文档数量">
                    <a-input :value="knowledgeBase?.documentCount || 0" disabled suffix="个" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="24">
                <a-col :span="12">
                  <a-form-item label="知识库名称" name="name">
                    <a-input v-model:value="formData.name" placeholder="请输入知识库名称" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="存储大小">
                    <a-input :value="formatFileSize(knowledgeBase?.storageSize || 0)" disabled />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="24">
                <a-col :span="24">
                  <a-form-item label="知识库描述" name="description">
                    <a-textarea v-model:value="formData.description" placeholder="请输入知识库描述" :rows="3" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="24">
                <a-col :span="12">
                  <a-form-item label="是否公开">
                    <a-switch v-model:checked="formData.isPublic" />
                    <span class="switch-hint">{{ formData.isPublic ? '公开后其他用户可见' : '仅自己可见' }}</span>
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>
          </div>

          <div class="document-section">
            <div class="section-header">
              <h3 class="section-title">文档列表</h3>
              <a-button type="primary" @click="showUploadModal">
                <PlusOutlined /> 上传文档
              </a-button>
            </div>

            <a-table
              :columns="columns"
              :data-source="documentList"
              :loading="docLoading"
              :pagination="pagination"
              row-key="id"
              @change="handleTableChange"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'fileName'">
                  <a-button type="link" class="file-name-btn" @click="handlePreview(record)">
                    {{ record.fileName }}
                  </a-button>
                </template>
                <template v-else-if="column.key === 'fileSize'">
                  {{ formatFileSize(record.fileSize) }}
                </template>
                <template v-else-if="column.key === 'status'">
                  <a-tag :color="getStatusColor(record.status)">
                    {{ record.statusDesc }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'createTime'">
                  {{ formatDate(record.createTime) }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-popconfirm
                    title="确定要删除这个文档吗？"
                    ok-text="确定"
                    cancel-text="取消"
                    @confirm="handleDeleteDocument(record.id)"
                  >
                    <a-button type="link" danger size="small">
                      <DeleteOutlined /> 删除
                    </a-button>
                  </a-popconfirm>
                </template>
              </template>
            </a-table>
          </div>
        </div>
      </a-spin>
    </div>

    <KnowledgeBaseModal
      v-model:visible="uploadModalVisible"
      :kb-id="kbId"
      :skip-first-step="true"
      @success="handleUploadSuccess"
    />

    <FilePreviewDrawer
      v-model:visible="previewVisible"
      :file-name="previewFileName"
      :file-url="previewFileUrl"
      :document-id="previewDocumentId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import {
  getKnowledgeBaseById,
  updateKnowledgeBase,
  queryDocuments,
  deleteDocument,
  type Document
} from '@/api/knowledgeBase'
import type { KnowledgeBase } from '@/types'
import {
  PlusOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import KnowledgeBaseModal from '@/components/KnowledgeBaseModal.vue'
import FilePreviewDrawer from '@/components/FilePreviewDrawer.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const kbId = computed(() => Number(route.params.id))

const loading = ref(false)
const docLoading = ref(false)
const saving = ref(false)
const uploadModalVisible = ref(false)
const previewVisible = ref(false)
const previewFileName = ref('')
const previewFileUrl = ref('')
const previewDocumentId = ref(0)
const knowledgeBase = ref<KnowledgeBase | null>(null)
const documentList = ref<Document[]>([])

const formData = reactive({
  name: '',
  description: '',
  isPublic: false
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`
})

const authorName = computed(() => {
  if (knowledgeBase.value && userStore.userInfo) {
    return userStore.userInfo.userName || '未知用户'
  }
  return '未知用户'
})

const columns = [
  { title: '文档名称', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: '文件类型', dataIndex: 'fileType', key: 'fileType', width: 100 },
  { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 100 }
]

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

function getStatusColor(status: number): string {
  const colors: Record<number, string> = {
    0: 'default',
    1: 'processing',
    2: 'success',
    3: 'error'
  }
  return colors[status] || 'default'
}

function goBack(): void {
  router.push('/dashboard/knowledge')
}

async function fetchKnowledgeBase(): Promise<void> {
  loading.value = true
  try {
    const res = await getKnowledgeBaseById(kbId.value)
    console.log(res)
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
    formData.isPublic = res.isPublic === 1
  } catch (e: unknown) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function fetchDocuments(): Promise<void> {
  docLoading.value = true
  try {
    const res = await queryDocuments({
      current: pagination.current,
      pageSize: pagination.pageSize,
      kbId: kbId.value,
      userId: userStore.userInfo?.id
    })
    documentList.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    docLoading.value = false
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
      isPublic: formData.isPublic ? 1 : 0
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

function handleTableChange(pag: { current: number; pageSize: number }): void {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchDocuments()
}

async function handleDeleteDocument(docId: number): Promise<void> {
  try {
    await deleteDocument(docId)
    message.success('删除成功')
    fetchDocuments()
    fetchKnowledgeBase()
  } catch (e) {
    console.error(e)
    message.error('删除失败')
  }
}

function showUploadModal(): void {
  uploadModalVisible.value = true
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

function handleUploadSuccess(): void {
  fetchDocuments()
  fetchKnowledgeBase()
}

onMounted(() => {
  fetchKnowledgeBase()
  fetchDocuments()
})
</script>

<style lang="scss" scoped>
.knowledge-detail {
  .page-header {
    .header-content {
      padding: 16px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        display: flex;
        align-items: center;
        gap: 16px;

        .back-btn {
          &:hover {
            cursor: pointer;
          }
          h2 {
            color: #5f6368;
            font-weight: 400;
          }
        }

        h2 {
          font-size: 24px;
          font-weight: 600;
          color: #202124;
          margin: 0;
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
        }
      }
    }

    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .page-content {
    height: calc(100% - 132px);
    overflow-y: auto;
    padding: 0 24px;
  }

  .detail-container {
    padding: 24px 0;
  }

  .info-section,
  .document-section {
    background: #fff;
    border-radius: 12px;
    padding: 24px;
    margin-bottom: 24px;
    border: 1px solid #f0f0f0;
  }

  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: #202124;
    margin-bottom: 20px;
    padding-bottom: 12px;
    border-bottom: 1px solid #f0f0f0;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding-bottom: 12px;
    border-bottom: 1px solid #f0f0f0;

    .section-title {
      margin-bottom: 0;
      padding-bottom: 0;
      border-bottom: none;
    }
  }

  .info-form {
    :deep(.ant-form-item-label) {
      label {
        color: #5f6368;
        font-weight: 500;
      }
    }

    :deep(.ant-input-disabled) {
      color: #202124;
      background: #f5f5f5;
    }
  }

  .switch-hint {
    margin-left: 12px;
    color: #999;
    font-size: 13px;
  }

  :deep(.ant-table) {
    .ant-table-thead > tr > th {
      background: #fafafa;
      font-weight: 600;
      color: #5f6368;
    }
  }

  .file-name-btn {
    padding: 0;
    height: auto;
    font-size: 14px;
    color: $primary-color;

    &:hover {
      color: darken($primary-color, 10%);
    }
  }
}
</style>
