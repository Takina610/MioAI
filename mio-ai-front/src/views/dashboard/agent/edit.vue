<template>
  <div class="agent-edit">
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="back-btn" @click="goBack">
            <h2>智能体管理</h2>
          </div>
          <h2> / </h2>
          <h2 class="editable-title" @click="showEditModal">{{ agentDetail?.name || '智能体编辑' }}</h2>
        </div>
        <div class="header-right">
          <a-button type="primary" @click="handlePublish" :loading="publishLoading">
            <SendOutlined /> 发布
          </a-button>
        </div>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content" v-if="!loading">
      <div class="content-wrapper">
        <div class="left-section">
          <div class="edit-section">
            <div class="section-header">
              <h3>资源配置</h3>
            </div>
            <div class="section-content">
              <div class="resource-section">
                <div class="resource-header">
                  <span class="resource-title">知识库</span>
                  <a-button type="link" size="small" @click="showKnowledgeDrawer">
                    <PlusOutlined /> 添加
                  </a-button>
                </div>
                <div class="resource-list" v-if="agentDetail?.knowledgeBases?.length">
                  <template v-for="kb in agentDetail.knowledgeBases" :key="kb.id">
                    <div class="resource-item">
                      <div class="resource-info">
                        <BookOutlined class="resource-icon" />
                        <div class="resource-text">
                          <span class="resource-name">{{ kb.name }}</span>
                          <a-typography-paragraph
                            :ellipsis="{ rows: 2, tooltip: true }"
                            :content="kb.description || '暂无描述'"
                            class="resource-desc"
                          />
                        </div>
                      </div>
                      <div class="resource-actions">
                        <a-button 
                          v-if="kb.documentCount > 0" 
                          type="text" 
                          size="small" 
                          class="expand-btn"
                          @click="toggleDetailKnowledgeExpand(kb.id)"
                        >
                          <FileTextOutlined /> {{ kb.documentCount }}
                        </a-button>
                        <div class="delete-btn" size="small" @click="handleRemoveKnowledge(kb.id)">
                          <DeleteOutlined />
                        </div>
                      </div>
                    </div>
                    <div 
                      v-if="detailExpandedKnowledgeIds.includes(kb.id) && kb.documentCount > 0" 
                      class="resource-expand-content"
                    >
                      <div class="expand-header">
                        <FileTextOutlined /> {{ kb.documentCount }} 个文档
                      </div>
                      <div class="document-list">
                        <div 
                          class="document-item" 
                          v-for="doc in (knowledgeDocuments.get(kb.id) || [])" 
                          :key="doc.id"
                        >
                          <FileTextOutlined class="doc-icon" />
                          <span class="doc-name">{{ doc.fileName }}</span>
                        </div>
                      </div>
                    </div>
                  </template>
                </div>
              </div>

              <a-divider style="margin: 12px 0" />

              <div class="resource-section">
                <div class="resource-header">
                  <span class="resource-title">MCP工具</span>
                  <a-button type="link" size="small" @click="showMcpDrawer">
                    <PlusOutlined /> 添加
                  </a-button>
                </div>
                <div class="resource-list" v-if="agentDetail?.mcpTools?.length">
                  <template v-for="mcp in agentDetail.mcpTools" :key="mcp.id">
                    <div class="resource-item">
                      <div class="resource-info">
                        <ToolOutlined class="resource-icon" />
                        <div class="resource-text">
                          <span class="resource-name">{{ mcp.name }}</span>
                          <a-typography-paragraph
                            :ellipsis="{ rows: 2, tooltip: true }"
                            :content="mcp.description || '暂无描述'"
                            class="resource-desc"
                          />
                        </div>
                      </div>
                      <div class="resource-actions">
                        <a-button 
                          v-if="getMcpToolCount(mcp) > 0" 
                          type="text" 
                          size="small" 
                          class="expand-btn"
                          @click="toggleDetailMcpExpand(mcp.id)"
                        >
                          <ToolOutlined /> {{ getMcpToolCount(mcp) }}
                        </a-button>
                        <div class="delete-btn" size="small" @click="handleRemoveMcp(mcp.id)">
                          <DeleteOutlined />
                        </div>
                      </div>
                    </div>
                    <div 
                      v-if="detailExpandedMcpIds.includes(mcp.id) && getMcpToolCount(mcp) > 0" 
                      class="resource-expand-content"
                    >
                      <div class="expand-header">
                        <ToolOutlined /> {{ getMcpToolCount(mcp) }} 个工具
                      </div>
                      <div class="tool-list-detail">
                        <div class="tool-item" v-for="tool in getMcpTools(mcp)" :key="tool.name">
                          <span class="tool-name">{{ tool.name }}</span>
                          <a-typography-paragraph
                            :ellipsis="{ rows: 1, tooltip: true }"
                            :content="tool.description"
                            class="tool-desc"
                          />
                        </div>
                      </div>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="right-section">
          <div class="edit-section">
            <div class="section-header">
              <h3>系统提示词</h3>
            </div>
            <div class="section-content">
              <a-form layout="vertical">
                <a-form-item>
                  <a-textarea
                    v-model:value="formData.systemPrompt"
                    placeholder="请输入系统提示词，定义智能体的角色和行为..."
                    :rows="12"
                    :maxlength="995904"
                    show-count
                  />
                </a-form-item>
              </a-form>
            </div>
          </div>

          <div class="edit-section">
            <div class="section-header">
              <h3>公开设置</h3>
            </div>
            <div class="section-content">
              <a-form layout="vertical">
                <a-form-item label="是否公开">
                  <a-radio-group v-model:value="formData.isPublic">
                    <a-radio :value="0">私有</a-radio>
                    <a-radio :value="1">公开</a-radio>
                  </a-radio-group>
                  <div class="form-tip">公开后，其他用户可以在应用广场看到此智能体</div>
                </a-form-item>
              </a-form>
            </div>
          </div>
        </div>
      </div>
    </div>

    <a-drawer
      v-model:open="knowledgeDrawerVisible"
      title="添加知识库"
      placement="right"
      :width="600"
      :footer-style="{ textAlign: 'right' }"
    >
      <template #extra>
        <a-button type="primary" @click="goToCreateKnowledge">
          <PlusOutlined /> 创建知识库
        </a-button>
      </template>
      <a-tabs v-model:activeKey="knowledgeTab">
        <a-tab-pane key="public" tab="公共知识库">
          <div class="drawer-list">
            <template v-for="kb in publicKnowledgeBases" :key="kb.id">
              <div
                class="resource-item"
                :class="{ 'already-added': isKnowledgeAlreadyAdded(kb.id) }"
              >
                <div class="resource-info" @click="handleKnowledgeClick(kb)">
                  <BookOutlined class="resource-icon" />
                  <div class="resource-text">
                    <div class="resource-header-row">
                      <span class="resource-name">{{ kb.name }}</span>
                      <span v-if="isKnowledgeAlreadyAdded(kb.id)" class="already-added-tag">已添加</span>
                    </div>
                    <a-typography-paragraph
                      :ellipsis="{ rows: 2, tooltip: true }"
                      :content="kb.description || '暂无描述'"
                      class="resource-desc"
                    />
                  </div>
                </div>
                <div class="resource-actions">
                  <a-button 
                    v-if="kb.documentCount > 0" 
                    type="text" 
                    size="small" 
                    class="expand-btn"
                    @click="toggleKnowledgeExpand(kb.id)"
                  >
                    <FileTextOutlined /> {{ kb.documentCount }}
                  </a-button>
                  <template v-if="isKnowledgeAlreadyAdded(kb.id)">
                    <div class="delete-btn" @click.stop="handleRemoveKnowledge(kb.id)">
                      <DeleteOutlined />
                    </div>
                  </template>
                  <template v-else>
                    <CheckOutlined v-if="selectedKnowledgeIds.includes(kb.id)" class="check-icon" />
                  </template>
                </div>
              </div>
              <div 
                v-if="expandedKnowledgeIds.includes(kb.id) && kb.documentCount > 0" 
                class="resource-expand-content"
              >
                <div class="expand-header">
                  <FileTextOutlined /> {{ kb.documentCount }} 个文档
                </div>
                <div class="document-list">
                  <div 
                    class="document-item" 
                    v-for="doc in (drawerKnowledgeDocuments.get(kb.id) || [])" 
                    :key="doc.id"
                  >
                    <FileTextOutlined class="doc-icon" />
                    <span class="doc-name">{{ doc.fileName }}</span>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </a-tab-pane>
        <a-tab-pane key="custom" tab="自定义知识库">
          <div class="drawer-list">
            <template v-for="kb in customKnowledgeBases" :key="kb.id">
              <div
                class="resource-item"
                :class="{ 'already-added': isKnowledgeAlreadyAdded(kb.id) }"
              >
                <div class="resource-info" @click="handleKnowledgeClick(kb)">
                  <BookOutlined class="resource-icon" />
                  <div class="resource-text">
                    <div class="resource-header-row">
                      <span class="resource-name">{{ kb.name }}</span>
                      <span v-if="isKnowledgeAlreadyAdded(kb.id)" class="already-added-tag">已添加</span>
                    </div>
                    <a-typography-paragraph
                      :ellipsis="{ rows: 2, tooltip: true }"
                      :content="kb.description || '暂无描述'"
                      class="resource-desc"
                    />
                  </div>
                </div>
                <div class="resource-actions">
                  <a-button 
                    v-if="kb.documentCount > 0" 
                    type="text" 
                    size="small" 
                    class="expand-btn"
                    @click="toggleKnowledgeExpand(kb.id)"
                  >
                    <FileTextOutlined /> {{ kb.documentCount }}
                  </a-button>
                  <template v-if="isKnowledgeAlreadyAdded(kb.id)">
                    <div class="delete-btn" @click.stop="handleRemoveKnowledge(kb.id)">
                      <DeleteOutlined />
                    </div>
                  </template>
                  <template v-else>
                    <CheckOutlined v-if="selectedKnowledgeIds.includes(kb.id)" class="check-icon" />
                  </template>
                </div>
              </div>
              <div 
                v-if="expandedKnowledgeIds.includes(kb.id) && kb.documentCount > 0" 
                class="resource-expand-content"
              >
                <div class="expand-header">
                  <FileTextOutlined /> {{ kb.documentCount }} 个文档
                </div>
                <div class="document-list">
                  <div 
                    class="document-item" 
                    v-for="doc in (drawerKnowledgeDocuments.get(kb.id) || [])" 
                    :key="doc.id"
                  >
                    <FileTextOutlined class="doc-icon" />
                    <span class="doc-name">{{ doc.fileName }}</span>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </a-tab-pane>
      </a-tabs>
      <template #footer>
        <a-button class="cancel-btn" @click="knowledgeDrawerVisible = false">取消</a-button>
        <a-button class="add-btn" type="primary" @click="handleAddKnowledge" :loading="addKnowledgeLoading">
          添加 ({{ selectedKnowledgeIds.length }})
        </a-button>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="mcpDrawerVisible"
      title="添加MCP工具"
      placement="right"
      :width="600"
      :footer-style="{ textAlign: 'right' }"
    >
      <template #extra>
        <a-button type="primary" @click="goToCreateMcp">
          <PlusOutlined /> 创建MCP
        </a-button>
      </template>
      <a-tabs v-model:activeKey="mcpTab">
        <a-tab-pane key="public" tab="MCP广场">
          <div class="drawer-list">
            <template v-for="mcp in publicMcpTools" :key="mcp.id">
              <div
                class="resource-item"
                :class="{ 'already-added': isMcpAlreadyAdded(mcp.id) }"
              >
                <div class="resource-info" @click="handleMcpClick(mcp)">
                  <ToolOutlined class="resource-icon" />
                  <div class="resource-text">
                    <div class="resource-header-row">
                      <span class="resource-name">{{ mcp.name }}</span>
                      <span v-if="isMcpAlreadyAdded(mcp.id)" class="already-added-tag">已添加</span>
                    </div>
                    <a-typography-paragraph
                      :ellipsis="{ rows: 2, tooltip: true }"
                      :content="mcp.description || '暂无描述'"
                      class="resource-desc"
                    />
                  </div>
                </div>
                <div class="resource-actions">
                  <a-button 
                    v-if="getMcpToolCount(mcp) > 0" 
                    type="text" 
                    size="small" 
                    class="expand-btn"
                    @click="toggleMcpExpand(mcp.id)"
                  >
                    <ToolOutlined /> {{ getMcpToolCount(mcp) }}
                  </a-button>
                  <template v-if="isMcpAlreadyAdded(mcp.id)">
                    <div class="delete-btn" @click.stop="handleRemoveMcp(mcp.id)">
                      <DeleteOutlined />
                    </div>
                  </template>
                  <template v-else>
                    <CheckOutlined v-if="selectedMcpIds.includes(mcp.id)" class="check-icon" />
                  </template>
                </div>
              </div>
              <div 
                v-if="expandedMcpIds.includes(mcp.id) && getMcpToolCount(mcp) > 0" 
                class="resource-expand-content"
              >
                <div class="expand-header">
                  <ToolOutlined /> {{ getMcpToolCount(mcp) }} 个工具
                </div>
                <div class="tool-list-detail">
                  <div class="tool-item" v-for="tool in getMcpTools(mcp)" :key="tool.name">
                    <span class="tool-name">{{ tool.name }}</span>
                    <a-typography-paragraph
                      :ellipsis="{ rows: 1, tooltip: true }"
                      :content="tool.description"
                      class="tool-desc"
                    />
                  </div>
                </div>
              </div>
            </template>
          </div>
        </a-tab-pane>
        <a-tab-pane key="custom" tab="自定义MCP">
          <div class="drawer-list">
            <template v-for="mcp in customMcpTools" :key="mcp.id">
              <div
                class="resource-item"
                :class="{ 'already-added': isMcpAlreadyAdded(mcp.id) }"
              >
                <div class="resource-info" @click="handleMcpClick(mcp)">
                  <ToolOutlined class="resource-icon" />
                  <div class="resource-text">
                    <div class="resource-header-row">
                      <span class="resource-name">{{ mcp.name }}</span>
                      <span v-if="isMcpAlreadyAdded(mcp.id)" class="already-added-tag">已添加</span>
                    </div>
                    <a-typography-paragraph
                      :ellipsis="{ rows: 2, tooltip: true }"
                      :content="mcp.description || '暂无描述'"
                      class="resource-desc"
                    />
                  </div>
                </div>
                <div class="resource-actions">
                  <a-button 
                    v-if="getMcpToolCount(mcp) > 0" 
                    type="text" 
                    size="small" 
                    class="expand-btn"
                    @click="toggleMcpExpand(mcp.id)"
                  >
                    <ToolOutlined /> {{ getMcpToolCount(mcp) }}
                  </a-button>
                  <template v-if="isMcpAlreadyAdded(mcp.id)">
                    <div class="delete-btn" @click.stop="handleRemoveMcp(mcp.id)">
                      <DeleteOutlined />
                    </div>
                  </template>
                  <template v-else>
                    <CheckOutlined v-if="selectedMcpIds.includes(mcp.id)" class="check-icon" />
                  </template>
                </div>
              </div>
              <div 
                v-if="expandedMcpIds.includes(mcp.id) && getMcpToolCount(mcp) > 0" 
                class="resource-expand-content"
              >
                <div class="expand-header">
                  <ToolOutlined /> {{ getMcpToolCount(mcp) }} 个工具
                </div>
                <div class="tool-list-detail">
                  <div class="tool-item" v-for="tool in getMcpTools(mcp)" :key="tool.name">
                    <span class="tool-name">{{ tool.name }}</span>
                    <a-typography-paragraph
                      :ellipsis="{ rows: 1, tooltip: true }"
                      :content="tool.description"
                      class="tool-desc"
                    />
                  </div>
                </div>
              </div>
            </template>
          </div>
        </a-tab-pane>
      </a-tabs>
      <template #footer>
        <a-button class="cancel-btn" @click="mcpDrawerVisible = false">取消</a-button>
        <a-button class="add-btn" type="primary" @click="handleAddMcp" :loading="addMcpLoading">
          添加 ({{ selectedMcpIds.length }})
        </a-button>
      </template>
    </a-drawer>

    <AgentCreateModal
      v-model:visible="editModalVisible"
      mode="edit"
      :agent-id="agentId"
      @success="handleEditSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  LeftOutlined,
  SendOutlined,
  PlusOutlined,
  DeleteOutlined,
  BookOutlined,
  ToolOutlined,
  CheckOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'
