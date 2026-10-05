<template>
  <TransitionGroup
    ref="rootRef"
    tag="div"
    class="attachment-cards"
    :class="{ light, card: variant === 'card' }"
    :css="false"
    @before-leave="onBeforeLeave"
    @enter="onEnter"
    @leave="onLeave"
  >
    <div
      v-for="item in items"
      :key="item.key"
      class="att-item"
      :class="{ clickable: downloadable && item.status === 'done', error: item.status === 'error' }"
      :title="item.status === 'error' ? '上传失败' : item.name"
      @click="downloadable && item.status === 'done' && handleDownload(item)"
    >
      <!-- 缩略图/图标区：图片类型直接出图（点击放大预览） -->
      <div class="att-thumb">
        <span
          v-if="item.previewSrc && item.status !== 'uploading' && !brokenThumbs.has(item.key)"
          class="att-img"
          @click.stop
        >
          <a-image
            :src="item.previewSrc"
            :alt="item.name"
            :width="40"
            :height="40"
            @error="brokenThumbs.add(item.key)"
          />
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

      <!-- 图片卡片只显示缩略图，名称/大小一概不显示；非图片保留名称与元信息 -->
      <div v-if="!isImageName(item.name)" class="att-text">
        <span class="att-name">{{ item.name }}</span>
        <span v-if="variant === 'card'" class="att-meta">{{ extLabel(item.name) }}<template v-if="sizeText(item)"> · {{ sizeText(item) }}</template></span>
        <span v-else class="att-meta">{{ sizeText(item) }}</span>
      </div>

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
  </TransitionGroup>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import gsap from 'gsap'
import { CloseOutlined } from '@ant-design/icons-vue'
import { attachmentIcon, extLabel, formatSize, isImageName } from '../attachmentUtils'
import { downloadAttachment } from '@/api/chat'
import type { AttachmentDisplay } from '@/types'

const props = defineProps<{
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

const rootRef = ref<{ $el: HTMLElement } | null>(null)

/** 加载失败的缩略图（文件可能已被删除/清理）：回退类型图标 */
const brokenThumbs = ref(new Set<string>())

function progressOf(item: AttachmentDisplay): number {
  return Math.min(1, Math.max(0, item.progress ?? 0))
}

function sizeText(item: AttachmentDisplay): string {
  return formatSize(item.size)
}

// ---------- GSAP 驱动的进出场动画（:css="false"，完成时机由 JS 回调控制，
// 不依赖 CSS transition 检测——离场宽度塌缩时兄弟卡片由文档流连续回流平滑左移） ----------

function onEnter(el: Element, done: () => void): void {
  gsap.fromTo(
    el,
    { opacity: 0, scale: 0.85 },
    { opacity: 1, scale: 1, duration: 0.2, ease: 'power2.out', onComplete: done },
  )
}

function onBeforeLeave(el: Element): void {
  const node = el as HTMLElement
  // 钉住当前宽度并转 border-box：GSAP 的 width 数值插值才有正确起点
  node.style.boxSizing = 'border-box'
  node.style.width = `${node.offsetWidth}px`
  node.style.overflow = 'hidden'
}

function onLeave(el: Element, done: () => void): void {
  gsap.to(el, {
    width: 0,
    opacity: 0,
    paddingLeft: 0,
    paddingRight: 0,
    marginLeft: -8,
    marginRight: -8,
    duration: 0.22,
    ease: 'power2.in',
    onComplete: done,
  })
}

/** 容器展开/收起（GSAP 接管，替代 CollapseTransition——少一层包装 div） */
watch(
  () => props.items.length,
  async (now, before) => {
    await nextTick()
    const root = rootRef.value?.$el as HTMLElement | undefined
    if (!root) return
    if (now === 0 && (before ?? 0) > 0) {
      gsap.to(root, {
        height: 0,
        paddingTop: 0,
        opacity: 0,
        duration: 0.25,
        ease: 'power2.out',
        overwrite: 'auto',
        onComplete: () => {
          root.style.height = ''
          root.style.paddingTop = ''
          root.style.opacity = ''
          root.style.overflow = ''
        },
      })
      root.style.overflow = 'hidden'
    } else if (now > 0 && (before ?? 0) === 0) {
      const target = root.scrollHeight
      gsap.fromTo(
        root,
        { height: 0, paddingTop: 0, opacity: 0 },
        {
          height: target,
          paddingTop: 10,
          opacity: 1,
          duration: 0.25,
          ease: 'power2.out',
          overwrite: 'auto',
          onComplete: () => {
            root.style.height = ''
            root.style.overflow = ''
          },
        },
      )
      root.style.overflow = 'hidden'
    }
  },
)

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

  // ---------- 卡片式（输入框待传区，横向胶囊） ----------
  &.card {
    .att-item {
      position: relative;
      display: flex;
      align-items: center;
      gap: 10px;
      max-width: 264px;
      padding: 7px 26px 7px 7px;
      background: #f7f8fa;
      border: 1px solid #e5e6eb;
      border-radius: 12px;

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

    .att-thumb {
      width: 40px;
      height: 40px;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 10px;
      background: #fff;
      overflow: hidden;

      .att-icon {
        font-size: 22px;
        color: $primary-color;
      }
    }

    .att-text {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .att-name {
      max-width: 168px;
      font-size: 13px;
      font-weight: 600;
      color: #1d2129;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      transition: color 0.2s;
    }

    .att-meta {
      margin-top: 2px;
      font-size: 11px;
      color: #86909c;
      white-space: nowrap;
    }

    .att-remove {
      top: -7px;
      right: -7px;
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
      width: 40px;
      height: 40px;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
      background: #fff;
      overflow: hidden;

      .att-icon {
        font-size: 20px;
        color: $primary-color;
      }
    }

    .att-text {
      display: flex;
      align-items: baseline;
      gap: 6px;
      min-width: 0;
    }

    .att-name {
      max-width: 140px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      transition: color 0.2s;
    }

    .att-meta {
      flex-shrink: 0;
      font-size: 11px;
      color: #86909c;
    }
  }

  // 图片缩略图与 a-image 预览
  .att-img {
    display: block;
    width: 100%;
    height: 100%;
    cursor: zoom-in;

    :deep(.ant-image) {
      width: 100%;
      height: 100%;
    }

    :deep(.ant-image img) {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  // X 删除按钮（两 variant 共用）
  .att-remove {
    position: absolute;
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
    transition: all 0.15s;
    z-index: 1;

    &:hover {
      background: #1d2129;
      border-color: #1d2129;
      color: #fff;
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
