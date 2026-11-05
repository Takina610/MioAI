<template>
  <div class="ui-table-wrap">
    <table v-if="rows.length" class="ui-table">
      <thead>
        <tr>
          <th v-for="(col, i) in columns" :key="i">{{ col }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, ri) in rows" :key="ri">
          <td
            v-for="(cell, ci) in row"
            :key="ci"
            :class="{ 'lead-col': ci === 0 }"
          >{{ cell }}</td>
        </tr>
      </tbody>
    </table>
    <div v-else class="ui-table-empty">暂无数据</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MessageBlock } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const columns = computed(() => (props.block.columns ?? []).filter(c => c != null && c !== ''))
const rows = computed(() => (props.block.rows ?? []).filter(r => Array.isArray(r)))
</script>

<style scoped lang="scss">
.ui-table-wrap {
  overflow-x: auto;
}

.ui-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  line-height: 1.6;

  th {
    padding: 8px 12px;
    background: $bg-cream;
    color: $text-secondary;
    font-weight: 600;
    text-align: left;
    white-space: nowrap;
  }

  td {
    padding: 8px 12px;
    border-bottom: 1px solid $border-warm-subtle;
    color: $text-secondary;
  }

  tbody tr:last-child td {
    border-bottom: none;
  }

  /* 首列是比较对象的锚点（方案名/阶段名），加重视觉 */
  .lead-col {
    font-weight: 600;
    white-space: nowrap;
  }
}

.ui-table-empty {
  padding: 20px 0;
  font-size: 13px;
  color: $text-muted;
  text-align: center;
}
</style>