import AgentCreateModal from '@/components/AgentCreateModal.vue'
import { getAgentDetail, publishAgent, updateAgent } from '@/api/agent'
import { addAgentKnowledge, removeAgentKnowledgeByKbId } from '@/api/agentKnowledge'
import { addAgentMcp, removeAgentMcpByMcpId } from '@/api/agentMcp'
import { getPublicKnowledgeBases, queryKnowledgeBases, queryDocuments } from '@/api/knowledgeBase'
import type { Document } from '@/api/knowledgeBase'
import { getPublicMcpTools, queryMcpTools } from '@/api/mcpTool'
import type { AgentDetail, KnowledgeBase, McpTool, AgentUpdateRequest } from '@/types'

const router = useRouter()
const route = useRoute()

const agentId = Number(route.params.id)
const loading = ref(true)
const publishLoading = ref(false)
const saveLoading = ref(false)
const addKnowledgeLoading = ref(false)
const addMcpLoading = ref(false)
const agentDetail = ref<AgentDetail | null>(null)

const formData = reactive({
  name: '',
  description: '',
  avatar: '',
  systemPrompt: '',
  isPublic: 0
})

const knowledgeDrawerVisible = ref(false)
const mcpDrawerVisible = ref(false)
const editModalVisible = ref(false)
const knowledgeTab = ref('public')
const mcpTab = ref('public')
const publicKnowledgeBases = ref<KnowledgeBase[]>([])
const customKnowledgeBases = ref<KnowledgeBase[]>([])
const publicMcpTools = ref<McpTool[]>([])
const customMcpTools = ref<McpTool[]>([])
const selectedKnowledgeIds = ref<number[]>([])
const selectedMcpIds = ref<number[]>([])
const expandedKnowledgeIds = ref<number[]>([])
const expandedMcpIds = ref<number[]>([])
const detailExpandedKnowledgeIds = ref<number[]>([])
const detailExpandedMcpIds = ref<number[]>([])
const knowledgeDocuments = ref<Map<number, Document[]>>(new Map())
const drawerKnowledgeDocuments = ref<Map<number, Document[]>>(new Map())

