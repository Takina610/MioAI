<template>
  <div class="dash-page">
    <PageHeader
      back-label="公共知识库"
      back-to="/dashboard/public-knowledge"
      :title="knowledgeBase?.name || '知识库详情'"
    />

    <div class="dash-page-content">
      <a-spin :spinning="loading">
        <KnowledgeInfoForm
          :name="knowledgeBase?.name || ''"
          :description="knowledgeBase?.description || ''"
          :author="authorName"
          :document-count="knowledgeBase?.documentCount || 0"
          :storage-size="formatFileSize(knowledgeBase?.storageSize || 0)"
        />

        <div class="dash-section">
          <h3 class="section-title">文档列表</h3>

          <DocumentTable
            :documents="documentList"
            :loading="docLoading"
            :pagination="pagination"
            @change="handleTableChange"
            @preview="handlePreview"
          />
        </div>
      </a-spin>
    </div>

    <FilePreviewDrawer
      v-model:visible="previewVisible"
      :file-name="previewFileName"
      :file-url="previewFileUrl"
      :document-id="previewDocumentId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  getKnowledgeBaseById,
  type Document
} from '@/api/knowledgeBase'
import { formatFileSize } from '@/utils/format'
import type { KnowledgeBase } from '@/types'
import FilePreviewDrawer from '@/components/FilePreviewDrawer.vue'
import PageHeader from '../components/PageHeader.vue'
import KnowledgeInfoForm from './components/KnowledgeInfoForm.vue'
import DocumentTable from './components/DocumentTable.vue'
import { useKbDocuments } from './composables/useKbDocuments'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const previewVisible = ref(false)
const previewFileName = ref('')
const previewFileUrl = ref('')
const previewDocumentId = ref(0)
const knowledgeBase = ref<KnowledgeBase | null>(null)

const { docLoading, documentList, pagination, fetchDocuments, handleTableChange } = useKbDocuments()

const kbId = computed(() => Number(route.params.id))

const authorName = computed(() => knowledgeBase.value?.userName || '未知用户')

async function fetchKnowledgeBase(): Promise<void> {
  loading.value = true
  try {
    const res = await getKnowledgeBaseById(kbId.value)
    if (!res) {
      router.push('/404')
      return
    }
    if (!res.isPublic) {
      router.push('/403')
      return
    }
    knowledgeBase.value = res
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
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
  fetchKnowledgeBase()
  fetchDocuments({ kbId: kbId.value, userId: knowledgeBase.value?.userId })
})
</script>
