<template>
  <a-drawer
    :open="open"
    @update:open="emit('update:open', $event)"
    :title="title"
    placement="right"
    :width="600"
    :footer-style="{ textAlign: 'right' }"
  >
    <template #extra>
      <a-button type="primary" @click="emit('create')">
        <PlusOutlined /> {{ createLabel }}
      </a-button>
    </template>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane v-for="tab in tabs" :key="tab.key" :tab="tab.label">
        <div class="drawer-list">
          <template v-for="item in tab.items" :key="item.id">
            <slot :item="item" />
          </template>
          <div v-if="!tab.items.length" class="drawer-empty">暂无可选内容</div>
        </div>
      </a-tab-pane>
    </a-tabs>

    <template #footer>
      <a-button class="cancel-btn" @click="emit('update:open', false)">取消</a-button>
      <a-button type="primary" :loading="confirmLoading" @click="emit('confirm')">
        添加 ({{ selectedCount }})
      </a-button>
    </template>
  </a-drawer>
</template>

<script setup lang="ts" generic="T extends { id: number }">
import { ref, watch } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'

export interface ResourceTab<T> {
  key: string
  label: string
  items: T[]
}

const props = defineProps<{
  open: boolean
  title: string
  createLabel: string
  tabs: ResourceTab<T>[]
  selectedCount: number
  confirmLoading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'create'): void
  (e: 'confirm'): void
}>()

defineSlots<{
  default(props: { item: T }): void
}>()

// 默认激活第一个 tab（技能抽屉只有 key='skills' 一个 tab，写死 'public' 会导致内容面板永远空白）
const activeTab = ref('')

watch(
  () => props.tabs,
  tabs => {
    if (!tabs.length) return
    if (!tabs.some(t => t.key === activeTab.value)) {
      activeTab.value = tabs[0].key
    }
  },
  { immediate: true, deep: true }
)
</script>

<style lang="scss" scoped>
.drawer-list {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .drawer-empty {
    padding: 24px 0;
    text-align: center;
    font-size: 13px;
    color: #8c8a82;
  }
}

.cancel-btn {
  margin-right: 8px;
}
</style>
