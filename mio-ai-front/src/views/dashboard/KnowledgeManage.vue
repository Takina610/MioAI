<template>
  <div class="knowledge-manage">
    <div class="page-header">
      <a-button type="primary" @click="showCreateModal">
        <PlusOutlined /> 创建知识库
      </a-button>
    </div>

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

      <a-empty v-if="!loading && knowledgeList.length === 0" description="暂无知识库" />
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
        <a-form-item name="type" label="类型">
          <a-select :value="formData.type" @update:value="formData.type = $event" placeholder="请选择类型">
            <a-select-option :value="1">文档型</a-select-option>
            <a-select-option :value="2">问答型</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="embeddingModel" label="嵌入模型">
          <a-input :value="formData.embeddingModel" @update:value="formData.embeddingModel = $event" placeholder="请输入嵌入模型名称" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { addKnowledgeBase, queryKnowledgeBases, updateKnowledgeBase, deleteKnowledgeBase } from '@/api/knowledgeBase'
import {
  PlusOutlined,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined,
  DatabaseOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'

const loading = ref(false)
const submitLoading = ref(false)
const modalVisible = ref(false)
const editingKb = ref(null)
const knowledgeList = ref([])
const formRef = ref(null)

const formData = reactive({
  name: '',
  description: '',
  type: 1,
  embeddingModel: 'text-embedding-v3'
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

async function fetchKnowledgeBases() {
  loading.value = true
  try {
    const res = await queryKnowledgeBases({ current: 1, pageSize: 100 })
    knowledgeList.value = res.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showCreateModal() {
  editingKb.value = null
  resetForm()
  modalVisible.value = true
}

function showEditModal(kb) {
  editingKb.value = kb
  Object.assign(formData, {
    id: kb.id,
    name: kb.name,
    description: kb.description,
    type: kb.type,
    embeddingModel: kb.embeddingModel
  })
  modalVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(formData, {
    name: '',
    description: '',
    type: 1,
    embeddingModel: 'text-embedding-v3'
  })
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitLoading.value = true
    
    if (editingKb.value) {
      await updateKnowledgeBase({ ...formData, id: editingKb.value.id })
      message.success('更新成功')
    } else {
      await addKnowledgeBase(formData)
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

function handleDelete(kb) {
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
  .page-header {
    margin-bottom: 24px;
    display: flex;
    justify-content: flex-end;

    :deep(.ant-btn-primary) {
      background: $primary-color;
      border-color: $primary-color;

      &:hover {
        background: darken($primary-color, 10%);
        border-color: darken($primary-color, 10%);
      }
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
