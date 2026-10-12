<template>
  <div class="memory-section">
    <div class="memory-header">
      <span class="memory-title">记忆</span>
    </div>
    <a-spin :spinning="loading">
      <div class="memory-list" v-if="files.length">
        <div v-for="file in files" :key="file.name" class="memory-item" @click="openViewer(file.name)">
          <FileTextOutlined class="memory-icon" />
          <div class="memory-text">
            <span class="memory-name">{{ file.name }}</span>
            <span class="memory-meta">{{ humanSize(file.size) }} · {{ formatTime(file.mtime) }}</span>
          </div>
          <div
            v-if="!file.index"
            class="memory-delete"
            @click.stop="handleDelete(file.name)"
          >
            <DeleteOutlined />
          </div>
        </div>
      </div>
      <div v-else-if="!loading" class="memory-empty">暂无记忆文件</div>
    </a-spin>

    <a-modal
      v-model:open="viewerOpen"
      :title="viewerName"
      :footer="null"
      width="640px"
    >
      <div class="memory-viewer">{{ viewerContent }}</div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { FileTextOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import {
  listAgentMemory,
  getAgentMemoryContent,
  deleteAgentMemory,
  type AgentMemoryFile
} from '@/api/agentMemory'

const props = defineProps<{ agentId: number }>()

const loading = ref(false)
const files = ref<AgentMemoryFile[]>([])
const viewerOpen = ref(false)
const viewerName = ref('')
const viewerContent = ref('')

onMounted(fetchFiles)

async function fetchFiles(): Promise<void> {
  try {
    loading.value = true
    files.value = await listAgentMemory(props.agentId)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function openViewer(name: string): Promise<void> {
  try {
    const res = await getAgentMemoryContent(props.agentId, name)
    viewerName.value = res.name
    viewerContent.value = res.content
    viewerOpen.value = true
  } catch (e) {
    console.error(e)
  }
}

function handleDelete(name: string): void {
  Modal.confirm({
    title: `删除记忆 ${name}？`,
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteAgentMemory(props.agentId, name)
        message.success('已删除')
        await fetchFiles()
      } catch (e) {
        console.error(e)
      }
    }
  })
}

function humanSize(bytes: number): string {
  if (bytes >= 1024) {
    return (bytes / 1024).toFixed(1) + ' KB'
  }
  return bytes + ' B'
}

function formatTime(ms: number): string {
  if (!ms) return ''
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style lang="scss" scoped>
.memory-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .memory-title {
    font-weight: 600;
    font-size: 14px;
  }
}

.memory-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.memory-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #f5f3ec;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    background: #ece9de;
  }

  .memory-icon {
    font-size: 18px;
    color: $primary-color;
  }

  .memory-text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1;
    min-width: 0;

    .memory-name {
      font-size: 14px;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .memory-meta {
      font-size: 12px;
      color: #6e6b62;
    }
  }

  .memory-delete {
    color: #a5a29a;
    cursor: pointer;
    transition: color 0.3s;

    &:hover {
      color: #c0564f;
    }
  }
}

.memory-empty {
  padding: 12px;
  font-size: 12px;
  color: #a5a29a;
  background: #f5f3ec;
  border-radius: 8px;
}

.memory-viewer {
  max-height: 60vh;
  overflow: auto;
  padding: 12px;
  background: #f5f3ec;
  border-radius: 8px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
