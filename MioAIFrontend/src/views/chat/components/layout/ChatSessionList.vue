<template>
  <div class="chat-list-section">
    <div class="section-title">历史会话</div>
    <div class="chat-list" @scroll="handleChatListScroll" ref="chatListRef">
      <template v-if="chatListLoading && chatList.length === 0">
        <div v-for="i in 3" :key="'skeleton-' + i" class="chat-item-skeleton">
          <a-skeleton :paragraph="{ rows: 1 }" :title="false" active />
        </div>
      </template>
      <div
        v-for="chat in chatList"
        :key="chat.id"
        class="chat-item"
        :class="{ active: currentChatId === chat.id }"
        @click="emit('select', chat.id)"
      >
        <div class="chat-item-content">
          <div class="chat-item-title">
            <RoseFourLoader
              v-if="streamingChatIds?.includes(chat.id)"
              :size="14"
              class="chat-item-spinner"
            />
            <PushpinFilled v-if="chat.pinned" class="chat-item-pin" />
            <span class="chat-item-title-text">{{ chat.title }}</span>
          </div>
          <div class="chat-item-time">{{ formatTime(chat.updateTime) }}</div>
        </div>
        <a-dropdown
          :trigger="['click']"
          :open="openMenuKey === chat.id"
          @open-change="(v: boolean) => (openMenuKey = v ? chat.id : '')"
        >
          <a-button type="text" size="small" class="chat-item-more" @click.stop>
            <MoreOutlined />
          </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="pin" @click="openMenuKey = ''; emit('pin', chat.id, !chat.pinned)">
                  <PushpinOutlined /> {{ chat.pinned ? '取消置顶' : '置顶' }}
                </a-menu-item>
                <a-menu-item key="share" @click="openMenuKey = ''; emit('share', chat.id)">
                  <ShareAltOutlined /> 分享对话
                </a-menu-item>
                <a-menu-item key="delete" class="delete-menu-item" @click="openMenuKey = ''; emit('delete', chat.id)">
                  <DeleteOutlined /> 删除对话
                </a-menu-item>
              </a-menu>
            </template>
        </a-dropdown>
      </div>
      <div v-if="chatListLoading && chatList.length > 0" class="chat-list-loading">
        <a-spin size="small" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ChatSession } from '@/types'
import RoseFourLoader from '@/components/ui/RoseFourLoader.vue'
import {
  MoreOutlined,
  DeleteOutlined,
  ShareAltOutlined,
  PushpinOutlined,
  PushpinFilled
} from '@ant-design/icons-vue'

defineProps<{
  chatList: ChatSession[]
  currentChatId: string
  chatListLoading: boolean
  /** 正在流式执行中的会话（对应列表项标题前显示加载动画） */
  streamingChatIds?: string[]
}>()

const emit = defineEmits<{
  (e: 'select', conversationId: string): void
  (e: 'share', conversationId: string): void
  (e: 'delete', conversationId: string): void
  (e: 'pin', conversationId: string, pinned: boolean): void
  (e: 'load-more'): void
}>()

const chatListRef = ref<HTMLElement | null>(null)

/** 受控的行内"…"菜单（单开）：面板收起时由父级调 closeMenus 收掉挂在 body 上的浮层 */
const openMenuKey = ref('')

function closeMenus(): void {
  openMenuKey.value = ''
}

function handleChatListScroll(event: Event): void {
  const target = event.target as HTMLElement
  const scrollBottom = target.scrollHeight - target.scrollTop - target.clientHeight

  if (scrollBottom < 50) {
    emit('load-more')
  }
}

/** 列表撑不满视口时继续加载下一页 */
function checkListFilled(): void {
  const el = chatListRef.value
  if (el && el.scrollHeight <= el.clientHeight) {
    emit('load-more')
  }
}

function scrollToTop(): void {
  if (chatListRef.value) {
    chatListRef.value.scrollTop = 0
  }
}

function formatTime(date: Date | string): string {
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return d.toLocaleDateString()
}

defineExpose({ checkListFilled, scrollToTop, closeMenus })
</script>

<style lang="scss" scoped>
.chat-list-section {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-top: 1px solid #e8e6dc;
  padding-top: 12px;

  .section-title {
    font-size: 12px;
    color: #8c8a82;
    padding: 0 4px;
    margin-bottom: 8px;
    font-weight: 500;
  }
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;

  &:hover {
    scrollbar-color: rgba(0, 0, 0, 0.2) transparent;
  }

  &::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }

  &::-webkit-scrollbar-button {
    display: none;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: transparent;
    border-radius: 3px;
    transition: background 0.3s;
  }

  &:hover::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.15);
  }

  &:hover::-webkit-scrollbar-thumb:hover {
    background: rgba(0, 0, 0, 0.25);
  }

  .chat-item {
    display: flex;
    align-items: center;
    padding: 10px 12px;
    margin: 4px 0;
    cursor: pointer;
    color: #5f5d55;
    border-radius: 8px;
    transition: all 0.2s;

    &:hover {
      background: rgba(42, 161, 169, 0.08);
    }

    &.active {
      background: rgba(42, 161, 169, 0.12);
      color: $primary-color;
    }

    .chat-item-content {
      flex: 1;
      min-width: 0;

      .chat-item-title {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 14px;
        min-width: 0;

        .chat-item-spinner {
          flex-shrink: 0;
        }

        // 置顶图钉：主色小标
        .chat-item-pin {
          flex-shrink: 0;
          font-size: 11px;
          color: $primary-color;
        }

        .chat-item-title-text {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }

      .chat-item-time {
        font-size: 12px;
        color: #8c8a82;
        margin-top: 2px;
      }
    }

    .chat-item-more {
      opacity: 0;
      transition: opacity 0.2s;
      rotate: 90deg;
    }

    &:hover .chat-item-more {
      opacity: 1;
    }
  }

  .chat-item-skeleton {
    padding: 10px 12px;
    margin: 4px 0;
  }

  .chat-list-loading {
    display: flex;
    justify-content: center;
    padding: 12px 0;
  }
}

.delete-menu-item {
  color: #c0453a !important;

  &:hover {
    background-color: #f9ece9 !important;
    color: #c0453a !important;
  }

  :deep(.ant-dropdown-menu-item-icon) {
    color: #c0453a !important;
  }
}

:deep(.delete-menu-item) {
  color: #c0453a !important;

  .ant-dropdown-menu-item-icon {
    color: #c0453a !important;
  }

  &:hover {
    background-color: #f9ece9 !important;
    color: #c0453a !important;
  }
}
</style>
