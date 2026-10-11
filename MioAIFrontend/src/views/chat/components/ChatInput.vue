<template>
  <div class="chat-center-area" :class="{ 'has-messages': hasMessages, embedded }">
    <div class="welcome-section" v-if="!hasMessages && !loading">
      <BotIcon
        v-if="agentIconConfig"
        :shape="agentIconConfig.shape"
        :fill="agentIconConfig.fill"
        :size="56"
        :live="false"
        eye-color="#faf9f5"
      />
      <img v-else :src="agentAvatar || '/logo.png'" alt="Agent" class="welcome-avatar" />
      <h2 class="welcome-title">我能帮什么忙吗，{{ userStore.userName }}？</h2>
    </div>
    <!-- 与输入框融合的上区（任务清单等，共享同一容器边框） -->
    <div class="chat-input-wrapper">
      <div
        class="chat-input-container"
        :class="{ 'drag-over': dragOver }"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
      >
        <slot name="above-input" />
        <!-- 待上传附件：一个文件一张卡片，直接作为输入容器的子元素（与输入框一体，无任何包装层）；
             GSAP 进出场由 usePendingCardAnimations 驱动——离场=四周向中间缩小消失+占位塌缩，
             兄弟卡片由文档流连续回流平滑补位 -->
        <AttachmentCard
          v-for="card in renderCards ?? []"
          :key="card.key"
          :ref="refSetter ? refSetter(card.key) : undefined"
          :item="card"
          :leaving="leavingKeys ? leavingKeys.has(card.key) : false"
          variant="card"
          removable
          @remove="emit('remove-pending', $event)"
        />
        <div class="input-main">
          <a-textarea
            v-model:value="value"
            :placeholder="`给 ${agentName || 'MioBot'} 发送消息`"
            :auto-size="{ minRows: 1, maxRows: 8 }"
            @pressEnter="handleEnter"
            @paste="onPaste"
            @focus="emit('input-focus')"
            @blur="emit('input-blur')"
            class="chat-textarea"
          />
        </div>
        <!-- zcode 式底部工具栏：左附件+思考等级、右发送 -->
        <div class="input-toolbar">
          <div class="toolbar-left">
            <input
              ref="fileInputRef"
              type="file"
              multiple
              class="hidden-file-input"
              @change="onFileChange"
            />
            <a-tooltip title="上传附件">
              <button type="button" class="attach-btn" @click="fileInputRef?.click()">
                <PlusOutlined />
              </button>
            </a-tooltip>
            <a-dropdown v-if="!cancelable" :trigger="['click']" placement="topLeft">
              <div class="effort-selector" @click.prevent>
                <BrainIcon :size="14" class="effort-icon" :class="{ dimmed: effort === 'none' }" />
                <span class="effort-label">{{ effortLabel }}</span>
                <DownOutlined class="effort-caret" />
              </div>
              <template #overlay>
                <a-menu :selected-keys="[effort]" @click="onEffortClick">
                  <a-menu-item v-for="opt in effortOptions" :key="opt.value">
                    <BrainIcon :size="13" class="menu-brain" :class="{ dimmed: opt.value === 'none' }" />
                    <span>{{ opt.label }}</span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>

          <button v-if="cancelable" type="button" class="cancel-btn" @mousedown.prevent @click="emit('cancel')">取消</button>
          <button
            v-if="cancelable"
            type="button"
            class="send-pill"
            :disabled="!value.trim() || loading"
            @click="emit('send')"
          >
            {{ loading ? '发送中…' : '发送' }}
          </button>
          <a-button
            v-if="!cancelable"
            type="primary"
            class="send-btn"
            :disabled="!value.trim() || loading"
            :loading="loading"
            @click="emit('send')"
          >
            <ArrowUpOutlined v-if="!loading" />
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useUserStore } from '@/store/user'
import { ArrowUpOutlined, DownOutlined, PlusOutlined } from '@ant-design/icons-vue'
import BrainIcon from '@/components/BrainIcon.vue'
import AttachmentCard from './AttachmentCard.vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { DEFAULT_BOT_ICON, parseBotIcon } from '@/components/bot-icon/types'
import type { PendingAttachment } from '@/types'

/** 思考档位全量标签（实际渲染哪些档由后端按模型能力返回） */
const EFFORT_LABELS: Record<string, string> = {
  max: '极限',
  xhigh: '超高',
  high: '最高',
  medium: '中等',
  low: '较低',
  minimal: '极低',
  none: '关闭'
}

/** 探测失败时的兜底档位 */
const FALLBACK_EFFORTS = ['high', 'medium', 'low', 'none']

