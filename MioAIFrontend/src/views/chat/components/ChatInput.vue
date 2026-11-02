<template>
  <div class="chat-center-area" :class="{ 'has-messages': hasMessages, embedded }">
    <!-- 消息列表底边渐隐：贴在输入区上缘，消息滚出列表边界时柔和消隐而非硬切 -->
    <div v-if="hasMessages && !embedded" class="list-fade-overlay" aria-hidden="true"></div>
    <div class="welcome-section" v-if="!hasMessages && !loading">
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
        <!-- 富文本输入区（contenteditable）：@ / 引用 token 以不可编辑内联节点长在文本流里
             （zcode 同构），不另起容器；modelValue 只承载纯文本，token 由 chatRefs 单独承载 -->
        <div class="input-main">
          <div
            ref="editorRef"
            class="chat-editor"
            contenteditable="true"
            role="textbox"
            aria-multiline="true"
            :data-placeholder="placeholder || `向 ${agentName || 'MioBot'} 提问，使用@添加上下文，使用/选择命令或能力`"
            @input="onEditorInput"
            @keydown="onEditorKeydown"
            @keyup="onEditorKeyup"
            @click="detectTrigger"
            @focus="onEditorFocus"
            @blur="onEditorBlur"
            @paste="onEditorPaste"
            @copy="onEditorCopy"
            @cut="onEditorCopy($event, true)"
            @compositionstart="onCompositionStart"
            @compositionend="onCompositionEnd"
          ></div>
        </div>
        <!-- @ / 触发的引用菜单：会话/知识库（@）与 命令/技能/MCP（/），键盘上下+回车选择 -->
        <div v-if="menuOpen" class="mention-menu" ref="menuBodyRef">
          <template v-for="section in menuSections" :key="section.title">
            <div class="menu-section-title">{{ section.title }}</div>
            <div
              v-for="row in section.rows"
              :key="row.key"
              class="menu-row"
              :class="[{ active: row.index === activeIndex }, 'tone-' + row.tone]"
              :data-index="row.index"
              @mousedown.prevent="row.pick()"
              @mouseenter="activeIndex = row.index"
            >
              <component :is="row.icon" class="row-icon" />
              <span class="row-main">
                <span class="row-title" :class="'tone-' + row.tone">{{ row.title }}</span>
                <span v-if="row.desc" class="row-desc">{{ row.desc }}</span>
              </span>
              <span v-if="row.suffix" class="row-suffix">{{ row.suffix }}</span>
            </div>
          </template>
          <div v-if="!flatMenuRows.length" class="menu-empty">
            {{ menuTrigger === '@' ? '未找到相关会话或知识库' : '未找到相关命令或能力' }}
          </div>
        </div>
        <!-- zcode 式底部工具栏：左附件+模型切换（多级菜单，+ 右侧）+思考等级、右发送 -->
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
            <!-- 模型切换器（zcode 同款）：供应商为 hover 子菜单（a-sub-menu），右浮出模型列表 -->
            <a-dropdown
              v-if="!cancelable"
              v-model:open="modelMenuOpen"
              :trigger="['click']"
              placement="topLeft"
              :overlay-style="{ userSelect: 'none' }"
            >
              <div class="model-selector" @click.prevent>
                <ApiOutlined class="model-icon" />
                <span class="model-label">{{ currentModelLabel }}</span>
                <DownOutlined class="effort-caret" />
              </div>
              <template #overlay>
                <a-menu
                  class="model-menu"
                  :selected-keys="currentModelKey ? [currentModelKey] : []"
                  :inline-indent="14"
                  @click="onModelMenuClick"
                >
                  <a-sub-menu v-for="g in cascadeGroups" :key="g.key">
                      <template #title>
                        <span class="group-label" :class="{ current: g.current }">
                          {{ g.label }}
                          <CheckOutlined v-if="g.current" class="group-check" />
                        </span>
                      </template>
                      <a-menu-item v-if="!g.models.length" disabled>该分组暂无可用模型</a-menu-item>
                      <a-menu-item v-for="m in g.models" :key="m.key" class="model-item">
                        <span class="model-menu-name">{{ m.id }}</span>
                      </a-menu-item>
                    </a-sub-menu>
                  <a-menu-divider />
                  <a-menu-item key="__manage__" class="manage-item">管理模型</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <a-dropdown
              v-if="!cancelable && effortOptions.length"
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
              :disabled="!canSend || loading"
              @click="handleSendClick"
            >
              {{ loading ? '发送中…' : '发送' }}
            </button>
            <!-- 主输入：生成中变停止按钮（点击中断当前轮次）；空闲为发送 -->
            <a-tooltip v-if="!cancelable" :title="loading ? '停止' : ''">
              <a-button
                type="primary"
                class="send-btn"
                :disabled="!loading && !canSend"
                @click="loading ? emit('stop') : handleSendClick()"
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
import { ref } from 'vue'
import {
  ApiOutlined,
  ArrowUpOutlined,
  CheckOutlined,
  DownOutlined,
  PlusOutlined
} from '@ant-design/icons-vue'
import BrainIcon from '@/components/icons/BrainIcon.vue'
import AttachmentCard from './AttachmentCard.vue'
import ContextUsageIndicator from './ContextUsageIndicator.vue'
import type { ChatRefItem, ContextUsageData, FreeModel, KnowledgeBase, McpTool, ModelProvider, ModelSelection, PendingAttachment, Skill } from '@/types'
import { useWelcomeGreeting } from '../composables/useWelcomeGreeting'
import { useModelPicker } from '../composables/useModelPicker'
import { useChatEditor } from '../composables/useChatEditor'

