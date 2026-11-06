<template>
  <div class="ui-resource-card">
    <span class="ui-resource-icon" :class="block.resourceType">
      <ApiOutlined v-if="block.resourceType === 'mcp'" />
      <DatabaseOutlined v-else />
    </span>
    <div class="ui-resource-card-body">
      <div class="ui-resource-card-head">
        <span class="ui-resource-card-name">{{ block.name }}</span>
        <span v-if="block.status" class="ui-resource-card-status">{{ block.status }}</span>
      </div>
      <div v-if="detailText" class="ui-resource-card-detail">{{ detailText }}</div>
    </div>
    <div v-if="actions.length" class="ui-resource-card-actions">
      <button
        v-for="(action, i) in actions"
        :key="i"
        class="ui-resource-card-btn"
        :class="{ primary: i === 0 }"
        @click="emit('navigate', action.path)"
      >{{ action.label }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ApiOutlined, DatabaseOutlined } from '@ant-design/icons-vue'
import type { MessageBlock } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()
/** 站内跳转：路径由后端生成（/dashboard/mcp/:id 或 /dashboard/knowledge/:id） */
const emit = defineEmits<{ (e: 'navigate', path: string): void }>()

/** 描述与 detail 合并成一行（有 detail 时优先，避免卡片过高） */
const detailText = computed(() => {
  const parts = [props.block.detail, props.block.description].filter(Boolean)
  return parts.join(' · ')
})

/** 只放行站内绝对路径，杜绝外链/伪协议 */
const actions = computed(() =>
  (props.block.jumpActions ?? []).filter(a => a?.label && a?.path?.startsWith('/'))
)
</script>

<style scoped lang="scss">
.ui-resource-card {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.ui-resource-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  font-size: 20px;
  border-radius: 10px;

  // 用主色透明度分级，不引入新色相
  &.mcp {
    color: $primary-color;
    background: rgba($primary-color, 0.1);
  }

  &.knowledge {
    color: $primary-color;
    background: rgba($primary-color, 0.16);
  }
}

.ui-resource-card-body {
  flex: 1;
  min-width: 180px;
}

.ui-resource-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ui-resource-card-name {
  font-size: 15px;
  font-weight: 600;
  color: $text-secondary;
}

.ui-resource-card-status {
  padding: 1px 7px;
  font-size: 11px;
  line-height: 1.6;
  color: $text-muted;
  background: $bg-cream;
  border: 1px solid $border-warm-subtle;
  border-radius: 10px;
}

.ui-resource-card-detail {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: $text-muted;
}

.ui-resource-card-actions {
  display: flex;
  gap: 8px;
}

.ui-resource-card-btn {
  padding: 6px 14px;
  font-size: 13px;
  color: $text-secondary;
  background: #fff;
  border: 1px solid $border-warm-subtle;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;

  &:hover {
    border-color: $primary-color;
    color: $primary-color;
  }

  &.primary {
    color: #fff;
    background: $primary-color;
    border-color: $primary-color;

    &:hover {
      opacity: 0.9;
      color: #fff;
    }
  }
}
</style>
