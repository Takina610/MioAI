<template>
  <div class="resource-item" :class="{ 'already-added': alreadyAdded }">
    <div class="resource-info" :class="{ clickable: selectable }" @click="handleClick">
      <component :is="icon" class="resource-icon" />
      <div class="resource-text">
        <div class="resource-header-row">
          <span class="resource-name">{{ name }}</span>
          <span v-if="alreadyAdded" class="already-added-tag">已添加</span>
        </div>
        <a-typography-paragraph
          :ellipsis="{ rows: 2, tooltip: true }"
          :content="description || '暂无描述'"
          class="resource-desc"
        />
      </div>
    </div>
    <div class="resource-actions" @click.stop>
      <a-button
        v-if="count > 0"
        type="text"
        size="small"
        class="expand-btn"
        @click="toggleExpand"
      >
        <component :is="countIcon" v-if="countIcon" /> {{ count }}
      </a-button>
      <div v-if="removable" class="delete-btn" @click="emit('remove')">
        <DeleteOutlined />
      </div>
      <CheckOutlined v-else-if="selectable && selected && !alreadyAdded" class="check-icon" />
    </div>
  </div>
  <div v-if="expanded && count > 0" class="resource-expand-content">
    <slot name="expand" />
  </div>
</template>

<script setup lang="ts">
import { ref, type Component } from 'vue'
import { DeleteOutlined, CheckOutlined, FileTextOutlined } from '@ant-design/icons-vue'

const props = withDefaults(defineProps<{
  icon: Component
  name: string
  description?: string
  /** 子项数量，大于 0 才显示展开按钮 */
  count?: number
  countIcon?: Component
  /** 抽屉选择模式：点击条目切换选中 */
  selectable?: boolean
  selected?: boolean
  /** 已添加到智能体（显示已添加标签） */
  alreadyAdded?: boolean
  /** 显示移除按钮（主列表常显；抽屉中仅已添加项显示） */
  removable?: boolean
}>(), {
  description: '',
  count: 0,
  countIcon: FileTextOutlined,
  selectable: false,
  selected: false,
  alreadyAdded: false,
  removable: false
})

const emit = defineEmits<{
  (e: 'select'): void
  (e: 'remove'): void
  (e: 'expand'): void
}>()

const expanded = ref(false)

function handleClick(): void {
  if (props.selectable && !props.alreadyAdded) {
    emit('select')
  }
}

function toggleExpand(): void {
  expanded.value = !expanded.value
  if (expanded.value) {
    emit('expand')
  }
}
</script>

<style lang="scss" scoped>
.resource-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
  transition: all 0.3s;

  &:hover {
    background: #f0f0f0;
  }

  &.already-added {
    opacity: 0.7;
  }

  .resource-info {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;

    &.clickable {
      cursor: pointer;
    }

    .resource-icon {
      font-size: 18px;
      color: $primary-color;
    }

    .resource-text {
      display: flex;
      flex-direction: column;
      gap: 2px;
      flex: 1;
      min-width: 0;

      .resource-header-row {
        display: flex;
        align-items: center;
        gap: 8px;

        .already-added-tag {
          font-size: 12px;
          color: #52c41a;
          background: rgba(82, 196, 26, 0.1);
          padding: 2px 8px;
          border-radius: 4px;
        }
      }

      .resource-name {
        font-size: 14px;
        font-weight: 500;
      }

      .resource-desc {
        cursor: default;
        font-size: 12px;
        color: #666;
        margin: 0 16px 0 0;
      }
    }
  }

  .resource-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;

    .expand-btn {
      color: #9b9aac;
      opacity: 0;
      transition: opacity 0.3s;
    }

    .delete-btn {
      color: #9b9aac;
      cursor: pointer;
      margin-left: 8px;
    }

    .check-icon {
      color: $primary-color;
      font-size: 16px;
      margin-left: 8px;
    }
  }

  &:hover .resource-actions .expand-btn {
    opacity: 1;
  }
}

.resource-expand-content {
  margin-top: 8px;
  padding: 12px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  max-height: 200px;
  overflow-y: auto;
}
</style>
