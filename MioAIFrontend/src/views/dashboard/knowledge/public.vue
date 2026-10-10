<template>
  <div class="dash-page">
    <PageHeader :title="`公共知识库 ${knowledgeList.length}`" />

    <div class="dash-page-content">
      <a-spin :spinning="loading">
        <a-row :gutter="[16, 16]" v-if="knowledgeList.length > 0">
          <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in knowledgeList" :key="kb.id">
            <div class="dash-card kb-card" @click="goToDetail(kb.id)">
              <div class="card-header">
                <DatabaseOutlined class="header-icon" />
                <h3 class="card-title">{{ kb.name }}</h3>
              </div>
              <p class="card-desc">{{ kb.description || '暂无描述' }}</p>
              <div class="card-footer">
                <span class="docs">
                  <FileOutlined /> {{ kb.documentCount || 0 }} 文档
                </span>
                <span class="storage">
                  {{ formatFileSize(kb.storageSize || 0) }}
                </span>
              </div>
              <div class="card-actions" @click.stop>
                <a-button class="action-btn primary-btn" @click="goToSimilaritySearch(kb.id)">
                  命中测试
                </a-button>
              </div>
            </div>
          </a-col>
        </a-row>

        <a-empty v-else-if="!loading" description="暂无公开知识库" />
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { DatabaseOutlined, FileOutlined } from '@ant-design/icons-vue'
import { getPublicKnowledgeBases } from '@/api/knowledgeBase'
import { formatFileSize } from '@/utils/format'
import type { KnowledgeBase, PageResponse } from '@/types'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()

const loading = ref(false)
const knowledgeList = ref<KnowledgeBase[]>([])

async function fetchKnowledgeBases(): Promise<void> {
  loading.value = true
  try {
    const res: PageResponse<KnowledgeBase> = await getPublicKnowledgeBases()
    knowledgeList.value = res?.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function goToDetail(kbId: number): void {
  router.push(`/dashboard/public-knowledge/${kbId}`)
}

function goToSimilaritySearch(kbId: number): void {
  router.push(`/dashboard/public-knowledge/similaritySearch/${kbId}`)
}

onMounted(() => {
  fetchKnowledgeBases()
})
</script>

<style lang="scss" scoped>
.kb-card {
  cursor: pointer;

  &:hover .card-footer {
    opacity: 0;
  }

  .card-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;

    .header-icon {
      font-size: 24px;
      color: $primary-color;
    }

    .card-title {
      color: #141413;
      margin: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      flex: 1;
    }
  }

  .card-desc {
    color: #5f5d55;
    font-size: 14px;
    line-height: 1.5;
    margin-bottom: 12px;
  }

  .card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-top: 12px;
    border-top: 1px solid #ece9de;
    font-size: 12px;
    color: #8c8a82;
    height: 36px;
    transition: opacity 0.3s;

    .docs {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .storage {
      color: #6e6b62;
    }
  }
}
</style>