const props = defineProps<{
  modelValue: string
  agentName?: string
  agentAvatar?: string
  /** agent.icon 原始 JSON（bot-icon 配置），存在时欢迎区用动态机器人图标 */
  agentIcon?: string
  hasMessages: boolean
  loading: boolean
  /** 思考强度（后端返回的该模型支持的档位之一） */
  effort?: string
  /** 当前模型支持的思考档位（按能力探测，如实渲染） */
  supportedEfforts?: string[]
  /** 待上传卡片渲染列表（含离场动画中的卡，由父级动画组合式维护） */
  renderCards?: PendingAttachment[]
  /** 离场中的键（隐藏 X，防动画期间重复触发） */
  leavingKeys?: Set<string>
  /** v-for 动态 ref 登记器（父级动画组合式需要元素引用驱动 GSAP） */
  refSetter?: (key: string) => (el: unknown) => void
  /** 嵌入模式（消息编辑复用）：去外层留白/居中/欢迎区，直接以输入框原貌嵌入 */
  embedded?: boolean
  /** 显示取消按钮（编辑模式用） */
  cancelable?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:effort', value: string): void
  (e: 'send'): void
  (e: 'cancel'): void
  (e: 'add-files', files: File[]): void
  (e: 'remove-pending', key: string): void
  (e: 'input-focus'): void
  (e: 'input-blur'): void
}>()

const userStore = useUserStore()

/** 欢迎logo：icon 配置优先；无配置且有头像则用头像图；都没有用默认机器人 */
const agentIconConfig = computed(() => parseBotIcon(props.agentIcon) ?? (props.agentAvatar ? null : DEFAULT_BOT_ICON))

const value = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v)
})

const effortOptions = computed(() =>
  (props.supportedEfforts?.length ? props.supportedEfforts : FALLBACK_EFFORTS)
    .map(v => ({ value: v, label: EFFORT_LABELS[v] ?? v }))
    .reverse()
)

const effort = computed(() => props.effort || 'high')

const effortLabel = computed(
  () => effortOptions.value.find(o => o.value === effort.value)?.label ?? EFFORT_LABELS[effort.value] ?? effort.value
)

function onEffortClick({ key }: { key: string | number }): void {
  emit('update:effort', String(key))
}

function handleEnter(e: KeyboardEvent): void {
  if (!e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
}

const fileInputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)

/** 粘贴上传：Ctrl+V 剪贴板里的文件（如截图）直接进待传区 */
function onPaste(event: ClipboardEvent): void {
  const files = Array.from(event.clipboardData?.files ?? [])
  if (files.length) {
    event.preventDefault()
    emit('add-files', files)
  }
}

/** 拖拽上传：文件拖入输入框容器 */
function onDrop(event: DragEvent): void {
  dragOver.value = false
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (files.length) {
    emit('add-files', files)
  }
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length) {
    emit('add-files', files)
  }
  // 允许再次选择同一个文件
  input.value = ''
}
</script>

