<template>
  <a-drawer
    :open="visible"
    :title="fileName"
    placement="right"
    :width="920"
    @close="handleClose"
  >
    <template #extra>
      <a-button type="primary" @click="handleDownload">
        <DownloadOutlined /> 下载
      </a-button>
    </template>

    <a-spin :spinning="loading" tip="加载中...">
      <div class="preview-container">
        <div v-if="error" class="error-message">
          <a-empty description="文件预览失败" />
          <p>{{ error }}</p>
        </div>

        <template v-else>
          <div v-if="fileType === 'txt'" class="text-preview">
            <pre>{{ textContent }}</pre>
          </div>

          <div v-else-if="fileType === 'md'" class="markdown-preview">
            <MarkdownView :content="textContent" />
          </div>

          <div v-else-if="fileType === 'pdf'" class="pdf-preview">
            <vue-office-pdf :src="proxyUrl" @rendered="handleRendered" @error="handlePreviewError" />
          </div>

          <div v-else-if="fileType === 'doc' || fileType === 'docx'" class="docx-preview">
            <vue-office-docx :src="proxyUrl" @rendered="handleRendered" @error="handlePreviewError" />
          </div>

          <div v-else class="unsupported-preview">
            <a-empty description="暂不支持该文件类型预览" />
          </div>
        </template>
      </div>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import { DownloadOutlined } from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import VueOfficePdf from "@vue-office/pdf/lib/v3/vue-office-pdf.mjs"
import VueOfficeDocx from "@vue-office/docx/lib/v3/vue-office-docx.mjs"
import "@vue-office/docx/lib/v3/index.css"

interface Props {
  visible: boolean
  fileName: string
  fileUrl: string
  documentId: number
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

const loading = ref(false)
const error = ref('')
const textContent = ref('')

const fileType = computed(() => {
  const name = props.fileName.toLowerCase()
  const ext = name.split('.').pop() || ''
  return ext
})

const proxyUrl = computed(() => {
  return `${baseUrl}/documents/preview/${props.documentId}`
})

watch(
  () => props.visible,
  (newVal) => {
    if (newVal && props.documentId) {
      loadPreview()
    } else {
      resetState()
    }
  }
)

async function loadPreview(): Promise<void> {
  const type = fileType.value
  if (!['txt', 'md', 'pdf', 'doc', 'docx'].includes(type)) {
    error.value = '不支持的文件类型'
    return
  }

  if (type === 'txt' || type === 'md') {
    loading.value = true
    error.value = ''
    try {
      const response = await fetch(proxyUrl.value)
      if (!response.ok) {
        throw new Error('文件加载失败')
      }
      const text = await response.text()
      textContent.value = text
    } catch (e) {
      error.value = e instanceof Error ? e.message : '文件加载失败'
    } finally {
      loading.value = false
    }
  } else {
    loading.value = true
    error.value = ''
  }
}

function resetState(): void {
  loading.value = false
  error.value = ''
  textContent.value = ''
}

function handleRendered(): void {
  loading.value = false
}

function handlePreviewError(e: Error): void {
  loading.value = false
  error.value = e.message || '文件预览失败'
}

function handleClose(): void {
  emit('update:visible', false)
}

function handleDownload(): void {
  const link = document.createElement('a')
  link.href = proxyUrl.value
  link.download = props.fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  message.success('开始下载')
}
</script>

<style lang="scss" scoped>
.preview-container {
  min-height: 300px;

  .error-message {
    text-align: center;
    padding: 40px 0;

    p {
      color: #8c8a82;
      margin-top: 16px;
    }
  }

  .text-preview {
    pre {
      background: #f0ede4;
      padding: 16px;
      border-radius: 8px;
      white-space: pre-wrap;
      word-wrap: break-word;
      font-size: 14px;
      line-height: 1.6;
      margin: 0;
    }
  }

  .markdown-preview {
    padding: 16px;
    background: #fff;
    line-height: 1.8;

    :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
      margin-top: 24px;
      margin-bottom: 16px;
      font-weight: 600;
      line-height: 1.25;
    }

    :deep(h1) { font-size: 2em; border-bottom: 1px solid #ece9de; padding-bottom: .3em; }
    :deep(h2) { font-size: 1.5em; border-bottom: 1px solid #ece9de; padding-bottom: .3em; }
    :deep(h3) { font-size: 1.25em; }
    :deep(h4) { font-size: 1em; }

    :deep(p) {
      margin-bottom: 16px;
    }

    :deep(code) {
      background: rgba(20,20,19, .05);
      border-radius: 3px;
      font-size: 85%;
      margin: 0;
      padding: .2em .4em;
    }

    :deep(pre) {
      background: #f5f3ec;
      border-radius: 6px;
      font-size: 85%;
      line-height: 1.45;
      margin-bottom: 16px;
      overflow: auto;
      padding: 16px;

      code {
        background: transparent;
        padding: 0;
      }
    }

    :deep(ul), :deep(ol) {
      padding-left: 2em;
      margin-bottom: 16px;
    }

    :deep(blockquote) {
      border-left: 4px solid #e0ddd2;
      color: #6e6b62;
      margin: 0 0 16px;
      padding: 0 1em;
    }

    :deep(table) {
      border-collapse: collapse;
      width: 100%;
      margin-bottom: 16px;

      th, td {
        border: 1px solid #e0ddd2;
        padding: 6px 13px;
      }

      th {
        background: #f5f3ec;
        font-weight: 600;
      }
    }

    :deep(img) {
      max-width: 100%;
      box-sizing: content-box;
      background: #fff;
    }
  }

  .pdf-preview,
  .docx-preview {
    :deep(.vue-office-pdf),
    :deep(.vue-office-docx) {
      width: 100%;
    }
  }

  .unsupported-preview {
    text-align: center;
    padding: 60px 0;
  }
}
</style>
