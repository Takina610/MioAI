<template>
  <div class="chat-list-section" :class="{ collapsed: isCollapsed }">
    <div class="section-title" v-show="!isCollapsed">历史会话</div>
    <div class="chat-list" @scroll="handleChatListScroll" ref="chatListRef">
      <template v-if="chatListLoading && chatList.length === 0">
        <div v-for="i in 3" :key="'skeleton-' + i" class="chat-item-skeleton">
          <a-skeleton :paragraph="{ rows: 1 }" :title="false" active />
        </div>
      </template>
      <a-tooltip placement="right" v-if="isCollapsed" v-for="chat in chatList" :key="chat.id">
        <template #title>{{ chat.title }}</template>
        <div
          class="chat-item chat-item-collapsed"
          :class="{ active: currentChatId === chat.id }"
          @click="emit('select', chat.id)"
        >
          <MessageOutlined />
        </div>
      </a-tooltip>
      <template v-if="!isCollapsed">
        <div
          v-for="chat in chatList"
          :key="chat.id"
          class="chat-item"
          :class="{ active: currentChatId === chat.id }"
          @click="emit('select', chat.id)"
        >
          <div class="chat-item-content">
            <div class="chat-item-title">{{ chat.title }}</div>
            <div class="chat-item-time">{{ formatTime(chat.updateTime) }}</div>
          </div>
          <a-dropdown :trigger="['click']">
            <a-button type="text" size="small" class="chat-item-more" @click.stop>
              <MoreOutlined />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="share" @click="emit('share', chat.id)">
                  <ShareAltOutlined /> 分享对话
                </a-menu-item>
                <a-menu-item key="delete" class="delete-menu-item" @click="emit('delete', chat.id)">
                  <DeleteOutlined /> 删除对话
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
        <div v-if="chatListLoading && chatList.length > 0" class="chat-list-loading">
          <a-spin size="small" />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ChatSession } from '@/types'
import {
  MessageOutlined,
  MoreOutlined,
  DeleteOutlined,
  ShareAltOutlined
} from '@ant-design/icons-vue'

defineProps<{
  chatList: ChatSession[]
  currentChatId: string
  chatListLoading: boolean
  isCollapsed: boolean
}>()

const emit = defineEmits<{
  (e: 'select', conversationId: string): void
  (e: 'share', conversationId: string): void
  (e: 'delete', conversationId: string): void
  (e: 'load-more'): void
}>()

const chatListRef = ref<HTMLElement | null>(null)

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

defineExpose({ checkListFilled, scrollToTop })
</script>

<style lang="scss" scoped>
.chat-list-section {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-top: 1px solid #e8eaed;
  padding-top: 12px;

  &.collapsed {
    padding-top: 12px;

    .chat-item {
      justify-content: center;
      padding: 10px;
    }
  }

  .section-title {
    font-size: 12px;
    color: #86909c;
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
    color: #5f6368;
    border-radius: 8px;
    transition: all 0.2s;

    &:hover {
      background: rgba(42, 161, 169, 0.08);
    }

    &.active {
      background: rgba(42, 161, 169, 0.12);
      color: $primary-color;
    }

    &.chat-item-collapsed {
      justify-content: center;
      width: 40px;
      height: 40px;
      margin: 4px auto;
      padding: 0;
    }

    .chat-item-content {
      flex: 1;
      min-width: 0;

      .chat-item-title {
        font-size: 14px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .chat-item-time {
        font-size: 12px;
        color: #909399;
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
  color: #ff4d4f !important;

  &:hover {
    background-color: #fff1f0 !important;
    color: #ff4d4f !important;
  }

  :deep(.ant-dropdown-menu-item-icon) {
    color: #ff4d4f !important;
  }
}

:deep(.delete-menu-item) {
  color: #ff4d4f !important;

  .ant-dropdown-menu-item-icon {
    color: #ff4d4f !important;
  }

  &:hover {
    background-color: #fff1f0 !important;
    color: #ff4d4f !important;
  }
}
</style>
