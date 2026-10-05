<template>
  <div
    class="att-item"
    :class="[{ card: variant === 'card' }, { bare: variant === 'card' && isImageName(item.name) }, { light }, { clickable: downloadable && item.status === 'done' }, { error: item.status === 'error' }]"
    :title="item.status === 'error' ? '上传失败' : item.name"
    @click="downloadable && item.status === 'done' && handleDownload()"
  >
    <!-- 缩略图/图标区：图片类型直接出图（点击放大预览），悬浮只显示居中眼睛 -->
    <div class="att-thumb">
      <span
        v-if="item.previewSrc && item.status !== 'uploading' && !broken"
        class="att-img"
        @click.stop
      >
        <a-image
          :src="item.previewSrc"
          :alt="item.name"
          :width="variant === 'card' ? 86 : 40"
          :height="variant === 'card' ? 86 : 40"
          @error="broken = true"
        />
        <span class="att-eye"><EyeOutlined /></span>
      </span>
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

    <!-- 图片卡片只显示缩略图：名称/大小一概不渲染；非图片保留名称与元信息 -->
    <div v-if="!isImageName(item.name)" class="att-text">
      <span class="att-name">{{ item.name }}</span>
      <span v-if="variant === 'card'" class="att-meta">{{ extLabel(item.name) }}<template v-if="sizeText(item)"> · {{ sizeText(item) }}</template></span>
      <span v-else class="att-meta">{{ sizeText(item) }}</span>
    </div>

    <!-- 上传成功后右上角 X 删除（离场动画期间隐藏防重复点击） -->
    <button
      v-if="removable && item.status !== 'uploading' && !leaving"
      type="button"
      class="att-remove"
      :title="item.status === 'error' ? '移除' : '删除'"
      @click.stop="emit('remove', item.key)"
    >
      <CloseOutlined />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CloseOutlined, EyeOutlined } from '@ant-design/icons-vue'
import { attachmentIcon, extLabel, formatSize, isImageName } from '../attachmentUtils'
import { downloadAttachment } from '@/api/chat'
import type { AttachmentDisplay } from '@/types'

const props = defineProps<{
  item: AttachmentDisplay
  /** 输入框卡片式 / 消息内行式 */
  variant?: 'card' | 'chip'
  /** 可下载（Agent 产出）：整条点击即下载 */
  downloadable?: boolean
  /** 可移除（输入框待传区） */
  removable?: boolean
  /** 浅色模式（用户气泡内反白） */
  light?: boolean
  /** 离场动画进行中（隐藏 X，防重复触发） */
  leaving?: boolean
}>()

const emit = defineEmits<{
  (e: 'remove', key: string): void
}>()

/** 缩略图加载失败（文件可能已被删除/清理）：回退类型图标 */
const broken = ref(false)

function progressOf(item: AttachmentDisplay): number {
  return Math.min(1, Math.max(0, item.progress ?? 0))
}

function sizeText(item: AttachmentDisplay): string {
  return formatSize(item.size)
}

async function handleDownload(): Promise<void> {
  try {
    await downloadAttachment(props.item.key, props.item.name)
  } catch (error) {
    console.error('附件下载失败:', error)
  }
}
</script>

<style lang="scss" scoped>
.att-item {
  position: relative;

  // ---------- 卡片式（输入框待传区，横向胶囊） ----------
  // inline-flex：卡片直接作为输入容器子元素（无任何包装层），同行自然排列换行
  &.card {
    display: inline-flex;
    align-items: center;
    gap: 12px;
    max-width: 264px;
    margin: 10px 0 0 16px;
    padding: 23px 26px 23px 16px;
    background: #f7f8fa;
    border: 1px solid #e5e6eb;
    border-radius: 12px;
    vertical-align: top;
    transition: border-color 0.2s;

    &:hover {
      border-color: #c9cdd4;

      .att-name {
        color: $primary-color;
      }
    }

    &.error {
      border-color: #f0c6a0;
      background: #fff9f0;
    }
  }

  // 图片卡片：裸缩略图——无可见外框/背景，直接显示图片本身（与文档卡同高齐平：
  // 透明 1px 边框补齐文档卡的边框厚度，几何完全一致）
  &.bare.card {
    padding: 0;
    background: transparent;
    border: 1px solid transparent;
    max-width: none;

    &:hover {
      border-color: transparent;
    }
  }

  // 缩略图尺寸（复合选择器：.card 在同一元素上，SCSS 的 `.card &` 会编译成
  // 祖先选择器永不命中——缩略图尺寸必须写在这里才能生效）
  // DeepSeek 比例：文档卡 86px 高（40px 图标 + 上下 23px 留白），图片缩略图与卡同高
  &.card .att-thumb {
    width: 40px;
    height: 40px;
    border-radius: 8px;

    .att-icon {
      font-size: 22px;
    }
  }

  &.bare.card .att-thumb {
    width: 86px;
    height: 86px;
    border-radius: 12px;
  }

  &:not(.card) .att-thumb {
    width: 40px;
    height: 40px;
    border-radius: 8px;

    .att-icon {
      font-size: 20px;
    }
  }

  // ---------- 行式（消息内） ----------
  &:not(.card) {
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
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fff;
    overflow: hidden;

    .att-icon {
      color: $primary-color;
    }
  }

  .att-text {
    display: flex;
    flex-direction: column;
    min-width: 0;

    :not(.card) & {
      flex-direction: row;
      align-items: baseline;
      gap: 6px;
    }
  }

  .att-name {
    max-width: 168px;
    font-size: 14px;
    font-weight: 600;
    color: #1d2129;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: color 0.2s;

    :not(.card) & {
      max-width: 140px;
      font-weight: 400;
    }
  }

  .att-meta {
    margin-top: 3px;
    font-size: 12px;
    color: #86909c;
    white-space: nowrap;

    :not(.card) & {
      margin-top: 0;
      flex-shrink: 0;
    }
  }

  // 图片缩略图：antd 默认遮罩（含 ... 文案）整个隐藏，自绘居中眼睛
  .att-img {
    position: relative;
    display: block;
    width: 100%;
    height: 100%;

    :deep(.ant-image) {
      width: 100%;
      height: 100%;
    }

    :deep(.ant-image img) {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    :deep(.ant-image-mask) {
      display: none;
    }

    // 悬浮：仅居中眼睛 icon（pointer-events:none 不挡 a-image 的点击预览）
    .att-eye {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.35);
      color: #fff;
      font-size: 18px;
      opacity: 0;
      transition: opacity 0.15s;
      pointer-events: none;
    }

    &:hover .att-eye {
      opacity: 1;
    }
  }

  // X 删除按钮
  // X 删除按钮：悬浮卡片时才出现（平时隐藏且不可点）
  .att-remove {
    position: absolute;
    top: -7px;
    right: -7px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    padding: 0;
    border: 1px solid #e5e6eb;
    border-radius: 50%;
    background: #fff;
    color: #86909c;
    font-size: 9px;
    cursor: pointer;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.15s, background 0.15s, color 0.15s, border-color 0.15s;
    z-index: 1;

    &:hover {
      background: #1d2129;
      border-color: #1d2129;
      color: #fff;
    }
  }

  &:hover > .att-remove {
    opacity: 1;
    pointer-events: auto;
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
    &:not(.card) {
      background: rgba(255, 255, 255, 0.14);
      border-color: rgba(255, 255, 255, 0.25);
      color: #fff;

      .att-thumb {
        background: rgba(255, 255, 255, 0.2);
      }

      .att-icon {
        color: rgba(255, 255, 255, 0.85);
      }

      .att-name {
        color: #fff;
      }

      .att-meta {
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
