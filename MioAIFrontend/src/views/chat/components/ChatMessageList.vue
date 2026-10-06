<template>
  <div class="chat-messages" ref="messagesRef" v-if="messages.length > 0">
    <div class="messages-wrapper">
      <div
        v-for="(msg, index) in messages"
        :key="msg.id"
        class="message"
        :class="msg.role"
        @mouseenter="handleMouseEnter(msg.id)"
        @mouseleave="hoverMessageId = ''"
      >
        <div class="message-content">
          <!-- assistant 按内容块顺序渲染（文本/思考/工具顺着显示），流式期间分支稳定不切换；
               多版本（编辑/重生成产生）按 activeVersion 选显示内容 -->
          <MioBotMessage
            v-if="msg.role === 'assistant'"
            :content="displayOf(msg).content"
            :blocks="displayOf(msg).blocks"
            :is-loading="isLoading && index === messages.length - 1 && isLatestVersion(msg)"
            :duration-ms="displayOf(msg).durationMs"
            :create-time="displayOf(msg).createTime"
            :interrupted="displayOf(msg).interrupted"
            :retry-notice="isLatestVersion(msg) ? msg.retryNotice : undefined"
            :chat-id="chatId"
          />
          <template v-else-if="editingId !== msg.id">
            <AttachmentCards
              v-if="inputAttachmentsOf(msg).length"
              :items="inputAttachmentsOf(msg)"
              variant="card"
              class="msg-attachments"
            />
            <MarkdownView class="message-text" :content="msg.content" />
          </template>
          <div v-else class="message-edit" @keydown.esc="cancelEdit">
            <ChatInput
              v-model="editText"
              embedded
              cancelable
              has-messages
              :loading="false"
              :render-cards="editRenderCards"
              :leaving-keys="editLeavingKeys"
              :ref-setter="editRefSetter"
              @send="confirmEdit(msg, index)"
              @cancel="cancelEdit"
              @add-files="emit('addEditFiles', $event)"
              @remove-pending="emit('removeEditAtt', $event)"
            />
          </div>
          <div class="message-actions">
            <!-- 操作行常驻显示（复制/编辑/重生成/版本切换）：仅编辑中隐藏；
                 生成中的最后一条消息还没有完整内容可操作，也隐藏 -->
            <div
              class="copy-area"
              v-show="editingId !== msg.id && !(isLoading && index === messages.length - 1)"
            >
              <a-tooltip :title="copiedMessageId === msg.id ? '已复制' : '复制'">
                <a-button type="text" size="small" class="copy-btn" :class="{ 'copied': copiedMessageId === msg.id }" @click="copyMessage(displayOf(msg).content, msg.id)">
                  <CheckOutlined v-if="copiedMessageId === msg.id" />
                  <CopyOutlined v-else />
                </a-button>
              </a-tooltip>
              <a-tooltip v-if="canModify && msg.role === 'user'" title="编辑">
                <a-button type="text" size="small" class="copy-btn" @click="startEdit(msg, index)">
                  <EditOutlined />
                </a-button>
              </a-tooltip>
              <a-tooltip v-if="canModify && msg.role !== 'user' && index === messages.length - 1" title="重新生成">
                <a-button type="text" size="small" class="copy-btn" @click="emit('regenerate')">
                  <RedoOutlined />
                </a-button>
              </a-tooltip>
              <!-- 回复版本切换 < n/n >：与复制/编辑同级同显隐，跟随提问显示便于对照 -->
              <div v-if="msg.role === 'user' && replyVersionTarget(index)" class="version-nav">
                <a-button type="text" size="small" class="version-btn" :disabled="versionOf(replyVersionTarget(index)!) <= 1" @click="emit('switchVersion', replyVersionTarget(index)!.id, versionOf(replyVersionTarget(index)!) - 1)">
                  <LeftOutlined />
                </a-button>
                <span class="version-text">{{ versionOf(replyVersionTarget(index)!) }} / {{ totalVersions(replyVersionTarget(index)!) }}</span>
                <a-button type="text" size="small" class="version-btn" :disabled="versionOf(replyVersionTarget(index)!) >= totalVersions(replyVersionTarget(index)!)" @click="emit('switchVersion', replyVersionTarget(index)!.id, versionOf(replyVersionTarget(index)!) + 1)">
                  <RightOutlined />
                </a-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, CheckOutlined, EditOutlined, RedoOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import type { AttachmentDisplay, AttachmentItem, ChatMessage, MessageBlock, PendingAttachment } from '@/types'
import { messageAttachmentDisplays } from '../attachmentUtils'
import MarkdownView from '@/components/MarkdownView.vue'
import MioBotMessage from './MioBotMessage.vue'
import ChatInput from './ChatInput.vue'
import AttachmentCards from './AttachmentCards.vue'