function isKnowledgeAlreadyAdded(kbId: number): boolean {
  return agentDetail.value?.knowledgeBases?.some(kb => kb.id === kbId) || false
}

function isMcpAlreadyAdded(mcpId: number): boolean {
  return agentDetail.value?.mcpTools?.some(mcp => mcp.id === mcpId) || false
}

function handleKnowledgeClick(kb: KnowledgeBase): void {
  if (isKnowledgeAlreadyAdded(kb.id)) return
  toggleKnowledge(kb.id)
}

function handleMcpClick(mcp: McpTool): void {
  if (isMcpAlreadyAdded(mcp.id)) return
  toggleMcp(mcp.id)
}

async function fetchKnowledgeDocuments(kbId: number, isDrawer: boolean = false): Promise<void> {
  const map = isDrawer ? drawerKnowledgeDocuments : knowledgeDocuments
  if (map.value.has(kbId)) return
  
  try {
    const res = await queryDocuments({ current: 1, pageSize: 100, kbId })
    map.value.set(kbId, res.records || [])
  } catch (e) {
    console.error(e)
  }
}

function toggleKnowledgeExpand(kbId: number): void {
  const index = expandedKnowledgeIds.value.indexOf(kbId)
  if (index > -1) {
    expandedKnowledgeIds.value.splice(index, 1)
  } else {
    expandedKnowledgeIds.value.push(kbId)
    fetchKnowledgeDocuments(kbId, true)
  }
}

