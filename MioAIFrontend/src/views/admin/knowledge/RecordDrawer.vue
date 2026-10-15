<template>
  <a-drawer
    :open="open"
    :title="drawerTitle"
    width="760"
    destroy-on-close
    @close="handleClose"
  >
    <a-tabs v-if="mode === 'detail'" v-model:activeKey="detailTab">
      <a-tab-pane key="info" tab="基本信息">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="ID">{{ record?.id }}</a-descriptions-item>
          <a-descriptions-item label="名称">{{ record?.name }}</a-descriptions-item>
          <a-descriptions-item label="归属用户">{{ record?.userName || '-' }} #{{ record?.userId }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ statusText(record?.status) }}</a-descriptions-item>
          <a-descriptions-item label="公开">{{ record?.isPublic === 1 ? '公开' : '私有' }}</a-descriptions-item>
          <a-descriptions-item label="分块配置">{{ record?.chunkSize ?? '-' }} / {{ record?.chunkOverlap ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="文档数">{{ record?.documentCount ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ record?.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ record?.createTime }}</a-descriptions-item>
        </a-descriptions>
      </a-tab-pane>

      <a-tab-pane key="documents" tab="文档">
        <div class="doc-toolbar">
          <a-upload :before-upload="handleUpload" :show-upload-list="false" accept=".md,.txt,.pdf,.docx,.html" multiple>
            <a-button :loading="uploading">上传文档</a-button>
          </a-upload>
          <a-button
            :loading="vectorizing"
            :disabled="documents.length === 0"
            @click="startVectorize"
          >
            {{ vectorizing ? `向量化中 ${vectorProgress}%` : '重新向量化' }}
          </a-button>
        </div>
        <div v-if="vectorizing" class="vector-progress">
          <a-progress :percent="vectorProgress" size="small" />
          <span class="adm-cell-muted">{{ vectorMessage }}</span>
        </div>
        <a-table
          :data-source="documents"
          :columns="docColumns"
          row-key="id"
          size="small"
          :loading="docsLoading"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <span class="adm-dot" :class="record.status === 1 ? 'is-success' : record.status === 0 ? 'is-pulse' : 'is-danger'">
                {{ record.statusDesc || statusText(record.status) }}
              </span>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-popconfirm title="删除该文档？向量数据与源文件一并清理。" ok-text="删除" cancel-text="取消" @confirm="handleDeleteDoc(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="retrievals" tab="检索记录">
        <LogRecordPanel
          v-if="record"
          log-type="rag"
          :fixed-query="{ kbId: record.id }"
          show-conversation
          show-model
        />
      </a-tab-pane>

      <a-tab-pane key="conversations" tab="相关会话">
        <UsageConversationsPanel v-if="record" :fixed-query="{ kbId: record.id }" />
      </a-tab-pane>
    </a-tabs>

    <a-form v-else :model="form" layout="vertical">
      <a-form-item label="名称" required>
        <a-input v-model:value="form.name" :maxlength="100" />
      </a-form-item>
      <a-form-item label="状态" v-if="!isCreate">
        <a-select v-model:value="form.status">
          <a-select-option :value="1">正常</a-select-option>
          <a-select-option :value="2">禁用</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="公开">
        <a-radio-group v-model:value="form.isPublic">
          <a-radio :value="0">私有</a-radio>
          <a-radio :value="1">公开</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="分块大小（token）">
        <a-input-number v-model:value="form.chunkSize" :min="100" :max="8000" style="width: 100%" />
      </a-form-item>
      <a-form-item label="分块重叠（token）">
        <a-input-number v-model:value="form.chunkOverlap" :min="0" :max="2000" style="width: 100%" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model:value="form.description" :rows="3" :maxlength="2000" />
      </a-form-item>
    </a-form>

    <template v-if="mode === 'form'" #footer>
      <a-space style="display: flex; justify-content: flex-end">
        <a-button @click="$emit('update:open', false)">取消</a-button>
        <a-button type="primary" :loading="submitting" @click="handleSubmit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import MioMessage from '@/utils/message'
import Storage from '@/utils/storage'
import { createKnowledgeBase, deleteAdminDocument, listDocuments, updateKnowledgeBase, uploadAdminDocuments, adminVectorizeFiles } from '@/api/adminKnowledge'
import type { KnowledgeBase } from '@/types'
import type { Document } from '@/api/knowledgeBase'
import LogRecordPanel from '@/views/admin/components/LogRecordPanel.vue'
import UsageConversationsPanel from '@/views/admin/components/UsageConversationsPanel.vue'

const props = defineProps<{
  open: boolean
  mode: 'detail' | 'form'
  record: KnowledgeBase | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'saved'): void
}>()

const isCreate = computed(() => props.mode === 'form' && props.record === null)
const drawerTitle = computed(() =>
  props.mode === 'detail' ? '知识库详情' : isCreate.value ? '新增知识库' : '编辑知识库'
)
const detailTab = ref('info')

const form = reactive({
  name: '',
  status: 1,
  isPublic: 0,
  chunkSize: undefined as number | undefined,
  chunkOverlap: undefined as number | undefined,
  description: ''
})
const submitting = ref(false)

const documents = ref<Document[]>([])
const docsLoading = ref(false)
const uploading = ref(false)
const vectorizing = ref(false)
const vectorProgress = ref(0)
const vectorMessage = ref('')
let eventSource: EventSource | null = null

const docColumns = [
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: '状态', key: 'status', width: 90 },
  { title: '', key: 'action', width: 60 }
]