const props = defineProps<{
  modelValue: string
  agentName?: string
  hasMessages: boolean
  loading: boolean
  /** 思考强度（后端返回的该模型支持的档位之一） */
  effort?: string
  /** 当前模型支持的思考档位：null/缺省=未解析（兜底档位），[]=该模型不支持任何档位（隐藏选择器） */
  supportedEfforts?: string[] | null
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
  /** 嵌入模式下仍启用 @ / 命令菜单（首页演示窗用；编辑复用场景保持关闭） */
  embeddedMenu?: boolean
  /** 自定义占位文案（首页首屏用；缺省按智能体名拼接 @/ 用法提示） */
  placeholder?: string
  /** 显示取消按钮（编辑模式用） */
  cancelable?: boolean
  /** 当前智能体绑定的知识库（@ 引用数据源之一） */
  knowledgeBases?: KnowledgeBase[]
  /** 当前智能体绑定的技能（/ 引用数据源） */
  skills?: Skill[]
  /** 当前智能体绑定的 MCP 服务（/ 引用数据源） */
  mcpTools?: McpTool[]
  /** 当前会话 id：切换即清空引用；@ 列表排除当前会话（自身上下文已在内） */
  currentChatId?: string
  /** OpenCode 免费模型目录（反代拉取，-free 档） */
  freeModels?: FreeModel[]
  /** 用户自定义供应商（含各自模型条目） */
  providers?: ModelProvider[]
  /** 当前对话模型选择 */
  currentModel?: ModelSelection | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:effort', value: string): void
  (e: 'send', refs: ChatRefItem[]): void
  /** 生成中点击停止：中断当前轮次 */
  (e: 'stop'): void
  (e: 'cancel'): void
  (e: 'add-files', files: File[]): void
  (e: 'remove-pending', key: string): void
  (e: 'input-focus'): void
  (e: 'input-blur'): void
  /** 切换对话模型（OpenCode 免费档 / 自定义供应商模型） */
  (e: 'select-model', selection: ModelSelection): void
  /** 打开模型管理弹窗（父级以 antd Modal 呈现个人中心的模型设置） */
  (e: 'manage-models'): void
}>()

// ---------- 打包：问候语 / 模型切换器 / 富文本编辑器 ----------
const { welcomeTitle } = useWelcomeGreeting()

const {
  modelMenuOpen,
  cascadeGroups,
  currentModelKey,
  currentModelLabel,
  onModelMenuClick,
  effortOptions,
  effort,
  effortLabel
} = useModelPicker({
  currentModel: () => props.currentModel,
  freeModels: () => props.freeModels ?? [],
  providers: () => props.providers ?? [],
  supportedEfforts: () => props.supportedEfforts,
  effort: () => props.effort,
  onSelect: selection => emit('select-model', selection),
  onManage: () => emit('manage-models')
})

function onEffortClick({ key }: { key: string | number }): void {
  emit('update:effort', String(key))
}

const {
  editorRef,
  chatRefs,
  canSend,
  menuOpen,
  menuTrigger,
  activeIndex,
  menuBodyRef,
  menuSections,
  flatMenuRows,
  onEditorInput,
  onEditorKeydown,
  onEditorKeyup,
  onEditorFocus,
  onEditorBlur,
  onEditorPaste,
  onEditorCopy,
  onCompositionStart,
  onCompositionEnd,
  detectTrigger
} = useChatEditor({
  modelValue: () => props.modelValue,
  onUpdateModelValue: v => emit('update:modelValue', v),
  onSend: sendFromEditor,
  onAddFiles: files => emit('add-files', files),
  onFocus: () => emit('input-focus'),
  onBlur: () => emit('input-blur'),
  menuEnabled: () => !(props.embedded && !props.embeddedMenu),
  currentChatId: () => props.currentChatId,
  knowledgeBases: () => props.knowledgeBases,
  skills: () => props.skills,
  mcpTools: () => props.mcpTools
})

/** 发送：把当前引用清单随事件上抛（编辑器内部 refs 由父级决定是否清空） */
function sendFromEditor(): void {
  emit('send', [...chatRefs.value])
}

function handleSendClick(): void {
  emit('send', [...chatRefs.value])
}

