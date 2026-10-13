<template>
  <div class="mio-bot-message">
    <!-- 状态行：进行中显示已工作时长（MCP装配失败附提示）；用户中断显示"已停止"
         （工作过程保持可见，无折叠入口）；非群聊完成后显示总结行并可展开工作过程。
         群聊无"已完成"折叠行——全部内容按时间序内联，无过程/正文之分 -->
    <div v-if="isLoading" class="status-line running">
      <span>已工作 {{ elapsedText }}</span>
      <span v-if="mcpNotice" class="retry-notice">{{ mcpNotice }}</span>
    </div>
    <div v-else-if="stopped" class="status-line stopped">
      <StopOutlined class="stopped-icon" />
      <span>已停止</span>
    </div>
    <div
      v-else-if="hasProcessArea"
      class="status-line done"
      @click="processExpanded = !processExpanded"
    >
      <CheckCircleOutlined class="done-icon" />
      <span>已完成 · 用时 {{ durationText }}</span>
      <CaretRightOutlined :rotate="processExpanded ? 90 : 0" class="caret-icon" />
    </div>

    <!-- 群聊：知识库引用行固定置顶 -->
    <template v-if="groupMode">
      <ResourceRow
        v-for="(kbBlock, ki) in kbBlocks"
        :key="`group-kb-${ki}`"
        :block="kbBlock"
      />
    </template>

    <!-- 工作过程（非群聊：进行中顺着流式显示；被中断后保持展开且无折叠按钮；完成后默认收起，
         点击状态行展开逐个查看）。页面在后台时浏览器抑制 CSS 过渡（会瞬收），
         故完成瞬间若页面隐藏则先保持展开，等用户切回页面可见时再播放收缩动画 -->
    <CollapseTransition
      v-if="!groupMode"
      :open="isLoading || stopped || processExpanded || holdProcessOpen"
      :seam-gap="PROCESS_SEAM_GAP"
    >
      <div v-if="hasProcessArea && (processEverOpened || isLoading || stopped || holdProcessOpen)" class="process-list">
        <!-- 知识库引用行：回合收尾按回答实际引用生成，固定置顶（与块序无关） -->
        <ResourceRow
          v-for="(kbBlock, ki) in kbBlocks"
          :key="`kb-${ki}`"
          :block="kbBlock"
        />
        <template v-for="item in processBlocks" :key="item.index">
          <!-- 文本块（过程中的叙述） -->
          <MarkdownView
            v-if="item.block.type === 'text'"
            class="answer-content"
            :content="item.block.text"
          />
          <ThinkingBlock
            v-else-if="item.block.type === 'thinking'"
            :block="item.block"
            :active="isActiveThinking(item.index)"
          />
          <!-- 问答块（AskUserQuestion）：选项卡片，作答提交后锁定展示所选 -->
          <QuestionCard
            v-else-if="item.block.type === 'question'"
            :block="item.block"
            :chat-id="chatId"
          />
          <!-- 工具块：语义化行（子智能体行点击打开右侧只读面板，其余点击展开执行结果） -->
          <ToolRow
            v-else-if="item.block.type === 'tool'"
            :block="item.block"
            :streaming="isLoading"
            @open="openSubagent(item.block, item.index)"
          />
          <!-- 技能行（zcode 式）：模型按需读取技能文件，展开看 SKILL.md 内容 -->
          <SkillRow
            v-else-if="item.block.type === 'skill_use'"
            :block="item.block"
          />
          <!-- 重连记录行：随时间出现在原位置；进行中带扫光，内容恢复后定格为静态 -->
          <div
            v-else-if="item.block.type === 'retry'"
            class="retry-line"
            :class="{ active: item.block.active }"
          >{{ item.block.label }}</div>
          <!-- 旧数据兼容：MCP 装配摘要/技能清单行（新回合不再产生，置顶的 kb 行在上方渲染） -->
          <ResourceRow
            v-else-if="item.block.type === 'mcp' || item.block.type === 'skill'"
            :block="item.block"
          />
        </template>
      </div>
    </CollapseTransition>

    <!-- 正文流式期间出现的重连行（非群聊）：渲染在过程区与正文之间（内容停在何处，重连就出现在何处） -->
    <div
      v-for="(line, ri) in trailingRetryBlocks"
      :key="`retry-trailing-${ri}`"
      class="retry-line"
      :class="{ active: line.active }"
    >{{ line.label }}</div>

    <!-- 回答区。非群聊：最后过程块之后的 text/member/ui 块按序混合渲染（无过程块时渲染全部）。
         群聊：全部块按时间序内联（群主正文=左对齐署名气泡、成员=成员气泡、思考/工具行
         原位折叠单行）——成员消息发出后，群主后续的思考/工具出现在其下方，严格时序 -->
    <template v-if="displaySegments.length">
      <template v-for="(seg, si) in displaySegments" :key="`seg-${si}`">
        <!-- 群主正文：左对齐群头像 + 米色气泡（与成员白底气泡区分；右侧只属于用户） -->
        <div v-if="seg.kind === 'text' && groupMode" class="group-own-row">
          <GroupAvatar v-if="groupMembers?.length" class="group-own-avatar" :members="groupMembers" :size="30" />
          <div class="group-own-bubble">
            <MarkdownView :content="seg.text" />
          </div>
        </div>
        <MarkdownView
          v-else-if="seg.kind === 'text'"
          class="answer-content"
          :class="{ 'with-process': processBlocks.length > 0 }"
          :content="seg.text"
        />
        <MemberBubble v-else-if="seg.kind === 'member'" :block="seg.block" />
        <ThinkingBlock
          v-else-if="seg.kind === 'thinking'"
          :block="seg.block"
          :active="isActiveThinking(seg.index)"
        />
        <ToolRow
          v-else-if="seg.kind === 'tool'"
          :block="seg.block"
          :streaming="isLoading"
          @open="openSubagent(seg.block, seg.index)"
        />
        <SkillRow v-else-if="seg.kind === 'skill_use'" :block="seg.block" />
        <QuestionCard v-else-if="seg.kind === 'question'" :block="seg.block" :chat-id="chatId" />
        <div
          v-else-if="seg.kind === 'retry'"
          class="retry-line"
          :class="{ active: seg.block.active }"
        >{{ seg.block.label }}</div>
        <ResourceRow v-else-if="seg.kind === 'resource'" :block="seg.block" />
        <UiBlockRenderer v-else :block="seg.block" :chat-id="chatId" @send="emit('sendPrompt', $event)" />
      </template>
    </template>

    <!-- 历史消息（无块信息）：直接渲染正文（群聊包成群主气泡保持视觉一致） -->
    <template v-else-if="!processBlocks.length && content">
      <div v-if="groupMode" class="group-own-row">
        <GroupAvatar v-if="groupMembers?.length" class="group-own-avatar" :members="groupMembers" :size="30" />
        <div class="group-own-bubble">
          <MarkdownView :content="content" />
        </div>
      </div>
      <MarkdownView v-else class="answer-content" :content="content" />
    </template>

    <!-- render_ui 参数流式传输中的轻量占位 -->
    <div v-if="uiRendering && isLoading" class="ui-rendering-hint">
      <RoseFourLoader :size="16" />
      <span>正在生成界面…</span>
    </div>

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
      <RoseFourLoader :size="36" />
    </div>

    <div v-if="interrupted" class="stream-interrupted">连接中断，本条回答可能不完整</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import {
  CaretRightOutlined,
  CheckCircleOutlined,
  StopOutlined
} from '@ant-design/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import RoseFourLoader from '@/components/RoseFourLoader.vue'
import CollapseTransition from '@/components/CollapseTransition.vue'
import AttachmentCards from './AttachmentCards.vue'
import ToolRow from './ToolRow.vue'
import ResourceRow from './ResourceRow.vue'
import SkillRow from './SkillRow.vue'
import ThinkingBlock from './ThinkingBlock.vue'
import QuestionCard from './QuestionCard.vue'
import MemberBubble from './MemberBubble.vue'
import GroupAvatar from '@/components/GroupAvatar.vue'
import UiBlockRenderer from './ui/UiBlockRenderer.vue'
import { messageAttachmentDisplays } from '../attachmentUtils'
import { formatDuration, type ToolBlock } from '../toolDisplay'
import { isMirroredToolBlock } from '../subagentTranscript'
import type { AttachmentDisplay, MessageBlock, SubagentPanelTarget } from '@/types'

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
  /** 用户主动中断（状态行显示"已停止"，工作过程保持可见且无折叠入口） */
  stopped?: boolean
  /** 本轮 MCP 工具装配失败提示（后端 notice 事件，瞬态不落历史） */
  mcpNotice?: string
  /** 所属会话 id（提交问答答案用） */
  chatId?: string
  /** 所属消息 id（打开子代理面板的定位键之一） */
  messageId?: string
  /** 当前显示的版本号（历史版本的块里也能打开子代理面板） */
  version?: number
  /** 群聊模式：全部块按时间序内联渲染，群主正文为左对齐群头像气泡 */
  groupMode?: boolean
  /** 群聊成员概要（群主行的堆叠群头像） */
  groupMembers?: import('@/types').AgentMemberBrief[]
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
  /** 动态 UI 交互（actions 按钮/表单提交）：编排好的文本作为用户消息发送 */
  (e: 'sendPrompt', text: string): void
}>()

