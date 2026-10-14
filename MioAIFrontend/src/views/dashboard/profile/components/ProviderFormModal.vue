<template>
  <a-modal
    :open="open"
    :title="editing ? '编辑供应商' : '添加供应商'"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    :width="620"
    @ok="handleSave"
    @cancel="emit('update:open', false)"
  >
    <!-- 模板选择（zcode ProviderTemplatePicker 同款）：新增时先选模板一键预填，编辑时不显示 -->
    <div v-if="!editing" class="template-picker">
      <div v-for="group in templateGroups" :key="group.id" class="template-group">
        <div class="group-title">{{ group.id }}</div>
        <div class="template-grid">
          <button
            v-for="tpl in group.templates"
            :key="tpl.id"
            type="button"
            class="template-card"
            :class="{ active: appliedTemplateId === tpl.id }"
            @click="applyTemplate(tpl)"
          >
            <span class="tpl-name">{{ tpl.name }}</span>
            <span class="tpl-meta">{{ tpl.models.length }} 个模型</span>
          </button>
        </div>
      </div>
      <div class="template-custom">
        <a-button size="small" :type="appliedTemplateId ? 'default' : 'primary'" @click="applyCustom">
          {{ appliedTemplateId ? '改为自定义配置' : '自定义供应商' }}
        </a-button>
      </div>
    </div>
    <a-form layout="vertical" class="provider-form">
      <a-form-item label="名称" class="compact-item">
        <a-input v-model:value="form.name" placeholder="例如 42x 网关" :maxlength="64" />
      </a-form-item>
      <a-form-item label="BaseURL" class="compact-item">
        <a-input v-model:value="form.baseUrl" placeholder="https://api.example.com" :maxlength="512" />
      </a-form-item>
      <a-form-item label="API 格式" class="compact-item">
        <a-select v-model:value="form.apiFormat" :options="formatOptions" />
      </a-form-item>
      <a-form-item label="API Key" class="compact-item">
        <a-input-password v-model:value="form.apiKey" placeholder="sk-…" :maxlength="512" autocomplete="off" />
      </a-form-item>

      <a-form-item label="模型列表" class="compact-item models-item">
        <div class="models-toolbar">
          <a-button size="small" @click="openAddModel">
            <PlusOutlined /> 添加模型
          </a-button>
        </div>
        <div class="model-rows">
          <div v-for="(m, i) in form.models" :key="m.id" class="model-row" :class="{ disabled: !m.enabled }">
            <span class="model-id">{{ m.id }}</span>
            <span v-if="badge(m)" class="mini-badge">{{ badge(m) }}</span>
            <span v-if="isVisionModel(m)" class="mini-badge vision">视觉</span>
            <div class="row-actions">
              <a-tooltip :title="testState[i]?.message || '测试连通性'">
                <button
                  type="button"
                  class="row-btn"
                  :class="{ ok: testState[i]?.ok === true, fail: testState[i]?.ok === false }"
                  :disabled="testLoading[i] === true"
                  @click="testModel(i)"
                >
                  <LoadingOutlined v-if="testLoading[i]" />
                  <ApiOutlined v-else />
                </button>
              </a-tooltip>
              <a-tooltip title="编辑">
                <EditOutlined class="row-btn" @click="openEditModel(i)" />
              </a-tooltip>
              <a-tooltip title="删除">
                <DeleteOutlined class="row-btn danger" @click="removeModel(i)" />
              </a-tooltip>
              <a-tooltip :title="m.enabled ? '聊天页可选' : '聊天页隐藏'">
                <a-switch
                  :checked="m.enabled"
                  size="small"
                  @change="(checked: any) => toggleEnabled(i, !!checked)"
                />
              </a-tooltip>
            </div>
          </div>
          <span v-if="!form.models.length" class="models-empty">暂无模型，点击「添加模型」配置</span>
        </div>
      </a-form-item>
    </a-form>

    <ModelEditModal
      v-model:open="editModalOpen"
      :editing-index="editingModelIndex"
      :editing="editingModel"
      @save="saveModelEntry"
    />
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { ApiOutlined, DeleteOutlined, EditOutlined, LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { testProviderModel } from '@/api/modelSetting'
import { addLocalProvider, refreshModelCatalog, updateLocalProvider } from '@/composables/useModelCatalog'
import { formatTokenBadge, isVisionModel } from '@/utils/modelPresets'
import { PROVIDER_TEMPLATES, PROVIDER_TEMPLATE_GROUPS, type ProviderTemplate } from '@/utils/providerTemplates'
import { API_FORMAT_LABELS, type ApiFormat, type ConnectivityTestResult, type ModelEntry, type ModelProvider } from '@/types'
import ModelEditModal from './ModelEditModal.vue'

const formatOptions = (Object.keys(API_FORMAT_LABELS) as Array<keyof typeof API_FORMAT_LABELS>)
  .map(value => ({ value, label: API_FORMAT_LABELS[value] }))

// 模板选择（zcode 同款）：分组渲染，点卡片一键预填名称/端点/协议/模型清单
const templateGroups = PROVIDER_TEMPLATE_GROUPS.map(id => ({
  id,
  templates: PROVIDER_TEMPLATES.filter(t => t.group === id),
}))
const appliedTemplateId = ref<string>('')
const templateLocked = ref(false)

function applyTemplate(tpl: ProviderTemplate): void {
  appliedTemplateId.value = tpl.id
  form.name = tpl.name
  form.baseUrl = tpl.baseUrl
  form.apiFormat = tpl.apiFormat
  form.models = tpl.models.map(md => ({ ...md }))
}

function applyCustom(): void {
  appliedTemplateId.value = ''
  form.name = ''
  form.baseUrl = ''
  form.apiFormat = 'openai'
  form.models = []
}

const props = defineProps<{
  open: boolean
  /** 编辑目标；null=新增 */
  editing?: ModelProvider | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'saved', provider: ModelProvider): void
}>()

