<template>
  <a-drawer
    :open="visible"
    :width="560"
    title="切片预览"
    class="chunk-drawer"
    @close="handleClose"
  >
    <a-spin :spinning="loading">
      <div class="drawer-header">
        <span class="doc-name">{{ fileName }}</span>
        <a-tag v-if="!loading && chunks.length">{{ chunks.length }} 个切片</a-tag>
      </div>

      <a-empty v-if="!loading && chunks.length === 0" description="该文档暂无向量切片，可能尚未完成向量化" />

      <div v-else class="chunk-list">
        <div v-for="chunk in chunks" :key="chunk.chunkId" class="chunk-item">
          <div class="chunk-meta">
            <span class="chunk-index">#{{ chunk.chunkIndex }}</span>
            <span class="chunk-length">{{ chunk.length }} 字符</span>
          </div>
          <p class="chunk-content">{{ chunk.content }}</p>
        </div>
      </div>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getDocumentChunks, type DocumentChunk } from '@/api/knowledgeBase'

const props = defineProps<{
  visible: boolean
  documentId: number
  fileName: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const loading = ref(false)
const chunks = ref<DocumentChunk[]>([])

watch(() => props.visible, (val) => {
  if (val && props.documentId) {
    fetchChunks()
  }
})

async function fetchChunks(): Promise<void> {
  loading.value = true
  chunks.value = []
  try {
    chunks.value = await getDocumentChunks(props.documentId)
  } catch (e) {
    console.error(e)
    message.error('切片加载失败')
  } finally {
    loading.value = false
  }
}

function handleClose(): void {
  emit('update:visible', false)
}
</script>

<style lang="scss" scoped>
.drawer-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;

  .doc-name {
    font-size: 14px;
    font-weight: 600;
    color: $text-dark;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunk-item {
  border: 1px solid #ece9de;
  border-radius: 10px;
  padding: 12px 14px;
  background: #faf9f5;

  .chunk-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;

    .chunk-index {
      font-size: 12px;
      font-weight: 600;
      color: $primary-color;
      background: rgba($primary-color, 0.08);
      border-radius: 4px;
      padding: 1px 6px;
    }

    .chunk-length {
      font-size: 12px;
      color: #8c8a82;
    }
  }

  .chunk-content {
    margin: 0;
    font-size: 13px;
    line-height: 1.7;
    color: $text-dark;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 220px;
    overflow-y: auto;
  }
}
</style>
