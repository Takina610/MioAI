<template>
  <div class="ui-form">
    <div v-for="(field, i) in fields" :key="i" class="ui-form-field">
      <label class="ui-form-label">
        {{ field.label }}
        <span v-if="field.required" class="ui-form-req">*</span>
      </label>
      <select v-if="field.type === 'select'" v-model="draft[i]" class="ui-form-input">
        <option value="" disabled>请选择</option>
        <option v-for="opt in field.options ?? []" :key="opt" :value="opt">{{ opt }}</option>
      </select>
      <textarea
        v-else-if="field.type === 'textarea'"
        v-model="draft[i]"
        class="ui-form-input"
        rows="3"
        :placeholder="field.placeholder"
      />
      <input
        v-else
        v-model="draft[i]"
        class="ui-form-input"
        :type="field.type === 'number' ? 'number' : 'text'"
        :placeholder="field.placeholder"
      >
    </div>
    <div class="ui-form-footer">
      <button class="ui-form-submit" :disabled="!submittable || submitted" @click="submit">
        {{ submitted ? '已提交' : submitLabel }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { MessageBlock, UiFormField } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()
const emit = defineEmits<{ (e: 'send', text: string): void }>()

const fields = computed<UiFormField[]>(() =>
  (props.block.fields ?? []).filter(f => f && f.label)
)
const submitLabel = computed(() => props.block.submitLabel || '提交')

/** 字段草稿按下标存值（select/text/number 为字符串） */
const draft = reactive<Record<number, string>>({})

const missingRequired = computed(() =>
  fields.value.some((f, i) => f.required && !(draft[i] ?? '').trim())
)
const anyFilled = computed(() =>
  fields.value.some((f, i) => (draft[i] ?? '').trim())
)
const submittable = computed(() => anyFilled.value && !missingRequired.value)

/** 提交后本地锁定（提交内容已成为对话里的用户消息，刷新后无需恢复锁定态） */
const submitted = ref(false)

function submit(): void {
  if (!submittable.value || submitted.value) return
  const lines: string[] = []
  for (let i = 0; i < fields.value.length; i++) {
    const value = (draft[i] ?? '').trim()
    if (value) lines.push(`- ${fields.value[i].label}：${value}`)
  }
  if (!lines.length) return
  submitted.value = true
  emit('send', `「${props.block.title || '表单'}」已提交：\n${lines.join('\n')}`)
}
</script>

<style scoped lang="scss">
.ui-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ui-form-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.ui-form-label {
  font-size: 13px;
  color: $text-secondary;
}

.ui-form-req {
  color: $primary-color;
}

.ui-form-input {
  padding: 7px 10px;
  background: #fff;
  border: 1px solid $border-warm;
  border-radius: 8px;
  font-size: 13px;
  color: $text-secondary;
  outline: none;
  transition: border-color 0.15s ease;

  &:focus {
    border-color: $primary-color;
  }
}

textarea.ui-form-input {
  resize: vertical;
  line-height: 1.6;
}

.ui-form-footer {
  display: flex;
  justify-content: flex-end;
}

.ui-form-submit {
  padding: 6px 18px;
  background: $primary-color;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  color: #fff;
  cursor: pointer;
  transition: opacity 0.15s ease;

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}
</style>
