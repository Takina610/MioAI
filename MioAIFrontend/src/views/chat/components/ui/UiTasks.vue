<template>
  <div class="ui-tasks">
    <div
      v-for="(item, i) in items"
      :key="i"
      class="ui-task"
      :class="item.status"
      @click="toggle(i)"
    >
      <span class="ui-task-check">
        <CheckOutlined v-if="item.status === 'done'" />
      </span>
      <span class="ui-task-text">{{ item.text }}</span>
      <span v-if="item.status === 'doing'" class="ui-task-doing">进行中</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CheckOutlined } from '@ant-design/icons-vue'
import { patchUiBlock } from '@/api/chat'
import type { MessageBlock, UiTaskItem } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const items = computed<UiTaskItem[]>(() =>
  ((props.block.items ?? []) as UiTaskItem[]).filter(item => item && item.text)
)

/** 勾选：乐观更新块内状态（块对象在消息 blocks 响应式树内，直改即触发渲染），
 *  落库失败（游客/网络）保留本地态——与游客会话整体不持久化的语义一致 */
async function toggle(index: number): Promise<void> {
  const item = items.value[index]
  if (!item || item.status === 'doing') return
  const next = item.status === 'done' ? 'todo' : 'done'
  item.status = next
  if (!props.chatId) return
  try {
    await patchUiBlock(props.chatId, props.block.id, index, next)
  } catch (error) {
    console.warn('任务状态未持久化（游客会话或网络问题）:', error)
  }
}
</script>

<style scoped lang="scss">
.ui-tasks {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ui-task {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: rgba(42, 161, 169, 0.06);
  }
}

.ui-task-check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border: 1.5px solid $border-warm;
  border-radius: 50%;
  font-size: 11px;
  color: #fff;
  transition: all 0.15s ease;
}

.ui-task.done .ui-task-check {
  background: $primary-color;
  border-color: $primary-color;
}

.ui-task-text {
  flex: 1;
  font-size: 13px;
  line-height: 1.6;
  color: $text-secondary;
}

.ui-task.done .ui-task-text {
  color: $text-muted;
  text-decoration: line-through;
  text-decoration-color: rgba(140, 138, 130, 0.5);
}

.ui-task-doing {
  font-size: 12px;
  color: $primary-color;
}

.ui-task.doing .ui-task-check {
  border-color: $primary-color;
}
</style>