function toggleMcpExpand(mcpId: number): void {
  const index = expandedMcpIds.value.indexOf(mcpId)
  if (index > -1) {
    expandedMcpIds.value.splice(index, 1)
  } else {
    expandedMcpIds.value.push(mcpId)
  }
}

function toggleDetailKnowledgeExpand(kbId: number): void {
  const index = detailExpandedKnowledgeIds.value.indexOf(kbId)
  if (index > -1) {
    detailExpandedKnowledgeIds.value.splice(index, 1)
  } else {
    detailExpandedKnowledgeIds.value.push(kbId)
    fetchKnowledgeDocuments(kbId, false)
  }
}

function toggleDetailMcpExpand(mcpId: number): void {
  const index = detailExpandedMcpIds.value.indexOf(mcpId)
  if (index > -1) {
    detailExpandedMcpIds.value.splice(index, 1)
  } else {
    detailExpandedMcpIds.value.push(mcpId)
  }
}

function getMcpToolCount(mcp: McpTool): number {
  if (!mcp.toolInfo) return 0
  try {
    const info = JSON.parse(mcp.toolInfo)
    if (Array.isArray(info)) {
      return info.length
    }
    return info.tools?.length || 0
  } catch {
    return 0
  }
}

function getMcpTools(mcp: McpTool): { name: string; description: string }[] {
  if (!mcp.toolInfo) return []
  try {
    const info = JSON.parse(mcp.toolInfo)
    if (Array.isArray(info)) {
      return info
    }
    return info.tools || []
  } catch {
    return []
  }
}

