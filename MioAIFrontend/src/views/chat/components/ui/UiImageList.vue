<template>
  <div class="ui-image-list">
    <div v-for="(item, i) in items" :key="i" class="ui-img-row">
      <a-image
        :src="item.thumbnailUrl || item.url"
        :alt="item.title || '图片'"
        loading="lazy"
        :width="96"
        :height="96"
        :style="{ objectFit: 'cover' }"
        :preview="{ src: item.url }"
      >
        <template #error>
          <div class="ui-img-row-fallback"><PictureOutlined /></div>
        </template>
        <template #placeholder>
          <div class="ui-img-row-loading"><a-spin size="small" /></div>
        </template>
      </a-image>
      <div class="ui-img-row-body">
        <div v-if="item.title" class="ui-img-row-title">{{ item.title }}</div>
        <div v-if="item.desc" class="ui-img-row-desc">{{ item.desc }}</div>
        <ImageSrcLine :image="item" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PictureOutlined } from '@ant-design/icons-vue'
import type { MessageBlock, UiImageMeta } from '@/types'
import ImageSrcLine from './ImageSrcLine.vue'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

/** 图文行：缩略图与文字是同一实体的联动展示；https 兜底过滤 */
const items = computed<UiImageMeta[]>(() =>
  ((props.block.items ?? []) as UiImageMeta[]).filter(it => !!it?.url && it.url.startsWith('https://'))
)
</script>

<style scoped lang="scss">
.ui-image-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 图左文右：同一实体的联动分栏（非无联动分栏），窄屏自动纵排 */
.ui-img-row {
  display: flex;
  gap: 12px;
  padding: 8px;
  background: #fff;
  border: 1px solid $border-warm-subtle;
  border-radius: 8px;

  @media (max-width: 560px) {
    flex-direction: column;
  }

  :deep(.ant-image) {
    flex-shrink: 0;
    border-radius: 6px;
    overflow: hidden;
  }
}

.ui-img-row-body {
  flex: 1;
  min-width: 0;
}

.ui-img-row-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-secondary;
  line-height: 1.5;
}

.ui-img-row-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: $text-muted;
}

.ui-img-row-fallback,
.ui-img-row-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  background: $bg-cream;
  color: $text-muted;
}
</style>
