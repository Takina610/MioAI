<template>
  <div class="mio-bot-message">
    <!-- 状态行：进行中显示已工作时长（瞬态重试时附提示）；完成后显示总结行并可展开工作过程 -->
    <div v-if="isLoading" class="status-line running">
      <span>已工作 {{ elapsedText }}</span>
      <span v-if="retryNotice" class="retry-notice">{{ retryNotice }}</span>
    </div>
    <div
      v-else-if="processBlocks.length"
      class="status-line done"
      @click="processExpanded = !processExpanded"
    >
      <CheckCircleOutlined class="done-icon" />
      <span>已完成 · 用时 {{ durationText }}</span>
      <CaretRightOutlined :rotate="processExpanded ? 90 : 0" class="caret-icon" />
    </div>

    <!-- 工作过程（进行中顺着流式显示；完成后默认收起，点击状态行展开逐个查看）。
         页面在后台时浏览器抑制 CSS 过渡（会瞬收），故完成瞬间若页面隐藏则先保持展开，
         等用户切回页面可见时再播放收缩动画 -->
    <CollapseTransition :open="isLoading || processExpanded || holdProcessOpen" :seam-gap="PROCESS_SEAM_GAP">
      <div v-if="processBlocks.length" class="process-list">
        <template v-for="item in processBlocks" :key="item.index">
          <!-- 文本块（过程中的叙述） -->
          <MarkdownView
            v-if="item.block.type === 'text'"
            class="answer-content"
            :content="item.block.text"
          />

          <!-- 思考块（zcode GUI 式）：收起=「图标 思考 · 摘要/时长」单行；展开=竖线内容区（与图标对齐），上方不再重复摘要 -->
          <div v-else-if="item.block.type === 'thinking'" class="thinking-block">
            <div class="thinking-bar" @click="toggleThinking(item.index)">
              <BrainIcon :size="13" class="think-icon" :class="{ active: isActiveThinking(item.index) }" />
              <span class="thinking-label" :class="{ running: isActiveThinking(item.index) }">{{ isActiveThinking(item.index) ? '正在思考' : '思考' }}</span>
              <span
                v-if="isActiveThinking(item.index) && !isThinkingExpanded(item.index)"
                class="thinking-live"
              >· {{ thinkingTail(item.block) }}</span>
              <span v-else-if="!isActiveThinking(item.index)" class="thinking-label">· {{ durationSuffix(item.block) }}</span>
              <CaretRightOutlined :rotate="isThinkingExpanded(item.index) ? 90 : 0" class="caret-icon" />
            </div>
            <CollapseTransition :open="isThinkingExpanded(item.index)">
              <div class="thinking-text">{{ item.block.text }}</div>
            </CollapseTransition>
          </div>

          <!-- 问答块（AskUserQuestion）：选项卡片，作答提交后锁定展示所选 -->
          <div
            v-else-if="item.block.type === 'question'"
            class="question-block"
            :class="{ answered: questionDone(item.block) }"
          >
            <div v-for="(q, qi) in item.block.questions" :key="qi" class="question-item">
              <div class="question-head">
                <span class="question-chip">{{ q.header }}</span>
                <span class="question-text">{{ q.question }}</span>
              </div>
              <template v-if="!questionDone(item.block)">
                <div
                  v-for="(opt, oi) in q.options"
                  :key="oi"
                  class="question-option"
                  :class="{ selected: optionSelected(item.block, qi, optionKey(opt, oi)) }"
                  @click="toggleOption(item.block, qi, optionKey(opt, oi))"
                >
                  <span class="option-check">{{ optionSelected(item.block, qi, optionKey(opt, oi)) ? '●' : '○' }}</span>
                  <span v-if="optionKey(opt, oi)" class="option-key">{{ optionKey(opt, oi) }}</span>
                  <span class="option-label">{{ opt.label }}</span>
                  <span v-if="opt.description" class="option-desc">{{ opt.description }}</span>
                </div>
                <pre v-if="previewOf(q, item.block, qi)" class="option-preview">{{ previewOf(q, item.block, qi) }}</pre>
                <div
                  class="question-option option-other"
                  :class="{ selected: otherSelected(item.block, qi) }"
                  @click="focusOther(item.block, qi)"
                >
                  <span class="option-check">{{ otherSelected(item.block, qi) ? '●' : '○' }}</span>
                  <input
                    class="option-custom"
                    :value="customOf(item.block, qi)"
                    placeholder="其他（自定义回答，可引用选项标签如 A）"
                    @input="setCustom(item.block, qi, ($event.target as HTMLInputElement).value)"
                    @click.stop
                  />
                </div>
              </template>
              <div v-else class="question-answered">
                <CheckCircleOutlined class="answered-icon" />
                <span>{{ answeredText(item.block, qi) }}</span>
              </div>
            </div>
            <div v-if="!questionDone(item.block)" class="question-actions">
              <button class="question-submit" :disabled="!submittable(item.block) || submitting" @click="submitAnswers(item.block)">
                {{ submitting ? '提交中…' : '提交回答' }}
              </button>
            </div>
          </div>

          <!-- 工具块：语义化行（子智能体行点击打开右侧只读面板，其余点击展开执行结果） -->
          <ToolRow
            v-else-if="item.block.type === 'tool'"
            :block="item.block"
            :streaming="isLoading"
            @open="openSubagent(item.block, item.index)"
          />
        </template>
      </div>
    </CollapseTransition>

    <!-- 最终回答（最后一个过程块之后的文本；无过程块时渲染全部文本） -->
    <MarkdownView
      v-if="finalText"
      class="answer-content"
      :class="{ 'with-process': processBlocks.length > 0 }"
      :content="finalText"
    />

    <!-- 历史消息（无块信息）：直接渲染正文 -->
    <MarkdownView v-else-if="!processBlocks.length && content" class="answer-content" :content="content" />

    <!-- Agent 本轮产出文件（outputs 目录新文件，点击即下载） -->
    <AttachmentCards
      v-if="outputAttachments.length"
      :items="outputAttachments"
      variant="card"
      downloadable
      class="output-attachments"
    />

    <!-- 流式尾部加载动画 -->
    <div v-if="isLoading" class="stream-tail">
      <ZcodeSpinner :size="14" />
    </div>

    <div v-if="interrupted" class="stream-interrupted">连接中断，本条回答可能不完整</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import {
  CaretRightOutlined,
  CheckCircleOutlined
} from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import ZcodeSpinner from '@/components/ZcodeSpinner.vue'
import CollapseTransition from '@/components/CollapseTransition.vue'
import BrainIcon from '@/components/BrainIcon.vue'
import AttachmentCards from './AttachmentCards.vue'
import ToolRow from './ToolRow.vue'
import { messageAttachmentDisplays } from '../attachmentUtils'
import { answerQuestion } from '@/api/chat'
import { formatDuration, type ToolBlock } from '../toolDisplay'
import { isMirroredToolBlock } from '../subagentTranscript'
import type { AttachmentDisplay, MessageBlock, SubagentPanelTarget } from '@/types'

