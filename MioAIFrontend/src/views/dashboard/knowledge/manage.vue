<template>
  <div class="dash-page">
    <PageHeader :title="`知识库管理 ${knowledgeList.length}`">
      <template #actions>
        <a-button v-if="userStore.isLoggedIn" type="primary" @click="createModalVisible = true">
          <PlusOutlined /> 创建知识库
        </a-button>
      </template>
    </PageHeader>

    <div class="dash-page-content">
      <LoginPrompt v-if="!userStore.isLoggedIn" @login="emit('login-required')" />

      <template v-else>
        <a-row v-if="knowledgeList.length > 0" :gutter="[16, 16]">
          <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in knowledgeList" :key="kb.id">
            <div class="dash-card knowledge-card" @click="goToDetail(kb.id)">
              <div class="card-header">
                <div class="card-icon-wrapper">
                  <DatabaseOutlined />
                </div>
                <h3 class="card-title">{{ kb.name }}</h3>
              </div>
              <p class="card-desc">{{ kb.description || '暂无描述' }}</p>
              <div class="card-stats">
                <div class="stat-item">
                  <FileTextOutlined />
                  <span>{{ kb.documentCount || 0 }} 文档</span>
                </div>
              </div>
              <div class="card-footer">
                <span class="update-time">更新于 {{ formatDate(kb.updateTime || kb.createTime) }}</span>
              </div>
              <div class="card-actions" @click.stop>
                <a-button class="action-btn primary-btn" @click="goToSimilaritySearch(kb.id)">
                  命中测试
                </a-button>
                <a-button class="action-btn danger-btn" @click="confirmDelete(kb)">
                  删除
                </a-button>
              </div>
            </div>
          </a-col>
        </a-row>

        <EmptyState
          v-else
          :image="emptyImage"
          description="你还没有知识库"
          hint="前往右上角创建知识库"
        />

        <KnowledgeBaseModal
          v-model:visible="createModalVisible"
          @success="fetchKnowledgeBases"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import { queryKnowledgeBases, deleteKnowledgeBase } from '@/api/knowledgeBase'
import { formatDate } from '@/utils/format'
import type { KnowledgeBase, PageResponse } from '@/types'
import {
  PlusOutlined,
  DatabaseOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'
import KnowledgeBaseModal from '@/components/KnowledgeBaseModal.vue'
import emptyImage from '@/assets/agent.png'
import PageHeader from '../components/PageHeader.vue'
import LoginPrompt from '../components/LoginPrompt.vue'
import EmptyState from '../components/EmptyState.vue'

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const userStore = useUserStore()
const router = useRouter()

const loading = ref(false)
const createModalVisible = ref(false)
const knowledgeList = ref<KnowledgeBase[]>([])

async function fetchKnowledgeBases(): Promise<void> {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const res: PageResponse<KnowledgeBase> = await queryKnowledgeBases({ current: 1, pageSize: 100 })
    knowledgeList.value = res.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function confirmDelete(kb: KnowledgeBase): void {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除知识库「${kb.name}」吗？删除后将无法恢复。`,
    okText: '确定',
    cancelText: '取消',
    okButtonProps: { danger: true },
    async onOk() {
      await deleteKnowledgeBase(kb.id)
      message.success('删除成功')
      fetchKnowledgeBases()
    }
  })
}

function goToDetail(kbId: number): void {
  router.push(`/dashboard/knowledge/${kbId}`)
}

function goToSimilaritySearch(kbId: number): void {
  router.push(`/dashboard/knowledge/similaritySearch/${kbId}`)
}

onMounted(() => {
  fetchKnowledgeBases()
})
</script>

<style lang="scss" scoped>
.knowledge-card {
  cursor: pointer;

  &:hover .card-footer {
    opacity: 0;
  }

  .card-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;

    .card-icon-wrapper {
      width: 40px;
      height: 40px;
      border-radius: 10px;

      .anticon {
        font-size: 20px;
      }
    }

    .card-title {
      font-size: 15px;
      color: $text-dark;
      margin: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      flex: 1;
    }
  }

  .card-desc {
    padding-bottom: 12px;
    margin-bottom: 12px;
    border-bottom: 1px solid #f0f0f0;
  }

  .card-stats {
    display: flex;
    gap: 16px;
    margin-bottom: 12px;

    .stat-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: #666;

      .anticon {
        color: $primary-color;
      }
    }
  }

  .card-footer {
    display: flex;
    align-items: center;
    height: 36px;
    transition: opacity 0.3s;

    .update-time {
      font-size: 12px;
      color: #999;
    }
  }
}
</style>