const saving = ref(false)
const form = reactive<{
  name: string
  baseUrl: string
  apiFormat: keyof typeof API_FORMAT_LABELS
  apiKey: string
  models: ModelEntry[]
}>({ name: '', baseUrl: '', apiFormat: 'openai', apiKey: '', models: [] })

const editModalOpen = ref(false)
const editingModelIndex = ref(-1)
const editingModel = ref<ModelEntry | null>(null)
const testLoading = ref<Record<number, boolean>>({})
const testState = ref<Record<number, ConnectivityTestResult | undefined>>({})

watch(
  () => [props.open, props.editing] as const,
  ([open]) => {
    if (!open) return
    const src = props.editing
    form.name = src?.name ?? ''
    form.baseUrl = src?.baseUrl ?? ''
    form.apiFormat = (src?.apiFormat as keyof typeof API_FORMAT_LABELS) ?? 'openai'
    form.apiKey = src?.apiKey ?? ''
    form.models = (src?.models ?? []).map(m => ({ ...m }))
    appliedTemplateId.value = ''
    testLoading.value = {}
    testState.value = {}
  }
)

function badge(m: ModelEntry): string | null {
  return formatTokenBadge(m.contextWindow)
}

function openAddModel(): void {
  editingModelIndex.value = -1
  editingModel.value = null
  editModalOpen.value = true
}

function openEditModel(index: number): void {
  editingModelIndex.value = index
  editingModel.value = form.models[index] ?? null
  editModalOpen.value = true
}

function saveModelEntry(entry: ModelEntry, index: number): void {
  if (index >= 0) {
    form.models.splice(index, 1, entry)
  } else if (form.models.some(m => m.id === entry.id)) {
    message.warning('该模型 ID 已存在')
    return
  } else {
    form.models.push(entry)
  }
  editModalOpen.value = false
}

function removeModel(index: number): void {
  form.models.splice(index, 1)
}

function toggleEnabled(index: number, enabled: boolean): void {
  form.models[index]!.enabled = enabled
}

