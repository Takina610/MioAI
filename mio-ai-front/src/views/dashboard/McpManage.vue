<template>
  <div class="mcp-manage">
    <div class="page-header">
      <a-button type="primary" @click="showCreateModal">
        <PlusOutlined /> 添加MCP工具
      </a-button>
    </div>

    <div class="mcp-list">
      <a-table
        :columns="columns"
        :data-source="mcpList"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showEditModal(record)">
                编辑
              </a-button>
              <a-button type="link" size="small" danger @click="handleDelete(record)">
                删除
              </a-button>
            </a-space>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'isPublic'">
            <a-tag :color="record.isPublic === 1 ? 'blue' : 'default'">
              {{ record.isPublic === 1 ? '公开' : '私有' }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      :open="modalVisible"
      @update:open="modalVisible = $event"
      :title="editingMcp ? '编辑MCP工具' : '添加MCP工具'"
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
          <a-input :value="formData.name" @update:value="formData.name = $event" placeholder="请输入工具名称" />
        </a-form-item>
        <a-form-item name="description" label="描述">
          <a-textarea
            :value="formData.description"
            @update:value="formData.description = $event"
            placeholder="请输入描述"
            :rows="3"
          />
        </a-form-item>
        <a-form-item name="serverName" label="服务名称">
          <a-input :value="formData.serverName" @update:value="formData.serverName = $event" placeholder="请输入MCP服务名称" />
        </a-form-item>
        <a-form-item name="toolName" label="工具名称">
          <a-input :value="formData.toolName" @update:value="formData.toolName = $event" placeholder="请输入工具名称" />
        </a-form-item>
        <a-form-item name="inputSchema" label="输入Schema">
          <a-textarea
            :value="formData.inputSchema"
            @update:value="formData.inputSchema = $event"
            placeholder="请输入JSON格式的输入Schema"
            :rows="4"
          />
        </a-form-item>
        <a-form-item name="isPublic" label="是否公开">
          <a-switch :checked="formData.isPublic" @update:checked="formData.isPublic = $event" :checked-value="1" :un-checked-value="0" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { addMcpTool, queryMcpTools, updateMcpTool, deleteMcpTool } from '@/api/mcpTool'
import { PlusOutlined } from '@ant-design/icons-vue'

const loading = ref(false)
const submitLoading = ref(false)
const modalVisible = ref(false)
const editingMcp = ref(null)
const mcpList = ref([])
const formRef = ref(null)

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '服务名称', dataIndex: 'serverName', key: 'serverName' },
  { title: '工具名称', dataIndex: 'toolName', key: 'toolName' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '公开', dataIndex: 'isPublic', key: 'isPublic' },
  { title: '操作', key: 'action', width: 150 }
]

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  onChange: (page) => {
    pagination.current = page
    fetchMcpTools()
  }
})

const formData = reactive({
  name: '',
  description: '',
  serverName: '',
  toolName: '',
  inputSchema: '',
  isPublic: 0
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  serverName: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  toolName: [{ required: true, message: '请输入工具名称', trigger: 'blur' }]
}

async function fetchMcpTools() {
  loading.value = true
  try {
    const res = await queryMcpTools({
      current: pagination.current,
      pageSize: pagination.pageSize
    })
    mcpList.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showCreateModal() {
  editingMcp.value = null
  resetForm()
  modalVisible.value = true
}

function showEditModal(mcp) {
  editingMcp.value = mcp
  Object.assign(formData, {
    id: mcp.id,
    name: mcp.name,
    description: mcp.description,
    serverName: mcp.serverName,
    toolName: mcp.toolName,
    inputSchema: mcp.inputSchema,
    isPublic: mcp.isPublic
  })
  modalVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(formData, {
    name: '',
    description: '',
    serverName: '',
    toolName: '',
    inputSchema: '',
    isPublic: 0
  })
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitLoading.value = true
    
    if (editingMcp.value) {
      await updateMcpTool({ ...formData, id: editingMcp.value.id })
      message.success('更新成功')
    } else {
      await addMcpTool(formData)
      message.success('添加成功')
    }
    
    modalVisible.value = false
    resetForm()
    fetchMcpTools()
  } catch (e) {
    console.error(e)
  } finally {
    submitLoading.value = false
  }
}

function handleDelete(mcp) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除工具「${mcp.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    async onOk() {
      await deleteMcpTool(mcp.id)
      message.success('删除成功')
      fetchMcpTools()
    }
  })
}

onMounted(() => {
  fetchMcpTools()
})
</script>

<style lang="scss" scoped>
.mcp-manage {
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

  :deep(.ant-table) {
    background: #fff;
    border-radius: 12px;
  }
}
</style>