function goBack(): void {
  router.push('/dashboard/agent')
}

function showEditModal(): void {
  editModalVisible.value = true
}

function handleEditSuccess(): void {
  editModalVisible.value = false
  fetchAgentDetail()
}

async function fetchAgentDetail(): Promise<void> {
  try {
    loading.value = true
    const res = await getAgentDetail(agentId)
    agentDetail.value = res
    formData.name = res.name || ''
    formData.description = res.description || ''
    formData.avatar = res.avatar || ''
    formData.systemPrompt = res.systemPrompt || ''
    formData.isPublic = res.isPublic ?? 0
  } catch (e) {
    console.error(e)
    message.error('获取智能体详情失败')
  } finally {
    loading.value = false
  }
}

async function handlePublish(): Promise<void> {
  try {
    publishLoading.value = true
    const data: AgentUpdateRequest = {
      id: agentId,
      name: formData.name,
      description: formData.description,
      avatar: formData.avatar,
      systemPrompt: formData.systemPrompt,
      isPublic: formData.isPublic
    }
    await publishAgent(agentId, data)
    message.success('发布成功')
    router.push('/dashboard/agent')
  } catch (e) {
    console.error(e)
    message.error('发布失败')
  } finally {
    publishLoading.value = false
  }
}

async function showKnowledgeDrawer(): Promise<void> {
  knowledgeDrawerVisible.value = true
  selectedKnowledgeIds.value = []
  await fetchKnowledgeBases()
}

