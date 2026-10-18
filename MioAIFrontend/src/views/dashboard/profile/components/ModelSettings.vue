<template>
  <div class="model-settings">
    <!-- OpenCode 免费模型：纯展示目录（切换在聊天框进行） -->
    <div class="model-section">
      <div class="section-head">
        <span class="section-title">OpenCode 免费模型</span>
        <LoadingOutlined v-if="catalogLoading" class="section-loading" />
      </div>
      <div class="model-chip-list">
        <span
          v-for="m in freeModels"
          :key="m.id"
          class="model-chip static"
        >
          {{ m.id }}
          <span v-if="m.vision" class="chip-badge">视觉</span>
        </span>
        <span v-if="!freeModels.length && !catalogLoading" class="section-empty">免费模型目录为空</span>
      </div>
    </div>

    <!-- 自定义供应商：管理入口（增删改/测试/启停），模型切换在聊天框进行 -->
    <div class="model-section">
      <div class="section-head">
        <span class="section-title">自定义供应商</span>
        <a-button size="small" @click="openAdd">
          <PlusOutlined /> 添加供应商
        </a-button>
      </div>
      <div class="provider-list">
        <div v-for="p in providers" :key="p.id" class="provider-card">
          <div class="provider-head">
            <span class="provider-name">{{ p.name }}</span>
            <span class="provider-url">{{ p.baseUrl }}</span>
            <div class="provider-actions">
              <EditOutlined class="action-icon" @click="openEdit(p)" />
              <DeleteOutlined class="action-icon danger" @click="confirmDelete(p)" />
            </div>
          </div>
          <div class="model-chip-list">
            <span
              v-for="m in enabledModels(p)"
              :key="m.id"
              class="model-chip static"
            >
              {{ m.id }}
              <span v-if="isVisionModel(m)" class="chip-badge">视觉</span>
            </span>
            <span v-if="!enabledModels(p).length" class="section-empty">未配置可用模型</span>
          </div>
        </div>
        <span v-if="!providers.length" class="section-empty">暂无自定义供应商</span>
      </div>
    </div>

    <ProviderFormModal v-model:open="modalOpen" :editing="editingProvider" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { deleteLocalProvider, enabledProviderModels, loadModelCatalog, refreshModelCatalog, useModelCatalog } from '@/composables/model/useModelCatalog'
import { isVisionModel } from '@/utils/modelPresets'
import ProviderFormModal from './ProviderFormModal.vue'
import type { ModelProvider } from '@/types'

const { freeModels, providers, loaded } = useModelCatalog()
const catalogLoading = computed(() => !loaded.value)

const modalOpen = ref(false)
const editingProvider = ref<ModelProvider | null>(null)

onMounted(() => {
  loadModelCatalog()
})

function enabledModels(p: ModelProvider) {
  return enabledProviderModels(p)
}

function openAdd(): void {
  editingProvider.value = null
  modalOpen.value = true
}

function openEdit(p: ModelProvider): void {
  editingProvider.value = p
  modalOpen.value = true
}

function confirmDelete(p: ModelProvider): void {
  Modal.confirm({
    title: `删除供应商「${p.name}」？`,
    content: '其下模型将不再可选；若为当前使用的模型会回退到 OpenCode 免费档。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      // 供应商仅存浏览器本地：删除即移除 localStorage 条目
      deleteLocalProvider(p.id)
      message.success('已删除')
      await refreshModelCatalog()
    }
  })
}
</script>

<style lang="scss" scoped>
.model-settings {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
  // 展示与管理界面禁止选中（与聊天页模型菜单一致）
  user-select: none;

  .model-section {
    display: flex;
    flex-direction: column;
    gap: 10px;

    .section-head {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .section-title {
        font-size: 14px;
        font-weight: 600;
        color: $text-dark;
      }

      .section-loading {
        color: $primary-color;
        font-size: 14px;
      }
    }

    .model-chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      .model-chip {
        padding: 5px 14px;
        border: 1px solid #e8e6dc;
        border-radius: 999px;
        background: #fff;
        color: #5f5d55;
        font-size: 13px;

        // 纯展示（切换在聊天框进行）：无指针/悬停态
        &.static {
          cursor: default;
        }

        .chip-badge {
          margin-left: 6px;
          padding: 1px 7px;
          border-radius: 999px;
          background: rgba(42, 161, 169, 0.12);
          color: $primary-color;
          font-size: 11px;
          font-weight: 400;
        }
      }
    }

    .section-empty {
      font-size: 13px;
      color: #8c8a82;
    }

    .provider-list {
      display: flex;
      flex-direction: column;
      gap: 10px;

      .provider-card {
        border: 1px solid #e8e6dc;
        border-radius: 12px;
        padding: 12px 16px;
        display: flex;
        flex-direction: column;
        gap: 10px;

        .provider-head {
          display: flex;
          align-items: center;
          gap: 12px;

          .provider-name {
            font-size: 14px;
            font-weight: 600;
            color: $text-dark;
            flex-shrink: 0;
          }

          .provider-url {
            font-size: 12px;
            color: #8c8a82;
            min-width: 0;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            flex: 1;
          }

          .provider-actions {
            display: flex;
            gap: 10px;
            opacity: 0;
            transition: opacity 0.15s;
            flex-shrink: 0;

            .action-icon {
              cursor: pointer;
              color: #6e6b62;
              font-size: 14px;

              &:hover {
                color: $primary-color;
              }

              &.danger:hover {
                color: #c0392b;
              }
            }
          }

          &:hover .provider-actions {
            opacity: 1;
          }
        }
      }
    }
  }
}
</style>
