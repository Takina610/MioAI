<template>
  <div class="admin-user-page">
    <!-- 统计卡片 -->
    <div class="stats-bar">
      <div class="stat-card total">
        <div class="stat-icon"><TeamOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">总用户</div>
        </div>
      </div>
      <div class="stat-card admin">
        <div class="stat-icon"><CrownOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.admin }}</div>
          <div class="stat-label">管理员</div>
        </div>
      </div>
      <div class="stat-card user">
        <div class="stat-icon"><UserOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.user }}</div>
          <div class="stat-label">普通用户</div>
        </div>
      </div>
    </div>

    <!-- 搜索与筛选 -->
    <div class="filter-bar">
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索用户名或账号"
        class="search-input"
        allow-clear
        @search="handleSearch"
        @change="handleSearchChange"
      />
      <a-select
        v-model:value="filterRole"
        placeholder="全部角色"
        class="role-select"
        allow-clear
        @change="handleSearch"
      >
        <a-select-option value="">全部角色</a-select-option>
        <a-select-option value="admin">管理员</a-select-option>
        <a-select-option value="user">普通用户</a-select-option>
      </a-select>
    </div>

    <!-- 用户卡片网格 -->
    <div v-if="!loading || userList.length > 0" class="user-grid">
      <div
        v-for="user in userList"
        :key="user.id"
        class="user-card"
        @click="openEditModal(user)"
      >
        <div class="card-header">
          <a-avatar :src="user.userAvatar" :size="64" class="user-avatar">
            {{ user.userName?.charAt(0) || 'U' }}
          </a-avatar>
          <a-tag :color="user.userRole === 'admin' ? '#2aa1a9' : '#6b7280'" class="role-tag">
            {{ user.userRole === 'admin' ? '管理员' : '普通用户' }}
          </a-tag>
        </div>
        <div class="card-body">
          <div class="user-name">{{ user.userName || '未设置昵称' }}</div>
          <div class="user-account">@{{ user.userAccount }}</div>
          <div class="user-profile" :title="user.userProfile">
            {{ user.userProfile || '暂无简介' }}
          </div>
        </div>
        <div class="card-footer">
          <span class="create-time"><CalendarOutlined /> {{ formatDate(user.createTime) }}</span>
          <div class="card-actions">
            <a-button type="link" size="small" @click.stop="openEditModal(user)">
              <EditOutlined />
            </a-button>
            <a-popconfirm
              title="确认删除该用户？"
              ok-text="删除"
              cancel-text="取消"
              @confirm.stop="handleDelete(user.id)"
            >
              <a-button type="link" size="small" danger @click.stop>
                <DeleteOutlined />
              </a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <a-empty v-if="!loading && userList.length === 0" description="暂无用户数据" class="empty-state" />

    <!-- 加载中 -->
    <div v-if="loading && userList.length === 0" class="loading-wrap">
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
        show-total
        @change="handlePageChange"
      />
    </div>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="editModalVisible"
      title="编辑用户"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleEditSubmit"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="用户昵称">
          <a-input v-model:value="editForm.userName" placeholder="请输入用户昵称" />
        </a-form-item>
        <a-form-item label="用户简介">
          <a-textarea v-model:value="editForm.userProfile" :rows="3" placeholder="请输入用户简介" />
        </a-form-item>
        <a-form-item label="用户角色">
          <a-select v-model:value="editForm.userRole" placeholder="请选择角色">
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
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
  UserOutlined,
  TeamOutlined,
  CrownOutlined,
  CalendarOutlined,
  EditOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import { searchUsers, updateUser, deleteUser } from '@/api/adminUser'
import type { UserAdminVO, UserAdminUpdateRequest } from '@/api/adminUser'

const loading = ref(false)
const userList = ref<UserAdminVO[]>([])
const searchKeyword = ref('')
const filterRole = ref('')

const pagination = reactive({
  current: 1,
  pageSize: 12,
  total: 0
})

const stats = reactive({
  total: 0,
  admin: 0,
  user: 0
})