<style lang="scss" scoped>
.chat-center-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 24px;
  width: 100%;
  box-sizing: border-box;

  // 嵌入模式（消息编辑复用）：去外层留白与居中，输入框原貌嵌入；间距与光晕对齐主输入框
  &.embedded {
    padding: 0;
    flex: none;
    align-items: stretch;

    .welcome-section {
      display: none;
    }

    .chat-input-wrapper {
      max-width: none;
    }

    .chat-input-wrapper .chat-input-container {
      box-shadow: 0 4px 18px rgba(42, 161, 169, 0.1);

      &:focus-within {
        box-shadow: 0 4px 22px rgba(42, 161, 169, 0.22);
      }
    }

    .chat-input-wrapper .chat-input-container .input-main {
      padding: 16px 16px 8px;
    }

    .chat-input-wrapper .chat-input-container .input-toolbar {
      padding: 8px 14px 12px 14px;
    }
  }

  // 有消息时组合器固定在底部（常规流布局，消息区不再被遮挡）
  &.has-messages {
    flex: none;
    padding: 20px 24px 20px;

    .welcome-section {
      display: none;
    }
  }

  .welcome-section {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 16px;
    margin-bottom: 32px;

    .welcome-avatar {
      width: 48px;
      height: 48px;
      border-radius: 25%;
      object-fit: cover;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      flex-shrink: 0;
    }

    .welcome-title {
      font-size: 24px;
      font-weight: 600;
      color: #141413;
      margin: 0;
      white-space: nowrap;
    }
  }

  .chat-input-wrapper {
    width: 100%;
    max-width: 800px;

    // 融合容器：待传卡片/任务清单（插槽）与输入框共处一个边框内
    .chat-input-container {
      width: 100%;
      background: #fff;
      border: 1px solid #e8e6dc;
      border-radius: 16px;
      box-shadow: 0 4px 14px rgba(20, 20, 19, 0.05);
      // 只过渡视觉属性：高度由卡片 GSAP 逐帧驱动，transition:all 会与之打架造成收回卡顿
      transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;

      // 文件拖入悬停提示
      &.drag-over {
        border-color: $primary-color;
        background: rgba(42, 161, 169, 0.04);
      }

      &:focus-within {
        border-color: $primary-color;
        box-shadow: 0 4px 16px rgba(42, 161, 169, 0.2);
      }

      .input-main {
        padding: 14px 16px 6px;

        .chat-textarea {
          width: 100%;
          border: none;
          background: transparent;
          resize: none;
          font-size: 15px;
          line-height: 1.5;
          color: #141413;

          &:focus {
            outline: none;
            box-shadow: none;
          }

          &::placeholder {
            color: #8c8a82;
          }

          :deep(.ant-input) {
            border: none;
            background: transparent;
            padding: 0;

            &:focus {
              outline: none;
              box-shadow: none;
            }
          }
        }
      }

      // zcode 式工具栏：左附件+思考等级 / 右发送（圆角方形）
      .input-toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 6px 10px 10px 12px;

        .toolbar-left {
          display: flex;
          align-items: center;
          gap: 4px;
        }
      }

      .hidden-file-input {
        display: none;
      }

      .attach-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        height: 30px;
        padding: 0;
        border: none;
        border-radius: 10px;
        background: transparent;
        color: #5f5d55;
        font-size: 15px;
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
          background: #f0ede4;
          color: $primary-color;
        }
      }

      .effort-selector {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 5px 10px;
        border-radius: 10px;
        font-size: 13px;
        color: #5f5d55;
        cursor: pointer;
        user-select: none;
        transition: background 0.2s;

        &:hover {
          background: #f0ede4;
        }

        .effort-icon {
          color: $primary-color;

          &.dimmed {
            color: #b0aea5;
          }
        }

        .effort-caret {
          font-size: 10px;
          color: #8c8a82;
        }
      }

      // 取消按钮（编辑模式）：靠右与发送成组
      .cancel-btn {
        margin-left: auto;
        margin-right: 8px;
        height: 30px;
        padding: 0 14px;
        border: 1px solid #d8d5cc;
        border-radius: 8px;
        background: #fff;
        color: #5f5d55;
        font-size: 13px;
        cursor: pointer;
        transition: all 0.2s;

        &:hover {
          border-color: $primary-color;
          color: $primary-color;
        }
      }

      // 发送 pill（编辑模式，原编辑发送按钮样式）
      .send-pill {
        height: 30px;
        padding: 0 16px;
        border: none;
        border-radius: 8px;
        background: $primary-color;
        color: #fff;
        font-size: 13px;
        cursor: pointer;
        transition: all 0.2s;

        &:hover:not(:disabled) {
          background: darken($primary-color, 8%);
        }

        &:disabled {
          background: #b0aea5;
          cursor: not-allowed;
        }
      }

      .send-btn {
        width: 34px;
        height: 34px;
        min-width: 34px;
        padding: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 10px;
        background: $primary-color;
        border-color: $primary-color;
        flex-shrink: 0;

        &:hover:not(:disabled) {
          background: darken($primary-color, 10%);
          border-color: darken($primary-color, 10%);
        }

        &:disabled {
          background: #e8e6dc;
          border-color: #e8e6dc;
          color: #b0aea5;
        }

        :deep(.anticon) {
          font-size: 15px;
          display: flex;
        }
      }
    }
  }
}

.menu-brain {
  color: $primary-color;
  margin-right: 8px;
  vertical-align: -2px;

  &.dimmed {
    color: #b0aea5;
  }
}

@media (max-width: 768px) {
  .chat-center-area {
    padding: 16px;

    .welcome-section {
      gap: 12px;

      .welcome-avatar {
        width: 40px;
        height: 40px;
      }

      .welcome-title {
        font-size: 20px;
      }
    }

    .chat-input-wrapper {
      .chat-input-container {
        .input-main {
          padding: 12px 14px 4px;

          .chat-textarea {
            font-size: 14px;
          }
        }

        .send-btn {
          width: 32px;
          height: 32px;
          min-width: 32px;

          :deep(.anticon) {
            font-size: 14px;
          }
        }
      }
    }
  }
}

@media (max-width: 480px) {
  .chat-center-area {
    .welcome-section {
      .welcome-title {
        font-size: 20px;
      }
    }
  }
}
</style>
