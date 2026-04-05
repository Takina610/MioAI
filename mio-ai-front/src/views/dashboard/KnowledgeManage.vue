<template>
  <div class="knowledge-manage">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h2>知识库管理</h2>
        </div>
        <div class="header-right" v-if="userStore.isLoggedIn">
          <a-button type="primary" @click="showCreateModal">
            <PlusOutlined /> 创建知识库
          </a-button>
        </div>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content">
      <template v-if="!userStore.isLoggedIn">
            <div class="login-prompt">
              <p class="prompt-title">登录以使用</p>
              <p class="prompt-desc">您当前处于未登录状态，登录后可使用完整服务</p>
              <a-button type="primary" @click="$emit('login-required')">
                登录
              </a-button>
            </div>
      </template>

      <template v-else>
        <div class="knowledge-list">
          <a-row :gutter="[16, 16]">
            <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in knowledgeList" :key="kb.id">
              <div class="knowledge-card">
                <div class="card-header">
                  <div class="icon-wrapper">
                    <DatabaseOutlined />
                  </div>
                  <a-dropdown :trigger="['click']">
                    <a-button type="text" class="more-btn">
                      <MoreOutlined />
                    </a-button>
                    <template #overlay>
                      <a-menu>
                        <a-menu-item key="edit" @click="showEditModal(kb)">
                          <EditOutlined /> 编辑
                        </a-menu-item>
                        <a-menu-item key="delete" @click="handleDelete(kb)">
                          <DeleteOutlined /> 删除
                        </a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                </div>
                <h3 class="card-title">{{ kb.name }}</h3>
                <p class="card-desc">{{ kb.description || '暂无描述' }}</p>
                <div class="card-stats">
                  <div class="stat-item">
                    <FileTextOutlined />
                    <span>{{ kb.documentCount || 0 }} 文档</span>
                  </div>
                </div>
                <div class="card-footer">
                  <a-tag :color="kb.status === 1 ? 'green' : 'default'">
                    {{ kb.status === 1 ? '启用' : '禁用' }}
                  </a-tag>
                  <span class="create-time">{{ formatDate(kb.createTime) }}</span>
                </div>
              </div>
            </a-col>
          </a-row>

          <div class="empty-container" v-if="knowledgeList.length === 0">
            <img src="@/assets/agent.png" alt="empty" class="empty-image" />
            <p class="empty-desc">你还没有知识库</p>
            <div class="empty-hint">前往右上角创建知识库</div>
          </div>
        </div>

        <a-modal
          :open="modalVisible"
          @update:open="modalVisible = $event"
          :title="editingKb ? '编辑知识库' : '创建知识库'"
          :confirm-loading="submitLoading"
          @ok="handleSubmit"
          @cancel="resetForm"
          width="600px"
        >
          <a-form
            ref="formRef"
            :model="formData"
            :rules="rules"
            layout="vertical"
          >
            <a-form-item name="name" label="名称">
              <a-input :value="formData.name" @update:value="formData.name = $event" placeholder="请输入知识库名称" />
            </a-form-item>
            <a-form-item name="description" label="描述">
              <a-textarea
                :value="formData.description"
                @update:value="formData.description = $event"
                placeholder="请输入描述"
                :rows="3"
              />
            </a-form-item>
          </a-form>
        </a-modal>
      </template>
    </div>    
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useUserStore } from '@/store/user'
import { addKnowledgeBase, queryKnowledgeBases, updateKnowledgeBase, deleteKnowledgeBase } from '@/api/knowledgeBase'
import type { KnowledgeBase, KnowledgeBaseAddRequest, KnowledgeBaseUpdateRequest, PageResponse } from '@/types'
import {
  PlusOutlined,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined,
  DatabaseOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'

defineEmits<{
  (e: 'login-required'): void
}>()

interface FormData {
  id?: number
  name: string
  description: string
}

const userStore = useUserStore()
const loading = ref<boolean>(false)
const submitLoading = ref<boolean>(false)
const modalVisible = ref<boolean>(false)
const editingKb = ref<KnowledgeBase | null>(null)
const knowledgeList = ref<KnowledgeBase[]>([])
const formRef = ref<FormInstance | null>(null)

const formData = reactive<FormData>({
  name: '',
  description: ''
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }]
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

async function fetchKnowledgeBases(): Promise<void> {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const res: PageResponse<KnowledgeBase> = await queryKnowledgeBases({ current: 1, pageSize: 100 })
    knowledgeList.value = res.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showCreateModal(): void {
  editingKb.value = null
  resetForm()
  modalVisible.value = true
}

function showEditModal(kb: KnowledgeBase): void {
  editingKb.value = kb
  Object.assign(formData, {
    id: kb.id,
    name: kb.name,
    description: kb.description,
    type: 1,
    embeddingModel: 'text-embedding-v3'
  })
  modalVisible.value = true
}

function resetForm(): void {
  formRef.value?.resetFields()
  Object.assign(formData, {
    name: '',
    description: '',
    type: 1,
    embeddingModel: 'text-embedding-v3'
  })
}

async function handleSubmit(): Promise<void> {
  try {
    await formRef.value?.validate()
    submitLoading.value = true
    
    if (editingKb.value) {
      await updateKnowledgeBase({ ...formData, id: editingKb.value.id } as KnowledgeBaseUpdateRequest)
      message.success('更新成功')
    } else {
      await addKnowledgeBase(formData as KnowledgeBaseAddRequest)
      message.success('创建成功')
    }
    
    modalVisible.value = false
    resetForm()
    fetchKnowledgeBases()
  } catch (e) {
    console.error(e)
  } finally {
    submitLoading.value = false
  }
}

function handleDelete(kb: KnowledgeBase): void {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除知识库「${kb.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    async onOk() {
      await deleteKnowledgeBase(kb.id)
      message.success('删除成功')
      fetchKnowledgeBases()
    }
  })
}

onMounted(() => {
  fetchKnowledgeBases()
})
</script>

<style lang="scss" scoped>
.knowledge-manage {
  height: 100%;
  .page-header {
    .header-content {
      padding: 16px 24px;
      text-align: center;
      display: flex;
      justify-content: space-between;
      align-items: flex-start;

      .header-left {
        h2 {
          font-size: 24px;
          font-weight: 600;
          color: #202124;
          margin-bottom: 8px;
        }
      }

      .header-right {
        :deep(.ant-btn-primary) {
          background: $primary-color;
          border-color: $primary-color;

          &:hover {
            background: darken($primary-color, 10%);
            border-color: darken($primary-color, 10%);
          }
        }
      }
    }

    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .page-content {
    height: calc(100% - 132px);
    display: flex;
    justify-content: center;
    flex-direction: column;
  }

  .login-prompt {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 400px;
    text-align: center;

    .prompt-title {
      font-size: 20px;
      font-weight: 600;
      color: #202124;
      margin-bottom: 12px;
    }

    .prompt-desc {
      font-size: 14px;
      color: #5f6368;
      margin-bottom: 24px;
    }

    :deep(.ant-btn-primary) {
      background: $primary-color;
      border-color: $primary-color;

      &:hover {
        background: darken($primary-color, 10%);
        border-color: darken($primary-color, 10%);
      }
    }
  }

  .knowledge-list {
    margin-top: 24px;
  }

  .empty-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 400px;
    text-align: center;

    .empty-image {
      width: 400px;
      height: auto;
      object-fit: contain;
      margin-bottom: -80px;
      position: relative;
      z-index: 1;
    }

    .empty-desc {
      font-size: 16px;
      color: #666;
      margin-bottom: 8px;
      position: relative;
      z-index: 1;
    }

    .empty-hint {
      font-size: 14px;
      color: #999;
      position: relative;
      z-index: 1;
    }
  }

  .knowledge-card {
    background: #fff;
    border-radius: 12px;
    padding: 20px;
    border: 1px solid #f0f0f0;
    transition: all 0.3s;

    &:hover {
      transform: translateY(-4px);
      box-shadow: $shadow-medium;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 16px;

      .icon-wrapper {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        background: rgba($primary-color, 0.1);
        display: flex;
        align-items: center;
        justify-content: center;

        .anticon {
          font-size: 24px;
          color: $primary-color;
        }
      }

      .more-btn {
        color: #999;

        &:hover {
          color: $primary-color;
        }
      }
    }

    .card-title {
      font-size: 16px;
      font-weight: 600;
      color: $text-dark;
      margin-bottom: 8px;
    }

    .card-desc {
      font-size: 13px;
      color: #666;
      margin-bottom: 16px;
      display: -webkit-box;
      line-clamp: 2;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .card-stats {
      display: flex;
      gap: 16px;
      margin-bottom: 16px;

      .stat-item {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 13px;
        color: #666;

        .anticon {
          color: $primary-color;
        }
      }
    }

    .card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .create-time {
        font-size: 12px;
        color: #999;
      }
    }
  }
}
</style>
