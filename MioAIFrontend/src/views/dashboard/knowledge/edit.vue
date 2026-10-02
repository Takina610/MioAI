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
        <KnowledgeInfoForm
          v-model:name="formData.name"
          v-model:description="formData.description"
          v-model:is-public="formData.isPublic"
          mode="edit"
          :author="authorName"
          :document-count="knowledgeBase?.documentCount || 0"
          :storage-size="formatFileSize(knowledgeBase?.storageSize || 0)"
        />

        <div class="dash-section document-section">
          <div class="section-header">
            <h3 class="section-title">文档列表</h3>
            <a-button type="primary" @click="uploadModalVisible = true">
              <PlusOutlined /> 上传文档
            </a-button>
          </div>

          <DocumentTable
            :documents="documentList"
            :loading="docLoading"
            :pagination="pagination"
            show-actions
            @change="handleTableChange"
            @preview="handlePreview"
            @delete="handleDeleteDocument"
          />
        </div>
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
  deleteDocument,
  type Document
} from '@/api/knowledgeBase'
import { formatFileSize } from '@/utils/format'
import type { KnowledgeBase } from '@/types'
import { PlusOutlined } from '@ant-design/icons-vue'
import KnowledgeBaseModal from '@/components/KnowledgeBaseModal.vue'
import FilePreviewDrawer from '@/components/FilePreviewDrawer.vue'
import PageHeader from '../components/PageHeader.vue'
import KnowledgeInfoForm from './components/KnowledgeInfoForm.vue'
import DocumentTable from './components/DocumentTable.vue'
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
  isPublic: 0
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
      isPublic: formData.isPublic
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

onMounted(() => {
  refresh()
})
</script>

<style lang="scss" scoped>
.document-section {
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
}
</style>