function statusText(status?: number) {
  return status === 1 ? '正常' : status === 2 ? '禁用' : status === 0 ? '创建中' : '-'
}

watch(
  () => props.open,
  open => {
    if (!open) return
    if (props.mode === 'detail' && props.record) {
      detailTab.value = 'info'
      fetchDocuments(props.record.id)
      return
    }
    form.name = props.record?.name ?? ''
    form.status = props.record?.status ?? 1
    form.isPublic = props.record?.isPublic ?? 0
    form.chunkSize = props.record?.chunkSize ?? undefined
    form.chunkOverlap = props.record?.chunkOverlap ?? undefined
    form.description = props.record?.description ?? ''
  }
)

async function fetchDocuments(kbId: number) {
  docsLoading.value = true
  try {
    const res = await listDocuments(kbId, { current: 1, pageSize: 100 })
    documents.value = res.records ?? []
  } catch {
    MioMessage.error('获取文档失败')
  } finally {
    docsLoading.value = false
  }
}

/** 上传：beforeUpload 返回 false 阻止 antd 自动上传，走自定义 FormData */
async function handleUpload(file: File) {
  if (!props.record) return false
  uploading.value = true
  try {
    await uploadAdminDocuments(props.record.id, [file])
    MioMessage.success(`已上传 ${file.name}，待向量化`)
    fetchDocuments(props.record.id)
  } catch {
    MioMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

/** 向量化（SSE 进度）：后台任务继续运行，关抽屉不打断 */
function startVectorize() {
  if (!props.record || vectorizing.value) return
  const token = Storage.getToken()
  vectorizing.value = true
  vectorProgress.value = 0
  vectorMessage.value = '准备中...'
  eventSource?.close()
  eventSource = adminVectorizeFiles(props.record.id, token, true)
  eventSource.onmessage = event => {
    try {
      const payload = JSON.parse(event.data) as { type: string; data?: { percent?: number; message?: string; fileName?: string } }
      if (payload.type === 'progress' && payload.data) {
        vectorProgress.value = Math.round(payload.data.percent ?? 0)
        vectorMessage.value = payload.data.message ?? payload.data.fileName ?? ''
      } else if (payload.type === 'done') {
        stopVectorize('向量化完成')
        fetchDocuments(props.record!.id)
      } else if (payload.type === 'error') {
        stopVectorize()
        MioMessage.error('向量化失败')
      } else if (payload.type === 'cancelled') {
        stopVectorize()
      }
    } catch {
      // 非 JSON 心跳忽略
    }
  }
  eventSource.onerror = () => {
    stopVectorize()
  }
}

function stopVectorize(message?: string) {
  vectorizing.value = false
  vectorMessage.value = message ?? vectorMessage.value
  eventSource?.close()
  eventSource = null
}

function handleClose() {
  // 向量化是后台任务，关闭抽屉不打断；仅断开进度监听
  if (vectorizing.value) {
    MioMessage.info('向量化仍在后台进行，可稍后回到本页查看进度')
  }
  stopVectorize()
  emit('update:open', false)
}

async function handleDeleteDoc(docId: number) {
  if (!props.record) return
  try {
    await deleteAdminDocument(props.record.id, docId)
    MioMessage.success('已删除')
    fetchDocuments(props.record.id)
  } catch {
    MioMessage.error('删除失败')
  }
}

async function handleSubmit() {
  if (!form.name.trim()) {
    MioMessage.warning('请填写名称')
    return
  }
  submitting.value = true
  try {
    if (isCreate.value) {
      await createKnowledgeBase({ ...form })
      MioMessage.success('知识库已创建')
    } else {
      await updateKnowledgeBase({ ...form, id: props.record!.id })
      MioMessage.success('已保存')
    }
    emit('update:open', false)
    emit('saved')
  } catch {
    MioMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.doc-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}

.vector-progress {
  margin-bottom: 10px;

  .adm-cell-muted {
    font-size: 12px;
  }
}
</style>
