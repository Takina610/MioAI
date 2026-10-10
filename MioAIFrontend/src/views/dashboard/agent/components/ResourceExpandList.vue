<template>
  <div class="resource-expand-list">
    <div class="expand-header">
      <component :is="icon" v-if="icon" />
      {{ title }}
    </div>
    <div class="expand-items">
      <div class="expand-item" v-for="item in items" :key="item.name">
        <component :is="rowIcon" v-if="rowIcon" class="row-icon" />
        <div class="item-text">
          <span class="item-name">{{ item.name }}</span>
          <a-typography-paragraph
            v-if="item.description"
            :ellipsis="{ rows: 1, tooltip: true }"
            :content="item.description"
            class="item-desc"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

export interface ExpandItem {
  name: string
  description?: string
}

withDefaults(defineProps<{
  /** 标题文案，如「3 个文档」 */
  title: string
  items: ExpandItem[]
  /** 标题前图标 */
  icon?: Component
  /** 每行前图标（文档列表使用） */
  rowIcon?: Component
}>(), {})
</script>

<style lang="scss" scoped>
.resource-expand-list {
  .expand-header {
    font-size: 12px;
    color: #6e6b62;
    display: flex;
    align-items: center;
    gap: 4px;
    margin-bottom: 8px;
  }

  .expand-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 0;
    border-bottom: 1px solid #f0ede4;

    &:last-child {
      border-bottom: none;
    }

    .row-icon {
      font-size: 14px;
      color: #8c8a82;
    }

    .item-text {
      flex: 1;
      min-width: 0;
    }

    .item-name {
      font-size: 13px;
      font-weight: 500;
      color: #141413;
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .item-desc {
      margin: 2px 0 0;
      font-size: 12px;
      color: #8c8a82;
    }
  }
}
</style>
