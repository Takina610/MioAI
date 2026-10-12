<template>
  <div class="dash-section info-card">
    <h3 class="section-title">基本信息</h3>

    <template v-if="mode === 'edit'">
      <div class="field">
        <label class="field-label">名称</label>
        <a-input v-model:value="name" :maxlength="50" />
      </div>
      <div class="field">
        <label class="field-label">描述</label>
        <a-textarea
          v-model:value="description"
          placeholder="这份知识库收录什么内容"
          :rows="3"
          :maxlength="200"
        />
      </div>
      <div class="field vis-row">
        <span class="field-label">公开性</span>
        <div class="vis-control">
          <span class="vis-text">{{ isPublicChecked ? '其他人可见' : '仅自己可见' }}</span>
          <a-switch v-model:checked="isPublicChecked" size="small" />
        </div>
      </div>
    </template>

    <template v-else>
      <div class="info-head">
        <span class="info-name">{{ name || '未命名知识库' }}</span>
      </div>
      <p class="info-desc" :class="{ muted: !description }">{{ description || '暂无描述' }}</p>
    </template>

    <div class="meta-list">
      <div class="meta-row"><span>作者</span><b>{{ author }}</b></div>
      <div class="meta-row"><span>文档</span><b>{{ documentCount }} 个</b></div>
      <div class="meta-row"><span>存储</span><b>{{ storageSize }}</b></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

withDefaults(defineProps<{
  /** edit 模式下名称/描述/公开性可编辑，view 模式整体只读 */
  mode?: 'view' | 'edit'
  author?: string
  documentCount?: number
  storageSize?: string
}>(), {
  mode: 'view',
  author: '未知用户',
  documentCount: 0,
  storageSize: '0 B'
})

const name = defineModel<string>('name', { default: '' })
const description = defineModel<string>('description', { default: '' })
/** 后端用 0/1 表示公开性，表单内用 switch 布尔值 */
const isPublic = defineModel<number>('isPublic', { default: 0 })

const isPublicChecked = computed({
  get: () => isPublic.value === 1,
  set: (val: boolean) => {
    isPublic.value = val ? 1 : 0
  }
})
</script>

<style lang="scss" scoped>
.info-card {
  .field {
    margin-bottom: 16px;

    .field-label {
      display: block;
      font-size: 13px;
      font-weight: 500;
      color: $text-secondary;
      margin-bottom: 8px;
    }
  }

  .vis-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 0;

    .field-label {
      margin-bottom: 0;
    }

    .vis-control {
      display: flex;
      align-items: center;
      gap: 10px;

      .vis-text {
        font-size: 13px;
        color: $text-muted;
      }
    }
  }

  .info-head {
    display: flex;
    align-items: center;
    gap: 10px;

    .info-name {
      font-size: 17px;
      font-weight: 600;
      color: $text-dark;
      word-break: break-word;
    }
  }

  .info-desc {
    margin: 8px 0 0;
    font-size: 13px;
    line-height: 1.7;
    color: $text-secondary;
    white-space: pre-wrap;
    word-break: break-word;

    &.muted {
      color: $text-muted;
    }
  }

  .meta-list {
    max-width: 360px;
    margin-top: 16px;
    padding-top: 14px;
    border-top: 1px solid $border-warm-subtle;
    display: flex;
    flex-direction: column;
    gap: 10px;

    .meta-row {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      font-size: 13px;

      span {
        color: $text-muted;
      }

      b {
        font-weight: 500;
        color: $text-dark;
        font-variant-numeric: tabular-nums;
      }
    }
  }
}
</style>
