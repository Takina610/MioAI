<template>
  <div class="dash-card kb-card" @click="emit('open')">
    <div class="kb-head">
      <div class="kb-icon"><DatabaseOutlined /></div>
      <h3 class="kb-name" :title="kb.name">{{ kb.name }}</h3>
    </div>

    <p class="kb-desc" :class="{ muted: !kb.description }">{{ kb.description || '暂无描述' }}</p>

    <div class="kb-meta">
      <span class="meta-item"><FileTextOutlined /> {{ kb.documentCount || 0 }} 个文档</span>
      <span class="meta-item">{{ formatFileSize(kb.storageSize || 0) }}</span>
      <span v-if="showAuthor && kb.userName" class="meta-item author">{{ kb.userName }}</span>
    </div>

    <div class="card-footer">
      <span class="update-time">更新于 {{ formatDate(kb.updateTime || kb.createTime) }}</span>
    </div>

    <div class="card-actions" @click.stop>
      <a-button class="action-btn primary-btn" @click="emit('hitTest')">
        <AimOutlined /> 命中测试
      </a-button>
      <a-button v-if="showDelete" class="action-btn danger-btn" @click="emit('delete')">
        删除
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { AimOutlined, DatabaseOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import type { KnowledgeBase } from '@/types'
import { formatDate, formatFileSize } from '@/utils/format'

defineProps<{
  kb: KnowledgeBase
  /** 公共库展示作者，我的知识库不展示 */
  showAuthor?: boolean
  showDelete?: boolean
}>()

const emit = defineEmits<{
  (e: 'open'): void
  (e: 'hitTest'): void
  (e: 'delete'): void
}>()
</script>

<style lang="scss" scoped>
.kb-card {
  cursor: pointer;
  display: flex;
  flex-direction: column;

  &:hover .card-footer {
    opacity: 0;
  }

  .kb-head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;

    .kb-icon {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: rgba($primary-color, 0.1);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      .anticon {
        font-size: 17px;
        color: $primary-color;
      }
    }

    .kb-name {
      font-size: 15px;
      font-weight: 600;
      color: $text-dark;
      margin: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .kb-desc {
    margin: 0 0 12px;
    font-size: 13px;
    line-height: 1.55;
    color: $text-secondary;
    min-height: 40px;
    display: -webkit-box;
    line-clamp: 2;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;

    &.muted {
      color: $text-muted;
    }
  }

  .kb-meta {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 12px;
    font-size: 12px;
    color: $text-muted;

    .meta-item {
      display: flex;
      align-items: center;
      gap: 4px;

      .anticon {
        color: $primary-color;
      }
    }

    .author {
      margin-left: auto;
      color: $text-secondary;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: 40%;
    }
  }

  .card-footer {
    display: flex;
    align-items: center;
    height: 36px;
    margin-top: auto;
    transition: opacity 0.3s;

    .update-time {
      font-size: 12px;
      color: $text-muted;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>
