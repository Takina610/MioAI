<template>
  <div class="dash-page">
    <PageHeader
      back-label="智能体管理"
      back-to="/dashboard/agent"
      :title="agentDetail?.name || '智能体编辑'"
      title-clickable
      @title-click="editModalVisible = true"
    >
      <template #actions>
        <a-button type="primary" :loading="publishLoading" @click="handlePublish">
          <SendOutlined /> 发布
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content" v-if="!loading">
      <div class="content-wrapper">
        <div class="left-section">
          <div class="dash-section">
            <h3 class="section-title">资源配置</h3>

            <div class="resource-section">
              <div class="resource-header">
                <span class="resource-title">知识库</span>
                <a-button type="link" size="small" @click="showKnowledgeDrawer">
                  <PlusOutlined /> 添加
                </a-button>
              </div>
              <div class="resource-list" v-if="agentDetail?.knowledgeBases?.length">
                <ResourceItem
                  v-for="kb in agentDetail.knowledgeBases"
                  :key="kb.id"
                  :icon="BookOutlined"
                  :name="kb.name"
                  :description="kb.description"
                  :count="kb.documentCount || 0"
                  :count-icon="FileTextOutlined"
                  removable
                  @remove="handleRemoveKnowledge(kb.id)"
                  @expand="fetchKnowledgeDocuments(kb.id, 'main')"
                >
                  <template #expand>
                    <ResourceExpandList
                      :icon="FileTextOutlined"
                      :title="`${kb.documentCount} 个文档`"
                      :items="getDocuments(kb.id, 'main')"
                    />
                  </template>
                </ResourceItem>
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
                <ResourceItem
                  v-for="mcp in agentDetail.mcpTools"
                  :key="mcp.id"
                  :icon="ToolOutlined"
                  :name="mcp.name"
                  :description="mcp.description"
                  :count="countMcpTools(mcp.toolInfo)"
                  :count-icon="ToolOutlined"
                  removable
                  @remove="handleRemoveMcp(mcp.id)"
                >
                  <template #expand>
                    <ResourceExpandList
                      :icon="ToolOutlined"
                      :title="`${countMcpTools(mcp.toolInfo)} 个工具`"
                      :items="getTools(mcp)"
                    />
                  </template>
                </ResourceItem>
              </div>
            </div>

            <a-divider style="margin: 12px 0" />

            <div class="resource-section">
              <div class="resource-header">
                <span class="resource-title">技能</span>
                <a-button type="link" size="small" @click="showSkillDrawer">
                  <PlusOutlined /> 添加
                </a-button>
              </div>
              <div class="resource-list" v-if="agentDetail?.skills?.length">
                <ResourceItem
                  v-for="skill in agentDetail.skills"
                  :key="skill.id"
                  :icon="ThunderboltOutlined"
                  :name="skill.name"
                  :description="skill.description"
                  :count="(skill.files?.length || 0) + 1"
                  :count-icon="FileTextOutlined"
                  removable
                  @remove="handleRemoveSkill(skill.id)"
                />
              </div>
            </div>
          </div>
        </div>

        <div class="right-section">
          <div class="dash-section">
            <h3 class="section-title">系统提示词</h3>
            <a-textarea
              v-model:value="formData.systemPrompt"
              placeholder="请输入系统提示词，定义智能体的角色和行为..."
              :rows="12"
              :maxlength="995904"
              show-count
            />
          </div>

          <div class="dash-section">
            <h3 class="section-title">公开设置</h3>
            <a-form layout="vertical">
              <a-form-item label="是否公开">
                <a-radio-group v-model:value="formData.isPublic">
                  <a-radio :value="0">私有</a-radio>
                  <a-radio :value="1">公开</a-radio>
                </a-radio-group>
              </a-form-item>
            </a-form>
          </div>
        </div>
      </div>
    </div>

    <ResourceSelectDrawer
      v-model:open="knowledgeDrawerVisible"
      title="添加知识库"
      create-label="创建知识库"
      :tabs="knowledgeTabs"
      :selected-count="selectedKnowledgeIds.length"
      :confirm-loading="addKnowledgeLoading"
      @create="router.push('/dashboard/knowledge')"
      @confirm="handleAddKnowledge"
    >
      <template #default="{ item: kb }">
        <ResourceItem
          selectable
          :already-added="isKnowledgeAlreadyAdded(kb.id)"
          :removable="isKnowledgeAlreadyAdded(kb.id)"
          :selected="selectedKnowledgeIds.includes(kb.id)"
          :icon="BookOutlined"
          :name="kb.name"
          :description="kb.description"
          :count="kb.documentCount || 0"
          :count-icon="FileTextOutlined"
          @select="toggleKnowledgeSelection(kb.id)"
          @remove="handleRemoveKnowledge(kb.id)"
          @expand="fetchKnowledgeDocuments(kb.id, 'drawer')"
        >
          <template #expand>
            <ResourceExpandList
              :icon="FileTextOutlined"
              :title="`${kb.documentCount} 个文档`"
              :items="getDocuments(kb.id, 'drawer')"
            />
          </template>
        </ResourceItem>
      </template>
    </ResourceSelectDrawer>

    <ResourceSelectDrawer
      v-model:open="mcpDrawerVisible"
      title="添加MCP工具"
      create-label="创建MCP"
      :tabs="mcpTabs"
      :selected-count="selectedMcpIds.length"
      :confirm-loading="addMcpLoading"
      @create="router.push('/dashboard/mcp')"
      @confirm="handleAddMcp"
    >
      <template #default="{ item: mcp }">
        <ResourceItem
          selectable
          :already-added="isMcpAlreadyAdded(mcp.id)"
          :removable="isMcpAlreadyAdded(mcp.id)"
          :selected="selectedMcpIds.includes(mcp.id)"
          :icon="ToolOutlined"
          :name="mcp.name"
          :description="mcp.description"
          :count="countMcpTools(mcp.toolInfo)"
          :count-icon="ToolOutlined"
          @select="toggleMcpSelection(mcp.id)"
          @remove="handleRemoveMcp(mcp.id)"
        >
          <template #expand>
            <ResourceExpandList
              :icon="ToolOutlined"
              :title="`${countMcpTools(mcp.toolInfo)} 个工具`"
              :items="getTools(mcp)"
            />
          </template>
        </ResourceItem>
      </template>
    </ResourceSelectDrawer>

    <ResourceSelectDrawer
      v-model:open="skillDrawerVisible"
      title="添加技能"
      create-label="创建技能"
      :tabs="skillTabs"
      :selected-count="selectedSkillIds.length"
      :confirm-loading="addSkillLoading"
      @create="router.push('/dashboard/skill')"
      @confirm="handleAddSkill"
    >
      <template #default="{ item: skill }">
        <ResourceItem
          selectable
          :already-added="isSkillAlreadyAdded(skill.id)"
          :removable="isSkillAlreadyAdded(skill.id)"
          :selected="selectedSkillIds.includes(skill.id)"
          :icon="ThunderboltOutlined"
          :name="skill.name"
          :description="skill.description"
          :count="(skill.files?.length || 0) + 1"
          :count-icon="FileTextOutlined"
          @select="toggleSkillSelection(skill.id)"
          @remove="handleRemoveSkill(skill.id)"
        />
      </template>
    </ResourceSelectDrawer>

    <AgentCreateModal
      v-model:visible="editModalVisible"
      mode="edit"
      :agent-id="agentId"
      @success="handleEditSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  SendOutlined,
  PlusOutlined,
  BookOutlined,
  ToolOutlined,
  FileTextOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import AgentCreateModal from '@/components/AgentCreateModal.vue'
