<template>
  <div class="admin-agent-page">
    <!-- 统计卡片 -->
    <div class="stats-bar">
      <div class="stat-card total">
        <div class="stat-icon"><RobotOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">总智能体</div>
        </div>
      </div>
      <div class="stat-card published">
        <div class="stat-icon"><CloudUploadOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.published }}</div>
          <div class="stat-label">已发布</div>
        </div>
      </div>
      <div class="stat-card draft">
        <div class="stat-icon"><EditOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.draft }}</div>
          <div class="stat-label">草稿</div>
        </div>
      </div>
      <div class="stat-card disabled">
        <div class="stat-icon"><StopOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.disabled }}</div>
          <div class="stat-label">已禁用</div>
        </div>
      </div>
    </div>

    <!-- 搜索与筛选 -->
    <div class="filter-bar">
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索智能体名称"
        class="search-input"
        allow-clear
        @search="handleSearch"
        @change="handleSearchChange"
      />
      <a-select
        v-model:value="filterStatus"
        placeholder="全部状态"
        class="status-select"
        allow-clear
        @change="handleSearch"
      >
        <a-select-option :value="0">草稿</a-select-option>
        <a-select-option :value="1">已发布</a-select-option>
        <a-select-option :value="2">已禁用</a-select-option>
      </a-select>
    </div>

    <!-- 智能体卡片网格 -->
    <div v-if="!loading || agentList.length > 0" class="agent-grid">
      <div v-for="agent in agentList" :key="agent.id" class="agent-card">
        <!-- 卡片头部 -->
        <div class="card-header">
          <a-avatar :src="agent.avatar" :size="56" class="agent-avatar">
            {{ agent.name?.charAt(0) || 'A' }}
          </a-avatar>
          <div class="header-info">
            <div class="agent-name">{{ agent.name || '未命名' }}</div>
            <div class="agent-meta">
              <a-tag :color="statusColor(agent.status)" class="status-tag">
                {{ statusText(agent.status) }}
              </a-tag>
              <span class="usage-count"><FireOutlined /> {{ agent.usageCount || 0 }}</span>
            </div>
          </div>
        </div>

        <!-- 描述 -->
        <div class="card-desc" :title="agent.description">
          {{ agent.description || '暂无描述' }}
        </div>

        <!-- 折叠：关联资源 -->
        <div class="card-collapse">
          <div class="collapse-trigger" @click="toggleExpand(agent.id)">
            <span class="collapse-text">
              <AppstoreOutlined /> 关联资源
              <span v-if="agent._resourceSummary" class="resource-summary">{{ agent._resourceSummary }}</span>
            </span>
            <DownOutlined :class="{ rotated: expandedIds.includes(agent.id) }" class="collapse-arrow" />
          </div>
          <transition name="expand">
            <div v-if="expandedIds.includes(agent.id)" class="collapse-content">
              <div v-if="!agent._detail" class="detail-loading">
                <a-spin size="small" />
              </div>
              <template v-else>
                <!-- 知识库 -->
                <div class="resource-section">
                  <div class="section-title"><BookOutlined /> 知识库</div>
                  <div v-if="agent._detail.knowledgeBases?.length" class="resource-list">
                    <div v-for="kb in agent._detail.knowledgeBases" :key="kb.id" class="resource-item">
                      <BookOutlined class="resource-icon" />
                      <div class="resource-text">
                        <span class="resource-name">{{ kb.name }}</span>
                        <span class="resource-desc">{{ kb.description || '暂无描述' }}</span>
                      </div>
                      <a-tag v-if="kb.documentCount > 0" class="doc-count">{{ kb.documentCount }} 文档</a-tag>
                    </div>
                  </div>
                  <div v-else class="empty-resource">暂无关联知识库</div>
                </div>

                <a-divider style="margin: 8px 0" />

                <!-- MCP 工具 -->
                <div class="resource-section">
                  <div class="section-title"><ToolOutlined /> MCP 工具</div>
                  <div v-if="agent._detail.mcpTools?.length" class="resource-list">
                    <div v-for="mcp in agent._detail.mcpTools" :key="mcp.id" class="resource-item">
                      <ToolOutlined class="resource-icon" />
                      <div class="resource-text">
                        <span class="resource-name">{{ mcp.name }}</span>
                        <span class="resource-desc">{{ mcp.description || '暂无描述' }}</span>
                      </div>
                    </div>
                  </div>
                  <div v-else class="empty-resource">暂无关联 MCP 工具</div>
                </div>
              </template>
            </div>
          </transition>
        </div>

        <!-- 卡片底部操作 -->
        <div class="card-footer">
          <span class="create-time"><CalendarOutlined /> {{ formatDate(agent.createTime) }}</span>
          <div class="card-actions">
            <a-button type="link" size="small" @click="openEditModal(agent)">
              <EditOutlined /> 编辑
            </a-button>
            <a-popconfirm
              title="确认删除该智能体？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleDelete(agent.id)"
            >
              <a-button type="link" size="small" danger>
                <DeleteOutlined />
              </a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <a-empty v-if="!loading && agentList.length === 0" description="暂无智能体数据" class="empty-state" />

    <!-- 加载中 -->
    <div v-if="loading && agentList.length === 0" class="loading-wrap">
      <a-spin size="large" />
    </div>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <a-pagination
        v-model:current="pagination.current"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-size-options="['12', '24', '48']"
        show-size-changer
        :show-total="(total: number) => `共 ${total} 条`"
        @change="handlePageChange"
      />
    </div>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="editModalVisible"
      title="编辑智能体"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleEditSubmit"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="智能体名称">
          <a-input v-model:value="editForm.name" placeholder="请输入智能体名称" />
        </a-form-item>
        <a-form-item label="智能体描述">
          <a-textarea v-model:value="editForm.description" :rows="3" placeholder="请输入智能体描述" />
        </a-form-item>
        <a-form-item label="系统提示词">
          <a-textarea v-model:value="editForm.systemPrompt" :rows="5" placeholder="请输入系统提示词" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="editForm.status" placeholder="请选择状态">
            <a-select-option :value="0" :disabled="editForm._originalStatus !== 0">
              草稿
              <span v-if="editForm._originalStatus !== 0" class="disabled-tip">（不可回退）</span>
            </a-select-option>
            <a-select-option :value="1">已发布</a-select-option>
            <a-select-option :value="2">已禁用</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="是否公开">
          <a-radio-group v-model:value="editForm.isPublic">
            <a-radio :value="0">私有</a-radio>
            <a-radio :value="1">公开</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  RobotOutlined,
  CloudUploadOutlined,
  EditOutlined,
  StopOutlined,
  AppstoreOutlined,
  BookOutlined,
  ToolOutlined,
  FireOutlined,
  CalendarOutlined,
  DeleteOutlined,
  DownOutlined
} from '@ant-design/icons-vue'
import { searchAgents, getAgentDetail, updateAgent, deleteAgent } from '@/api/adminAgent'
import type { AgentAdminUpdateRequest } from '@/api/adminAgent'
import type { Agent, AgentDetail } from '@/types'