async function fetchKnowledgeBases(): Promise<void> {
  try {
    const [publicRes, customRes] = await Promise.all([
      getPublicKnowledgeBases({ current: 1, size: 100 }),
      queryKnowledgeBases({ current: 1, pageSize: 100 })
    ])
    publicKnowledgeBases.value = publicRes.records || []
    customKnowledgeBases.value = customRes.records || []
  } catch (e) {
    console.error(e)
  }
}

function toggleKnowledge(id: number): void {
  const index = selectedKnowledgeIds.value.indexOf(id)
  if (index > -1) {
    selectedKnowledgeIds.value.splice(index, 1)
  } else {
    selectedKnowledgeIds.value.push(id)
  }
}

async function handleAddKnowledge(): Promise<void> {
  if (selectedKnowledgeIds.value.length === 0) {
    message.warning('请选择要添加的知识库')
    return
  }

  try {
    addKnowledgeLoading.value = true
    await Promise.all(
      selectedKnowledgeIds.value.map(kbId =>
        addAgentKnowledge({ agentId, kbId, enabled: 1 })
      )
    )
    message.success('添加成功')
    knowledgeDrawerVisible.value = false
    fetchAgentDetail()
  } catch (e) {
    console.error(e)
    message.error('添加失败')
  } finally {
    addKnowledgeLoading.value = false
  }
}

async function handleRemoveKnowledge(kbId: number): Promise<void> {
  try {
    await removeAgentKnowledgeByKbId(agentId, kbId)
    message.success('移除成功')
    fetchAgentDetail()
  } catch (e) {
    console.error(e)
    message.error('移除失败')
  }
}

async function showMcpDrawer(): Promise<void> {
  mcpDrawerVisible.value = true
  selectedMcpIds.value = []
  await fetchMcpTools()
}

async function fetchMcpTools(): Promise<void> {
  try {
    const [publicRes, customRes] = await Promise.all([
      getPublicMcpTools({ current: 1, size: 100 }),
      queryMcpTools({ current: 1, pageSize: 100 })
    ])
    publicMcpTools.value = publicRes.records || []
    customMcpTools.value = customRes.records || []
  } catch (e) {
    console.error(e)
  }
}

function toggleMcp(id: number): void {
  const index = selectedMcpIds.value.indexOf(id)
  if (index > -1) {
    selectedMcpIds.value.splice(index, 1)
  } else {
    selectedMcpIds.value.push(id)
  }
}

