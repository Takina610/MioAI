<template>
  <a-drawer
    :open="open"
    :title="drawerTitle"
    width="560"
    destroy-on-close
    @close="$emit('update:open', false)"
  >
    <a-tabs v-if="mode === 'detail'" v-model:activeKey="detailTab">
      <a-tab-pane key="info" tab="基本信息">
        <a-descriptions :column="1" bordered size="small">
      <a-descriptions-item label="ID">{{ record?.id }}</a-descriptions-item>
      <a-descriptions-item label="名称">{{ record?.name }}</a-descriptions-item>
      <a-descriptions-item label="状态">{{ statusText(record?.status) }}</a-descriptions-item>
      <a-descriptions-item label="公开">{{ record?.isPublic === 1 ? '公开' : '私有' }}</a-descriptions-item>
      <a-descriptions-item label="归属用户 ID">{{ record?.userId }}</a-descriptions-item>
      <a-descriptions-item label="描述">{{ record?.description || '-' }}</a-descriptions-item>
      <a-descriptions-item label="连接配置">
        <pre class="detail-pre">{{ record?.config || '-' }}</pre>
      </a-descriptions-item>
      <a-descriptions-item label="工具清单">
        <pre class="detail-pre">{{ record?.toolInfo || '-' }}</pre>
      </a-descriptions-item>
      <a-descriptions-item label="创建时间">{{ formatDateTime(record?.createTime) }}</a-descriptions-item>
      <a-descriptions-item label="更新时间">{{ formatDateTime(record?.updateTime) }}</a-descriptions-item>
        </a-descriptions>
      </a-tab-pane>

      <a-tab-pane key="calls" tab="调用记录">
        <LogRecordPanel
          v-if="record"
          log-type="tool"
          :fixed-query="{ mcpId: record.id }"
          show-conversation
        />
      </a-tab-pane>

      <a-tab-pane key="conversations" tab="相关会话">
        <UsageConversationsPanel v-if="record" :fixed-query="{ mcpId: record.id }" />
      </a-tab-pane>
    </a-tabs>

    <a-form v-else :model="form" layout="vertical">
      <a-form-item label="名称" required>
        <a-input v-model:value="form.name" :maxlength="100" />
      </a-form-item>
      <a-form-item label="状态" v-if="!isCreate">
        <a-select v-model:value="form.status">
          <a-select-option :value="0">未激活</a-select-option>
          <a-select-option :value="1">正常</a-select-option>
          <a-select-option :value="2">异常</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="公开">
        <a-radio-group v-model:value="form.isPublic">
          <a-radio :value="0">私有</a-radio>
          <a-radio :value="1">公开</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="连接配置（JSON）">
        <a-textarea v-model:value="form.config" :rows="8" placeholder='{"command":"npx","args":["-y","..."]}' />
      </a-form-item>
      <a-form-item label="工具清单（JSON）">
        <a-textarea v-model:value="form.toolInfo" :rows="4" />
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
import { formatDateTime } from '@/utils/format'
import { createMcpTool, updateMcpTool } from '@/api/adminMcp'
import type { McpTool } from '@/types/mcpTool'
import LogRecordPanel from '@/views/admin/components/LogRecordPanel.vue'
import UsageConversationsPanel from '@/views/admin/components/UsageConversationsPanel.vue'

const detailTab = ref('info')

const props = defineProps<{
  open: boolean
  mode: 'detail' | 'form'
  record: McpTool | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'saved'): void
}>()

const isCreate = computed(() => props.mode === 'form' && props.record === null)
const drawerTitle = computed(() =>
  props.mode === 'detail' ? 'MCP 详情' : isCreate.value ? '新增 MCP' : '编辑 MCP'
)

const form = reactive({
  name: '',
  status: 0,
  isPublic: 0,
  config: '',
  toolInfo: '',
  description: ''
})
const submitting = ref(false)

function statusText(status?: number) {
  return status === 1 ? '正常' : status === 2 ? '异常' : status === 0 ? '未激活' : '-'
}

watch(
  () => props.open,
  open => {
    if (!open || props.mode !== 'form') return
    form.name = props.record?.name ?? ''
    form.status = props.record?.status ?? 0
    form.isPublic = props.record?.isPublic ?? 0
    form.config = props.record?.config ?? ''
    form.toolInfo = props.record?.toolInfo ?? ''
    form.description = props.record?.description ?? ''
  }
)

async function handleSubmit() {
  if (!form.name.trim()) {
    MioMessage.warning('请填写名称')
    return
  }
  if (isCreate.value && !form.config.trim()) {
    MioMessage.warning('请填写连接配置')
    return
  }
  submitting.value = true
  try {
    if (isCreate.value) {
      await createMcpTool({ ...form })
      MioMessage.success('MCP 已创建')
    } else {
      await updateMcpTool({ ...form, id: props.record!.id })
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
.detail-pre {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}
</style>