type ThinkingBlock = Extract<MessageBlock, { type: 'thinking' }>
type QuestionBlock = Extract<MessageBlock, { type: 'question' }>

interface Props {
  content: string
  blocks?: MessageBlock[]
  isLoading?: boolean
  /** 本条回复耗时（毫秒），usage/持久化提供） */
  durationMs?: number
  /** 消息创建时间（进行中据此计算已工作时长） */
  createTime?: Date
  /** 流式传输异常中断（界面提示回答可能不完整） */
  interrupted?: boolean
  /** 瞬态失败自动重试提示（后端 retry 事件，内容恢复即清除） */
  retryNotice?: string
  /** 所属会话 id（提交问答答案用） */
  chatId?: string
  /** 所属消息 id（打开子代理面板的定位键之一） */
  messageId?: string
  /** 当前显示的版本号（历史版本的块里也能打开子代理面板） */
  version?: number
}

const props = withDefaults(defineProps<Props>(), {
  blocks: () => [],
  isLoading: false,
  createTime: () => new Date(),
  version: 1
})

const emit = defineEmits<{
  /** 子智能体行被点击：页面打开该子代理的只读对话面板 */
  (e: 'openSubagent', target: SubagentPanelTarget): void
}>()

function openSubagent(block: ToolBlock, index: number): void {
  emit('openSubagent', {
    messageId: props.messageId ?? '',
    version: props.version,
    blockKey: block.id ? `id:${block.id}` : `idx:${index}`
  })
}

