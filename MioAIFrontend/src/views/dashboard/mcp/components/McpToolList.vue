<template>
  <div class="mcp-tool-list">
    <template v-if="tools.length > 0">
      <div class="tool-list">
        <div class="tool-item" v-for="tool in tools" :key="tool.name">
          <div class="tool-header">
            <ToolOutlined class="tool-icon" />
            <span class="tool-name">{{ tool.name }}</span>
          </div>
          <p class="tool-desc">{{ tool.description || '暂无描述' }}</p>
        </div>
      </div>
    </template>
    <template v-else>
      <a-empty :description="emptyText" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ToolOutlined } from '@ant-design/icons-vue'
import type { McpToolInfo } from '@/types'

withDefaults(defineProps<{
  tools: McpToolInfo[]
  emptyText?: string
}>(), {
  emptyText: '暂无工具信息'
})
</script>

<style lang="scss" scoped>
.tool-list {
  max-height: 280px;
  overflow-y: auto;
}

.tool-item {
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 6px;
  margin-bottom: 6px;

  &:last-child {
    margin-bottom: 0;
  }

  .tool-header {
    display: flex;
    align-items: center;
    margin-bottom: 2px;

    .tool-icon {
      color: $primary-color;
      margin-right: 6px;
      font-size: 12px;
    }

    .tool-name {
      font-weight: 500;
      color: #333;
      font-size: 13px;
    }
  }

  .tool-desc {
    font-size: 12px;
    color: #666;
    margin: 0;
    padding-left: 20px;
  }
}
</style>
