<template>
  <div class="admin-knowledge-page">
    <!-- 统计卡片 -->
    <div class="stats-bar">
      <div class="stat-card total">
        <div class="stat-icon"><DatabaseOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">总知识库</div>
        </div>
      </div>
      <div class="stat-card active">
        <div class="stat-icon"><CheckCircleOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.active }}</div>
          <div class="stat-label">正常</div>
        </div>
      </div>
      <div class="stat-card creating">
        <div class="stat-icon"><LoadingOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.creating }}</div>
          <div class="stat-label">创建中</div>
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
        placeholder="搜索知识库名称"
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
        <a-select-option :value="0">创建中</a-select-option>
        <a-select-option :value="1">正常</a-select-option>
        <a-select-option :value="2">已禁用</a-select-option>
      </a-select>
    </div>

    <!-- 知识库卡片网格 -->
    <div v-if="!loading || knowledgeList.length > 0" class="knowledge-grid">
      <div v-for="kb in knowledgeList" :key="kb.id" class="knowledge-card">
        <!-- 卡片头部 -->
        <div class="card-header">
          <div class="kb-icon-wrap">
            <DatabaseOutlined class="kb-icon" />
          </div>
          <div class="header-info">
            <div class="kb-name">{{ kb.name || '未命名' }}</div>
            <div class="kb-meta">
              <a-tag :color="statusColor(kb.status)" class="status-tag">
                {{ statusText(kb.status) }}
              </a-tag>
              <span class="visibility-tag">
                <GlobalOutlined v-if="kb.isPublic === 1" /> <LockOutlined v-else />
                {{ kb.isPublic === 1 ? '公开' : '私有' }}
              </span>
              <span class="doc-count"><FileTextOutlined /> {{ kb.documentCount || 0 }} 文档</span>
            </div>
          </div>
        </div>

        <!-- 描述 -->
        <div class="card-desc" :title="kb.description">
          {{ kb.description || '暂无描述' }}
        </div>

        <!-- 存储大小 -->
        <div class="card-storage">
          <CloudServerOutlined /> 存储大小: {{ formatFileSize(kb.storageSize || 0) }}
        </div>

        <!-- 折叠：文档列表 -->
        <div class="card-collapse">
          <div class="collapse-trigger" @click="toggleExpand(kb.id)">
            <span class="collapse-text">
              <FileTextOutlined /> 文档列表
              <span v-if="kb._docLoaded" class="resource-summary">{{ kb._docs?.length || 0 }} 个文档</span>
            </span>
            <DownOutlined :class="{ rotated: expandedIds.includes(kb.id) }" class="collapse-arrow" />
          </div>
          <transition name="expand">
            <div v-if="expandedIds.includes(kb.id)" class="collapse-content">
              <div v-if="!kb._docLoaded" class="detail-loading">
                <a-spin size="small" />
              </div>
              <template v-else>
                <div v-if="kb._docs && kb._docs.length > 0" class="doc-list">
                  <div v-for="doc in kb._docs" :key="doc.id" class="doc-item">
                    <div class="doc-info">
                      <div class="doc-header">
                        <FileTextOutlined class="doc-icon" />
                        <span class="doc-name" :title="doc.fileName" @click="handlePreview(doc)">{{ doc.fileName }}</span>
                      </div>
                      <div class="doc-meta">
                        <span class="doc-type">{{ doc.fileType }}</span>
                        <span class="doc-size">{{ formatFileSize(doc.fileSize) }}</span>
                        <a-tag :color="docStatusColor(doc.status)" class="doc-status-tag">
                          {{ doc.statusDesc || docStatusText(doc.status) }}
                        </a-tag>
                      </div>
                    </div>
                  </div>
                </div>
                <div v-else class="empty-resource">暂无文档</div>
              </template>
            </div>
          </transition>
        </div>

        <!-- 卡片底部操作 -->
        <div class="card-footer">
          <span class="create-time"><CalendarOutlined /> {{ formatDate(kb.createTime) }}</span>
          <div class="card-actions">
            <a-button type="link" size="small" @click="openEditModal(kb)">
              <EditOutlined /> 编辑
            </a-button>
            <a-popconfirm
              title="确认删除该知识库？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleDelete(kb.id)"
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
    <a-empty v-if="!loading && knowledgeList.length === 0" description="暂无知识库数据" class="empty-state" />

    <!-- 加载中 -->
    <div v-if="loading && knowledgeList.length === 0" class="loading-wrap">
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
      title="编辑知识库"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleEditSubmit"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="知识库名称">
          <a-input v-model:value="editForm.name" placeholder="请输入知识库名称" />
        </a-form-item>
        <a-form-item label="知识库描述">
          <a-textarea v-model:value="editForm.description" :rows="3" placeholder="请输入知识库描述" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="editForm.status" placeholder="请选择状态">
            <a-select-option :value="0">创建中</a-select-option>
            <a-select-option :value="1">正常</a-select-option>
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

    <!-- 文件预览抽屉 -->
    <FilePreviewDrawer
      v-model:visible="previewVisible"
      :file-name="previewFileName"
      :file-url="previewFileUrl"
      :document-id="previewDocumentId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  DatabaseOutlined,
  CheckCircleOutlined,
  LoadingOutlined,
  StopOutlined,
  GlobalOutlined,
  LockOutlined,
  FileTextOutlined,
  CloudServerOutlined,
  CalendarOutlined,
  EditOutlined,
  DeleteOutlined,
  DownOutlined
} from '@ant-design/icons-vue'
import {
  searchKnowledgeBases,
  listDocuments,
  updateKnowledgeBase,
  deleteKnowledgeBase
} from '@/api/adminKnowledge'
import type { KnowledgeAdminUpdateRequest } from '@/api/adminKnowledge'
import type { KnowledgeBase } from '@/types'
import type { Document } from '@/api/knowledgeBase'
import FilePreviewDrawer from '@/components/FilePreviewDrawer.vue'

