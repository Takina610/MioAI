<template>
  <div class="admin-mcp-page">
    <!-- 统计卡片 -->
    <div class="stats-bar">
      <div class="stat-card total">
        <div class="stat-icon"><ToolOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">总 MCP 工具</div>
        </div>
      </div>
      <div class="stat-card active">
        <div class="stat-icon"><CheckCircleOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.active }}</div>
          <div class="stat-label">正常</div>
        </div>
      </div>
      <div class="stat-card inactive">
        <div class="stat-icon"><PauseCircleOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.inactive }}</div>
          <div class="stat-label">未激活</div>
        </div>
      </div>
      <div class="stat-card error">
        <div class="stat-icon"><ExclamationCircleOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.error }}</div>
          <div class="stat-label">异常</div>
        </div>
      </div>
    </div>

    <!-- 搜索与筛选 -->
    <div class="filter-bar">
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索 MCP 工具名称"
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
        <a-select-option :value="0">未激活</a-select-option>
        <a-select-option :value="1">正常</a-select-option>
        <a-select-option :value="2">异常</a-select-option>
      </a-select>
    </div>

    <!-- MCP 卡片网格 -->
    <div v-if="!loading || mcpList.length > 0" class="mcp-grid">
      <div v-for="mcp in mcpList" :key="mcp.id" class="mcp-card">
        <!-- 卡片头部 -->
        <div class="card-header">
          <div class="mcp-icon-wrap">
            <ToolOutlined class="mcp-icon" />
          </div>
          <div class="header-info">
            <div class="mcp-name">{{ mcp.name || '未命名' }}</div>
            <div class="mcp-meta">
              <a-tag :color="statusColor(mcp.status)" class="status-tag">
                {{ statusText(mcp.status) }}
              </a-tag>
              <span class="visibility-tag">
                <GlobalOutlined v-if="mcp.isPublic === 1" /> <LockOutlined v-else />
                {{ mcp.isPublic === 1 ? '公开' : '私有' }}
              </span>
            </div>
          </div>
        </div>

        <!-- 描述 -->
        <div class="card-desc" :title="mcp.description">
          {{ mcp.description || '暂无描述' }}
        </div>

        <!-- 折叠：工具信息（tool_info） -->
        <div class="card-collapse">
          <div class="collapse-trigger" @click="toggleExpand(mcp.id)">
            <span class="collapse-text">
              <AppstoreOutlined /> 工具列表
              <span v-if="mcp._toolCount !== undefined" class="resource-summary">{{ mcp._toolCount }} 个工具</span>
            </span>
            <DownOutlined :class="{ rotated: expandedIds.includes(mcp.id) }" class="collapse-arrow" />
          </div>
          <transition name="expand">
            <div v-if="expandedIds.includes(mcp.id)" class="collapse-content">
              <div v-if="!mcp._toolInfos" class="detail-loading">
                <a-spin size="small" />
              </div>
              <template v-else>
                <div v-if="mcp._toolInfos.length > 0" class="tool-list">
                  <div v-for="tool in mcp._toolInfos" :key="tool.name" class="tool-item">
                    <div class="tool-header">
                      <ToolOutlined class="tool-icon" />
                      <span class="tool-name">{{ tool.name }}</span>
                    </div>
                    <p class="tool-desc">{{ tool.description || '暂无描述' }}</p>
                  </div>
                </div>
                <div v-else class="empty-resource">暂无工具信息</div>
              </template>
            </div>
          </transition>
        </div>

        <!-- 卡片底部操作 -->
        <div class="card-footer">
          <span class="create-time"><CalendarOutlined /> {{ formatDate(mcp.createTime) }}</span>
          <div class="card-actions">
            <a-button type="link" size="small" @click="openEditModal(mcp)">
              <EditOutlined /> 编辑
            </a-button>
            <a-popconfirm
              title="确认删除该 MCP 工具？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleDelete(mcp.id)"
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
    <a-empty v-if="!loading && mcpList.length === 0" description="暂无 MCP 工具数据" class="empty-state" />

    <!-- 加载中 -->
    <div v-if="loading && mcpList.length === 0" class="loading-wrap">
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
      title="编辑 MCP 工具"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleEditSubmit"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="工具名称">
          <a-input v-model:value="editForm.name" placeholder="请输入工具名称" />
        </a-form-item>
        <a-form-item label="工具描述">
          <a-textarea v-model:value="editForm.description" :rows="3" placeholder="请输入工具描述" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="editForm.status" placeholder="请选择状态">
            <a-select-option :value="0">未激活</a-select-option>
            <a-select-option :value="1">正常</a-select-option>
            <a-select-option :value="2">异常</a-select-option>
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
  ToolOutlined,
  CheckCircleOutlined,
  PauseCircleOutlined,
  ExclamationCircleOutlined,
  AppstoreOutlined,
  GlobalOutlined,
  LockOutlined,
  CalendarOutlined,
  EditOutlined,
  DeleteOutlined,
  DownOutlined
} from '@ant-design/icons-vue'
import { searchMcpTools, getMcpToolById, updateMcpTool, deleteMcpTool } from '@/api/adminMcp'
import type { McpAdminUpdateRequest } from '@/api/adminMcp'
import type { McpTool, McpToolInfo } from '@/types/mcpTool'

