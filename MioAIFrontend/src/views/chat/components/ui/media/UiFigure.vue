<template>
  <figure class="ui-figure">
    <a-image
      :src="img.url"
      :alt="img.alt || img.title || caption"
      loading="lazy"
      :wrapper-style="{ display: 'block' }"
      :style="{ aspectRatio: ratioCss, objectFit: fit }"
      :preview="{ src: img.url }"
    >
      <template #error>
        <div class="ui-figure-fallback">
          <PictureOutlined />
          <span>{{ img.title || '图片加载失败' }}</span>
        </div>
      </template>
      <template #placeholder>
        <div class="ui-figure-loading"><a-spin size="small" /></div>
      </template>
    </a-image>
    <figcaption class="ui-figure-caption">
      <span>{{ caption }}</span>
      <ImageSrcLine :image="img" />
    </figcaption>
  </figure>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PictureOutlined } from '@ant-design/icons-vue'
import type { MessageBlock, UiImageMeta } from '@/types'
import ImageSrcLine from './ImageSrcLine.vue'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const img = computed<UiImageMeta>(() => props.block.image ?? { url: '' })
const caption = computed(() => props.block.caption || '')

const ratioCss = computed(() => {
  const ratio = props.block.ratio
  if (ratio && ratio !== 'auto') return ratio.replace('/', ' / ')
  if (img.value.width && img.value.height) return `${img.value.width} / ${img.value.height}`
  return '16 / 9'
})

const fit = computed(() => (props.block.fit === 'contain' ? 'contain' : 'cover'))
</script>

<style scoped lang="scss">
.ui-figure {
  margin: 0;

  :deep(img) {
    width: 100%;
    border-radius: 8px;
    background: $bg-cream;
  }
}

.ui-figure-caption {
  display: flex;
  flex-direction: column;
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: $text-muted;
}

.ui-figure-fallback {
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

.ui-figure-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 140px;
  background: $bg-cream;
  border-radius: 8px;
}
</style>
