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
      <a-descriptions-item label="安装来源">{{ sourceText(record) }}</a-descriptions-item>
      <a-descriptions-item label="归属用户 ID">{{ record?.userId }}</a-descriptions-item>
      <a-descriptions-item label="描述">{{ record?.description || '-' }}</a-descriptions-item>
      <a-descriptions-item label="SKILL.md">
        <pre class="detail-pre">{{ record?.content || '-' }}</pre>
      </a-descriptions-item>
      <a-descriptions-item label="创建时间">{{ formatDateTime(record?.createTime) }}</a-descriptions-item>
      <a-descriptions-item label="更新时间">{{ formatDateTime(record?.updateTime) }}</a-descriptions-item>
        </a-descriptions>
      </a-tab-pane>

      <a-tab-pane key="usage" tab="使用记录">
        <LogRecordPanel
          v-if="record"
          log-type="skill"
          :fixed-query="{ skillId: record.id }"
        />
      </a-tab-pane>

      <a-tab-pane key="conversations" tab="相关会话">
        <UsageConversationsPanel v-if="record" :fixed-query="{ skillId: record.id }" />
      </a-tab-pane>
    </a-tabs>

    <a-form v-else :model="form" layout="vertical">
      <a-form-item label="名称" required>
        <a-input v-model:value="form.name" :maxlength="100" />
      </a-form-item>
      <a-form-item label="状态">
        <a-radio-group v-model:value="form.status">
          <a-radio :value="1">正常</a-radio>
          <a-radio :value="0">禁用</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="公开">
        <a-radio-group v-model:value="form.isPublic">
          <a-radio :value="0">私有</a-radio>
          <a-radio :value="1">公开</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="SKILL.md 正文">
        <a-textarea v-model:value="form.content" :rows="12" />
      </a-form-item>
      <a-form-item label="来源 URL">
        <a-input v-model:value="form.sourceUrl" :maxlength="500" placeholder="本地创建可留空" />
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
import { createSkill, updateSkill } from '@/api/adminSkill'
import type { Skill } from '@/types/skill'
import LogRecordPanel from '@/views/admin/components/LogRecordPanel.vue'
import UsageConversationsPanel from '@/views/admin/components/UsageConversationsPanel.vue'

const detailTab = ref('info')

const props = defineProps<{
  open: boolean
  mode: 'detail' | 'form'
  record: Skill | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'saved'): void
}>()

const isCreate = computed(() => props.mode === 'form' && props.record === null)
const drawerTitle = computed(() =>
  props.mode === 'detail' ? '技能详情' : isCreate.value ? '新增技能' : '编辑技能'
)

const form = reactive({
  name: '',
  status: 1,
  isPublic: 0,
  content: '',
  sourceUrl: '',
  description: ''
})
const submitting = ref(false)

function statusText(status?: number) {
  return status === 1 ? '正常' : status === 0 ? '禁用' : '-'
}

function sourceText(record: Skill | null) {
  if (!record) return '-'
  if (record.repoOwner) return `GitHub ${record.repoOwner}/${record.repoName}`
  if (record.sourceUrl) return record.sourceUrl
  return '本地'
}

watch(
  () => props.open,
  open => {
    if (!open || props.mode !== 'form') return
    form.name = props.record?.name ?? ''
    form.status = props.record?.status ?? 1
    form.isPublic = props.record?.isPublic ?? 0
    form.content = props.record?.content ?? ''
    form.sourceUrl = props.record?.sourceUrl ?? ''
    form.description = props.record?.description ?? ''
  }
)

async function handleSubmit() {
  if (!form.name.trim()) {
    MioMessage.warning('请填写名称')
    return
  }
  submitting.value = true
  try {
    if (isCreate.value) {
      await createSkill({ ...form })
      MioMessage.success('技能已创建')
    } else {
      await updateSkill({ ...form, id: props.record!.id })
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
  max-height: 260px;
  overflow-y: auto;
}
</style>
