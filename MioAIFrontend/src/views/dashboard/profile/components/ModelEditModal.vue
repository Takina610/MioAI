<template>
  <a-modal
    :open="open"
    :title="editingIndex >= 0 ? '编辑模型' : '添加模型'"
    :width="560"
    :footer="null"
    @cancel="emit('update:open', false)"
  >
    <div class="smart-row">
      <span class="smart-label">
        智能配置
        <a-tooltip title="根据模型 ID 智能匹配推荐配置；手动修改某项后该项以手动为准">
          <QuestionCircleOutlined class="help-icon" />
        </a-tooltip>
      </span>
      <a-switch v-model:checked="smartConfig" size="small" />
      <span v-if="smartMatched" class="match-hint">已匹配到智能配置</span>
    </div>
    <a-form layout="vertical" class="model-edit-form">
      <a-form-item label="模型 ID" class="compact-item">
        <a-input v-model:value="form.id" placeholder="模型 ID" :maxlength="128" @press-enter="emitSave" />
      </a-form-item>
      <a-form-item label="上下文窗口" class="compact-item">
        <a-input-number
          :value="form.contextWindow"
          :placeholder="contextWindowPlaceholder"
          :min="0"
          style="width: 100%"
          @update:value="onContextInput"
        />
      </a-form-item>
      <a-form-item label="最大输出 Token" class="compact-item">
        <a-input-number
          :value="form.maxOutputTokens"
          :placeholder="maxOutputPlaceholder"
          :min="0"
          style="width: 100%"
          @update:value="onMaxOutputInput"
        />
      </a-form-item>

      <a-collapse ghost class="advanced">
        <a-collapse-panel key="adv" header="高级配置">
          <a-form-item label="输入类型" class="compact-item">
            <div class="check-group">
              <a-checkbox checked disabled>文本</a-checkbox>
              <a-checkbox v-model:checked="inputImage">图片</a-checkbox>
              <a-checkbox v-model:checked="inputVideo">视频</a-checkbox>
              <a-checkbox v-model:checked="inputPdf">PDF</a-checkbox>
            </div>
          </a-form-item>
          <a-form-item label="模型能力" class="compact-item">
            <div class="check-group">
              <a-checkbox v-model:checked="capStructured">结构化输出</a-checkbox>
              <a-checkbox v-model:checked="capWebSearch">原生联网搜索</a-checkbox>
              <a-checkbox v-model:checked="capSystemMessage">对话中系统消息</a-checkbox>
            </div>
          </a-form-item>
          <a-form-item>
            <template #label>
              <span>推理等级（从低到高，英文，如 disabled、low、high、max）</span>
            </template>
            <div class="level-editor">
              <span v-for="(lv, i) in form.reasoningLevels" :key="`${lv}-${i}`" class="level-chip">
                <template v-if="chipEditIndex === i">
                  <input
                    ref="editInputRef"
                    v-model="editingValue"
                    class="level-input"
                    :maxlength="32"
                    @blur="commitEdit"
                    @keydown.enter.prevent="commitEdit"
                    @keydown.esc.prevent="cancelEdit"
                  />
                </template>
                <template v-else>
                  <button type="button" class="level-value" @click="beginEdit(i)">{{ lv }}</button>
                  <button
                    type="button"
                    class="level-remove"
                    :disabled="form.reasoningLevels.length <= 1"
                    @click="removeLevel(i)"
                  >
                    <CloseOutlined />
                  </button>
                </template>
              </span>
              <span v-if="adding" class="level-chip">
                <input
                  ref="addInputRef"
                  v-model="editingValue"
                  class="level-input"
                  placeholder="disabled"
                  :maxlength="32"
                  @blur="commitAdd"
                  @keydown.enter.prevent="commitAdd"
                  @keydown.esc.prevent="cancelEdit"
                />
              </span>
              <button v-else type="button" class="level-add" aria-label="添加推理等级" @click="beginAdd">
                <PlusOutlined />
              </button>
            </div>
          </a-form-item>
        </a-collapse-panel>
      </a-collapse>

      <div class="form-footer">
        <a-button type="link" size="small" class="reset-link" @click="resetForm">重置表单</a-button>
        <div class="footer-actions">
          <a-button @click="emit('update:open', false)">取消</a-button>
          <a-button type="primary" :disabled="!form.id.trim()" @click="emitSave">保存</a-button>
        </div>
      </div>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { CloseOutlined, PlusOutlined, QuestionCircleOutlined } from '@ant-design/icons-vue'
import type { ModelEntry } from '@/types'
import { matchModelPreset } from '@/utils/modelPresets'

const props = defineProps<{
  open: boolean
  /** 编辑目标在 models 数组中的下标；-1=新增 */
  editingIndex: number
  /** 编辑目标的当前值 */
  editing?: ModelEntry | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'save', entry: ModelEntry, index: number): void
}>()