const processExpanded = ref(false)
const expandedThinking = ref<Set<number>>(new Set())

/** .mio-bot-message 的 flex gap：工作过程折叠层的接缝补偿（抵消 display 切换时 gap 的瞬移） */
const PROCESS_SEAM_GAP = 12

// 完成于后台标签页时暂缓收缩：等页面重新可见再收（动画才不会被浏览器吞掉）
const holdProcessOpen = ref(false)
let holdVisibleListener: (() => void) | null = null

watch(
  () => props.isLoading,
  (loading) => {
    if (!loading && typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      holdProcessOpen.value = true
      const onVisible = () => {
        if (document.visibilityState !== 'visible') return
        holdProcessOpen.value = false
        document.removeEventListener('visibilitychange', onVisible)
        holdVisibleListener = null
      }
      if (holdVisibleListener) {
        document.removeEventListener('visibilitychange', holdVisibleListener)
      }
      document.addEventListener('visibilitychange', onVisible)
      holdVisibleListener = onVisible
    }
  }
)

onUnmounted(() => {
  if (holdVisibleListener) {
    document.removeEventListener('visibilitychange', holdVisibleListener)
    holdVisibleListener = null
  }
})

/** 过程块 = 最后一个非文本块及其之前的全部（叙述/思考/工具），携带原 blocks 下标
 *  （思考块活跃判定与子代理面板定位键都按原下标）；其后的是最终回答。
 *  附件块不属过程：单独渲染在正文下方。子代理镜像工具行不进主消息流——
 *  它们归属右侧的子代理只读面板（子代理完成后其 Agent 行可点击查看） */
const lastNonTextIndex = computed(() => {
  for (let i = props.blocks.length - 1; i >= 0; i--) {
    const type = props.blocks[i].type
    if (type !== 'text' && type !== 'attachments') return i
  }
  return -1
})

const processBlocks = computed(() => {
  if (lastNonTextIndex.value < 0) return []
  const out: Array<{ block: MessageBlock; index: number }> = []
  for (let i = 0; i <= lastNonTextIndex.value; i++) {
    const block = props.blocks[i]
    if (block.type === 'attachments' || isMirroredToolBlock(block)) continue
    out.push({ block, index: i })
  }
  return out
})

const outputAttachments = computed<AttachmentDisplay[]>(() =>
  messageAttachmentDisplays(
    props.blocks
      .filter((b): b is Extract<MessageBlock, { type: 'attachments' }> => b.type === 'attachments')
      .filter(b => b.side === 'output')
      .flatMap(b => b.items)
  )
)

const finalText = computed(() => {
  if (!props.blocks.length) return ''
  const tail = lastNonTextIndex.value >= 0
    ? props.blocks.slice(lastNonTextIndex.value + 1)
    : props.blocks
  return tail.filter(b => b.type === 'text').map(b => b.text).join('')
})

// ---------- 问答块（AskUserQuestion）的草稿与提交 ----------

/** 每个问答块的作答草稿：按块 id 存各问题的已选标签与自定义文本 */
const questionDrafts = ref<
  Record<string, { selections: Record<number, string[]>; custom: Record<number, string> }>
>({})
const submitting = ref(false)
/** 本端已提交的块（SSE answered 广播到达前乐观锁定） */
const submittedIds = ref<Set<string>>(new Set())

function draftOf(block: QuestionBlock): { selections: Record<number, string[]>; custom: Record<number, string> } {
  if (!questionDrafts.value[block.id]) {
    questionDrafts.value[block.id] = { selections: {}, custom: {} }
  }
  return questionDrafts.value[block.id]
}

function questionDone(block: QuestionBlock): boolean {
  return block.status === 'answered' || submittedIds.value.has(block.id)
}

function optionSelected(block: QuestionBlock, questionIndex: number, label: string): boolean {
  return (draftOf(block).selections[questionIndex] ?? []).includes(label)
}

/** 选项作答键：优先模型给的 key，旧数据回退 label */
function optionKey(opt: QuestionBlock['questions'][number]['options'][number], index: number): string {
  return opt.key || opt.label || String(index + 1)
}