const props = defineProps<{
  messages: ChatMessage[]
  isLoading: boolean
  /** 登录用户才提供编辑/重新生成（依赖服务端历史截断） */
  canModify: boolean
  /** 当前会话 id（问答卡片提交答案用） */
  chatId?: string
  /** 编辑中卡片渲染列表（含离场动画中的卡，由父级动画组合式维护） */
  editRenderCards?: PendingAttachment[]
  /** 编辑中离场的键（隐藏 X） */
  editLeavingKeys?: Set<string>
  /** 编辑卡片 v-for 动态 ref 登记器（父级动画组合式需要元素引用） */
  editRefSetter?: (key: string) => (el: unknown) => void
}>()

const emit = defineEmits<{
  /** 编辑用户消息后以新内容重发（截断该消息及其后的历史） */
  (e: 'edit', index: number, content: string, attachments: AttachmentItem[]): void
  /** 对最后一条回复重新生成（截断旧回复后重发） */
  (e: 'regenerate'): void
  /** 切换回复版本（编辑/重生成产生的历次回复） */
  (e: 'switchVersion', messageId: string, version: number): void
  /** 编辑开始（父级据此从消息初始化附件编辑列表） */
  (e: 'editStart', index: number): void
  /** 编辑取消（父级清理附件编辑状态） */
  (e: 'editCancel'): void
  /** 编辑中新追加文件（父级负责上传） */
  (e: 'addEditFiles', files: File[]): void
  /** 编辑中移除附件（父级决定沙箱文件去留） */
  (e: 'removeEditAtt', key: string): void
}>()

const messagesRef = ref<HTMLElement | null>(null)
const hoverMessageId = ref<string>('')
const copiedMessageId = ref<string>('')
const editingId = ref<string>('')
const editText = ref<string>('')

// ---------- 回复版本（编辑/重新生成产生的历次回复） ----------
/** 该提问下方回复的版本组目标（回复带多版本时返回它，供切换器渲染） */
function replyVersionTarget(index: number): ChatMessage | undefined {
  const reply = props.messages[index + 1]
  if (!reply || reply.role !== 'assistant') return undefined
  return hasVersions(reply) ? reply : undefined
}

function totalVersions(msg: ChatMessage): number {
  return (msg.history?.length ?? 0) + 1
}

function versionOf(msg: ChatMessage): number {
  return msg.activeVersion ?? totalVersions(msg)
}

function hasVersions(msg: ChatMessage): boolean {
  return msg.role === 'assistant' && totalVersions(msg) > 1
}

function isLatestVersion(msg: ChatMessage): boolean {
  return versionOf(msg) === totalVersions(msg)
}

/** 按当前版本号取显示内容：最新=消息本体，旧版=history 快照 */
function displayOf(msg: ChatMessage): Pick<ChatMessage, 'content' | 'blocks' | 'durationMs' | 'createTime' | 'interrupted'> {
  const version = versionOf(msg)
  if (version === totalVersions(msg) || !msg.history?.length) {
    return msg
  }
  return msg.history[version - 1] ?? msg
}

function startEdit(msg: ChatMessage, index: number): void {
  editingId.value = msg.id
  editText.value = msg.content
  emit('editStart', index)
}

/** 用户消息随发的附件（attachments 输入块）；图片类型直接出缩略图 */
function inputAttachmentsOf(msg: ChatMessage): AttachmentDisplay[] {
  return messageAttachmentDisplays(
    (msg.blocks ?? [])
      .filter((b): b is Extract<MessageBlock, { type: 'attachments' }> => b.type === 'attachments')
      .filter(b => b.side === 'input')
      .flatMap(b => b.items)
  )
}

function cancelEdit(): void {
  editingId.value = ''
  editText.value = ''
  emit('editCancel')
}

function confirmEdit(msg: ChatMessage, index: number): void {
  const content = editText.value.trim()
  if (!content) {
    message.info('内容不能为空，已取消编辑')
    cancelEdit()
    return
  }
  // 同步捕获编辑后的附件清单（父级据此发送与清理沙箱孤儿）
  const finalAttachments = (props.editRenderCards ?? [])
    .filter(p => p.status === 'done' && p.item && !props.editLeavingKeys?.has(p.key))
    .map(p => p.item as AttachmentItem)
  // 内容未变化也照常发送（= 从这条消息重新发送）；只有空内容才取消
  cancelEdit()
  emit('edit', index, content, finalAttachments)
}

/** 原生 input 直读 DOM 同步（IME 输入下 antd v-model 中间层断链的双通道保底） */
function onEditNativeInput(e: Event): void {
  const value = (e.target as HTMLTextAreaElement | null)?.value
  if (typeof value === 'string') {
    editText.value = value
  }
}