import { countMcpTools } from '@/utils/mcpTool'
import PageHeader from '../components/PageHeader.vue'
import ResourceItem from './components/ResourceItem.vue'
import ResourceExpandList from './components/ResourceExpandList.vue'
import ResourceSelectDrawer from './components/ResourceSelectDrawer.vue'
import { useAgentEdit } from './composables/useAgentEdit'
import { useAgentResources } from './composables/useAgentResources'

const router = useRouter()
const agentId = Number(useRoute().params.id)
const editModalVisible = ref(false)

const { loading, publishLoading, agentDetail, formData, fetchAgentDetail, handlePublish } = useAgentEdit(agentId)

const {
  knowledgeDrawerVisible,
  mcpDrawerVisible,
  skillDrawerVisible,
  addKnowledgeLoading,
  addMcpLoading,
  addSkillLoading,
  publicKnowledgeBases,
  customKnowledgeBases,
  publicMcpTools,
  customMcpTools,
  publicSkills,
  customSkills,
  selectedKnowledgeIds,
  selectedMcpIds,
  selectedSkillIds,
  isKnowledgeAlreadyAdded,
  isMcpAlreadyAdded,
  isSkillAlreadyAdded,
  toggleKnowledgeSelection,
  toggleMcpSelection,
  toggleSkillSelection,
  fetchKnowledgeDocuments,
  getDocuments,
  getTools,
  showKnowledgeDrawer,
  showMcpDrawer,
  showSkillDrawer,
  handleAddKnowledge,
  handleRemoveKnowledge,
  handleAddMcp,
  handleRemoveMcp,
  handleAddSkill,
  handleRemoveSkill
} = useAgentResources(agentId, agentDetail, fetchAgentDetail)

const knowledgeTabs = computed(() => [
  { key: 'public', label: '公共知识库', items: publicKnowledgeBases.value },
  { key: 'custom', label: '自定义知识库', items: customKnowledgeBases.value }
])

const mcpTabs = computed(() => [
  { key: 'public', label: 'MCP广场', items: publicMcpTools.value },
  { key: 'custom', label: '自定义MCP', items: customMcpTools.value }
])

const skillTabs = computed(() => [
  { key: 'public', label: '公开技能', items: publicSkills.value },
  { key: 'custom', label: '我的技能', items: customSkills.value }
])

function handleEditSuccess(): void {
  editModalVisible.value = false
  fetchAgentDetail()
}
</script>

<style lang="scss" scoped>
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

  &::-webkit-scrollbar {
    display: none;
  }
}

.right-section {
  width: 400px;
  flex-shrink: 0;
  max-height: 730px;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.resource-section {
  &:last-child {
    margin-bottom: 0;
  }

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
}
</style>