function openSubagent(block: ToolBlock, index: number): void {
  emit('openSubagent', {
    messageId: props.messageId ?? '',
    version: props.version,
    blockKey: block.id ? `id:${block.id}` : `idx:${index}`
  })
}

const processExpanded = ref(false)

/** .mio-bot-message 的 flex gap：工作过程折叠层的接缝补偿（抵消 display 切换时 gap 的瞬移） */
const PROCESS_SEAM_GAP = 12

// 完成于后台标签页时暂缓收缩：等页面重新可见再收（动画才不会被浏览器吞掉）
const holdProcessOpen = ref(false)
let holdVisibleListener: (() => void) | null = null

/** 过程区懒挂载：完成后默认收起（v-show 隐藏但子树仍挂载），长会话进入时
 *  全部历史工具行/结果一次性挂载造成进入顿挫——首次展开才挂内容，此后保持挂载
 *  （collapse 动画需要内容在场；流式/中断/后台暂缓态恒为展开，直接挂载。
 *  注意本 watch 须在 holdProcessOpen 声明之后，getter 立即求值有 TDZ） */
const processEverOpened = ref(false)
watch(
  () => props.isLoading || props.stopped || processExpanded.value || holdProcessOpen.value,
  (open) => { if (open) processEverOpened.value = true },
  { immediate: true }
)

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
 *  仅非群聊使用：群聊全文时序内联（inlineSegments），无过程/正文边界。
 *  动态 UI 块（ui）属回答内容：不参与过程边界判定，回答区内与文本按序混合渲染。
 *  附件块不属过程：单独渲染在正文下方。子代理镜像工具行不进主消息流——
 *  它们归属右侧的子代理只读面板（子代理完成后其 Agent 行可点击查看）。
 *  知识库引用行同样是上下文行：固定置顶渲染。重连行是状态行：不参与"过程/正文"
 *  边界判定（出现在正文流式期间的重连不能把正文劈成两段），过程区内的按原位置
 *  内联渲染，正文期间的出现为尾随行（渲染在过程区与正文之间） */
