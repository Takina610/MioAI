<template>
  <span class="hit-text">
    <template v-for="(seg, i) in segments" :key="i">
      <mark v-if="seg.hit" class="hit-mark">{{ seg.text }}</mark>
      <template v-else>{{ seg.text }}</template>
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { extractQueryTerms, splitHitSegments } from '../composables/hitHighlight'

const props = defineProps<{
  text: string
  query: string
}>()

const segments = computed(() => splitHitSegments(props.text, extractQueryTerms(props.query)))
</script>

<style lang="scss" scoped>
.hit-mark {
  background: rgba($primary-color, 0.16);
  color: inherit;
  border-radius: 2px;
  padding: 0 1px;
  box-shadow: inset 0 -1.5px 0 rgba($primary-color, 0.45);
}
</style>
