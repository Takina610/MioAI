<template>
  <div class="dash-page">
    <PageHeader
      back-label="智能体管理"
      back-to="/dashboard/agent"
      :title="agentDetail?.name || '智能体编辑'"
      :description="agentDetail?.description || ''"
      title-clickable
      @title-click="editModalVisible = true"
    >
      <template #icon>
        <span class="page-agent-icon" @click="editModalVisible = true">
          <BotIcon
            v-if="agentIconConfig"
            :shape="agentIconConfig.shape"
            :fill="agentIconConfig.fill"
            :size="34"
            :live="false"
            eye-color="#f0eee6"
          />
          <img v-else :src="agentDetail?.avatar || '/logo.png'" alt="" class="page-agent-avatar" />
        </span>
      </template>
      <template #actions>
        <div class="public-switch">
          <span class="switch-label">公开</span>
          <a-switch
            :checked="formData.isPublic === 1"
            @change="(checked: any) => (formData.isPublic = checked ? 1 : 0)"
          />
        </div>
        <a-button type="primary" :loading="publishLoading" @click="handlePublish">
          <SendOutlined /> 发布
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content" v-if="!loading">
      <div class="edit-sections">
        <div class="dash-section prompt-section">
          <h3 class="section-title">系统提示词</h3>
          <a-textarea
            v-model:value="formData.systemPrompt"
            placeholder="定义这个智能体的角色、语气与行为边界"
            :auto-size="{ minRows: 8, maxRows: 20 }"
            :maxlength="995904"
            show-count
          />
        </div>

        <div class="resource-grid">
          <div class="dash-section resource-card">
            <div class="resource-header">
              <span class="resource-title">知识库<span v-if="agentDetail?.knowledgeBases?.length" class="resource-count">{{ agentDetail.knowledgeBases.length }}</span></span>
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
                  <KbRetrievalConfig :agent-id="agentId" :kb-id="kb.id" />
                </template>
              </ResourceItem>
            </div>
            <div v-else class="resource-empty">未绑定</div>
          </div>

          <div class="dash-section resource-card">
            <div class="resource-header">
              <span class="resource-title">MCP工具<span v-if="agentDetail?.mcpTools?.length" class="resource-count">{{ agentDetail.mcpTools.length }}</span></span>
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
            <div v-else class="resource-empty">未绑定</div>
          </div>

          <div class="dash-section resource-card">
            <div class="resource-header">
              <span class="resource-title">技能<span v-if="agentDetail?.skills?.length" class="resource-count">{{ agentDetail.skills.length }}</span></span>
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
            <div v-else class="resource-empty">未绑定</div>
          </div>
        </div>

        <div class="dash-section memory-card">
          <AgentMemorySection :agent-id="agentId" />
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
      create-label="安装技能"
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
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { parseBotIcon } from '@/components/bot-icon/types'
import { countMcpTools } from '@/utils/mcpTool'
import PageHeader from '../components/PageHeader.vue'
import ResourceItem from './components/ResourceItem.vue'
import ResourceExpandList from './components/ResourceExpandList.vue'
import KbRetrievalConfig from './components/KbRetrievalConfig.vue'
import ResourceSelectDrawer from './components/ResourceSelectDrawer.vue'
import AgentMemorySection from './components/AgentMemorySection.vue'
import { useAgentEdit } from './composables/useAgentEdit'
import { useAgentResources } from './composables/useAgentResources'

const router = useRouter()
const agentId = Number(useRoute().params.id)
const editModalVisible = ref(false)
const agentIconConfig = computed(() => parseBotIcon(agentDetail.value?.icon))

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
  { key: 'skills', label: '技能', items: customSkills.value }
])

function handleEditSuccess(): void {
  editModalVisible.value = false
  fetchAgentDetail()
}
</script>

<style lang="scss" scoped>
.page-agent-icon {
  display: inline-flex;
  align-items: center;
  margin-right: 10px;
  cursor: pointer;
  flex-shrink: 0;

  .page-agent-avatar {
    width: 32px;
    height: 32px;
    border-radius: 25%;
    object-fit: cover;
    display: block;
  }
}

.public-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 8px;

  .switch-label {
    font-size: 13px;
    color: #5f5d55;
  }
}

// 纵向流：提示词全宽 → 三资源卡自适应并排 → 记忆全宽（无联动内容不分栏）
.edit-sections {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1400px;
}

.prompt-section {
  .section-title {
    margin-bottom: 12px;
  }
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 16px;
  align-items: start;
}

.resource-card {
  .resource-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .resource-title {
      font-weight: 600;
      font-size: 14px;
      display: flex;
      align-items: center;
      gap: 8px;

      .resource-count {
        min-width: 20px;
        height: 18px;
        padding: 0 6px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 11px;
        font-weight: 500;
        color: $primary-color;
        background: rgba(42, 161, 169, 0.1);
        border-radius: 9px;
      }
    }
  }

  .resource-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .resource-empty {
    padding: 12px;
    font-size: 12px;
    color: #a5a29a;
    background: #f5f3ec;
    border-radius: 8px;
  }
}
</style>