const lastNonTextIndex = computed(() => {
  for (let i = props.blocks.length - 1; i >= 0; i--) {
    const type = props.blocks[i].type
    if (type !== 'text' && type !== 'attachments' && type !== 'kb' && type !== 'retry'
      && type !== 'ui' && type !== 'member') return i
  }
  return -1
})

const processBlocks = computed(() => {
  if (props.groupMode || lastNonTextIndex.value < 0) return []
  const out: Array<{ block: MessageBlock; index: number }> = []
  for (let i = 0; i <= lastNonTextIndex.value; i++) {
    const block = props.blocks[i]
    if (block.type === 'attachments' || block.type === 'kb' || block.type === 'member'
      || isMirroredToolBlock(block)) continue
    // render_ui 的工具行不进过程区：其成品是回答区的 ui 块，过程行重复且只有原始 JSON
    if (block.type === 'tool' && block.tool === 'render_ui') continue
    // ui 块是回答内容：永不进过程区，全部按原序并入回答区（answerSegments）
    if (block.type === 'ui') continue
    out.push({ block, index: i })
  }
  return out
})

/** 正文流式期间出现的重连行（位于过程边界之后）：渲染在过程区与正文之间（仅非群聊） */
const trailingRetryBlocks = computed(() => {
  if (props.groupMode) return []
  const start = lastNonTextIndex.value + 1
  if (start <= 0) {
    // 无过程内容时：重连行从块首就可能出现（首个模型请求即失败）
    return props.blocks.filter((b): b is Extract<MessageBlock, { type: 'retry' }> => b.type === 'retry')
  }
  return props.blocks
    .slice(start)
    .filter((b): b is Extract<MessageBlock, { type: 'retry' }> => b.type === 'retry')
})