async function handleAddMcp(): Promise<void> {
  if (selectedMcpIds.value.length === 0) {
    message.warning('请选择要添加的MCP工具')
    return
  }

  try {
    addMcpLoading.value = true
    await Promise.all(
      selectedMcpIds.value.map(mcpId =>
        addAgentMcp({ agentId, mcpId, enabled: 1 })
      )
    )
    message.success('添加成功')
    mcpDrawerVisible.value = false
    fetchAgentDetail()
  } catch (e) {
    console.error(e)
    message.error('添加失败')
  } finally {
    addMcpLoading.value = false
  }
}

async function handleRemoveMcp(mcpId: number): Promise<void> {
  try {
    await removeAgentMcpByMcpId(agentId, mcpId)
    message.success('移除成功')
    fetchAgentDetail()
  } catch (e) {
    console.error(e)
    message.error('移除失败')
  }
}

function goToCreateKnowledge(): void {
  router.push('/dashboard/knowledge')
}

function goToCreateMcp(): void {
  router.push('/dashboard/mcp')
}

const hasSaved = ref(false)

async function autoSave(): Promise<void> {
  if (hasSaved.value) return
  try {
    const data: AgentUpdateRequest = {
      id: agentId,
      name: formData.name,
      description: formData.description,
      avatar: formData.avatar,
      systemPrompt: formData.systemPrompt,
      isPublic: formData.isPublic
    }
    await updateAgent(data)
    hasSaved.value = true
  } catch (e) {
    console.error('自动保存失败', e)
  }
}