/** 单选：点击预置选项即唯一选中，并清空"其他" */
function toggleOption(block: QuestionBlock, questionIndex: number, key: string): void {
  const draft = draftOf(block)
  const current = draft.selections[questionIndex] ?? []
  draft.selections[questionIndex] = current.includes(key) ? [] : [key]
  if (draft.selections[questionIndex].length) {
    draft.custom[questionIndex] = ''
  }
}

/** "其他"是否为当前选中（有自定义文本且未选预置项） */
function otherSelected(block: QuestionBlock, questionIndex: number): boolean {
  const draft = draftOf(block)
  return !(draft.selections[questionIndex] ?? []).length && !!(draft.custom[questionIndex] ?? '').trim()
}

/** 点击"其他"行：聚焦输入框并即时成为唯一选中 */
function focusOther(block: QuestionBlock, questionIndex: number): void {
  draftOf(block).selections[questionIndex] = []
}

function customOf(block: QuestionBlock, questionIndex: number): string {
  return draftOf(block).custom[questionIndex] ?? ''
}

function setCustom(block: QuestionBlock, questionIndex: number, value: string): void {
  draftOf(block).custom[questionIndex] = value
  if (value.trim()) {
    // 输入自定义即选中"其他"：清除预置选项（单选互斥）
    draftOf(block).selections[questionIndex] = []
  }
}

/** 当前悬选选项的预览（单选显示已选项 preview；多选显示最近选中项） */
function previewOf(q: QuestionBlock['questions'][number], block: QuestionBlock, questionIndex: number): string | undefined {
  const selected = draftOf(block).selections[questionIndex] ?? []
  if (!selected.length) return undefined
  const option = q.options.find((opt, i) => selected.includes(opt.key || opt.label || String(i + 1)))
  return option?.preview
}

function submittable(block: QuestionBlock): boolean {
  return block.questions.some((_, qi) => {
    const draft = draftOf(block)
    return (draft.selections[qi] ?? []).length > 0 || (draft.custom[qi] ?? '').trim().length > 0
  })
}

async function submitAnswers(block: QuestionBlock): Promise<void> {
  if (!props.chatId || submitting.value) return
  submitting.value = true
  try {
    const answers = block.questions.map((_, qi) => {
      const draft = draftOf(block)
      return {
        index: qi,
        selections: draft.selections[qi] ?? [],
        custom: (draft.custom[qi] ?? '').trim() || undefined
      }
    })
    await answerQuestion(props.chatId, block.id, answers)
    submittedIds.value = new Set([...submittedIds.value, block.id])
  } catch (error) {
    console.error('提交回答失败:', error)
  } finally {
    submitting.value = false
  }
}

function answeredText(block: QuestionBlock, questionIndex: number): string {
  const answered = block.answers?.find(item => item.index === questionIndex)
  const fromBroadcast = answered?.selections ?? []
  if (fromBroadcast.length) return fromBroadcast.join('、')
  const draft = questionDrafts.value[block.id]
  const selections = draft?.selections[questionIndex] ?? []
  const custom = (draft?.custom[questionIndex] ?? '').trim()
  return [...selections, ...(custom ? [custom] : [])].join('、') || '未作答'
}

const durationText = computed(() =>
  props.durationMs ? formatDuration(props.durationMs) : '')

// 进行中：每秒跳动计算"已工作 X分Y秒"
const now = ref(Date.now())
let elapsedTimer: number | undefined
watch(
  () => props.isLoading,
  (loading) => {
    if (loading) {
      elapsedTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
    } else if (elapsedTimer !== undefined) {
      clearInterval(elapsedTimer)
      elapsedTimer = undefined
    }
  },
  { immediate: true }
)
onUnmounted(() => {
  if (elapsedTimer !== undefined) clearInterval(elapsedTimer)
})

const elapsedText = computed(() => formatDuration(now.value - props.createTime.getTime()))

// ---------- 思考块（zcode 式单行折叠条） ----------

/** 流式进行中的思考 = 消息末块且尚未收尾（换块/结束时会写入 durationMs） */
function isActiveThinking(index: number): boolean {
  const block = props.blocks[index]
  return props.isLoading && index === props.blocks.length - 1
    && block?.type === 'thinking' && block.durationMs == null
}

function isThinkingExpanded(index: number): boolean {
  return expandedThinking.value.has(index)
}

