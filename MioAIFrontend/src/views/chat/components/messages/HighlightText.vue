<template>
  <span class="highlight-text">
    <template v-for="(seg, i) in segments" :key="i">
      <mark v-if="seg.hit" class="hl">{{ seg.text }}</mark>
      <template v-else>{{ seg.text }}</template>
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * fuse.js 匹配高亮：按 [start, end] 闭区间切分文本，命中段包 <mark>。
 * 重叠/相邻区间先合并，indices 缺省时整段原样输出。
 */
const props = defineProps<{
  text: string
  indices?: Array<[number, number]>
}>()

const segments = computed<Array<{ text: string; hit: boolean }>>(() => {
  const t = props.text
  const raw = props.indices ?? []
  if (!raw.length || !t) return [{ text: t, hit: false }]

  const merged: Array<[number, number]> = []
  for (const seg of [...raw].sort((a, b) => a[0] - b[0])) {
    const last = merged[merged.length - 1]
    if (last && seg[0] <= last[1] + 1) {
      last[1] = Math.max(last[1], seg[1])
    } else {
      merged.push([seg[0], seg[1]])
    }
  }

  const out: Array<{ text: string; hit: boolean }> = []
  let pos = 0
  for (const [s, e] of merged) {
    if (s > pos) out.push({ text: t.slice(pos, s), hit: false })
    out.push({ text: t.slice(s, e + 1), hit: true })
    pos = e + 1
  }
  if (pos < t.length) out.push({ text: t.slice(pos), hit: false })
  return out
})
</script>