/** 知识库引用行（后端收尾按回答引用生成；旧数据在块首/块尾都兼容） */
const kbBlocks = computed(() =>
  props.blocks.filter((b): b is Extract<MessageBlock, { type: 'kb' }> => b.type === 'kb')
)

const hasProcessArea = computed(() =>
  !props.groupMode
  && (processBlocks.value.length > 0 || kbBlocks.value.length > 0 || trailingRetryBlocks.value.length > 0)
)

const outputAttachments = computed<AttachmentDisplay[]>(() =>
  messageAttachmentDisplays(
    props.blocks
      .filter((b): b is Extract<MessageBlock, { type: 'attachments' }> => b.type === 'attachments')
      .filter(b => b.side === 'output')
      .flatMap(b => b.items)
  )
)

/** render_ui 参数流式传输中：回答区末尾给轻量占位（参数校验完成后 ui 块瞬间落位） */
const uiRendering = computed(() =>
  props.blocks.some(b => b.type === 'tool' && b.tool === 'render_ui' && b.status === 'running')
)

// ---------- 回答区段（统一两模式的渲染管线） ----------

type ThinkingBlockType = Extract<MessageBlock, { type: 'thinking' }>
type QuestionBlockType = Extract<MessageBlock, { type: 'question' }>
type MemberBlockType = Extract<MessageBlock, { type: 'member' }>
type UiBlockType = Extract<MessageBlock, { type: 'ui' }>
type RetryBlockType = Extract<MessageBlock, { type: 'retry' }>
type ResourceBlockType = Extract<MessageBlock, { type: 'mcp' | 'skill' }>

type AnswerSegment =
  | { kind: 'text'; text: string }
  | { kind: 'member'; block: MemberBlockType }
  | { kind: 'ui'; block: UiBlockType }
  | { kind: 'thinking'; block: ThinkingBlockType; index: number }
  | { kind: 'tool'; block: ToolBlock; index: number }
  | { kind: 'skill_use'; block: Extract<MessageBlock, { type: 'skill_use' }>; index: number }
  | { kind: 'question'; block: QuestionBlockType; index: number }
  | { kind: 'retry'; block: RetryBlockType; index: number }
  | { kind: 'resource'; block: ResourceBlockType; index: number }

/** 非群聊最终回答 = 全部 ui/member 块（按原序，永不进过程区）+ 过程边界之后的 text 块：
 *  相邻 text 块合并成一段 Markdown，ui/member 块保持独立位置（流式期 render_ui 落在两段文本
 *  之间时正文自然分段）。边界后仍可能出现 thinking（模型收尾前的短思考）——它不再把已落位的
 *  ui 块打回过程区。actions（后续操作按钮）固定沉底：按钮是"预测用户下一步"的组件，
 *  放在整条回答末尾才符合直觉 */
const answerSegments = computed<AnswerSegment[]>(() => {
  const boundary = lastNonTextIndex.value
  const segments: AnswerSegment[] = []
  const trailingActions: AnswerSegment[] = []
  for (let i = 0; i < props.blocks.length; i++) {
    const block = props.blocks[i]
    if (block.type === 'ui') {
      const seg: AnswerSegment = { kind: 'ui', block }
      if (block.kind === 'actions') trailingActions.push(seg)
      else segments.push(seg)
    } else if (block.type === 'member') {
      segments.push({ kind: 'member', block })
    } else if (block.type === 'text' && i > boundary && block.text) {
      const last = segments[segments.length - 1]
      if (last && last.kind === 'text') last.text += block.text
      else segments.push({ kind: 'text', text: block.text })
    }
  }
  return [...segments, ...trailingActions]
})

