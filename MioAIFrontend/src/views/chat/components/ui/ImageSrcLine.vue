<template>
  <div v-if="hasAny" class="img-src-line">
    <template v-if="image.author || image.license || sourceLink">
      <span v-if="image.author">{{ image.author }}</span>
      <span v-if="image.author && image.license">·</span>
      <span v-if="image.license" class="img-src-license">{{ image.license }}</span>
      <span v-if="sourceLink && (image.author || image.license)">·</span>
      <a v-if="sourceLink" :href="sourceLink" target="_blank" rel="noopener noreferrer" class="img-src-link">
        来源{{ image.sourceName ? ` ${image.sourceName}` : '' }}
      </a>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { UiImageMeta } from '@/types'

const props = defineProps<{ image: UiImageMeta }>()

/** 来源页只放行 https（防模型伪造 javascript:/http 链接，后端已校验，前端二次防御） */
const sourceLink = computed(() =>
  props.image.sourceUrl && props.image.sourceUrl.startsWith('https://') ? props.image.sourceUrl : ''
)

const hasAny = computed(() => props.image.author || props.image.license || sourceLink.value)
</script>

<style scoped lang="scss">
/* 图片来源行：作者 · 授权 · 来源链接（照实展示，无授权信息不声称可自由使用） */
.img-src-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: $text-muted;
}

.img-src-license {
  text-transform: uppercase;
}

.img-src-link {
  color: $primary-color;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}
</style>
