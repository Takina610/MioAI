<template>
  <div class="version-history">
    <div v-if="loading" class="version-loading">
      <a-spin size="small" />
    </div>
    <div v-else-if="versions.length === 0" class="version-empty">暂无版本</div>
    <div v-else class="version-list">
      <div v-for="ver in versions" :key="ver.id" class="version-row">
        <div class="version-main">
          <span class="version-no">{{ ver.version }}</span>
          <span v-if="ver.rollbackFrom" class="version-rollback">回滚自 {{ ver.rollbackFrom }}</span>
          <span class="version-time">{{ formatDateTime(ver.createTime) }}</span>
        </div>
        <div class="version-actions">
          <a-button type="link" size="small" @click="viewVersion(ver)">查看</a-button>
          <a-popconfirm
            title="回滚到该版本？当前配置将先快照为新版本"
            ok-text="回滚"
            cancel-text="取消"
            :ok-button-props="{ danger: false }"
            @confirm="handleRollback(ver)"
          >
            <a-button type="link" size="small" :loading="rollbackLoading === ver.id">回滚</a-button>
          </a-popconfirm>
        </div>
      </div>
    </div>

    <a-modal
      v-model:open="viewVisible"
      :title="`版本 ${viewing?.version || ''}`"
      :footer="null"
      width="640px"
      wrap-class-name="version-view-modal"
    >
      <template v-if="viewing">
        <div class="view-field">
          <span class="view-label">名称</span>
          <span>{{ viewing.name || '—' }}</span>
        </div>
        <div class="view-field">
          <span class="view-label">描述</span>
          <span>{{ viewing.description || '—' }}</span>
        </div>
        <div class="view-field">
          <span class="view-label">系统提示词</span>
          <pre class="view-prompt">{{ viewing.systemPrompt || '—' }}</pre>
        </div>
        <div class="view-field">
          <span class="view-label">开场白</span>
          <span>{{ viewing.openingMessage || '—' }}</span>
        </div>
        <div v-if="viewing.openingQuestions?.length" class="view-field">
          <span class="view-label">推荐问题</span>
          <div class="view-questions">
            <a-tag v-for="(q, i) in viewing.openingQuestions" :key="i" class="view-question-tag">{{ q }}</a-tag>
          </div>
        </div>
        <div class="view-field">
          <span class="view-label">温度</span>
          <span>{{ viewing.temperature != null ? viewing.temperature.toFixed(1) : '默认' }}</span>
        </div>
        <div class="view-field">
          <span class="view-label">绑定资源</span>
          <div v-if="viewing.bindings?.length" class="view-bindings">
            <div v-for="(b, i) in viewing.bindings" :key="i" class="view-binding-row">
              <span class="binding-type">{{ bindingTypeName(b.type) }}</span>
              <span class="binding-name" :class="{ deleted: b.targetDeleted }">
                {{ b.targetDeleted ? '已删除' : b.name }}
              </span>
            </div>
          </div>
          <span v-else>—</span>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getAgentVersions, rollbackAgentVersion } from '@/api/agent'
import { formatDateTime } from '@/utils/format'
import type { AgentVersion } from '@/types'

const props = defineProps<{
  agentId: number
}>()

const emit = defineEmits<{
  (e: 'rolled-back', version: string): void
}>()

const loading = ref(true)
const versions = ref<AgentVersion[]>([])
const rollbackLoading = ref<number | null>(null)
const viewVisible = ref(false)
const viewing = ref<AgentVersion | null>(null)

async function fetchVersions(): Promise<void> {
  loading.value = true
  try {
    versions.value = (await getAgentVersions(props.agentId)) || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function viewVersion(ver: AgentVersion): void {
  viewing.value = ver
  viewVisible.value = true
}

async function handleRollback(ver: AgentVersion): Promise<void> {
  try {
    rollbackLoading.value = ver.id
    const newVersion = await rollbackAgentVersion(props.agentId, ver.id)
    message.success(`已回滚并生成新版本 ${newVersion}`)
    await fetchVersions()
    emit('rolled-back', newVersion)
  } catch (e) {
    console.error(e)
    message.error('回滚失败')
  } finally {
    rollbackLoading.value = null
  }
}

function bindingTypeName(type: string): string {
  const names: Record<string, string> = { kb: '知识库', mcp: 'MCP', skill: '技能' }
  return names[type] || type
}

defineExpose({ refresh: fetchVersions })

onMounted(fetchVersions)
</script>

<style lang="scss" scoped>
.version-history {
  .version-loading {
    padding: 12px 0;
    text-align: center;
  }

  .version-empty {
    padding: 12px;
    font-size: 12px;
    color: #a5a29a;
    background: #f5f3ec;
    border-radius: 8px;
  }
}

.version-list {
  display: flex;
  flex-direction: column;
  max-height: 320px;
  overflow: auto;
}

.version-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
  border-bottom: 1px solid #f0eee6;

  &:last-child {
    border-bottom: none;
  }

  .version-main {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;

    .version-no {
      font-weight: 600;
      font-size: 13px;
      color: $primary-color;
      font-variant-numeric: tabular-nums;
    }

    .version-rollback {
      font-size: 11px;
      color: #8c8a82;
      background: #f5f3ec;
      padding: 1px 8px;
      border-radius: 9px;
    }

    .version-time {
      font-size: 12px;
      color: #8c8a82;
    }
  }

  .version-actions {
    flex-shrink: 0;
  }
}
</style>

<style lang="scss">
.version-view-modal {
  .view-field {
    margin-bottom: 12px;

    .view-label {
      display: block;
      font-size: 12px;
      font-weight: 600;
      color: #8c8a82;
      margin-bottom: 4px;
    }

    .view-prompt {
      margin: 0;
      padding: 10px 12px;
      background: #f5f3ec;
      border-radius: 8px;
      font-size: 12px;
      line-height: 1.7;
      white-space: pre-wrap;
      word-break: break-word;
      max-height: 260px;
      overflow: auto;
      font-family: inherit;
    }

    .view-questions {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;

      .view-question-tag {
        margin: 0;
        border-radius: 12px;
      }
    }

    .view-bindings {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .view-binding-row {
        display: flex;
        gap: 10px;
        font-size: 13px;

        .binding-type {
          color: #8c8a82;
          flex-shrink: 0;
          width: 48px;
        }

        .binding-name.deleted {
          color: #a5a29a;
        }
      }
    }
  }
}
</style>