interface KbWithDetail extends KnowledgeBase {
  _docs?: Document[]
  _docLoaded?: boolean
}

const loading = ref(false)
const knowledgeList = ref<KbWithDetail[]>([])
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
  creating: 0,
  disabled: 0
})

const editModalVisible = ref(false)
const editForm = reactive<KnowledgeAdminUpdateRequest>({
  id: 0,
  name: '',
  description: '',
  status: 0,
  isPublic: 0
})

// 文件预览
const previewVisible = ref(false)
const previewFileName = ref('')
const previewFileUrl = ref('')
const previewDocumentId = ref(0)

async function fetchKnowledgeList() {
  loading.value = true
  try {
    const res = await searchKnowledgeBases({
      current: pagination.current,
      pageSize: pagination.pageSize,
      name: searchKeyword.value || undefined,
      status: filterStatus.value
    })
    knowledgeList.value = (res.records || []).map(kb => ({ ...kb, _docs: undefined, _docLoaded: false }))
    pagination.total = res.total || 0
    stats.total = pagination.total
    stats.active = knowledgeList.value.filter(k => k.status === 1).length
    stats.creating = knowledgeList.value.filter(k => k.status === 0).length
    stats.disabled = knowledgeList.value.filter(k => k.status === 2).length
  } catch (e) {
    message.error('获取知识库列表失败')
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

  // 懒加载文档列表
  const kb = knowledgeList.value.find(k => k.id === id)
  if (kb && !kb._docLoaded) {
    try {
      const res = await listDocuments(id, {
        current: 1,
        pageSize: 100
      })
      kb._docs = res.records || []
      kb._docLoaded = true
    } catch (e) {
      message.error('获取文档列表失败')
    }
  }
}

function handleSearch() {
  pagination.current = 1
  fetchKnowledgeList()
}

function handleSearchChange() {
  if (!searchKeyword.value) {
    handleSearch()
  }
}

function handlePageChange() {
  fetchKnowledgeList()
}

function openEditModal(kb: KnowledgeBase) {
  editForm.id = kb.id
  editForm.name = kb.name || ''
  editForm.description = kb.description || ''
  editForm.status = kb.status
  editForm.isPublic = kb.isPublic
  editModalVisible.value = true
}

async function handleEditSubmit() {
  try {
    await updateKnowledgeBase(editForm)
    message.success('更新成功')
    editModalVisible.value = false
    fetchKnowledgeList()
  } catch (e) {
    message.error('更新失败')
  }
}

async function handleDelete(id: number) {
  try {
    await deleteKnowledgeBase(id)
    message.success('删除成功')
    fetchKnowledgeList()
  } catch (e) {
    message.error('删除失败')
  }
}

function handlePreview(doc: Document): void {
  if (!doc.filePath) {
    message.warning('文件路径不存在')
    return
  }
  previewFileName.value = doc.fileName
  previewFileUrl.value = doc.filePath
  previewDocumentId.value = doc.id
  previewVisible.value = true
}

function statusText(status: number): string {
  return { 0: '创建中', 1: '正常', 2: '已禁用' }[status] || '未知'
}

function statusColor(status: number): string {
  return { 0: '#f59e0b', 1: '#10b981', 2: '#c0453a' }[status] || '#a5a29a'
}

function docStatusText(status: number): string {
  return { 0: '待处理', 1: '处理中', 2: '已完成', 3: '失败' }[status] || '未知'
}

function docStatusColor(status: number): string {
  return { 0: 'default', 1: 'processing', 2: 'success', 3: 'error' }[status] || 'default'
}

function formatFileSize(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => {
  fetchKnowledgeList()
})
</script>

<style lang="scss" scoped>
.admin-knowledge-page {
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
      &.creating .stat-icon { background: linear-gradient(135deg, #f59e0b, #d97706); }
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

  .knowledge-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: 16px;
    margin-bottom: 24px;

    .knowledge-card {
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

        .kb-icon-wrap {
          width: 56px;
          height: 56px;
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: 12px;
          background: linear-gradient(135deg, #f2f8f8, #e0f7f7);
          border: 1px solid #d1f0f0;
          flex-shrink: 0;

          .kb-icon {
            font-size: 24px;
            color: #2aa1a9;
          }
        }

        .header-info {
          flex: 1;
          min-width: 0;

          .kb-name {
            font-size: 16px;
            font-weight: 600;
            color: #141413;
            margin-bottom: 4px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .kb-meta {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;

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

            .doc-count {
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
        margin-bottom: 8px;
      }

      .card-storage {
        font-size: 12px;
        color: #a5a29a;
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

          .doc-list {
            display: flex;
            flex-direction: column;
            gap: 8px;
            max-height: 300px;
            overflow-y: auto;

            .doc-item {
              padding: 10px 12px;
              background: #fff;
              border-radius: 6px;
              border: 1px solid #ece9de;

              .doc-info {
                .doc-header {
                  display: flex;
                  align-items: center;
                  margin-bottom: 4px;

                  .doc-icon {
                    color: #2aa1a9;
                    font-size: 14px;
                    margin-right: 6px;
                    flex-shrink: 0;
                  }

                  .doc-name {
                    font-weight: 500;
                    color: #141413;
                    font-size: 13px;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                    cursor: pointer;
                    transition: color 0.2s;

                    &:hover {
                      color: #2aa1a9;
                    }
                  }
                }

                .doc-meta {
                  display: flex;
                  align-items: center;
                  gap: 8px;
                  padding-left: 20px;

                  .doc-type, .doc-size {
                    font-size: 11px;
                    color: #a5a29a;
                  }

                  .doc-status-tag {
                    font-size: 11px;
                    border-radius: 4px;
                    margin: 0;
                  }
                }
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
