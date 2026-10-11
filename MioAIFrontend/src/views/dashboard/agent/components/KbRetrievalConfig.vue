<template>
  <div class="kb-retrieval-config">
    <div class="config-title">
      检索参数
      <span class="config-sub">仅对该知识库的召回生效</span>
    </div>
    <a-spin :spinning="loading" size="small">
      <div class="form-grid">
        <div class="form-item">
          <label>召回数量</label>
          <a-input-number v-model:value="form.topK" :min="1" :max="50" size="small" />
        </div>
        <div class="form-item">
          <label>相似度阈值</label>
          <a-input-number v-model:value="form.threshold" :min="0" :max="1" :step="0.05" size="small" />
        </div>
        <div class="form-item">
          <label>检索方式</label>
          <a-select v-model:value="form.method" size="small" class="method-select">
            <a-select-option value="hybrid">混合检索</a-select-option>
            <a-select-option value="vector">向量检索</a-select-option>
          </a-select>
        </div>
        <div class="form-item">
          <label>启用重排</label>
          <a-switch v-model:checked="form.enableRerank" size="small" />
        </div>
        <div class="form-item" v-if="form.enableRerank">
          <label>重排保留</label>
          <a-input-number v-model:value="form.rerankTopN" :min="1" :max="20" size="small" />
        </div>
        <div class="form-item">
          <label>启用绑定</label>
          <a-switch v-model:checked="enabled" size="small" />
        </div>
      </div>
      <div class="actions">
        <a-button size="small" type="primary" :loading="saving" :disabled="!dirty" @click="save">
          保存
        </a-button>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  getAgentKbBindings,
  updateAgentKnowledgeBinding,
  type AgentKnowledgeBinding
} from '@/api/agentKnowledge'

const props = defineProps<{
  agentId: number
  kbId: number
}>()

const loading = ref(false)
const saving = ref(false)
const binding = ref<AgentKnowledgeBinding | null>(null)

const form = reactive({
  topK: 5,
  threshold: 0.7,
  method: 'hybrid' as 'vector' | 'hybrid',
  enableRerank: false,
  rerankTopN: 5
})
const enabled = ref(1)

const snapshot = ref('')

const dirty = computed(() => {
  const current = JSON.stringify({
    topK: form.topK,
    threshold: form.threshold,
    method: form.method,
    enableRerank: form.enableRerank,
    rerankTopN: form.rerankTopN,
    enabled: enabled.value
  })
  return current !== snapshot.value
})

function fillFromBinding(data: AgentKnowledgeBinding): void {
  form.topK = data.topK ?? 5
  form.threshold = data.threshold ?? 0.7
  form.method = data.method === 'vector' ? 'vector' : 'hybrid'
  form.enableRerank = !!data.enableRerank
  form.rerankTopN = data.rerankTopN ?? 5
  enabled.value = data.enabled ?? 1
  takeSnapshot()
}

function takeSnapshot(): void {
  snapshot.value = JSON.stringify({
    topK: form.topK,
    threshold: form.threshold,
    method: form.method,
    enableRerank: form.enableRerank,
    rerankTopN: form.rerankTopN,
    enabled: enabled.value
  })
}

onMounted(async () => {
  loading.value = true
  try {
    const bindings = await getAgentKbBindings(props.agentId)
    const hit = (bindings || []).find(b => b.kbId === props.kbId)
    if (hit) {
      binding.value = hit
      fillFromBinding(hit)
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})

async function save(): Promise<void> {
  if (!binding.value) {
    message.warning('绑定信息未加载')
    return
  }
  saving.value = true
  try {
    await updateAgentKnowledgeBinding(binding.value.id, {
      topK: form.topK,
      threshold: form.threshold,
      method: form.method,
      enableRerank: form.enableRerank,
      rerankTopN: form.rerankTopN,
      enabled: enabled.value ? 1 : 0
    })
    takeSnapshot()
    message.success('检索参数已保存')
  } catch (e) {
    console.error(e)
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.kb-retrieval-config {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px dashed #e0ddd2;
  border-radius: 8px;
  background: #faf9f5;

  .config-title {
    font-size: 12px;
    font-weight: 600;
    color: $text-dark;
    margin-bottom: 10px;

    .config-sub {
      font-weight: 400;
      color: #8c8a82;
      margin-left: 6px;
    }
  }

  .form-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 10px 16px;

    .form-item {
      display: flex;
      align-items: center;
      gap: 6px;

      label {
        font-size: 12px;
        color: $text-secondary;
        white-space: nowrap;
      }

      :deep(.ant-input-number-sm),
      :deep(.ant-select-sm) {
        width: 90px;
      }
    }
  }

  .actions {
    margin-top: 10px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
