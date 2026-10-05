<template>
  <div class="attachment-cards" :class="{ light, card: variant === 'card' }">
    <div
      v-for="item in items"
      :key="item.key"
      class="att-item"
      :class="{ clickable: downloadable && item.status === 'done', error: item.status === 'error' }"
      :title="item.status === 'error' ? '上传失败' : item.name"
      @click="downloadable && item.status === 'done' && handleDownload(item)"
    >
      <!-- 缩略图/图标区 -->
      <div class="att-thumb">
        <img v-if="item.previewSrc && item.status !== 'uploading'" :src="item.previewSrc" :alt="item.name" />
        <svg v-else-if="item.status === 'uploading'" class="ring" viewBox="0 0 36 36">
          <circle class="ring-bg" cx="18" cy="18" r="15.5" />
          <circle
            class="ring-fg"
            cx="18"
            cy="18"
            r="15.5"
            :stroke-dasharray="`${Math.max(2, 97.4 * progressOf(item))} 97.4`"
          />
          <text x="18" y="21" text-anchor="middle">{{ Math.round(progressOf(item) * 100) }}%</text>
        </svg>
        <component v-else :is="attachmentIcon(item.name)" class="att-icon" />
      </div>

      <!-- 卡片式：文件名在下方 -->
      <span v-if="variant === 'card'" class="att-name">{{ item.name }}</span>
      <!-- 行式：文件名+大小在右侧 -->
      <template v-else>
        <span class="att-name">{{ item.name }}</span>
        <span class="att-size">{{ formatSize(item.size) }}</span>
      </template>

      <!-- 上传成功后右上角 X 删除 -->
      <button
        v-if="removable && item.status !== 'uploading'"
        type="button"
        class="att-remove"
        :title="item.status === 'error' ? '移除' : '删除'"
        @click.stop="emit('remove', item.key)"
      >
        <CloseOutlined />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { CloseOutlined } from '@ant-design/icons-vue'
import { attachmentIcon, formatSize } from '../attachmentUtils'
import { downloadAttachment } from '@/api/chat'
import type { AttachmentDisplay } from '@/types'

defineProps<{
  items: AttachmentDisplay[]
  /** 输入框卡片式 / 消息内行式 */
  variant?: 'card' | 'chip'
  /** 可下载（Agent 产出）：整条点击即下载 */
  downloadable?: boolean
  /** 可移除（输入框待传区） */
  removable?: boolean
  /** 浅色模式（用户气泡内反白） */
  light?: boolean
}>()

const emit = defineEmits<{
  (e: 'remove', key: string): void
}>()

function progressOf(item: AttachmentDisplay): number {
  return Math.min(1, Math.max(0, item.progress ?? 0))
}

async function handleDownload(item: AttachmentDisplay): Promise<void> {
  try {
    await downloadAttachment(item.key, item.name)
  } catch (error) {
    console.error('附件下载失败:', error)
  }
}
</script>

<style lang="scss" scoped>
.attachment-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  // ---------- 卡片式（输入框待传区） ----------
  &.card {
    .att-item {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      width: 72px;
      padding: 6px 4px 5px;
      background: #f7f8fa;
      border: 1px solid #e5e6eb;
      border-radius: 10px;
      transition: border-color 0.2s;

      &:hover {
        border-color: #c9cdd4;
      }

      &.error .att-thumb {
        color: #d48806;
      }
    }

    .att-thumb {
      width: 52px;
      height: 52px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
      background: #fff;
      overflow: hidden;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .att-icon {
        font-size: 24px;
        color: $primary-color;
      }
    }

    .att-name {
      max-width: 64px;
      margin-top: 4px;
      font-size: 11px;
      color: #4e5969;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .att-remove {
      position: absolute;
      top: -6px;
      right: -6px;
      width: 18px;
      height: 18px;
      padding: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: 1px solid #e5e6eb;
      border-radius: 50%;
      background: #fff;
      color: #86909c;
      font-size: 9px;
      cursor: pointer;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
      transition: all 0.15s;

      &:hover {
        background: #1d2129;
        border-color: #1d2129;
        color: #fff;
      }
    }
  }

  // ---------- 行式（消息内） ----------
  &:not(.card) {
    .att-item {
      position: relative;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      max-width: 260px;
      padding: 5px 10px;
      background: #f7f8fa;
      border: 1px solid #e5e6eb;
      border-radius: 8px;
      font-size: 12px;
      color: #4e5969;
      user-select: none;

      &.clickable {
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
          background: #eef1f4;

          .att-name {
            color: $primary-color;
          }
        }
      }
    }

    .att-thumb {
      width: 30px;
      height: 30px;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 6px;
      background: #fff;
      overflow: hidden;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .att-icon {
        font-size: 16px;
        color: $primary-color;
      }
    }

    .att-name {
      max-width: 140px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      transition: color 0.2s;
    }

    .att-size {
      flex-shrink: 0;
      font-size: 11px;
      color: #86909c;
    }
  }

  // 进度环（SVG）
  .ring {
    width: 36px;
    height: 36px;

    .ring-bg {
      fill: none;
      stroke: #e5e6eb;
      stroke-width: 3;
    }

    .ring-fg {
      fill: none;
      stroke: $primary-color;
      stroke-width: 3;
      stroke-linecap: round;
      transform: rotate(-90deg);
      transform-origin: center;
      transition: stroke-dasharray 0.2s;
    }

    text {
      font-size: 8px;
      fill: #4e5969;
    }
  }

  // 用户气泡内：反白配色
  &.light {
    &:not(.card) .att-item {
      background: rgba(255, 255, 255, 0.14);
      border-color: rgba(255, 255, 255, 0.25);
      color: #fff;

      .att-thumb {
        background: rgba(255, 255, 255, 0.2);
      }

      .att-icon {
        color: rgba(255, 255, 255, 0.85);
      }

      .att-size {
        color: rgba(255, 255, 255, 0.65);
      }
    }

    .ring text {
      fill: #fff;
    }

    .ring-bg {
      stroke: rgba(255, 255, 255, 0.3);
    }
  }
}
</style>
