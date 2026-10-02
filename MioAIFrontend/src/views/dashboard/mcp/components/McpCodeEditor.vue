<template>
  <div class="mcp-code-editor">
    <div class="line-numbers" ref="lineNumbersRef">
      <div class="line-number" v-for="line in lineCount" :key="line">{{ line }}</div>
    </div>
    <textarea
      v-if="!readonly"
      :value="modelValue"
      @input="onInput"
      @scroll="syncScroll"
      ref="textareaRef"
      class="code-textarea"
    ></textarea>
    <pre v-else class="code-content">{{ modelValue }}</pre>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: string
  /** 只读模式展示 pre，编辑模式展示 textarea */
  readonly?: boolean
  /** 最少显示的行数 */
  minLines?: number
}>(), {
  readonly: false,
  minLines: 1
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const lineNumbersRef = ref<HTMLElement | null>(null)

const lineCount = computed(() => Math.max(props.modelValue.split('\n').length, props.minLines))

function onInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}

function syncScroll(): void {
  if (textareaRef.value && lineNumbersRef.value) {
    lineNumbersRef.value.scrollTop = textareaRef.value.scrollTop
  }
}
</script>

<style lang="scss" scoped>
.mcp-code-editor {
  display: flex;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  overflow: hidden;
  background: #1e1e1e;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;

  .line-numbers {
    background: #252526;
    color: #858585;
    padding: 8px 0;
    text-align: right;
    user-select: none;
    min-width: 40px;
    overflow-y: hidden;
    border-right: 1px solid #3c3c3c;
    flex-shrink: 0;

    .line-number {
      padding: 0 12px;
      line-height: 22px;
      font-size: 13px;
    }
  }

  .code-textarea,
  .code-content {
    flex: 1;
    background: #1e1e1e;
    color: #d4d4d4;
    margin: 0;
    padding: 8px 12px;
    font-family: inherit;
    font-size: 13px;
    line-height: 22px;
  }

  .code-textarea {
    border: none;
    outline: none;
    resize: none;

    &::placeholder {
      color: #6a6a6a;
    }

    &:focus {
      box-shadow: none;
    }
  }

  .code-content {
    overflow: auto;
    white-space: pre;
  }
}
</style>
