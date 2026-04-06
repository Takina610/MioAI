<template>
  <div class="public-knowledge">
    <div class="page-header">
      <div class="header-content">
        <h2>公共知识库</h2>
        <p class="desc">探索公开的知识库资源</p>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content">
      <a-spin :spinning="loading">
        <a-row :gutter="[16, 16]" v-if="knowledgeList.length > 0">
          <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in knowledgeList" :key="kb.id">
            <a-card hoverable class="kb-card" @click="viewKnowledge(kb)">
              <div class="card-header">
                <DatabaseOutlined class="header-icon" />
                <h3>{{ kb.name }}</h3>
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
            </a-card>
          </a-col>
        </a-row>

        <a-empty v-else-if="!loading" description="暂无公开知识库" />
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { DatabaseOutlined, FileOutlined } from '@ant-design/icons-vue'
import { getPublicKnowledgeBases } from '@/api/knowledgeBase'
import type { KnowledgeBase, PageResponse } from '@/types'

const loading = ref<boolean>(false)
const knowledgeList = ref<KnowledgeBase[]>([])

onMounted(() => {
  fetchKnowledgeBases()
})

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

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function viewKnowledge(kb: KnowledgeBase): void {
  console.log('查看知识库:', kb)
}
</script>

<style lang="scss" scoped>
.public-knowledge {
  height: 100%;
  .page-header {
    .header-content {
      padding: 10px 24px;
      h2 {
        font-size: 24px;
        font-weight: 600;
        color: #202124;
      }

      .desc {
        color: #5f6368;
        font-size: 14px;
        margin-bottom: 0px;
      }
    }
    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .page-content {
    height: calc(100% - 140px);
    overflow-y: auto;
    padding: 24px;
  }

  .kb-card {
    border-radius: 12px;
    transition: all 0.3s;

    &:hover {
      border-color: $primary-color;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
    }

    .card-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;

      .header-icon {
        font-size: 24px;
        color: #2aa1a9;
      }

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: #202124;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .card-desc {
      color: #5f6368;
      font-size: 14px;
      line-height: 1.5;
      margin-bottom: 12px;
      display: -webkit-box;
      line-clamp: 2;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 12px;
      border-top: 1px solid #f0f0f0;
      font-size: 12px;
      color: #999;

      .docs {
        display: flex;
        align-items: center;
        gap: 4px;
      }

      .storage {
        color: #666;
      }
    }
  }
}
</style>
