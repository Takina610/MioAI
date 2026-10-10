<template>
  <div class="admin-page">
    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon total"><DatabaseOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">全部技能</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon active"><CheckCircleOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.active }}</div>
          <div class="stat-label">正常</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon disabled"><StopOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.disabled }}</div>
          <div class="stat-label">已禁用</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon installed"><DownloadOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.installedCount }}</div>
          <div class="stat-label">已安装</div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="toolbar">
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索技能名称"
        class="search-input"
        allow-clear
        @search="handleSearch"
        @change="handleSearchChange"
      />
      <a-select v-model:value="filterStatus" placeholder="状态" allow-clear class="status-filter" @change="handleSearch">
        <a-select-option :value="1">正常</a-select-option>
        <a-select-option :value="0">已禁用</a-select-option>
      </a-select>
    </div>

    <!-- 卡片网格 -->
    <div class="card-grid" v-if="skillList.length > 0">
      <div class="skill-card" v-for="skill in skillList" :key="skill.id">
        <div class="card-header">
          <ThunderboltOutlined class="card-icon" />
          <span class="card-name" :title="skill.name">{{ skill.name }}</span>
          <a-tag :color="skill.status === 1 ? 'green' : 'red'" class="status-tag">
            {{ skill.statusDesc || statusText(skill.status) }}
          </a-tag>
        </div>

        <div class="card-meta">
          <span class="meta-item"><UserOutlined /> {{ skill.userName || '未知用户' }}</span>
          <span class="meta-item">
            <DownloadOutlined v-if="skill.installed === 1" />
            <CloudOutlined v-else />
            {{ skill.installed === 1 ? '已安装' : '未安装' }}
          </span>
          <span class="meta-item"><FileTextOutlined /> {{ (skill.files?.length || 0) + 1 }} 文件</span>
        </div>

        <p class="card-desc">{{ skill.description || '暂无描述' }}</p>

        <!-- 折叠：内容预览 -->
        <div class="card-collapse">
          <div class="collapse-trigger" @click="toggleExpand(skill.id)">
            <span class="collapse-text"><FileMarkdownOutlined /> SKILL.md 内容</span>
            <DownOutlined :class="{ rotated: expandedIds.includes(skill.id) }" class="collapse-arrow" />
          </div>
          <transition name="expand">
            <pre v-if="expandedIds.includes(skill.id)" class="content-preview">{{ skill.content || '（空）' }}</pre>
          </transition>
        </div>

        <div class="card-footer">
          <span class="create-time"><CalendarOutlined /> {{ formatDate(skill.updateTime || skill.createTime) }}</span>
          <div class="card-actions">
            <a-button type="link" size="small" @click="openEditModal(skill)">
              <EditOutlined /> 编辑
            </a-button>
            <a-popconfirm
              title="删除后绑定该技能的智能体将不再使用它，确认删除？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleDelete(skill.id)"
            >
              <a-button type="link" size="small" danger>
                <DeleteOutlined />
              </a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>
    </div>

    <a-empty v-if="!loading && skillList.length === 0" description="暂无技能数据" class="empty-state" />

    <div v-if="loading && skillList.length === 0" class="loading-wrap">
      <a-spin size="large" />
    </div>

    <div class="pagination-wrap">
      <a-pagination
        v-model:current="pagination.current"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-size-options="['12', '24', '48']"
        show-size-changer
        :show-total="(total: number) => `共 ${total} 条`"
        @change="fetchSkills"
      />
    </div>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="editModalVisible"
      title="编辑技能"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="handleEditSubmit"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="技能名称">
          <a-input v-model:value="editForm.name" placeholder="技能名称" />
        </a-form-item>
        <a-form-item label="技能描述">
          <a-textarea v-model:value="editForm.description" :rows="3" placeholder="技能描述" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="editForm.status" placeholder="状态">
            <a-select-option :value="1">正常</a-select-option>
            <a-select-option :value="0">已禁用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  DatabaseOutlined,
  CheckCircleOutlined,
  StopOutlined,
  CloudOutlined,
  DownloadOutlined,
  UserOutlined,
  FileTextOutlined,
  FileMarkdownOutlined,
  ThunderboltOutlined,
  CalendarOutlined,
  EditOutlined,
  DeleteOutlined,
  DownOutlined
} from '@ant-design/icons-vue'
import { searchSkills, updateSkill, deleteSkill } from '@/api/adminSkill'
import type { Skill } from '@/types/skill'
import { formatDate } from '@/utils/format'

