<template>
  <div class="agent-manage">
    <div class="page-header">
      <a-button type="primary" @click="showCreateModal">
        <PlusOutlined /> 创建智能体
      </a-button>
    </div>

    <div class="agent-list">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="agent in agentList" :key="agent.id">
          <div class="agent-card" @click="handleCardClick(agent)">
            <div class="card-header">
              <a-avatar :size="48" :src="agent.avatar">
                {{ agent.name?.charAt(0)?.toUpperCase() }}
              </a-avatar>
              <a-dropdown :trigger="['click']" @click.stop>
                <a-button type="text" class="more-btn">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="edit" @click="showEditModal(agent)">
                      <EditOutlined /> 编辑
                    </a-menu-item>
                    <a-menu-item key="delete" @click="handleDelete(agent)">
                      <DeleteOutlined /> 删除
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
            <h3 class="card-title">{{ agent.name }}</h3>
            <p class="card-desc">{{ agent.description || '暂无描述' }}</p>
            <div class="card-footer">
              <a-tag :color="getTypeColor(agent.type)">
                {{ getTypeName(agent.type) }}
              </a-tag>
              <span class="create-time">{{ formatDate(agent.createTime) }}</span>
            </div>
          </div>
        </a-col>
      </a-row>

      <a-empty v-if="!loading && agentList.length === 0" description="暂无智能体" />
    </div>

    <a-modal
      v-model="modalVisible"
      :title="editingAgent ? '编辑智能体' : '创建智能体'"
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
          <a-input v-model="formData.name" placeholder="请输入智能体名称" />
        </a-form-item>
        <a-form-item name="description" label="描述">
          <a-textarea
            v-model="formData.description"
            placeholder="请输入描述"
            :rows="3"
          />
        </a-form-item>
        <a-form-item name="type" label="类型">
          <a-select v-model="formData.type" placeholder="请选择类型">
            <a-select-option :value="0">内置智能体</a-select-option>
            <a-select-option :value="1">自定义智能体</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="systemPrompt" label="系统提示词">
          <a-textarea
            v-model="formData.systemPrompt"
            placeholder="请输入系统提示词"
            :rows="5"
          />
        </a-form-item>
        <a-form-item name="isPublic" label="是否公开">
          <a-switch v-model="formData.isPublic" :checked-value="1" :un-checked-value="0" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { addAgent, queryAgents, updateAgent, deleteAgent } from '@/api/agent'
import {
  PlusOutlined,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'

const loading = ref(false)
const submitLoading = ref(false)
const modalVisible = ref(false)
const editingAgent = ref(null)
const agentList = ref([])
const formRef = ref(null)

const formData = reactive({
  name: '',
  description: '',
  type: 1,
  systemPrompt: '',
  isPublic: 0
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function getTypeName(type) {
  const types = { 0: '内置', 1: '自定义' }
  return types[type] || '未知'
}

function getTypeColor(type) {
  const colors = { 0: 'blue', 1: 'green' }
  return colors[type] || 'default'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

async function fetchAgents() {
  loading.value = true
  try {
    const res = await queryAgents({ current: 1, pageSize: 100 })
    agentList.value = res.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showCreateModal() {
  editingAgent.value = null
  resetForm()
  modalVisible.value = true
}

function showEditModal(agent) {
  editingAgent.value = agent
  Object.assign(formData, {
    id: agent.id,
    name: agent.name,
    description: agent.description,
    type: agent.type,
    systemPrompt: agent.systemPrompt,
    isPublic: agent.isPublic
  })
  modalVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(formData, {
    name: '',
    description: '',
    type: 1,
    systemPrompt: '',
    isPublic: 0
  })
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitLoading.value = true
    
    if (editingAgent.value) {
      await updateAgent({ ...formData, id: editingAgent.value.id })
      message.success('更新成功')
    } else {
      await addAgent(formData)
      message.success('创建成功')
    }
    
    modalVisible.value = false
    resetForm()
    fetchAgents()
  } catch (e) {
    console.error(e)
  } finally {
    submitLoading.value = false
  }
}

function handleDelete(agent) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除智能体「${agent.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    async onOk() {
      await deleteAgent(agent.id)
      message.success('删除成功')
      fetchAgents()
    }
  })
}

function handleCardClick(agent) {
  console.log('click agent:', agent)
}

onMounted(() => {
  fetchAgents()
})
</script>

<style lang="scss" scoped>
.agent-manage {
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

  .agent-card {
    background: #fff;
    border-radius: 12px;
    padding: 20px;
    cursor: pointer;
    transition: all 0.3s;
    border: 1px solid #f0f0f0;

    &:hover {
      transform: translateY(-4px);
      box-shadow: $shadow-medium;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 16px;

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
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
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
