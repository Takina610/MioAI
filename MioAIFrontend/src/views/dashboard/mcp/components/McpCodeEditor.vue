<template>
  <div class="mcp-code-editor" :class="{ readonly }" ref="containerRef"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { EditorView, basicSetup } from 'codemirror'
import { EditorState, Compartment } from '@codemirror/state'
import { placeholder } from '@codemirror/view'
import { json, jsonParseLinter } from '@codemirror/lang-json'
import { linter } from '@codemirror/lint'
import { oneDark } from '@codemirror/theme-one-dark'

const props = withDefaults(defineProps<{
  modelValue: string
  /** 只读模式仍保留高亮，仅禁止编辑 */
  readonly?: boolean
  /** 编辑区最小高度(px) */
  minHeight?: number
  /** 编辑区最大高度(px)，超出内部滚动 */
  maxHeight?: number
  /** 内容为空时的占位文本 */
  placeholder?: string
}>(), {
  readonly: false,
  minHeight: 180,
  maxHeight: 420,
  placeholder: ''
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const containerRef = ref<HTMLElement | null>(null)
let view: EditorView | null = null
const editableComp = new Compartment()

/** 将编辑器内容格式化（ compact JSON 不换行时也能展开） */
function format(): void {
  if (!view) return
  try {
    const formatted = JSON.stringify(JSON.parse(view.state.doc.toString()), null, 2)
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: formatted }
    })
  } catch {
    // JSON 非法时交给 lint 标红，不在这里报错
  }
}

/** 光标移到第一个语法错误处（校验失败时定位） */
function focusFirstError(): void {
  if (!view) return
  const doc = view.state.doc
  try {
    JSON.parse(doc.toString())
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    const m = msg.match(/position (\d+)/i)
    const pos = m ? Number(m[1]) : 0
    view.dispatch({ selection: { anchor: Math.min(pos, doc.length) }, scrollIntoView: true })
    view.focus()
  }
}

defineExpose({ format, focusFirstError })

onMounted(() => {
  if (!containerRef.value) return
  const extensions = [
    basicSetup,
    json(),
    linter(props.readonly ? () => [] : jsonParseLinter()),
    oneDark,
    editableComp.of([
      EditorView.editable.of(!props.readonly),
      EditorState.readOnly.of(props.readonly)
    ]),
    EditorView.updateListener.of((update) => {
      if (update.docChanged && !props.readonly) {
        emit('update:modelValue', update.state.doc.toString())
      }
    }),
    EditorView.theme({
      '&': { fontSize: '13px' },
      '.cm-scroller': {
        fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
        lineHeight: '1.6'
      },
      '.cm-gutters': { cursor: 'default' }
    }),
    ...(props.placeholder ? [placeholder(props.placeholder)] : [])
  ]
  view = new EditorView({
    state: EditorState.create({ doc: props.modelValue, extensions }),
    parent: containerRef.value
  })
})

watch(() => props.modelValue, (value) => {
  if (!view) return
  const current = view.state.doc.toString()
  if (value !== current) {
    view.dispatch({ changes: { from: 0, to: current.length, insert: value } })
  }
})

watch(() => props.readonly, (value) => {
  if (!view) return
  view.dispatch({
    effects: editableComp.reconfigure([
      EditorView.editable.of(!value),
      EditorState.readOnly.of(value)
    ])
  })
})

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})
</script>

<style lang="scss" scoped>
.mcp-code-editor {
  border: 1px solid #d8d5cc;
  border-radius: 6px;
  overflow: hidden;

  :deep(.cm-editor) {
    min-height: v-bind('props.minHeight + "px"');
    max-height: v-bind('props.maxHeight + "px"');
    background: #1e1e1e;

    &.cm-focused {
      outline: none;
    }
  }

  :deep(.cm-scroller) {
    overflow: auto;
  }

  &.readonly {
    :deep(.cm-editor) {
      cursor: default;

      .cm-content {
        caret-color: transparent;
      }
    }
  }
}
</style>