// 智能配置（zcode 语义）：推荐值以 placeholder 灰显，空值保存时采用推荐；
// 手动改过的字段转手动管理；关闭开关时空字段立即填入推荐值
const smartConfig = ref(true)
const manualContext = ref(false)
const manualMaxOutput = ref(false)
// reasoningLevels 恒为数组（保存时为空数组则不落该字段）
const form = reactive<ModelEntry & { reasoningLevels: string[] }>({ id: '', enabled: true, reasoningLevels: [] })

const preset = computed(() => matchModelPreset(form.id))
const smartMatched = computed(() => smartConfig.value && !!preset.value)
const contextWindowPlaceholder = computed(() =>
  smartConfig.value && !manualContext.value && preset.value ? String(preset.value.contextWindow) : 'token')
const maxOutputPlaceholder = computed(() =>
  smartConfig.value && !manualMaxOutput.value && preset.value?.maxOutputTokens ? String(preset.value.maxOutputTokens) : 'token')

function onContextInput(v: number | null): void {
  form.contextWindow = v ?? undefined
  if (v != null) manualContext.value = true
}

function onMaxOutputInput(v: number | null): void {
  form.maxOutputTokens = v ?? undefined
  if (v != null) manualMaxOutput.value = true
}

const inputImage = ref(false)
const inputVideo = ref(false)
const inputPdf = ref(false)
const capStructured = ref(false)
const capWebSearch = ref(false)
const capSystemMessage = ref(false)

// 推理等级 chip 编辑（zcode 同款：点击改、X 删、+ 增；数组顺序即低→高）
const chipEditIndex = ref<number | null>(null)
const adding = ref(false)
const editingValue = ref('')
const editInputRef = ref<HTMLInputElement | null>(null)
const addInputRef = ref<HTMLInputElement | null>(null)

watch(
  () => [props.open, props.editing] as const,
  ([open]) => {
    if (!open) return
    const src = props.editing
    form.id = src?.id ?? ''
    form.contextWindow = src?.contextWindow
    form.maxOutputTokens = src?.maxOutputTokens
    form.vision = src?.vision
    form.reasoningLevels = [...(src?.reasoningLevels ?? [])]
    form.enabled = src?.enabled !== false
    const inputs = src?.inputs ?? (src?.vision ? ['image'] : ['text'])
    inputImage.value = inputs.includes('image')
    inputVideo.value = inputs.includes('video')
    inputPdf.value = inputs.includes('pdf')
    const caps = src?.capabilities ?? []
    capStructured.value = caps.includes('structured_output')
    capWebSearch.value = caps.includes('web_search')
    capSystemMessage.value = caps.includes('system_message')
    // 编辑已有值的场景：视为手动配置（智能配置默认关）；新增走推荐接管
    smartConfig.value = !(src?.contextWindow || src?.maxOutputTokens)
    manualContext.value = !!src
    manualMaxOutput.value = !!src
    cancelEdit()
  }
)

// 智能配置开关：开启回推荐接管（清非手动已填值），关闭只补空的推荐输入（不以切换吞掉编辑）
watch(smartConfig, on => {
  if (on) {
    if (!manualContext.value) form.contextWindow = undefined
    if (!manualMaxOutput.value) form.maxOutputTokens = undefined
  } else {
    if (form.contextWindow == null && preset.value && !manualContext.value) {
      form.contextWindow = preset.value.contextWindow
    }
    if (form.maxOutputTokens == null && preset.value?.maxOutputTokens && !manualMaxOutput.value) {
      form.maxOutputTokens = preset.value.maxOutputTokens
    }
  }
})

async function focusEditingInput(): Promise<void> {
  await nextTick()
  ;(chipEditIndex.value !== null ? editInputRef.value : addInputRef.value)?.focus()
}

function beginEdit(index: number): void {
  adding.value = false
  chipEditIndex.value = index
  editingValue.value = form.reasoningLevels[index] ?? ''
  void focusEditingInput()
}

function beginAdd(): void {
  chipEditIndex.value = null
  adding.value = true
  editingValue.value = ''
  void focusEditingInput()
}

function cancelEdit(): void {
  chipEditIndex.value = null
  adding.value = false
  editingValue.value = ''
}

/** 提交编辑/新增：去重、去空；编辑原值未变则视为查看而非修改 */
function commitEdit(): void {
  if (chipEditIndex.value === null) return
  applyLevel(chipEditIndex.value)
}

function commitAdd(): void {
  if (!adding.value) return
  applyLevel(form.reasoningLevels.length)
}