interface AgentWithDetail extends Agent {
  _detail?: AgentDetail
  _resourceSummary?: string
}

const loading = ref(false)
const agentList = ref<AgentWithDetail[]>([])
const searchKeyword = ref('')
const filterStatus = ref<number | undefined>(undefined)
const expandedIds = ref<number[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 12,
  total: 0
})

const stats = reactive({
  total: 0,
  published: 0,
  draft: 0,
  disabled: 0
})

const editModalVisible = ref(false)
const editForm = reactive<AgentAdminUpdateRequest & { _originalStatus?: number }>({
  id: 0,
  name: '',
  description: '',
  systemPrompt: '',
  status: 0,
  isPublic: 0,
  _originalStatus: 0
})

async function fetchAgentList() {
  loading.value = true
  try {
    const res = await searchAgents({
      current: pagination.current,
      pageSize: pagination.pageSize,
      name: searchKeyword.value || undefined,
      status: filterStatus.value
    })
    agentList.value = (res.records || []).map(a => ({ ...a, _detail: undefined, _resourceSummary: undefined }))
    pagination.total = res.total || 0
    stats.total = pagination.total
    stats.published = agentList.value.filter(a => a.status === 1).length
    stats.draft = agentList.value.filter(a => a.status === 0).length
    stats.disabled = agentList.value.filter(a => a.status === 2).length
  } catch (e) {
    message.error('获取智能体列表失败')
  } finally {
    loading.value = false
  }
}

