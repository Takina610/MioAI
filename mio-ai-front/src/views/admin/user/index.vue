<template>
  <div class="admin-page">
    <a-card title="用户管理" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="userList"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'avatar'">
            <a-avatar :src="record.userAvatar" :size="40">
              {{ record.userName?.charAt(0) || 'U' }}
            </a-avatar>
          </template>
          <template v-if="column.key === 'role'">
            <a-tag :color="record.userRole === 'admin' ? 'red' : 'blue'">
              {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleView(record)">查看</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { TablePaginationConfig } from 'ant-design-vue'

interface UserItem {
  id: number
  userName: string
  userAccount: string
  userAvatar: string
  userProfile: string
  userRole: string
  createTime: string
}

const loading = ref(false)
const userList = ref<UserItem[]>([])

const columns = [
  { title: '头像', key: 'avatar', width: 80 },
  { title: '用户名', dataIndex: 'userName', key: 'userName' },
  { title: '账号', dataIndex: 'userAccount', key: 'userAccount' },
  { title: '角色', key: 'role', width: 100 },
  { title: '简介', dataIndex: 'userProfile', key: 'userProfile', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 120 }
]

const pagination: TablePaginationConfig = {
  pageSize: 10,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`
}

async function fetchUserList() {
  loading.value = true
  try {
    // TODO: 替换为实际的用户列表 API
    // const res = await listUsers()
    // userList.value = res
    message.info('用户列表 API 待接入')
  } finally {
    loading.value = false
  }
}

function handleView(record: UserItem) {
  message.info(`查看用户: ${record.userName}`)
}

onMounted(() => {
  fetchUserList()
})
</script>

<style lang="scss" scoped>
.admin-page {
  .ant-card {
    border-radius: 8px;
  }
}
</style>