interface McpWithDetail extends McpTool {
  _toolInfos?: McpToolInfo[]
  _toolCount?: number
}

const loading = ref(false)
const mcpList = ref<McpWithDetail[]>([])
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
  active: 0,
  inactive: 0,
  error: 0
})

const editModalVisible = ref(false)
const editForm = reactive<McpAdminUpdateRequest>({
  id: 0,
  name: '',
  description: '',
  status: 0,
  isPublic: 0
})

async function fetchMcpList() {
  loading.value = true
  try {
    const res = await searchMcpTools({
      current: pagination.current,
      pageSize: pagination.pageSize,
      name: searchKeyword.value || undefined,
      status: filterStatus.value
    })
    mcpList.value = (res.records || []).map(m => ({ ...m, _toolInfos: undefined, _toolCount: undefined }))
    pagination.total = res.total || 0
    stats.total = pagination.total
    stats.active = mcpList.value.filter(m => m.status === 1).length
    stats.inactive = mcpList.value.filter(m => m.status === 0).length
    stats.error = mcpList.value.filter(m => m.status === 2).length
  } catch (e) {
    message.error('获取 MCP 列表失败')
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

  // 懒加载工具信息
  const mcp = mcpList.value.find(m => m.id === id)
  if (mcp && !mcp._toolInfos) {
    try {
      const detail = await getMcpToolById(id)
      let toolInfos: McpToolInfo[] = []
      if (detail.toolInfo) {
        try {
          toolInfos = JSON.parse(detail.toolInfo)
        } catch {
          toolInfos = []
        }
      }
      mcp._toolInfos = toolInfos
      mcp._toolCount = toolInfos.length
    } catch (e) {
      message.error('获取工具信息失败')
    }
  }
}

function handleSearch() {
  pagination.current = 1
  fetchMcpList()
}

function handleSearchChange() {
  if (!searchKeyword.value) {
    handleSearch()
  }
}

function handlePageChange() {
  fetchMcpList()
}

function openEditModal(mcp: McpTool) {
  editForm.id = mcp.id
  editForm.name = mcp.name || ''
  editForm.description = mcp.description || ''
  editForm.status = mcp.status
  editForm.isPublic = mcp.isPublic
  editModalVisible.value = true
}

async function handleEditSubmit() {
  try {
    await updateMcpTool(editForm)
    message.success('更新成功')
    editModalVisible.value = false
    fetchMcpList()
  } catch (e) {
    message.error('更新失败')
  }
}

async function handleDelete(id: number) {
  try {
    await deleteMcpTool(id)
    message.success('删除成功')
    fetchMcpList()
  } catch (e) {
    message.error('删除失败')
  }
}

function statusText(status: number): string {
  return { 0: '未激活', 1: '正常', 2: '异常' }[status] || '未知'
}

function statusColor(status: number): string {
  return { 0: '#a5a29a', 1: '#10b981', 2: '#c0453a' }[status] || '#a5a29a'
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => {
  fetchMcpList()
})
</script>

<style lang="scss" scoped>
.admin-mcp-page {
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
      &.active .stat-icon { background: linear-gradient(135deg, #10b981, #059669); }
      &.inactive .stat-icon { background: linear-gradient(135deg, #f59e0b, #d97706); }
      &.error .stat-icon { background: linear-gradient(135deg, #c0453a, #a83a30); }

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

  .mcp-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: 16px;
    margin-bottom: 24px;

    .mcp-card {
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

        .mcp-icon-wrap {
          width: 56px;
          height: 56px;
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: 12px;
          background: linear-gradient(135deg, #f2f8f8, #e0f7f7);
          border: 1px solid #d1f0f0;
          flex-shrink: 0;

          .mcp-icon {
            font-size: 24px;
            color: #2aa1a9;
          }
        }

        .header-info {
          flex: 1;
          min-width: 0;

          .mcp-name {
            font-size: 16px;
            font-weight: 600;
            color: #141413;
            margin-bottom: 4px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .mcp-meta {
            display: flex;
            align-items: center;
            gap: 10px;

            .status-tag {
              font-size: 12px;
              border-radius: 4px;
              margin: 0;
            }

            .visibility-tag {
              font-size: 12px;
              color: #a5a29a;
              display: flex;
              align-items: center;
              gap: 4px;
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

          .tool-list {
            display: flex;
            flex-direction: column;
            gap: 8px;

            .tool-item {
              padding: 10px 12px;
              background: #fff;
              border-radius: 6px;
              border: 1px solid #ece9de;

              .tool-header {
                display: flex;
                align-items: center;
                margin-bottom: 2px;

                .tool-icon {
                  color: #2aa1a9;
                  font-size: 14px;
                  margin-right: 6px;
                }

                .tool-name {
                  font-weight: 500;
                  color: #141413;
                  font-size: 13px;
                }
              }

              .tool-desc {
                font-size: 12px;
                color: #a5a29a;
                margin: 0;
                padding-left: 20px;
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