// ---------- 文件选择 / 拖拽上传 ----------
const fileInputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)

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
      position: relative;
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

        // 富文本输入区：与原 textarea 同排版；引用 token 内联在文本流中
        .chat-editor {
          width: 100%;
          min-height: 24px;
          max-height: 180px;
          overflow-y: auto;
          font-size: 15px;
          line-height: 1.5;
          color: #141413;
          white-space: pre-wrap;
          word-break: break-word;
          outline: none;
          caret-color: $primary-color;
          cursor: text;

          // placeholder 绝对定位浮在下层：不占行盒，空态光标停在行首而非占位文字之后
          position: relative;

          &[data-empty]::before {
            content: attr(data-placeholder);
            position: absolute;
            left: 0;
            top: 0;
            color: #8c8a82;
            pointer-events: none;
          }

          // token 内是真实文本节点（可选中、复制经拦截转标记）；图标为装饰不参与选区
          :deep(.editor-token) {
            display: inline-flex;
            align-items: center;
            gap: 5px;
            vertical-align: baseline;
            margin: 0 2px;
            cursor: default;
            font-weight: 500;

            .token-icon {
              font-size: 13px;
              flex-shrink: 0;
              user-select: none;
              pointer-events: none;
            }

            &.is-session { color: $token-session; }
            &.is-kb { color: $token-kb; }
            &.is-skill { color: $token-skill; }
            &.is-mcp { color: $token-mcp; }
            &.is-compact { color: $token-compact; }
          }
        }
      }

      // @ / 触发的引用菜单：悬浮于输入容器上方（覆盖消息区）
      .mention-menu {
        position: absolute;
        left: 0;
        right: 0;
        bottom: calc(100% + 8px);
        z-index: 40;
        max-height: 320px;
        overflow-y: auto;
        padding: 8px;
        background: #fff;
        border: 1px solid #e8e6dc;
        border-radius: 14px;
        box-shadow: 0 12px 40px rgba(20, 20, 19, 0.14);

        .menu-section-title {
          padding: 8px 10px 4px;
          font-size: 12px;
          color: #8c8a82;
          user-select: none;
        }

        .menu-row {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 8px 10px;
          border-radius: 8px;
          cursor: pointer;
          color: #3d3b35;
          font-size: 14px;

          &:hover,
          &.active {
            background: rgba(42, 161, 169, 0.1);
            color: #141413;
          }

          .row-icon {
            flex-shrink: 0;
            font-size: 14px;
            color: #8c8a82;
          }

          &.tone-session .row-icon { color: $token-session; }
          &.tone-kb .row-icon { color: $token-kb; }
          &.tone-skill .row-icon { color: $token-skill; }
          &.tone-mcp .row-icon { color: $token-mcp; }
          &.tone-compact .row-icon { color: $token-compact; }

          .row-main {
            flex: 1;
            min-width: 0;
            display: flex;
            align-items: baseline;
            gap: 8px;
          }

          // 名称优先且完整显示（不截断）；描述在剩余空间内让位截断
          .row-title {
            flex-shrink: 0;
            white-space: nowrap;
            font-weight: 500;

            &.tone-session { color: $token-session; }
            &.tone-kb { color: $token-kb; }
            &.tone-skill { color: $token-skill; }
            &.tone-mcp { color: $token-mcp; }
            &.tone-compact { color: $token-compact; }
          }

          .row-desc {
            flex: 1;
            min-width: 0;
            font-size: 12px;
            color: #8c8a82;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }

          .row-suffix {
            flex-shrink: 0;
            font-size: 11px;
            color: #8c8a82;
            max-width: 80px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
        }

        .menu-empty {
          padding: 12px 10px;
          font-size: 13px;
          color: #8c8a82;
          user-select: none;
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

      // 模型切换器触发钮：与思考档位同款视觉（图标+文字+下箭头）
      .model-selector {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        max-width: 260px;
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

        .model-icon {
          color: $primary-color;
          font-size: 13px;
        }

        .model-label {
          min-width: 0;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .effort-caret {
          font-size: 10px;
          color: #8c8a82;
          flex-shrink: 0;
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

// 模型切换菜单（overlay 挂 body，但节点属于本组件模板、scoped 属性可命中）
.model-menu-name {
  display: inline-block;
  max-width: 240px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}

.model-check {
  color: $primary-color;
  margin-left: 8px;
  font-size: 12px;
}

// zcode 同款模型菜单：供应商为 hover 子菜单（antd a-sub-menu 右浮出模型列表）
.model-menu {
  min-width: 220px;
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #e8e6dc;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(20, 20, 19, 0.14);

  :deep(.ant-menu-item-group-title),
  :deep(.ant-menu-submenu-title) {
    user-select: none;
  }

  :deep(.ant-menu-submenu-title) {
    .group-label {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      min-width: 0;
      max-width: 220px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;

      .group-check {
        color: $primary-color;
        font-size: 12px;
      }

      &.current {
        color: #141413;
        font-weight: 500;
      }
    }
  }

  :deep(.ant-menu-item.model-item) {
    display: flex;
    align-items: center;

    .model-menu-name {
      min-width: 0;
      max-width: 240px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  :deep(.ant-menu-item.manage-item) {
    color: #6e6b62;
    user-select: none;

    &:hover {
      color: $primary-color;
    }
  }
}

// 移动端（≤768/480px）的输入区尺寸、触控目标与安全区见
// mobile/styles/chat.scss（按 html.is-mobile 标记生效，断点定义在 mobile/breakpoints.ts）
</style>
