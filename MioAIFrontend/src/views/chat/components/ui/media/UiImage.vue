<template>
  <div class="ui-image">
    <a-image
      :src="img.url"
      :alt="img.alt || img.title || '图片'"
      loading="lazy"
      :wrapper-style="{ display: 'block' }"
      :style="{ aspectRatio: ratioCss, objectFit: fit }"
      :preview="{ src: img.url }"
      @error="onError"
    >
      <template #error>
        <div class="ui-image-fallback">
          <PictureOutlined />
          <span>{{ img.title || img.alt || '图片加载失败' }}</span>
        </div>
      </template>
      <template #placeholder>
        <div class="ui-image-loading"><a-spin size="small" /></div>
      </template>
    </a-image>
    <div v-if="img.caption || img.title" class="ui-image-caption">{{ img.caption || img.title }}</div>
    <ImageSrcLine :image="img" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PictureOutlined } from '@ant-design/icons-vue'
import type { MessageBlock, UiImageMeta } from '@/types'
import ImageSrcLine from './ImageSrcLine.vue'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

/** 图片安全兜底：只渲染 https 地址（后端已按登记簿校验，前端二次防御） */
const img = computed<UiImageMeta>(() => props.block.image ?? { url: '' })

const ratioCss = computed(() => {
  const ratio = props.block.ratio
  if (ratio && ratio !== 'auto') return ratio.replace('/', ' / ')
  if (img.value.width && img.value.height) return `${img.value.width} / ${img.value.height}`
  return '16 / 9'
})

const fit = computed(() => (props.block.fit === 'contain' ? 'contain' : 'cover'))

const onError = () => {
  /* antd Image 错误槽位接管展示；占位不吞整条消息 */
}
</script>

<style scoped lang="scss">
.ui-image {
  :deep(img) {
    width: 100%;
    border-radius: 8px;
    background: $bg-cream;
  }
}

.ui-image-caption {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: $text-secondary;
}

.ui-image-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 140px;
  background: $bg-cream;
  border-radius: 8px;
  font-size: 13px;
  color: $text-muted;
}

.ui-image-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 140px;
  background: $bg-cream;
  border-radius: 8px;
}
</style>
