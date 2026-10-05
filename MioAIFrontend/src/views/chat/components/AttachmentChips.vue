<template>
  <div class="attachment-chips" :class="{ light }">
    <div
      v-for="(item, index) in items"
      :key="item.path"
      class="chip"
      :class="{ clickable: downloadable }"
      :title="downloadable ? '点击下载' : item.name"
      @click="downloadable && handleDownload(item)"
    >
      <component :is="iconOf(item.name)" class="chip-icon" />
      <span class="chip-name">{{ item.name }}</span>
      <span class="chip-size">{{ sizeText(item.size) }}</span>
      <button
        v-if="removable"
        type="button"
        class="chip-remove"
        @click.stop="emit('remove', index)"
      >
        <CloseOutlined />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { markRaw, type Component } from 'vue'
import {
  CloseOutlined,
  FileExcelOutlined,
  FileMarkdownOutlined,
  FilePdfOutlined,
  FilePptOutlined,
  FileTextOutlined,
  FileUnknownOutlined,
  FileWordOutlined,
  FileZipOutlined,
  PictureOutlined,
  SoundOutlined,
  VideoCameraOutlined
} from '@ant-design/icons-vue'
import { downloadAttachment } from '@/api/chat'
import type { AttachmentItem } from '@/types'

defineProps<{
  items: AttachmentItem[]
  /** 可下载（Agent 产出）：整条 chip 点击即下载 */
  downloadable?: boolean
  /** 可移除（输入框待传列表） */
  removable?: boolean
  /** 浅色模式（用户气泡内反白） */
  light?: boolean
}>()

const emit = defineEmits<{
  (e: 'remove', index: number): void
}>()

const ICONS: Array<[RegExp, Component]> = [
  [/\.(png|jpe?g|gif|webp|svg|bmp|ico)$/i, markRaw(PictureOutlined)],
  [/\.(mp3|wav|flac|aac|ogg|m4a)$/i, markRaw(SoundOutlined)],
  [/\.(mp4|mov|avi|mkv|webm)$/i, markRaw(VideoCameraOutlined)],
  [/\.pdf$/i, markRaw(FilePdfOutlined)],
  [/\.(docx?|rtf|odt)$/i, markRaw(FileWordOutlined)],
  [/\.(xlsx?|csv|ods)$/i, markRaw(FileExcelOutlined)],
  [/\.(pptx?)$/i, markRaw(FilePptOutlined)],
  [/\.md$/i, markRaw(FileMarkdownOutlined)],
  [/\.(zip|rar|7z|tar|gz|bz2)$/i, markRaw(FileZipOutlined)],
  [/\.(txt|log|json|xml|ya?ml|html?|css|js|ts|py|java|cs|sh)$/i, markRaw(FileTextOutlined)]
]

function iconOf(name: string): Component {
  for (const [pattern, icon] of ICONS) {
    if (pattern.test(name)) return icon
  }
  return FileUnknownOutlined
}

function sizeText(size: number): string {
  if (!size) return ''
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(size / 1024))} KB`
}

async function handleDownload(item: AttachmentItem): Promise<void> {
  try {
    await downloadAttachment(item.path, item.name)
  } catch (error) {
    console.error('附件下载失败:', error)
  }
}
</script>

<style lang="scss" scoped>
.attachment-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  .chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    max-width: 260px;
    padding: 4px 10px;
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

        .chip-name {
          color: $primary-color;
        }
      }
    }

    .chip-icon {
      flex-shrink: 0;
      font-size: 13px;
      color: $primary-color;
    }

    .chip-name {
      max-width: 150px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      transition: color 0.2s;
    }

    .chip-size {
      flex-shrink: 0;
      font-size: 11px;
      color: #86909c;
    }

    .chip-remove {
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 16px;
      height: 16px;
      padding: 0;
      border: none;
      border-radius: 50%;
      background: transparent;
      color: #86909c;
      font-size: 10px;
      cursor: pointer;
      transition: all 0.15s;

      &:hover {
        background: #e5e6eb;
        color: #1d2129;
      }
    }
  }

  // 用户气泡内：反白配色
  &.light .chip {
    background: rgba(255, 255, 255, 0.14);
    border-color: rgba(255, 255, 255, 0.25);
    color: #fff;

    .chip-icon {
      color: rgba(255, 255, 255, 0.85);
    }

    .chip-size {
      color: rgba(255, 255, 255, 0.65);
    }

    .chip-remove {
      color: rgba(255, 255, 255, 0.7);

      &:hover {
        background: rgba(255, 255, 255, 0.2);
        color: #fff;
      }
    }
  }
}
</style>
