<template>
  <div class="chat-center-area" :class="{ 'has-messages': hasMessages, embedded }">
    <!-- 消息列表底边渐隐：贴在输入区上缘，消息滚出列表边界时柔和消隐而非硬切 -->
    <div v-if="hasMessages && !embedded" class="list-fade-overlay" aria-hidden="true"></div>
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
      <h2 class="welcome-title">{{ welcomeTitle }}</h2>
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
            <a-dropdown
              v-if="!cancelable"
              :trigger="['click']"
              placement="topLeft"
              :overlay-style="{ userSelect: 'none' }"
            >
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

          <div class="toolbar-right">
            <ContextUsageIndicator v-if="!embedded && contextUsage" :data="contextUsage" />
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
            <!-- 主输入：生成中变停止按钮（点击中断当前轮次）；空闲为发送 -->
            <a-tooltip v-if="!cancelable" :title="loading ? '停止' : ''">
              <a-button
                type="primary"
                class="send-btn"
                :disabled="!loading && !value.trim()"
                @click="loading ? emit('stop') : emit('send')"
              >
                <span v-if="loading" class="stop-square" />
                <ArrowUpOutlined v-else />
              </a-button>
            </a-tooltip>
          </div>
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
import ContextUsageIndicator from './ContextUsageIndicator.vue'
import { DEFAULT_BOT_ICON, parseBotIcon } from '@/components/bot-icon/types'
import type { ContextUsageData, PendingAttachment } from '@/types'

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
  /** 会话上下文容量估算（null/缺省隐藏指示器） */
  contextUsage?: ContextUsageData | null
  /** 嵌入模式（消息编辑复用）：去外层留白/居中/欢迎区，直接以输入框原貌嵌入 */
  embedded?: boolean
  /** 显示取消按钮（编辑模式用） */
  cancelable?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:effort', value: string): void
  (e: 'send'): void
  /** 生成中点击停止：中断当前轮次 */
  (e: 'stop'): void
  (e: 'cancel'): void
  (e: 'add-files', files: File[]): void
  (e: 'remove-pending', key: string): void
  (e: 'input-focus'): void
  (e: 'input-blur'): void
}>()

const userStore = useUserStore()

// ---------- 新对话问候语：按时段分短语池随机取一句；有简短画像时混入画像变体 ----------
// [起始小时, 结束小时(含), 短语池]；池内含 {profile} 的变体仅在画像存在且简短时启用
const GREETING_BUCKETS: Array<[number, number, string[]]> = [
  [0, 4, ['还没睡，{name}？', '夜深了，{name}，想聊点什么？', '这么晚还没休息？{name}，我在。', '还没睡，{name}？{profile}的事不急于这一晚。']],
  [5, 8, ['早安，{name}！今天想从什么开始？', '早上好，{name}。新的一天，有什么想做的？', '早安，{name}！今天{profile}的日程还顺利吗？']],
  [9, 11, ['上午好，{name}！有什么要一起解决的？', '嗨，{name}，上午想研究点什么？', '上午好，{name}！{profile}的活儿需要搭把手吗？']],
  [12, 13, ['中午好，{name}！忙碌间隙想聊点什么？', '午安，{name}。有什么我能帮忙的？']],
  [14, 17, ['下午好，{name}！下午想推进点什么？', '嗨，{name}，下午进展如何？', '下午好，{name}！{profile}那边今天有什么新进展？']],
  [18, 22, ['晚上好，{name}！今晚想聊点什么？', '晚上好呀，{name}。有什么新鲜事想问？', '晚上好，{name}！今天{profile}的事还顺利吗？']],
  [23, 23, ['还没睡，{name}？', '夜深了，{name}，想聊点什么？', '这么晚还没休息？{name}，我在。', '还没睡，{name}？{profile}的事不急于这一晚。']]
]

const welcomeTitle = computed(() => {
  const name = userStore.userName?.trim() || '朋友'
  const profile = userStore.userInfo?.userProfile?.trim()
  const hour = new Date().getHours()
  const pool = GREETING_BUCKETS.find(([from, to]) => hour >= from && hour <= to)?.[2]
    ?? ['嗨，{name}！有什么想聊的？']
  // 画像缺失或过长时不用画像变体，避免占位符漏出
  const usable = pool.filter(p => !p.includes('{profile}') || (profile && profile.length <= 12))
  const picked = usable[Math.floor(Math.random() * usable.length)]
  return picked.split('{name}').join(name).split('{profile}').join(profile ?? '')
})

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
  position: relative;
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

  // 消息列表底边渐隐：悬在输入区上缘（负 top 压在列表底边之上），越靠近输入框越实
  .list-fade-overlay {
    position: absolute;
    top: -34px;
    left: 0;
    right: 0;
    height: 34px;
    z-index: 2;
    pointer-events: none;
    background: linear-gradient(to bottom, rgba(250, 249, 245, 0) 0%, #faf9f5 82%);
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

      // zcode 式工具栏：左附件+思考等级 / 右上下文容量+发送（圆角方形）
      .input-toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 6px 10px 10px 12px;
        user-select: none;

        .toolbar-left {
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .toolbar-right {
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

      // 取消按钮（编辑模式）：与发送成组靠右
      .cancel-btn {
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

        // 生成中的停止态：背景保持主题色，白色小方块即停止（zcode 同款）
        .stop-square {
          width: 11px;
          height: 11px;
          border-radius: 2px;
          background: #fff;
          display: inline-block;
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