/**
 * 群聊全文时序内联：一切渲染块按 blocks 原序进段——群主正文（text→气泡）、成员消息、
 * 思考/工具/技能/问答/重连行原位折叠单行、ui 块。过程行不再集中到顶部，成员消息
 * 发出后群主的后续思考/工具出现在其下方，消息流与真实发生顺序一致。
 * attachments 沉底统一渲染、kb 置顶渲染、stopped 标记不参与渲染；
 * render_ui 工具行跳过（成品是 ui 块），actions 段沉底。
 */
const inlineSegments = computed<AnswerSegment[]>(() => {
  if (!props.groupMode) return []
  const segments: AnswerSegment[] = []
  const trailingActions: AnswerSegment[] = []
  props.blocks.forEach((block, i) => {
    switch (block.type) {
      case 'text': {
        if (!block.text) break
        const last = segments[segments.length - 1]
        if (last && last.kind === 'text') last.text += block.text
        else segments.push({ kind: 'text', text: block.text })
        break
      }
      case 'member':
        segments.push({ kind: 'member', block })
        break
      case 'ui': {
        const seg: AnswerSegment = { kind: 'ui', block }
        if (block.kind === 'actions') trailingActions.push(seg)
        else segments.push(seg)
        break
      }
      case 'thinking':
        segments.push({ kind: 'thinking', block, index: i })
        break
      case 'tool':
        if (block.tool !== 'render_ui' && !isMirroredToolBlock(block)) {
          segments.push({ kind: 'tool', block, index: i })
        }
        break
      case 'skill_use':
        segments.push({ kind: 'skill_use', block, index: i })
        break
      case 'question':
        segments.push({ kind: 'question', block, index: i })
        break
      case 'retry':
        segments.push({ kind: 'retry', block, index: i })
        break
      case 'mcp':
      case 'skill':
        segments.push({ kind: 'resource', block, index: i })
        break
      default:
        break
    }
  })
  return [...segments, ...trailingActions]
})

const displaySegments = computed<AnswerSegment[]>(() =>
  props.groupMode ? inlineSegments.value : answerSegments.value
)

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

// ---------- 思考块活跃判定（展开/收起在 ThinkingBlock 组件内部自持） ----------

/** 流式进行中的思考 = 消息末块且尚未收尾（换块/结束时会写入 durationMs） */
function isActiveThinking(index: number): boolean {
  const block = props.blocks[index]
  return props.isLoading && index === props.blocks.length - 1
    && block?.type === 'thinking' && block.durationMs == null
}

</script>

<style lang="scss" scoped>
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

  // 用户主动中断：静态行，无折叠入口
  &.stopped {
    .stopped-icon {
      color: #b0aea5;
      font-size: 13px;
    }
  }
}

// 重新连接行（zcode 式）：灰字；进行中用工具动词同款渐变扫光（animated-gradient-text）
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

.retry-line {
  font-size: 13px;
  color: #8c8a82;
  user-select: none;
  align-self: flex-start;

  &.active {
    @include animated-gradient-text($primary-color, rgba(42, 161, 169, 0.25));
  }
}

.process-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.answer-content {
  font-size: 14px;
  padding: 0 4px;
}

// ---------- 群聊模式：群主正文 = 左对齐群头像 + 米色气泡（与成员白底气泡区分；右侧只属于用户） ----------
.group-own-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 10px 0;
}

.group-own-avatar {
  flex-shrink: 0;
  margin-top: 4px;
}

.group-own-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 4px 14px 14px 14px;
  background: #ece9de;
  color: #141413;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;

  :deep(code) {
    background: rgba(20, 20, 19, 0.08);
  }

  :deep(pre.md-code) {
    background: rgba(20, 20, 19, 0.05);
    border-color: transparent;

    code {
      color: #141413;
    }
  }

  :deep(a) {
    color: $primary-color;
    text-decoration: underline;
  }

  :deep(blockquote) {
    background: rgba(20, 20, 19, 0.05);
    color: #5f5d55;
    border-left-color: #b0aea5;
  }
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

// render_ui 参数流式传输中的轻量占位
.ui-rendering-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
  font-size: 13px;
  color: $text-muted;
  user-select: none;
}

.stream-interrupted {
  font-size: 12px;
  color: #d48806;
}
</style>