async function toggleExpand(id: number) {
  const idx = expandedIds.value.indexOf(id)
  if (idx > -1) {
    expandedIds.value.splice(idx, 1)
    return
  }
  expandedIds.value.push(id)

  // 懒加载详情
  const agent = agentList.value.find(a => a.id === id)
  if (agent && !agent._detail) {
    try {
      const detail = await getAgentDetail(id)
      agent._detail = detail
      const kbCount = detail.knowledgeBases?.length || 0
      const mcpCount = detail.mcpTools?.length || 0
      agent._resourceSummary = `${kbCount} 知识库 · ${mcpCount} MCP`
    } catch (e) {
      message.error('获取智能体详情失败')
    }
  }
}

function handleSearch() {
  pagination.current = 1
  fetchAgentList()
}

function handleSearchChange() {
  if (!searchKeyword.value) {
    handleSearch()
  }
}

function handlePageChange() {
  fetchAgentList()
}

function openEditModal(agent: Agent) {
  editForm.id = agent.id
  editForm.name = agent.name || ''
  editForm.description = agent.description || ''
  editForm.systemPrompt = agent.systemPrompt || ''
  editForm.status = agent.status
  editForm.isPublic = agent.isPublic
  editForm._originalStatus = agent.status
  editModalVisible.value = true
}

async function handleEditSubmit() {
  try {
    const { _originalStatus, ...data } = editForm
    await updateAgent(data)
    message.success('更新成功')
    editModalVisible.value = false
    fetchAgentList()
  } catch (e) {
    message.error('更新失败')
  }
}

async function handleDelete(id: number) {
  try {
    await deleteAgent(id)
    message.success('删除成功')
    fetchAgentList()
  } catch (e) {
    message.error('删除失败')
  }
}

function statusText(status: number): string {
  return { 0: '草稿', 1: '已发布', 2: '已禁用' }[status] || '未知'
}

function statusColor(status: number): string {
  return { 0: '#a5a29a', 1: '#2aa1a9', 2: '#c0453a' }[status] || '#a5a29a'
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => {
  fetchAgentList()
})
</script>

