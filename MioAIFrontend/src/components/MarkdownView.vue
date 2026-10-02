<template>
  <div class="md-view">
    <div v-for="(block, index) in blocks" :key="block.key" class="md-block" v-html="block.html"></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderBlocks } from '@/utils/markdown/streaming'
import 'katex/dist/katex.min.css'
import 'highlight.js/styles/github.css'

const props = defineProps<{ content: string }>()

const blocks = computed(() => renderBlocks(props.content))
</script>
