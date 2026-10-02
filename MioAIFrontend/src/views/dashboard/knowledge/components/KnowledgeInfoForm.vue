<template>
  <div class="dash-section">
    <h3 class="section-title">基本信息</h3>
    <a-form layout="vertical" class="info-form">
      <a-row :gutter="24">
        <a-col :span="12">
          <a-form-item label="作者">
            <a-input :value="author" disabled />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="文档数量">
            <a-input :value="documentCount" disabled suffix="个" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="24">
        <a-col :span="12">
          <a-form-item label="知识库名称">
            <a-input v-if="mode === 'edit'" v-model:value="name" placeholder="请输入知识库名称" />
            <a-input v-else :value="name" disabled />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="存储大小">
            <a-input :value="storageSize" disabled />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="24">
        <a-col :span="24">
          <a-form-item label="知识库描述">
            <a-textarea v-if="mode === 'edit'" v-model:value="description" placeholder="请输入知识库描述" :rows="3" />
            <a-textarea v-else :value="description" disabled :rows="3" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row v-if="mode === 'edit'" :gutter="24">
        <a-col :span="12">
          <a-form-item label="是否公开">
            <a-switch v-model:checked="isPublicChecked" />
            <span class="switch-hint">{{ isPublicChecked ? '公开后其他用户可见' : '仅自己可见' }}</span>
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>
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
.info-form {
  :deep(.ant-form-item-label) {
    label {
      color: #5f6368;
      font-weight: 500;
    }
  }

  :deep(.ant-input-disabled) {
    color: #202124;
    background: #f5f5f5;
  }
}

.switch-hint {
  margin-left: 12px;
  color: #999;
  font-size: 13px;
}
</style>