<style lang="scss" scoped>
.admin-agent-page {
  padding: 8px 0;

  .stats-bar {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 24px;

    .stat-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px 24px;
      background: linear-gradient(180deg, #ffffff 0%, #f5f3ec 100%);
      border: 1px solid #e0ddd2;
      border-radius: 12px;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04), inset 0 1px 0 #ffffff;

      .stat-icon {
        width: 48px;
        height: 48px;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 10px;
        font-size: 22px;
        color: #fff;
      }

      &.total .stat-icon { background: linear-gradient(135deg, #2aa1a9, #1d7a80); }
      &.published .stat-icon { background: linear-gradient(135deg, #10b981, #059669); }
      &.draft .stat-icon { background: linear-gradient(135deg, #f59e0b, #d97706); }
      &.disabled .stat-icon { background: linear-gradient(135deg, #c0453a, #a83a30); }

      .stat-info {
        .stat-value {
          font-size: 24px;
          font-weight: 700;
          color: #141413;
          line-height: 1.2;
        }
        .stat-label {
          font-size: 13px;
          color: #6e6b62;
          margin-top: 2px;
        }
      }
    }
  }

  .filter-bar {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;

    .search-input { width: 320px; }
    .status-select { width: 140px; }
  }

  .agent-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: 16px;
    margin-bottom: 24px;

    .agent-card {
      background: #ffffff;
      border: 1px solid #e8e6dc;
      border-radius: 12px;
      padding: 20px;
      transition: all 0.25s ease;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);

      &:hover {
        transform: translateY(-3px);
        box-shadow: 0 8px 20px rgba(42, 161, 169, 0.1), 0 2px 6px rgba(0, 0, 0, 0.06);
        border-color: #2aa1a9;
      }

      .card-header {
        display: flex;
        align-items: center;
        gap: 14px;
        margin-bottom: 12px;

        .agent-avatar {
          border: 2px solid #f0ede4;
          flex-shrink: 0;
        }

        .header-info {
          flex: 1;
          min-width: 0;

          .agent-name {
            font-size: 16px;
            font-weight: 600;
            color: #141413;
            margin-bottom: 4px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .agent-meta {
            display: flex;
            align-items: center;
            gap: 10px;

            .status-tag {
              font-size: 12px;
              border-radius: 4px;
              margin: 0;
            }

            .usage-count {
              font-size: 12px;
              color: #a5a29a;
            }
          }
        }
      }

      .card-desc {
        font-size: 13px;
        color: #6e6b62;
        line-height: 1.5;
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        min-height: 40px;
        margin-bottom: 12px;
      }

      .card-collapse {
        margin-bottom: 12px;

        .collapse-trigger {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 8px 12px;
          background: #f5f3ec;
          border-radius: 8px;
          cursor: pointer;
          transition: background 0.2s;

          &:hover {
            background: #f2f8f8;
          }

          .collapse-text {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 13px;
            color: #5f5d55;
            font-weight: 500;

            .resource-summary {
              color: #a5a29a;
              font-size: 12px;
              margin-left: 4px;
            }
          }

          .collapse-arrow {
            font-size: 12px;
            color: #a5a29a;
            transition: transform 0.2s;

            &.rotated {
              transform: rotate(180deg);
            }
          }
        }

        .collapse-content {
          padding: 12px;
          background: #f5f3ec;
          border-radius: 8px;
          margin-top: 4px;

          .detail-loading {
            display: flex;
            justify-content: center;
            padding: 16px 0;
          }

          .resource-section {
            .section-title {
              display: flex;
              align-items: center;
              gap: 6px;
              font-size: 12px;
              font-weight: 600;
              color: #5f5d55;
              margin-bottom: 8px;
            }

            .resource-list {
              display: flex;
              flex-direction: column;
              gap: 8px;

              .resource-item {
                display: flex;
                align-items: center;
                gap: 8px;
                padding: 8px;
                background: #fff;
                border-radius: 6px;
                border: 1px solid #ece9de;

                .resource-icon {
                  color: #2aa1a9;
                  font-size: 14px;
                  flex-shrink: 0;
                }

                .resource-text {
                  flex: 1;
                  min-width: 0;

                  .resource-name {
                    display: block;
                    font-size: 13px;
                    font-weight: 500;
                    color: #141413;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                  }

                  .resource-desc {
                    display: block;
                    font-size: 12px;
                    color: #a5a29a;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                  }
                }

                .doc-count {
                  font-size: 11px;
                  border-radius: 4px;
                  margin: 0;
                  flex-shrink: 0;
                }
              }
            }

            .empty-resource {
              font-size: 12px;
              color: #a5a29a;
              text-align: center;
              padding: 8px 0;
            }
          }
        }
      }

      .card-footer {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding-top: 12px;
        border-top: 1px solid #f0ede4;

        .create-time {
          font-size: 12px;
          color: #a5a29a;
        }

        .card-actions {
          display: flex;
          gap: 4px;
        }
      }
    }
  }

  .empty-state { margin: 60px 0; }

  .loading-wrap {
    display: flex;
    justify-content: center;
    padding: 80px 0;
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
  }
}

.disabled-tip {
  color: #a5a29a;
  font-size: 12px;
  margin-left: 4px;
}

// 折叠动画
.expand-enter-active, .expand-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}
.expand-enter-from, .expand-leave-to {
  opacity: 0;
  max-height: 0;
}
.expand-enter-to, .expand-leave-from {
  opacity: 1;
  max-height: 500px;
}
</style>