function toggleThinking(index: number): void {
  const next = new Set(expandedThinking.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  expandedThinking.value = next
}

/** 单行实时摘要：取思考文本的最后一段（最新内容），超长截头保尾 */
function thinkingTail(block: ThinkingBlock): string {
  const lines = block.text.split('\n').map(l => l.trim()).filter(Boolean)
  const tail = lines[lines.length - 1] ?? ''
  return tail.length > 90 ? '…' + tail.slice(-90) : tail
}

/** 收起态标签后缀：「持续了 X 秒」；不足 1 秒沿用 zcode 的「持续了几秒」 */
function durationSuffix(block: ThinkingBlock): string {
  if (block.durationMs == null) return ''
  const seconds = Math.round(block.durationMs / 1000)
  if (seconds < 1) return '持续了几秒'
  if (seconds < 60) return `持续了 ${seconds} 秒`
  return `持续了 ${formatDuration(block.durationMs)}`
}

</script>

<style lang="scss" scoped>
// zcode animated-gradient-text（styles.css）：运行态文案渐变扫光。
// 代替旋转图标——长期运行的工具行里旋转动画持续占用渲染资源，zcode 只让文字扫光
@keyframes gradient-flow {
  0% {
    background-position: 100% 0;
  }
  50% {
    background-position: 0% 0;
  }
  100% {
    background-position: 100% 0;
  }
}

.mio-bot-message {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

// 状态行（进行中计时 / 完成总结行）
.status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #8c8a82;
  user-select: none;

  .retry-notice {
    padding: 1px 8px;
    border-radius: 8px;
    background: #fff7e6;
    color: #d48806;
    font-size: 12px;
  }

  &.done {
    cursor: pointer;
    padding: 4px 8px;
    margin: 0 -8px;
    width: fit-content;
    border-radius: 8px;
    color: #5f5d55;
    transition: background 0.2s;

    &:hover {
      background: #f0ede4;
    }

    .done-icon {
      color: #00b42a;
      font-size: 14px;
    }

    .caret-icon {
      font-size: 11px;
    }
  }
}

.process-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.caret-icon {
  font-size: 11px;
  color: #8c8a82;
  transition: transform 0.2s;
}

// 思考块（zcode GUI 式：图标+「思考 · 摘要」单行；展开后竖线内容区与图标对齐）
.thinking-block {
  .thinking-bar {
    display: flex;
    align-items: center;
    gap: 6px;
    max-width: 100%;
    padding: 4px 10px;
    margin: 0 -10px;
    border-radius: 8px;
    cursor: pointer;
    user-select: none;
    color: #8c8a82;
    font-size: 13px;
    transition: background 0.2s;

    &:hover {
      background: #f0ede4;
    }

    .think-icon {
      flex-shrink: 0;
      color: #8c8a82;

      // 思考进行中：图标微呼吸提示活跃（加载动画只出现在消息下方）
      &.active {
        color: $primary-color;
        animation: think-pulse 1.6s ease-in-out infinite;
      }
    }

    .thinking-live {
      flex: 1;
      min-width: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .thinking-label {
      flex-shrink: 0;
      white-space: nowrap;

      // 思考进行中：「正在思考」文案扫光（与工具行动词同一套 zcode 动效）
      &.running {
        font-weight: 500;
        @include animated-gradient-text(#8c8a82, rgba(140,138,130, 0.25));
      }
    }
  }

  // 竖线从内容第一行贯穿到最后一行，x 位置与上方图标中心对齐（图标 13px → 中心 ≈ 6px）
  .thinking-text {
    margin: 4px 0 6px 6px;
    padding: 2px 0 4px 14px;
    border-left: 2px solid #e8e6dc;
    font-size: 13px;
    line-height: 1.65;
    color: #8c8a82;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 280px;
    overflow-y: auto;
    @include thin-scrollbar;
  }
}

@keyframes think-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

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

.answer-content {
  font-size: 14px;
  padding: 0 4px;
}

// Agent 产出附件区（正文下方，可下载）
.output-attachments {
  padding: 0 4px;
}

// 流式尾部加载动画
.stream-tail {
  display: flex;
  align-items: center;
  padding: 0 4px;
  user-select: none;
}

.stream-interrupted {
  font-size: 12px;
  color: #d48806;
}
</style>