const loading = ref(false)
const skillList = ref<Skill[]>([])
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
  disabled: 0,
  installedCount: 0
})

const editModalVisible = ref(false)
const saving = ref(false)
const editForm = reactive<{ id: number; name: string; description: string; status?: number }>({
  id: 0,
  name: '',
  description: ''
})

async function fetchSkills() {
  loading.value = true
  try {
    const res = await searchSkills({
      current: pagination.current,
      pageSize: pagination.pageSize,
      name: searchKeyword.value || undefined,
      status: filterStatus.value
    })
    skillList.value = res.records || []
    pagination.total = res.total || 0
    stats.total = pagination.total
    stats.active = skillList.value.filter(s => s.status === 1).length
    stats.disabled = skillList.value.filter(s => s.status === 0).length
    stats.installedCount = skillList.value.filter(s => s.installed === 1).length
  } catch (e) {
    message.error('获取技能列表失败')
  } finally {
    loading.value = false
  }
}

function toggleExpand(id: number) {
  const idx = expandedIds.value.indexOf(id)
  if (idx > -1) {
    expandedIds.value.splice(idx, 1)
  } else {
    expandedIds.value.push(id)
  }
}

function handleSearch() {
  pagination.current = 1
  fetchSkills()
}

function handleSearchChange() {
  if (!searchKeyword.value) {
    handleSearch()
  }
}

function openEditModal(skill: Skill) {
  editForm.id = skill.id
  editForm.name = skill.name || ''
  editForm.description = skill.description || ''
  editForm.status = skill.status
  editModalVisible.value = true
}

async function handleEditSubmit() {
  saving.value = true
  try {
    await updateSkill({ ...editForm })
    message.success('更新成功')
    editModalVisible.value = false
    fetchSkills()
  } catch (e) {
    message.error('更新失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteSkill(id)
    message.success('删除成功')
    fetchSkills()
  } catch (e) {
    message.error('删除失败')
  }
}

function statusText(status: number): string {
  return { 1: '正常', 0: '已禁用' }[status] || '未知'
}

onMounted(() => {
  fetchSkills()
})
</script>

<style lang="scss" scoped>
.admin-page {
  padding: 24px;
}

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;

  .stat-card {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px 20px;
    background: #fff;
    border: 1px solid #ece9de;
    border-radius: 12px;

    .stat-icon {
      width: 42px;
      height: 42px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;

      &.total { background: rgba(#2aa1a9, 0.1); color: #2aa1a9; }
      &.active { background: rgba(#10b981, 0.1); color: #10b981; }
      &.disabled { background: rgba(#c0453a, 0.1); color: #c0453a; }
      &.installed { background: rgba(#f59e0b, 0.1); color: #f59e0b; }
    }

    .stat-value {
      font-size: 20px;
      font-weight: 600;
      color: #141413;
    }

    .stat-label {
      font-size: 12px;
      color: #8c8a82;
    }
  }
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;

  .search-input {
    max-width: 320px;
  }

  .status-filter {
    width: 140px;
  }
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.skill-card {
  padding: 16px;
  background: #fff;
  border: 1px solid #ece9de;
  border-radius: 12px;

  .card-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;

    .card-icon { color: #2aa1a9; }

    .card-name {
      flex: 1;
      font-weight: 600;
      color: #141413;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .card-meta {
    display: flex;
    gap: 14px;
    margin-bottom: 8px;
    font-size: 12px;
    color: #8c8a82;

    .meta-item {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }

  .card-desc {
    font-size: 13px;
    color: #6e6b62;
    margin-bottom: 10px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .card-collapse {
    border-top: 1px dashed #ece9de;
    padding-top: 8px;

    .collapse-trigger {
      display: flex;
      align-items: center;
      justify-content: space-between;
      cursor: pointer;
      font-size: 12px;
      color: #6e6b62;

      .collapse-arrow {
        transition: transform 0.2s;
        &.rotated { transform: rotate(180deg); }
      }
    }

    .content-preview {
      max-height: 240px;
      overflow: auto;
      margin-top: 8px;
      padding: 10px;
      background: #f5f3ec;
      border-radius: 8px;
      font-size: 12px;
      white-space: pre-wrap;
      word-break: break-word;
    }
  }

  .card-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid #ece9de;

    .create-time {
      font-size: 12px;
      color: #a5a29a;
    }
  }
}

.empty-state,
.loading-wrap {
  padding: 60px 0;
  display: flex;
  justify-content: center;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