function applyLevel(index: number): void {
  const value = editingValue.value.trim()
  const isExisting = index < form.reasoningLevels.length
  if (!value || form.reasoningLevels.some((lv, i) => i !== index && lv === value)
    || (isExisting && value === form.reasoningLevels[index])) {
    cancelEdit()
    return
  }
  if (isExisting) {
    form.reasoningLevels.splice(index, 1, value)
  } else {
    form.reasoningLevels.push(value)
  }
  cancelEdit()
}

function removeLevel(index: number): void {
  form.reasoningLevels.splice(index, 1)
}

function buildEntry(): ModelEntry {
  const inputs = ['text', ...(inputImage.value ? ['image'] : []), ...(inputVideo.value ? ['video'] : []), ...(inputPdf.value ? ['pdf'] : [])]
  const capabilities = [
    ...(capStructured.value ? ['structured_output'] : []),
    ...(capWebSearch.value ? ['web_search'] : []),
    ...(capSystemMessage.value ? ['system_message'] : [])
  ]
  return {
    id: form.id.trim(),
    contextWindow: form.contextWindow
      ?? (smartConfig.value && !manualContext.value ? preset.value?.contextWindow : undefined),
    maxOutputTokens: form.maxOutputTokens
      ?? (smartConfig.value && !manualMaxOutput.value ? preset.value?.maxOutputTokens : undefined),
    vision: inputImage.value || undefined,
    inputs,
    capabilities,
    reasoningLevels: form.reasoningLevels.length ? [...form.reasoningLevels] : undefined,
    enabled: form.enabled !== false
  }
}

function emitSave(): void {
  if (!form.id.trim()) return
  emit('save', buildEntry(), props.editingIndex)
}

function resetForm(): void {
  form.id = ''
  form.contextWindow = undefined
  form.maxOutputTokens = undefined
  form.vision = undefined
  form.reasoningLevels = []
  form.enabled = true
  inputImage.value = false
  inputVideo.value = false
  inputPdf.value = false
  capStructured.value = false
  capWebSearch.value = false
  capSystemMessage.value = false
  manualContext.value = false
  manualMaxOutput.value = false
  cancelEdit()
}
</script>

<style lang="scss" scoped>
.model-edit-form {
  user-select: none;

  // 智能配置行：紧凑、紧跟弹窗标题，与模型 ID 小间距（zcode 同款）
  .smart-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 2px 0 10px;

    .smart-label {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 14px;
      color: $text-dark;

      .help-icon {
        font-size: 12px;
        color: #b0aea5;
      }
    }

    .match-hint {
      font-size: 12px;
      color: #2f9e44;
    }
  }

  :deep(input),
  :deep(textarea) {
    user-select: text;
  }

  .compact-item {
    margin-bottom: 20px;

    &:deep(.ant-form-item-label) {
      padding-bottom: 6px;
    }

    &:deep(.ant-form-item-label) {
      padding-bottom: 6px;
    }
  }

  .smart-label {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;

    .smart-hint {
      font-size: 12px;
      font-weight: 400;
      color: #8c8a82;
    }
  }

  .check-group {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 16px;
  }

  // 推理等级 chips（zcode 同款）：点值改、X 删、+ 增；顺序即低→高
  .level-editor {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;

    .level-chip {
      display: inline-flex;
      align-items: center;
      height: 28px;
      border: 1px solid #e8e6dc;
      border-radius: 8px;
      background: #fff;
      padding-right: 2px;

      .level-value {
        border: none;
        background: transparent;
        padding: 0 8px;
        height: 26px;
        min-width: 24px;
        color: $text-dark;
        font-size: 13px;
        cursor: pointer;

        &:hover {
          color: $primary-color;
        }
      }

      .level-remove {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 20px;
        height: 20px;
        border: none;
        border-radius: 6px;
        background: transparent;
        color: #8c8a82;
        font-size: 10px;
        cursor: pointer;

        &:hover:not(:disabled) {
          color: #c0392b;
        }

        &:disabled {
          visibility: hidden;
        }
      }

      .level-input {
        width: 90px;
        height: 26px;
        border: none;
        outline: none;
        background: transparent;
        padding: 0 8px;
        font-size: 13px;
        color: $text-dark;
      }
    }

    .level-add {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border: 1px solid #e8e6dc;
      border-radius: 8px;
      background: #fff;
      color: #6e6b62;
      font-size: 11px;
      cursor: pointer;
      transition: all 0.15s;

      &:hover {
        border-color: $primary-color;
        color: $primary-color;
      }
    }
  }

  .advanced {
    margin: 0 -16px;

    :deep(.ant-collapse-header) {
      padding: 4px 16px !important;
      font-size: 13px;
      color: #6e6b62;
    }
  }

  .form-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .reset-link {
      padding: 0;
      font-size: 13px;
      color: #8c8a82;
    }

    .footer-actions {
      display: flex;
      gap: 8px;
    }
  }
}
</style>