function handleBeforeUnload(e: BeforeUnloadEvent): void {
  if (!hasSaved.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

onBeforeUnmount(() => {
  autoSave()
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(async (to, from, next) => {
  if (!hasSaved.value) {
    await autoSave()
    message.success('应用已自动保存')
  }
  next()
})

onMounted(() => {
  fetchAgentDetail()
  window.addEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style lang="scss" scoped>
.agent-edit {
  height: 100%;
  display: flex;
  flex-direction: column;

  .page-header {
    flex-shrink: 0;

    .header-content {
      padding: 16px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        display: flex;
        align-items: center;
        gap: 12px;

        .back-btn {
          &:hover {
            cursor: pointer;
          }
          h2 {
            color: #5f6368;
            font-weight: 400;
          }
        }

        h2 {
          font-size: 20px;
          font-weight: 600;
          color: #202124;
          margin: 0;
        }

        .editable-title {
          cursor: pointer;
          transition: color 0.3s;

          &:hover {
            color: $primary-color;
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
    flex: 1;
    overflow-y: auto;
    padding: 24px;
  }

  .content-wrapper {
    display: flex;
    gap: 24px;
    max-width: 1400px;
  }

  .left-section {
    flex: 1;
    min-width: 0;
    max-height: 730px;
    overflow-y: auto;
    scrollbar-width: none;
    -ms-overflow-style: none;
  }

  .right-section {
    width: 400px;
    flex-shrink: 0;
    max-height: 730px;
    overflow-y: auto;
  }

  .edit-section {
    background: #fff;
    border-radius: 12px;
    margin-bottom: 24px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);

    .section-header {
      padding: 16px 20px;
      border-bottom: 1px solid #f0f0f0;

      h3 {
        font-size: 16px;
        font-weight: 600;
        margin: 0;
      }
    }

    .section-content {
      padding: 20px;

      .form-tip {
        font-size: 12px;
        color: #999;
        margin-top: 8px;
      }
    }
  }

  .resource-section {
    .resource-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;

      .resource-title {
        font-weight: 600;
        font-size: 14px;
      }
    }

    .resource-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .resource-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px;
      background: #fafafa;
      border-radius: 8px;

      &:hover {
        background: #f0f0f0;
      }

      .resource-info {
        display: flex;
        align-items: center;
        gap: 12px;

        .resource-icon {
          font-size: 18px;
          color: $primary-color;
        }

        .resource-text {
          display: flex;
          flex-direction: column;
          gap: 2px;
          flex: 1;
          min-width: 0;

          .resource-header-row {
            display: flex;
            align-items: center;
            gap: 8px;
          }

          .resource-name {
            font-size: 14px;
            font-weight: 500;
          }

          .resource-desc {
            font-size: 12px;
            color: #666;
            margin: 0;
            margin-right: 16px;
          }
        }
      }

      .resource-actions {
        display: flex;
        align-items: center;
        gap: 8px;

        .expand-btn {
          color: #9b9aac;
          opacity: 0;
          transition: opacity 0.3s;
        }

        .delete-btn {
          color: #9b9aac;
          cursor: pointer;
          margin-left: 10px;
          margin-right: 10px;
        }
      }

      &:hover .resource-actions .expand-btn {
        opacity: 1;
      }
    }

    .resource-expand-content {
      margin-top: 8px;
      padding: 12px;
      background: #fff;
      border-radius: 8px;
      border: 1px solid #f0f0f0;
      max-height: 200px;
      overflow-y: auto;

      .expand-header {
        font-size: 12px;
        color: #666;
        display: flex;
        align-items: center;
        gap: 4px;
        margin-bottom: 8px;
      }

      .document-list {
        .document-item {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 6px 0;
          border-bottom: 1px solid #f5f5f5;

          &:last-child {
            border-bottom: none;
          }

          .doc-icon {
            font-size: 14px;
            color: #999;
          }

          .doc-name {
            font-size: 13px;
            color: #333;
            flex: 1;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }
        }
      }

      .tool-list-detail {
        .tool-item {
          padding: 8px 0;
          border-bottom: 1px solid #f5f5f5;

          &:last-child {
            border-bottom: none;
          }

          .tool-name {
            font-size: 13px;
            font-weight: 500;
            color: #333;
            display: block;
            margin-bottom: 2px;
          }

          .tool-desc {
            margin: 0;
            font-size: 12px;
            color: #999;
          }
        }
      }
    }
  }
}

.drawer-list {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .resource-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px;
    background: #fafafa;
    border-radius: 8px;
    transition: all 0.3s;

    &:hover {
      background: #f0f0f0;
    }

    &.already-added {
      opacity: 0.7;
    }

    .resource-info {
      display: flex;
      align-items: center;
      gap: 12px;
      flex: 1;
      min-width: 0;
      cursor: pointer;

      .resource-icon {
        font-size: 18px;
        color: $primary-color;
      }

      .resource-text {
        display: flex;
        flex-direction: column;
        gap: 2px;
        flex: 1;
        min-width: 0;

        .resource-header-row {
          display: flex;
          align-items: center;
          gap: 8px;

          .already-added-tag {
            font-size: 12px;
            color: #52c41a;
            background: rgba(82, 196, 26, 0.1);
            padding: 2px 8px;
            border-radius: 4px;
          }
        }

        .resource-name {
          font-size: 14px;
          font-weight: 500;
        }

        .resource-desc {
          font-size: 12px;
          color: #666;
          margin: 0;
          margin-right: 16px;
        }
      }
    }

    .resource-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-shrink: 0;

      .expand-btn {
        color: #9b9aac;
        opacity: 0;
        transition: opacity 0.3s;
      }

      .delete-btn {
        color: #9b9aac;
        cursor: pointer;
        margin-left: 8px;
      }

      .check-icon {
        color: $primary-color;
        font-size: 16px;
        margin-left: 8px;
      }
    }

    &:hover .resource-actions .expand-btn {
      opacity: 1;
    }
  }

  .resource-expand-content {
    margin-top: 8px;
    padding: 12px;
    background: #fff;
    border-radius: 8px;
    border: 1px solid #f0f0f0;
    max-height: 200px;
    overflow-y: auto;

    .expand-header {
      font-size: 12px;
      color: #666;
      display: flex;
      align-items: center;
      gap: 4px;
      margin-bottom: 8px;
    }

    .document-list {
      .document-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 6px 0;
        border-bottom: 1px solid #f5f5f5;

        &:last-child {
          border-bottom: none;
        }

        .doc-icon {
          font-size: 14px;
          color: #999;
        }

        .doc-name {
          font-size: 13px;
          color: #333;
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }

    .tool-list-detail {
      .tool-item {
        padding: 8px 0;
        border-bottom: 1px solid #f5f5f5;

        &:last-child {
          border-bottom: none;
        }

        .tool-name {
          font-size: 13px;
          font-weight: 500;
          color: #333;
          display: block;
          margin-bottom: 4px;
        }

        .tool-desc {
          font-size: 12px;
          color: #666;
          margin: 0;
        }
      }
    }
  }
}

.cancel-btn {
  margin-right: 8px;
}
</style>