const editModalVisible = ref(false)
const editForm = reactive<UserAdminUpdateRequest>({
  id: 0,
  userName: '',
  userProfile: '',
  userRole: 'user'
})

async function fetchUserList() {
  loading.value = true
  try {
    const res = await searchUsers({
      current: pagination.current,
      pageSize: pagination.pageSize,
      keyword: searchKeyword.value || undefined,
      userRole: filterRole.value || undefined
    })
    userList.value = res.records || []
    pagination.total = res.total || 0
    // 更新统计
    stats.total = pagination.total
    // 简单统计（实际可从后端获取）
    stats.admin = userList.value.filter(u => u.userRole === 'admin').length
    stats.user = userList.value.filter(u => u.userRole === 'user').length
  } catch (e) {
    message.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchUserList()
}

function handleSearchChange() {
  if (!searchKeyword.value) {
    handleSearch()
  }
}

function handlePageChange() {
  fetchUserList()
}

function openEditModal(user: UserAdminVO) {
  editForm.id = user.id
  editForm.userName = user.userName || ''
  editForm.userProfile = user.userProfile || ''
  editForm.userRole = user.userRole || 'user'
  editModalVisible.value = true
}

async function handleEditSubmit() {
  try {
    await updateUser({ ...editForm })
    message.success('更新成功')
    editModalVisible.value = false
    fetchUserList()
  } catch (e) {
    message.error('更新失败')
  }
}

async function handleDelete(id: number) {
  try {
    await deleteUser(id)
    message.success('删除成功')
    fetchUserList()
  } catch (e) {
    message.error('删除失败')
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => {
  fetchUserList()
})
</script>

<style lang="scss" scoped>
.admin-user-page {
  padding: 8px 0;

  .stats-bar {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    margin-bottom: 24px;

    .stat-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px 24px;
      background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
      border: 1px solid #e2e8f0;
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

      &.total .stat-icon {
        background: linear-gradient(135deg, #2aa1a9, #1d7a80);
      }

      &.admin .stat-icon {
        background: linear-gradient(135deg, #f59e0b, #d97706);
      }

      &.user .stat-icon {
        background: linear-gradient(135deg, #6366f1, #4f46e5);
      }

      .stat-info {
        .stat-value {
          font-size: 24px;
          font-weight: 700;
          color: #1f2937;
          line-height: 1.2;
        }

        .stat-label {
          font-size: 13px;
          color: #6b7280;
          margin-top: 2px;
        }
      }
    }
  }

  .filter-bar {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;

    .search-input {
      width: 320px;
    }

    .role-select {
      width: 140px;
    }
  }

  .user-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
    margin-bottom: 24px;

    .user-card {
      background: #ffffff;
      border: 1px solid #e8eaed;
      border-radius: 12px;
      padding: 20px;
      cursor: pointer;
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
        justify-content: space-between;
        margin-bottom: 14px;

        .user-avatar {
          border: 2px solid #f3f4f6;
        }

        .role-tag {
          font-size: 12px;
          border-radius: 4px;
        }
      }

      .card-body {
        margin-bottom: 14px;

        .user-name {
          font-size: 16px;
          font-weight: 600;
          color: #1f2937;
          margin-bottom: 4px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .user-account {
          font-size: 13px;
          color: #6b7280;
          margin-bottom: 8px;
        }

        .user-profile {
          font-size: 12px;
          color: #9ca3af;
          line-height: 1.5;
          overflow: hidden;
          text-overflow: ellipsis;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          min-height: 36px;
        }
      }

      .card-footer {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding-top: 12px;
        border-top: 1px solid #f3f4f6;

        .create-time {
          font-size: 12px;
          color: #9ca3af;
        }

        .card-actions {
          display: flex;
          gap: 4px;
          opacity: 0;
          transition: opacity 0.2s;
        }
      }

      &:hover .card-actions {
        opacity: 1;
      }
    }
  }

  .empty-state {
    margin: 60px 0;
  }

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
</style>
