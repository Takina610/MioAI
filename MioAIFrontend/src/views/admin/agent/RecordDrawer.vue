<template>
  <a-drawer
    :open="open"
    :title="drawerTitle"
    width="720"
    destroy-on-close
    @close="$emit('update:open', false)"
  >
    <!-- ============ 详情：tabs（基本信息 / 版本 / 群成员 / 会话） ============ -->
    <a-tabs v-if="mode === 'detail'" v-model:activeKey="detailTab">
      <a-tab-pane key="info" tab="基本信息">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="ID">{{ record?.id }}</a-descriptions-item>
          <a-descriptions-item label="名称">{{ record?.name }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ typeText(record?.type) }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ statusText(record?.status) }}</a-descriptions-item>
          <a-descriptions-item label="公开">{{ record?.isPublic === 1 ? '公开' : '私有' }}</a-descriptions-item>
          <a-descriptions-item label="归属用户">{{ record?.userName || '-' }} #{{ record?.userId }}</a-descriptions-item>
          <a-descriptions-item label="使用次数">{{ record?.usageCount ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="版本">{{ record?.version || '-' }}</a-descriptions-item>
          <a-descriptions-item label="头像 URL">{{ record?.avatar || '-' }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ record?.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="系统提示词">
            <pre class="detail-pre">{{ record?.systemPrompt || '-' }}</pre>
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatDateTime(record?.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{ formatDateTime(record?.updateTime) }}</a-descriptions-item>
        </a-descriptions>
        <div class="bindings">
          <div class="binding-group">
            <span class="binding-title">绑定知识库</span>
            <a-tag v-for="kb in bindings.knowledgeBases" :key="kb.id" class="binding-tag">{{ kb.name }}</a-tag>
            <span v-if="!bindings.knowledgeBases.length" class="adm-cell-muted">无</span>
          </div>
          <div class="binding-group">
            <span class="binding-title">绑定 MCP</span>
            <a-tag v-for="mcp in bindings.mcpTools" :key="mcp.id" class="binding-tag">{{ mcp.name }}</a-tag>
            <span v-if="!bindings.mcpTools.length" class="adm-cell-muted">无</span>
          </div>
          <div class="binding-group">
            <span class="binding-title">绑定技能</span>
            <a-tag v-for="sk in bindings.skills" :key="sk.id" class="binding-tag">{{ sk.name }}</a-tag>
            <span v-if="!bindings.skills.length" class="adm-cell-muted">无</span>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="versions" tab="版本">
        <a-table
          :data-source="versions"
          :columns="versionColumns"
          row-key="id"
          size="small"
          :loading="tabLoading"
          :pagination="{ pageSize: 8 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'version'">{{ record.data.version }}</template>
            <template v-else-if="column.key === 'name'">{{ record.data.name }}</template>
            <template v-else-if="column.key === 'rollbackFrom'">{{ record.data.rollbackFrom || '-' }}</template>
            <template v-else-if="column.key === 'createTime'">{{ formatDateTime(record.data.createTime) }}</template>
            <template v-else-if="column.key === 'action'">
              <a-popconfirm title="删除该版本快照？" ok-text="删除" cancel-text="取消" @confirm="deleteVersion(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane v-if="record?.type === 2" key="members" tab="群成员">
        <div class="member-add">
          <a-select
            v-model:value="memberToAdd"
            show-search
            placeholder="选择要加入的智能体"
            style="flex: 1"
            :filter-option="filterAgentOption"
          >
            <a-select-option v-for="c in memberCandidates" :key="c.value" :value="c.value" :label="c.label">
              <div class="member-option">
                <BotIcon
                  v-if="iconConfig(c.icon)"
                  :shape="iconConfig(c.icon)!.shape"
                  :fill="iconConfig(c.icon)!.fill"
                  :size="18"
                  :live="false"
                />
                <a-avatar v-else :src="c.avatar" :size="18">{{ c.label.charAt(0) }}</a-avatar>
                <span>{{ c.label }}</span>
              </div>
            </a-select-option>
          </a-select>
          <a-button type="primary" :disabled="!memberToAdd" :loading="memberAdding" @click="addMember">添加</a-button>
        </div>
        <a-table
          :data-source="members"
          :columns="memberColumns"
          row-key="id"
          size="small"
          :loading="tabLoading"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'member'">
              <div class="cell-member">
                <BotIcon
                  v-if="iconConfig(record.agentIcon)"
                  :shape="iconConfig(record.agentIcon)!.shape"
                  :fill="iconConfig(record.agentIcon)!.fill"
                  :size="22"
                  :live="false"
                />
                <a-avatar v-else :src="record.agentAvatar" :size="22">
                  {{ (record.agentName ?? 'A').charAt(0) }}
                </a-avatar>
                <span>{{ record.agentName || `智能体 ${record.data.memberAgentId}` }}</span>
              </div>
            </template>
            <template v-else-if="column.key === 'createTime'">{{ formatDateTime(record.data.createTime) }}</template>
            <template v-else-if="column.key === 'action'">
              <a-popconfirm title="移出该成员？" ok-text="移出" cancel-text="取消" @confirm="removeMember(record.id)">
                <a-button type="link" size="small" danger>移出</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="conversations" tab="会话">
        <div class="conv-filters">
          <a-input v-model:value="convFilter.ownerName" placeholder="用户名" class="adm-filter-item" allow-clear @press-enter="loadConversations" />
          <a-date-picker v-model:value="convFilter.startDate" placeholder="开始日期" value-format="YYYY-MM-DD" class="adm-filter-item" @change="loadConversations" />
          <a-date-picker v-model:value="convFilter.endDate" placeholder="结束日期" value-format="YYYY-MM-DD" class="adm-filter-item" @change="loadConversations" />
          <a-button @click="loadConversations">查询</a-button>
        </div>
        <a-table
          :data-source="conversations"
          :columns="convColumns"
          row-key="id"
          size="small"
          :loading="tabLoading"
          :pagination="{ pageSize: 8 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">
              <div class="conv-title-cell">{{ record.data.title || '未命名会话' }}</div>
              <span class="adm-cell-muted">{{ record.data.conversationId }}</span>
            </template>
            <template v-else-if="column.key === 'user'">{{ record.userName || record.data.userId }}</template>
            <template v-else-if="column.key === 'updateTime'">{{ formatDateTime(record.data.updateTime) }}</template>
            <template v-else-if="column.key === 'action'">
              <a-space :size="0">
                <a-button type="link" size="small" @click="openShareView(record.data)">查看</a-button>
                <a-popconfirm title="删除该会话？" ok-text="删除" cancel-text="取消" @confirm="deleteConv(record.data.conversationId)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- ============ 表单：新增 / 编辑 ============ -->
    <a-form v-else :model="form" layout="vertical">
      <a-form-item label="名称" required>
        <a-input v-model:value="form.name" :maxlength="100" />
      </a-form-item>
      <a-form-item label="类型">
        <a-radio-group v-model:value="form.type" :disabled="!isCreate">
          <a-radio :value="1">智能体</a-radio>
          <a-radio :value="2">群聊</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model:value="form.status">
          <a-select-option :value="0" :disabled="!isCreate && originalStatus !== 0">草稿</a-select-option>
          <a-select-option :value="1">已发布</a-select-option>
          <a-select-option :value="2">禁用</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="公开">
        <a-radio-group v-model:value="form.isPublic">
          <a-radio :value="0">私有</a-radio>
          <a-radio :value="1">公开</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="图标">
        <div class="icon-field">
          <BotIcon v-if="iconPreview" :shape="iconPreview.shape" :fill="iconPreview.fill" :size="40" :live="false" />
          <span v-else class="adm-cell-muted">未设置（使用头像）</span>
          <a-button @click="iconDesignerOpen = true">设计图标</a-button>
        </div>
      </a-form-item>
      <a-form-item label="头像 URL">
        <a-input v-model:value="form.avatar" :maxlength="500" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model:value="form.description" :rows="3" :maxlength="2000" />
      </a-form-item>
      <a-form-item label="系统提示词">
        <a-textarea v-model:value="form.systemPrompt" :rows="8" :maxlength="10000" />
      </a-form-item>
    </a-form>

    <template v-if="mode === 'form'" #footer>
      <a-space style="display: flex; justify-content: flex-end">
        <a-button @click="$emit('update:open', false)">取消</a-button>
        <a-button type="primary" :loading="submitting" @click="handleSubmit">保存</a-button>
      </a-space>
    </template>

    <!-- 图标设计器（独立窗口） -->
    <a-modal v-model:open="iconDesignerOpen" title="设计图标" :width="420" :footer="null" destroy-on-close>
      <BotIconDesigner :config="designerConfig" @change="designerConfig = $event" />
      <div style="display: flex; justify-content: flex-end; margin-top: 12px">
        <a-button type="primary" @click="applyIcon">使用该图标</a-button>
      </div>
    </a-modal>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import MioMessage from '@/utils/message'
import { formatDateTime } from '@/utils/format'
import { createAgent, updateAgent } from '@/api/adminAgent'
import { deleteRuntime, searchRuntime, addGroupMember } from '@/api/adminRuntime'
import { searchConversations, deleteConversation } from '@/api/adminConversation'
import type { Agent } from '@/types'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import BotIconDesigner from '@/components/bot-icon/BotIconDesigner.vue'
import { parseBotIcon, serializeBotIcon, DEFAULT_BOT_ICON, type BotIconConfig } from '@/components/bot-icon/types'
import type { AdminRuntimeRow } from '@/api/adminRuntime'
import type { ConversationRow } from '@/api/adminConversation'

const props = defineProps<{
  open: boolean
  mode: 'detail' | 'form'
  record: Agent | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'saved'): void
}>()

const isCreate = computed(() => props.mode === 'form' && props.record === null)
const drawerTitle = computed(() =>
  props.mode === 'detail' ? '智能体详情' : isCreate.value ? '新增智能体' : '编辑智能体'
)
const originalStatus = ref(0)

const form = reactive({
  name: '',
  type: 1,
  status: 0,
  isPublic: 0,
  avatar: '',
  icon: '',
  description: '',
  systemPrompt: ''
})
const submitting = ref(false)

const iconPreview = computed(() => parseBotIcon(form.icon))
const designerConfig = ref<BotIconConfig>({ ...DEFAULT_BOT_ICON })
const iconDesignerOpen = ref(false)

function typeText(type?: number) {
  return type === 2 ? '群聊' : type === 3 ? 'MioBot' : '智能体'
}

function statusText(status?: number) {
  return status === 1 ? '已发布' : status === 2 ? '禁用' : '草稿'
}

function applyIcon() {
  form.icon = serializeBotIcon(designerConfig.value)
  iconDesignerOpen.value = false
}

// ---------- 详情 tabs 数据 ----------
const detailTab = ref('info')
const tabLoading = ref(false)
const bindings = ref<{ knowledgeBases: { id: number; name: string }[]; mcpTools: { id: number; name: string }[]; skills: { id: number; name: string }[] }>({
  knowledgeBases: [],
  mcpTools: [],
  skills: []
})
const versions = ref<AdminRuntimeRow[]>([])
const members = ref<AdminRuntimeRow[]>([])
const memberToAdd = ref<number | undefined>(undefined)
const memberAdding = ref(false)
const memberCandidates = ref<{ label: string; value: number; icon: string | null; avatar: string | null }[]>([])
const conversations = ref<ConversationRow[]>([])
const convFilter = reactive({ ownerName: '', startDate: undefined as string | undefined, endDate: undefined as string | undefined })

const versionColumns = [
  { title: '版本', key: 'version', width: 80 },
  { title: '名称快照', key: 'name', ellipsis: true },
  { title: '回滚来源', key: 'rollbackFrom', width: 90 },
  { title: '发布时间', key: 'createTime', width: 150 },
  { title: '', key: 'action', width: 60 }
]
const memberColumns = [
  { title: '成员', key: 'member' },
  { title: '加入时间', key: 'createTime', width: 160 },
  { title: '', key: 'action', width: 70 }
]
const convColumns = [
  { title: '会话', key: 'title', ellipsis: true },
  { title: '用户', key: 'user', width: 120 },
  { title: '更新时间', key: 'updateTime', width: 150 },
  { title: '', key: 'action', width: 120 }
]

function filterAgentOption(input: string, option: { label: string }) {
  return option.label.toLowerCase().includes(input.toLowerCase())
}

/** bot-icon 配置解析（群成员与候选下拉共用） */
function iconConfig(raw?: string | null): BotIconConfig | null {
  return parseBotIcon(raw ?? null)
}

async function loadTabData(tab: string) {
  if (!props.record) return
  tabLoading.value = true
  try {
    if (tab === 'info') {
      const { getAgentDetail } = await import('@/api/adminAgent')
      const detail = await getAgentDetail(props.record.id)
      bindings.value.knowledgeBases = detail?.knowledgeBases ?? []
      bindings.value.mcpTools = detail?.mcpTools ?? []
      bindings.value.skills = detail?.skills ?? []
    } else if (tab === 'versions') {
      const res = await searchRuntime('versions', { current: 1, pageSize: 50, agentId: props.record.id })
      versions.value = res.records ?? []
    } else if (tab === 'members' && props.record.type === 2) {
      const res = await searchRuntime('group-members', { current: 1, pageSize: 50, groupAgentId: props.record.id })
      members.value = res.records ?? []
    } else if (tab === 'conversations') {
      await loadConversations()
    }
  } finally {
    tabLoading.value = false
  }
}

async function loadConversations() {
  if (!props.record) return
  tabLoading.value = true
  try {
    const res = await searchConversations({
      current: 1,
      pageSize: 50,
      agentId: props.record.id,
      ownerName: convFilter.ownerName || undefined,
      startDate: convFilter.startDate || undefined,
      endDate: convFilter.endDate || undefined
    })
    conversations.value = res.records ?? []
  } catch {
    MioMessage.error('获取会话失败')
  } finally {
    tabLoading.value = false
  }
}

/** 可加入的候选成员：全部普通智能体（type=1），排除已在群中的 */
async function loadMemberCandidates() {
  try {
    const { searchAgents } = await import('@/api/adminAgent')
    const inGroup = new Set(members.value.map(m => (m.data as { memberAgentId: number }).memberAgentId))
    const collected: { label: string; value: number; icon: string | null; avatar: string | null }[] = []
    for (let current = 1; current <= 20; current++) {
      const res = await searchAgents({ current, pageSize: 50, type: 1 })
      for (const a of res.records ?? []) {
        if (!inGroup.has(a.id)) {
          collected.push({ label: a.name, value: a.id, icon: a.icon ?? null, avatar: a.avatar ?? null })
        }
      }
      if (!res.total || collected.length + inGroup.size >= res.total) break
    }
    memberCandidates.value = collected
  } catch {
    memberCandidates.value = []
  }
}

async function addMember() {
  if (!props.record || !memberToAdd.value) return
  memberAdding.value = true
  try {
    await addGroupMember(props.record.id, memberToAdd.value)
    MioMessage.success('已添加')
    memberToAdd.value = undefined
    await loadTabData('members')
    await loadMemberCandidates()
  } catch {
    MioMessage.error('添加失败')
  } finally {
    memberAdding.value = false
  }
}

async function removeMember(id: number) {
  try {
    await deleteRuntime('group-members', id)
    MioMessage.success('已移出')
    await loadTabData('members')
    await loadMemberCandidates()
  } catch {
    MioMessage.error('移出失败')
  }
}

async function deleteVersion(id: number) {
  try {
    await deleteRuntime('versions', id)
    MioMessage.success('已删除')
    await loadTabData('versions')
  } catch {
    MioMessage.error('删除失败')
  }
}

function openShareView(conversation: { conversationId: string }) {
  if (!props.record) return
  window.open(`/share/${props.record.id}/${conversation.conversationId}`, '_blank')
}

async function deleteConv(conversationId: string) {
  try {
    await deleteConversation(conversationId)
    MioMessage.success('已删除')
    await loadConversations()
  } catch {
    MioMessage.error('删除失败')
  }
}

watch(
  () => props.open,
  open => {
    if (!open) return
    if (props.mode === 'detail') {
      detailTab.value = 'info'
      convFilter.ownerName = ''
      convFilter.startDate = undefined
      convFilter.endDate = undefined
      return
    }
    originalStatus.value = props.record?.status ?? 0
    form.name = props.record?.name ?? ''
    form.type = props.record?.type === 2 ? 2 : 1
    form.status = props.record?.status ?? 0
    form.isPublic = props.record?.isPublic ?? 0
    form.avatar = props.record?.avatar ?? ''
    form.icon = props.record?.icon ?? ''
    form.description = props.record?.description ?? ''
    form.systemPrompt = props.record?.systemPrompt ?? ''
  }
)

watch(detailTab, tab => {
  if (props.mode !== 'detail') return
  loadTabData(tab)
  if (tab === 'members') loadMemberCandidates()
})

watch(
  () => [props.open, props.mode] as const,
  ([open, mode]) => {
    if (open && mode === 'detail') {
      loadTabData('info')
    }
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
      await createAgent({ ...form })
      MioMessage.success('智能体已创建')
    } else {
      await updateAgent({ ...form, id: props.record!.id })
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
  max-height: 220px;
  overflow-y: auto;
}

.icon-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.member-add {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.conv-filters {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.cell-member {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $text-secondary;
  font-size: 13px;
}

.member-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.conv-title-cell {
  color: $text-dark;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bindings {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 14px;

  .binding-group {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;

    .binding-title {
      font-size: 13px;
      color: $text-muted;
      width: 76px;
      flex-shrink: 0;
    }

    .binding-tag {
      margin: 0;
    }
  }
}
</style>
