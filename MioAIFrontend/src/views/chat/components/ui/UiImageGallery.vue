<template>
  <div class="ui-gallery">
    <a-image-preview-group>
      <a-image
        v-for="(img, i) in images"
        :key="i"
        :src="img.thumbnailUrl || img.url"
        :alt="img.title || '图片'"
        loading="lazy"
        :style="{ objectFit: 'cover' }"
        :preview="{ src: img.url }"
      >
        <template #error>
          <div class="ui-gallery-fallback"><PictureOutlined /></div>
        </template>
        <template #placeholder>
          <div class="ui-gallery-loading"><a-spin size="small" /></div>
        </template>
      </a-image>
    </a-image-preview-group>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PictureOutlined } from '@ant-design/icons-vue'
import type { MessageBlock, UiImageMeta } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const images = computed<UiImageMeta[]>(() =>
  (props.block.images ?? []).filter((it): it is UiImageMeta => !!it?.url && it.url.startsWith('https://'))
)
</script>

<style scoped lang="scss">
/* 图集网格：正方形裁切，点击整组翻看大图；≤4 张时同样是网格（不引入轮播） */
.ui-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 8px;

  :deep(.ant-image) {
    aspect-ratio: 1;
    border-radius: 8px;
    overflow: hidden;
    background: $bg-cream;
  }

  :deep(img) {
    width: 100%;
    height: 100%;
  }
}

.ui-gallery-fallback,
.ui-gallery-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 120px;
  background: $bg-cream;
  color: $text-muted;
}
</style>
