<template>
  <div class="github-import">
    <div class="url-row">
      <a-input
        v-model:value="url"
        placeholder="https://github.com/owner/repo"
        size="large"
        allow-clear
        :disabled="importing"
        @pressEnter="handlePreview"
      />
      <a-button
        type="primary"
        size="large"
        :loading="parsing"
        :disabled="!url.trim() || importing"
        @click="handlePreview"
      >
        解析
      </a-button>
    </div>

    <template v-if="preview">
      <div class="preview-meta">
        <GithubOutlined />
        <span class="repo-name">{{ preview.owner }}/{{ preview.repo }}</span>
        <a-tag color="green">{{ preview.branch }}</a-tag>
        <span v-if="preview.totalMatched > preview.files.length" class="meta-note">
          共 {{ preview.totalMatched }} 个文件，仅展示前 {{ preview.files.length }} 个
        </span>
      </div>

      <a-table
        size="small"
        :columns="columns"
        :data-source="preview.files"
        row-key="path"
        :pagination="false"
        :scroll="{ y: 240 }"
        :loading="parsing"
        :row-selection="{ selectedRowKeys, onChange: handleSelectionChange }"
      />

      <div v-if="preview.skippedUnsupported || preview.skippedTooLarge" class="skip-note">
        已跳过 {{ preview.skippedUnsupported }} 个不支持格式、{{ preview.skippedTooLarge }} 个超过 50MB 的文件
      </div>

      <div class="import-actions">
        <span class="selected-info">已选 {{ selectedRowKeys.length }} 个文件</span>
        <a-button
          type="primary"
          :loading="importing"
          :disabled="selectedRowKeys.length === 0"
          @click="handleImport"
        >
          导入选中文件
        </a-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { GithubOutlined } from '@ant-design/icons-vue'
import {
  previewGithubImport,
  importGithubFiles,
  type GithubImportItem,
  type GithubPreview
} from '@/api/knowledgeBase'
import { formatFileSize } from '@/utils/format'

interface Props {
  kbId: number | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'imported', items: GithubImportItem[]): void
}>()

const columns = [
  { title: '文件路径', dataIndex: 'path', ellipsis: true },
  { title: '大小', dataIndex: 'size', width: 100, customRender: ({ text }: { text: number }) => formatFileSize(text) }
]

const url = ref('')
const parsing = ref(false)
const importing = ref(false)
const preview = ref<GithubPreview | null>(null)
const selectedRowKeys = ref<string[]>([])

async function handlePreview(): Promise<void> {
  if (!url.value.trim()) return
  parsing.value = true
  preview.value = null
  selectedRowKeys.value = []
  try {
    const result = await previewGithubImport(url.value.trim())
    preview.value = result
    selectedRowKeys.value = result.files.map(f => f.path)
    if (result.files.length === 0) {
      message.warning('该链接下没有可导入的文件')
    }
  } catch (e) {
    console.error(e)
  } finally {
    parsing.value = false
  }
}

function handleSelectionChange(keys: (number | string)[]): void {
  selectedRowKeys.value = keys as string[]
}

async function handleImport(): Promise<void> {
  if (!props.kbId || !preview.value || selectedRowKeys.value.length === 0) return
  importing.value = true
  try {
    const items = await importGithubFiles(props.kbId, url.value.trim(), selectedRowKeys.value)
    const successCount = items.filter(i => i.status === 'success').length
    const failCount = items.length - successCount
    if (successCount === 0) {
      message.error('导入失败，请稍后重试')
      return
    }
    if (failCount > 0) {
      message.warning(`成功导入 ${successCount} 个文件，${failCount} 个失败`)
    } else {
      message.success(`成功导入 ${successCount} 个文件`)
    }
    emit('imported', items)
    preview.value = null
    selectedRowKeys.value = []
    url.value = ''
  } catch (e) {
    console.error(e)
  } finally {
    importing.value = false
  }
}
</script>

<style lang="scss" scoped>
.github-import {
  .url-row {
    display: flex;
    gap: 12px;
    margin-bottom: 16px;
  }

  .preview-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    font-size: 13px;
    color: $text-dark;

    .repo-name {
      font-weight: 500;
    }

    .meta-note {
      color: #8c8a82;
    }
  }

  .skip-note {
    margin-top: 8px;
    font-size: 12px;
    color: #8c8a82;
  }

  .import-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #ece9de;

    .selected-info {
      font-size: 13px;
      color: #6e6b62;
    }

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
