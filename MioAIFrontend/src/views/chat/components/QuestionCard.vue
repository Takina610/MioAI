<template>
  <!-- 问答块（AskUserQuestion）：选项卡片，作答提交后锁定展示所选。
       草稿/提交状态组件内部自持（每块独立），多端同步靠后端 answered 广播 -->
  <div class="question-block" :class="{ answered: questionDone }">
    <div v-for="(q, qi) in block.questions" :key="qi" class="question-item">
      <div class="question-head">
        <span class="question-chip">{{ q.header }}</span>
        <span class="question-text">{{ q.question }}</span>
      </div>
      <template v-if="!questionDone">
        <div
          v-for="(opt, oi) in q.options"
          :key="oi"
          class="question-option"
          :class="{ selected: optionSelected(qi, keyOf(opt, oi)) }"
          @click="toggleOption(qi, keyOf(opt, oi))"
        >
          <span class="option-check">{{ optionSelected(qi, keyOf(opt, oi)) ? '●' : '○' }}</span>
          <span v-if="keyOf(opt, oi)" class="option-key">{{ keyOf(opt, oi) }}</span>
          <span class="option-label">{{ opt.label }}</span>
          <span v-if="opt.description" class="option-desc">{{ opt.description }}</span>
        </div>
        <pre v-if="previewOf(q, qi)" class="option-preview">{{ previewOf(q, qi) }}</pre>
        <div
          class="question-option option-other"
          :class="{ selected: otherSelected(qi) }"
          @click="focusOther(qi)"
        >
          <span class="option-check">{{ otherSelected(qi) ? '●' : '○' }}</span>
          <input
            class="option-custom"
            :value="customOf(qi)"
            placeholder="其他（自定义回答，可引用选项标签如 A）"
            @input="setCustom(qi, ($event.target as HTMLInputElement).value)"
            @click.stop
          />
        </div>
      </template>
      <div v-else class="question-answered">
        <CheckCircleOutlined class="answered-icon" />
        <span>{{ answeredText(qi) }}</span>
      </div>
    </div>
    <div v-if="!questionDone" class="question-actions">
      <button class="question-submit" :disabled="!submittable || submitting" @click="submitAnswers">
        {{ submitting ? '提交中…' : '提交回答' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { CheckCircleOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { answerQuestion } from '@/api/chat'
import type { MessageBlock, QuestionAnswer, QuestionItem } from '@/types'

type QuestionBlockType = Extract<MessageBlock, { type: 'question' }>

const props = defineProps<{
  block: QuestionBlockType
  /** 所属会话 id（提交问答答案用） */
  chatId?: string
  /** 流式已结束但块仍 pending：门闸已随连接丢失，锁定为未作答不可再提交 */
  lockPending?: boolean
}>()

const questionDone = computed(() => props.block.status === 'answered' || submittedIds.value.has(props.block.id))

/** 每个问答块的作答草稿：按块 id 存各问题的已选标签与自定义文本 */
const drafts = reactive<Record<string, { selections: Record<number, string[]>; custom: Record<number, string> }>>({})
const submitting = ref(false)
/** 本端已提交的块（SSE answered 广播到达前乐观锁定） */
const submittedIds = ref<Set<string>>(new Set())

function draftOf(): { selections: Record<number, string[]>; custom: Record<number, string> } {
  if (!drafts[props.block.id]) {
    drafts[props.block.id] = { selections: {}, custom: {} }
  }
  return drafts[props.block.id]
}

/** 选项作答键：优先模型给的 key，旧数据回退 label */
function keyOf(opt: QuestionItem['options'][number], index: number): string {
  return opt.key || opt.label || String(index + 1)
}

function optionSelected(questionIndex: number, key: string): boolean {
  return (draftOf().selections[questionIndex] ?? []).includes(key)
}

/** 单选：点击预置选项即唯一选中，并清空"其他" */
function toggleOption(questionIndex: number, key: string): void {
  const draft = draftOf()
  const current = draft.selections[questionIndex] ?? []
  draft.selections[questionIndex] = current.includes(key) ? [] : [key]
  if (draft.selections[questionIndex].length) {
    draft.custom[questionIndex] = ''
  }
}

/** "其他"是否为当前选中（有自定义文本且未选预置项） */
function otherSelected(questionIndex: number): boolean {
  const draft = draftOf()
  return !(draft.selections[questionIndex] ?? []).length && !!(draft.custom[questionIndex] ?? '').trim()
}

function focusOther(questionIndex: number): void {
  draftOf().selections[questionIndex] = []
}

function customOf(questionIndex: number): string {
  return draftOf().custom[questionIndex] ?? ''
}

function setCustom(questionIndex: number, value: string): void {
  draftOf().custom[questionIndex] = value
  if (value.trim()) {
    // 输入自定义即选中"其他"：清除预置选项（单选互斥）
    draftOf().selections[questionIndex] = []
  }
}

/** 当前悬选选项的预览（单选显示已选项 preview；多选显示最近选中项） */
function previewOf(q: QuestionItem, questionIndex: number): string | undefined {
  const selected = draftOf().selections[questionIndex] ?? []
  if (!selected.length) return undefined
  const option = q.options.find((opt, i) => selected.includes(opt.key || opt.label || String(i + 1)))
  return option?.preview
}

const submittable = computed(() =>
  props.block.questions.some((_, qi) => {
    const draft = draftOf()
    return (draft.selections[qi] ?? []).length > 0 || (draft.custom[qi] ?? '').trim().length > 0
  })
)

async function submitAnswers(): Promise<void> {
  if (!props.chatId || submitting.value || props.lockPending) return
  submitting.value = true
  try {
    const answers = props.block.questions.map((_, qi) => {
      const draft = draftOf()
      return {
        index: qi,
        selections: draft.selections[qi] ?? [],
        custom: (draft.custom[qi] ?? '').trim() || undefined
      }
    })
    await answerQuestion(props.chatId, props.block.id, answers)
    submittedIds.value = new Set([...submittedIds.value, props.block.id])
  } catch (error) {
    console.error('提交回答失败:', error)
    message.error('提交回答失败，请重试')
  } finally {
    submitting.value = false
  }
}

function answeredText(questionIndex: number): string {
  const answered = props.block.answers?.find(item => item.index === questionIndex)
  const fromBroadcast = answered?.selections ?? []
  if (fromBroadcast.length) return fromBroadcast.join('、')
  const draft = drafts[props.block.id]
  const selections = draft?.selections[questionIndex] ?? []
  const custom = (draft?.custom[questionIndex] ?? '').trim()
  return [...selections, ...(custom ? [custom] : [])].join('、') || '未作答'
}
</script>

<style lang="scss" scoped>
// 问答块（AskUserQuestion）：待答=选项卡片；已答=所选摘录
.question-block {
  margin: 6px 0;
  padding: 10px 12px;
  border: 1px solid #e8e6dc;
  border-radius: 10px;
  background: #faf9f5;

  &.answered {
    background: #f3f6ee;
    border-color: #dbe7d3;
  }
}

.question-item {
  & + .question-item {
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px dashed #e8e6dc;
  }
}

.question-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.question-chip {
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(42, 161, 169, 0.1);
  color: $primary-color;
  font-size: 11px;
  line-height: 18px;
  white-space: nowrap;
}

.question-text {
  font-size: 13px;
  font-weight: 600;
  color: #141413;
}

.question-option {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 6px 10px;
  margin: 4px 0;
  border: 1px solid #e8e6dc;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  transition: border-color 0.15s, background 0.15s;

  &:hover {
    border-color: rgba(42, 161, 169, 0.45);
  }

  &.selected {
    border-color: $primary-color;
    background: rgba(42, 161, 169, 0.08);
  }
}

.option-key {
  padding: 0 7px;
  border: 1px solid rgba(42, 161, 169, 0.35);
  border-radius: 6px;
  color: $primary-color;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  flex-shrink: 0;
}

.option-check {
  font-size: 12px;
  color: $primary-color;
  flex-shrink: 0;
}

.question-option:not(.selected) .option-check {
  color: #b0aea5;
}

.option-label {
  font-size: 13px;
  color: #141413;
  white-space: nowrap;
}

.option-desc {
  font-size: 12px;
  color: #8c8a82;
}

.option-preview {
  margin: 4px 0;
  padding: 8px 10px;
  background: #f5f3ec;
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #5f5d55;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 180px;
  overflow-y: auto;
  @include thin-scrollbar;
}

.option-custom {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  padding: 2px 4px;
  font-size: 12px;
  outline: none;
  user-select: text;
  -webkit-user-select: text;

  &::placeholder {
    color: #b0aea5;
  }
}

.option-other {
  cursor: text;
}

.question-answered {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #141413;

  .answered-icon {
    color: #00b42a;
    font-size: 14px;
  }
}

.question-actions {
  margin-top: 8px;
  text-align: right;
}

.question-submit {
  padding: 5px 16px;
  border: none;
  border-radius: 8px;
  background: $primary-color;
  color: #fff;

  &:hover:not(:disabled) {
    background: darken($primary-color, 8%);
  }
  font-size: 12px;
  cursor: pointer;

  &:disabled {
    background: #b0aea5;
    cursor: not-allowed;
  }
}
</style>
