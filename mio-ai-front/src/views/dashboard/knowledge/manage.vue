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
                  <h3 class="card-title">{{ kb.name }}</h3>
                </div>
                <p class="card-desc">{{ kb.description || '暂无描述' }}</p>
                <div class="card-stats">
                  <div class="stat-item">
                    <FileTextOutlined />
                    <span>{{ kb.documentCount || 0 }} 文档</span>
                  </div>
                </div>
                <div class="card-footer">
                  <span class="update-time">更新于 {{ formatDate(kb.updateTime || kb.createTime) }}</span>
                </div>
                <div class="card-actions">
                  <a-button class="edit-btn" @click="goToDetail(kb.id)">
                    <EditOutlined /> 编辑
                  </a-button>
                  <a-button class="delete-btn" @click="confirmDelete(kb)">
                    <DeleteOutlined /> 删除
                  </a-button>
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

        <KnowledgeBaseModal
          v-model:visible="createModalVisible"
          @success="handleCreateSuccess"
        />
      </template>
    </div>    
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useUserStore } from '@/store/user'
import { addKnowledgeBase, queryKnowledgeBases, deleteKnowledgeBase } from '@/api/knowledgeBase'
import type { KnowledgeBase, KnowledgeBaseAddRequest, PageResponse } from '@/types'
import { useRouter } from 'vue-router'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  DatabaseOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'
import KnowledgeBaseModal from '@/components/KnowledgeBaseModal.vue'

defineEmits<{
  (e: 'login-required'): void
}>()

interface FormData {
  id?: number
  name: string
  description: string
}

const userStore = useUserStore()
const router = useRouter()
const loading = ref<boolean>(false)
const submitLoading = ref<boolean>(false)
const modalVisible = ref<boolean>(false)
const createModalVisible = ref<boolean>(false)
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
  createModalVisible.value = true
}

function handleCreateSuccess(): void {
  fetchKnowledgeBases()
}

function goToDetail(kbId: number): void {
  router.push(`/dashboard/knowledge/${kbId}`)
}

function confirmDelete(kb: KnowledgeBase): void {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除知识库「${kb.name}」吗？删除后将无法恢复。`,
    okText: '确定',
    cancelText: '取消',
    okButtonProps: { danger: true },
    async onOk() {
      await deleteKnowledgeBase(kb.id)
      message.success('删除成功')
      fetchKnowledgeBases()
    }
  })
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
    overflow-y: auto;
    padding: 0 24px;
  }

  .login-prompt {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: calc(100vh - 300px);
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
    min-height: calc(100vh - 300px);
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
    position: relative;

    &:hover {
      border-color: $primary-color;
      box-shadow: $shadow-medium;

      .card-footer {
        opacity: 0;
      }

      .card-actions {
        opacity: 1;
        visibility: visible;
      }
    }

    .card-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;

      .icon-wrapper {
        width: 40px;
        height: 40px;
        border-radius: 10px;
        background: rgba($primary-color, 0.1);
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;

        .anticon {
          font-size: 20px;
          color: $primary-color;
        }
      }

      .card-title {
        font-size: 15px;
        font-weight: 600;
        color: $text-dark;
        margin: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        flex: 1;
      }
    }

    .card-desc {
      padding-bottom: 12px;
      font-size: 13px;
      color: #666;
      margin-bottom: 12px;
      display: -webkit-box;
      line-clamp: 2;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: wrap;
      border-bottom: 1px solid #f0f0f0;
    }

    .card-stats {
      display: flex;
      gap: 16px;
      margin-bottom: 12px;

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
      justify-content: flex-start;
      align-items: center;
      height: 36px;
      transition: opacity 0.3s;

      .update-time {
        font-size: 12px;
        color: #999;
      }
    }

    .card-actions {
      display: flex;
      gap: 8px;
      opacity: 0;
      visibility: hidden;
      transition: all 0.3s;
      position: absolute;
      bottom: 20px;
      left: 20px;
      right: 20px;
      height: 36px;

      .edit-btn,
      .delete-btn {
        flex: 1;
        height: 36px;
        font-size: 13px;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 4px;
        border-radius: 8px;
      }

      .edit-btn {
        background: $primary-color;
        border: 1px solid $primary-color;
        color: #fff;

        &:hover {
          background: darken($primary-color, 10%);
          border-color: darken($primary-color, 10%);
          color: #fff;
        }
      }

      .delete-btn {
        background: #fff;
        border: 1px solid #ff4d4f;
        color: #ff4d4f;

        &:hover {
          background: #fff1f0;
          color: #ff4d4f;
          border-color: #ff4d4f;
        }
      }
    }
  }
}
</style>