/** 按当前表单端点配置测试该模型连通性（无需先保存供应商） */
async function testModel(index: number): Promise<void> {
  const m = form.models[index]
  if (!m) return
  if (!form.baseUrl.trim()) {
    message.warning('请先填写 BaseURL')
    return
  }
  testLoading.value[index] = true
  try {
    const result = await testProviderModel({
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim(),
      apiFormat: form.apiFormat,
      model: m.id
    })
    testState.value[index] = result
    if (result.ok) {
      message.success(`${m.id} 连通正常（${result.latencyMs}ms）`)
    } else {
      message.error(`${m.id} ${result.message}`)
    }
  } catch (e) {
    console.error(e)
  } finally {
    testLoading.value[index] = false
  }
}

async function handleSave(): Promise<void> {
  if (!form.name.trim() || !form.baseUrl.trim()) {
    message.warning('请填写名称与 BaseURL')
    return
  }
  saving.value = true
  try {
    const payload = {
      id: props.editing?.id,
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      apiFormat: form.apiFormat,
      apiKey: form.apiKey.trim(),
      models: form.models.map(m => ({ ...m }))
    }
    // 供应商仅存浏览器本地（localStorage），不上传服务端
    if (payload.id) {
      updateLocalProvider({ ...payload, id: payload.id, userId: 0 })
    } else {
      addLocalProvider(payload)
    }
    message.success('已保存')
    await refreshModelCatalog()
    emit('saved', { ...payload, id: payload.id ?? 0, userId: 0 })
    emit('update:open', false)
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.template-picker {
  user-select: none;
  margin-bottom: 16px;

  .template-group {
    margin-bottom: 12px;

    .group-title {
      font-size: 12px;
      color: #8c8a82;
      margin-bottom: 6px;
    }
  }

  .template-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }

  .template-card {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    padding: 9px 12px;
    border: 1px solid #e8e6dc;
    border-radius: 10px;
    background: #fff;
    cursor: pointer;
    text-align: left;
    transition: all 0.15s;

    &:hover {
      border-color: $primary-color;
    }

    &.active {
      border-color: $primary-color;
      background: rgba(42, 161, 169, 0.08);
    }

    .tpl-name {
      font-size: 13px;
      font-weight: 500;
      color: $text-dark;
    }

    .tpl-meta {
      font-size: 11px;
      color: #8c8a82;
    }
  }

  .template-custom {
    display: flex;
    justify-content: flex-end;
    margin-top: 4px;
  }
}

.provider-form {
  // 弹窗正文整体禁止选中（输入控件内文本除外）
  user-select: none;

  :deep(input),
  :deep(textarea) {
    user-select: text;
  }

  .compact-item {
    margin-bottom: 20px;

    &:deep(.ant-form-item-label) {
      padding-bottom: 6px;
    }
  }

  .models-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 8px;
  }

  .model-rows {
    display: flex;
    flex-direction: column;
    border: 1px solid #ece9de;
    border-radius: 10px;

    .model-row {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;

      & + .model-row {
        border-top: 1px solid #f2f0e8;
      }

      &.disabled .model-id {
        color: #b0aea5;
        text-decoration: line-through;
      }

      .model-id {
        flex: 1;
        min-width: 0;
        font-size: 13px;
        color: $text-dark;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .mini-badge {
        flex-shrink: 0;
        padding: 1px 7px;
        border-radius: 999px;
        background: #f0ede4;
        color: #6e6b62;
        font-size: 11px;

        &.vision {
          background: rgba(42, 161, 169, 0.12);
          color: $primary-color;
        }
      }

      .row-actions {
        display: flex;
        align-items: center;
        gap: 10px;
        flex-shrink: 0;

        .row-btn {
          display: inline-flex;
          align-items: center;
          border: none;
          background: transparent;
          padding: 0;
          color: #6e6b62;
          font-size: 13px;
          cursor: pointer;
          transition: color 0.15s;

          &:hover:not(:disabled) {
            color: $primary-color;
          }

          &.danger:hover {
            color: #c0392b;
          }

          &.ok {
            color: #2f9e44;
          }

          &.fail {
            color: #c0392b;
          }

          &:disabled {
            cursor: default;
          }
        }
      }
    }

    .models-empty {
      padding: 10px 12px;
      font-size: 13px;
      color: #8c8a82;
    }
  }
}
</style>