function scrollToBottom(): void {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

/** 用户是否贴在底部（留少量阈值）：流式跟随只在贴底时拉滚动，滚上去阅读时不打扰 */
function isNearBottom(): boolean {
  if (!messagesRef.value) {
    return true
  }
  const el = messagesRef.value
  return el.scrollHeight - el.scrollTop - el.clientHeight < 120
}

function handleMouseEnter(msgId: string): void {
  hoverMessageId.value = msgId
  if (copiedMessageId.value && copiedMessageId.value !== msgId) {
    copiedMessageId.value = ''
  }
}

async function copyMessage(content: string, messageId: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(content)
    copiedMessageId.value = messageId
  } catch (e) {
    console.error(e)
    message.error('复制失败')
  }
}

defineExpose({ scrollToBottom, isNearBottom })
</script>

<style lang="scss" scoped>
.chat-messages {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
  // 组合器已是常规流布局，不再需要为悬浮输入框预留大片底部空间
  padding-bottom: 24px;
  // 永久预留滚动条槽位：消息变长时滚动条出现/消失不再挤压内容列
  // （居中栏整体左移+文本重排=用户看到的"从右侧偏一点往左"与展开抖动）
  scrollbar-gutter: stable;
  @include thin-scrollbar;

  .messages-wrapper {
    max-width: 800px;
    margin: 0 auto;

    .message {
      display: flex;
      gap: 16px;
      margin-bottom: 24px;

      &.user {
        flex-direction: row-reverse;

        .message-content {
          align-items: flex-end;

          // 气泡上方的附件 chips 右对齐
          .msg-attachments {
            justify-content: flex-end;
            margin-bottom: 8px;
          }
        }
      }

      .message-avatar {
        flex-shrink: 0;

        .agent-avatar-msg {
          background: $primary-color;
        }
      }

      .message-content {
        flex: 1;
        /* 允许 flex 项收缩到内容以下：宽表格/代码块由内部滚动，不撑破气泡产生页面横向滚动 */
        min-width: 0;
        display: flex;
        flex-direction: column;
        position: relative;

        .message-text {
          max-width: 70%;
          padding: 12px 16px;
          border-radius: 12px;
          font-size: 14px;
          line-height: 1.6;
          word-break: break-word;
        }

        .stream-interrupted {
          font-size: 12px;
          color: #d48806;
          margin-top: 4px;
        }

        // 编辑态：嵌入输入框组件，宽度与底部输入框一致（800px 居中）
        .message-edit {
          width: 100%;
          max-width: 800px;
          margin: 4px auto 0;
        }

        .message-loading {
          display: flex;
          gap: 6px;
          padding: 8px 0;

          span {
            width: 8px;
            height: 8px;
            background: $primary-color;
            border-radius: 50%;
            animation: loading-bounce 1.4s infinite ease-in-out both;

            &:nth-child(1) {
              animation-delay: -0.32s;
            }

            &:nth-child(2) {
              animation-delay: -0.16s;
            }

            &:nth-child(3) {
              animation-delay: 0s;
            }
          }
        }

        .message-actions {
          min-height: 35px;
          margin-top: 8px;
          display: flex;
          gap: 8px;
          align-items: center;

          .copy-btn {
            color: #86909c;
            padding: 4px 8px;
            height: auto;
            font-size: 14px;
            transition: all 0.2s;

            &:hover {
              color: $primary-color;
              background: rgba($primary-color, 0.08);
            }

            &.copied {
              color: #52c41a;
            }
          }

          // 回复版本切换 < n/n >
          .version-nav {
            display: inline-flex;
            align-items: center;
            margin-left: 4px;
            color: #86909c;

            .version-btn {
              color: #86909c;
              padding: 2px 6px;
              height: auto;
              font-size: 12px;

              &:hover:not(:disabled) {
                color: $primary-color;
              }
            }

            .version-text {
              font-size: 12px;
              min-width: 36px;
              text-align: center;
              user-select: none;
            }
          }
        }

      }

      &.user .message-text {
        background: $primary-color;
        color: #fff;

        :deep(code) {
          background: rgba(255, 255, 255, 0.2);
        }

        :deep(pre.md-code) {
          background: rgba(255, 255, 255, 0.1);
          border-color: transparent;

          code {
            color: #fff;
          }
        }

        :deep(a) {
          color: #fff;
          text-decoration: underline;
        }

        :deep(blockquote) {
          background: rgba(255, 255, 255, 0.12);
          color: rgba(255, 255, 255, 0.9);
          border-left-color: rgba(255, 255, 255, 0.6);
        }
      }

      &.assistant .message-text {
        background: transparent;
        color: #202124;
        max-width: 100%;
        padding: 0;
      }
    }
  }
}

@keyframes loading-bounce {
  0%, 80%, 100% {
    transform: scale(0);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
