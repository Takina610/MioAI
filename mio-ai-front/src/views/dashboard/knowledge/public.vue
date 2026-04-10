<template>
  <div class="public-knowledge">
    <div class="page-header">
      <div class="header-content">
        <h2>公共知识库 {{ knowledgeList.length }}</h2>
      </div>
      <div class="header-line"></div>
    </div>

    <div class="page-content">
      <a-spin :spinning="loading">
        <a-row :gutter="[16, 16]" v-if="knowledgeList.length > 0">
          <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in knowledgeList" :key="kb.id">
            <div class="kb-card" @click="goToDetail(kb.id)">
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
              <div class="card-actions" @click.stop>
                <a-button class="test-btn" @click="goToSimilaritySearch(kb.id)">
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
import type { KnowledgeBase, PageResponse } from '@/types'

const router = useRouter()

const loading = ref<boolean>(false)
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

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
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
    background: #fff;
    border-radius: 12px;
    padding: 20px;
    border: 1px solid #f0f0f0;
    transition: all 0.3s;
    position: relative;
    cursor: pointer;

    &:hover {
      border-color: $primary-color;
      box-shadow: $shadow-medium;

      .card-footer {
        opacity: 0;
      }

      .card-actions {
        opacity: 1;
        visibility: visible;
      }
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

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: #202124;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        flex: 1;
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
      height: 36px;
      transition: opacity 0.3s;

      .docs {
        display: flex;
        align-items: center;
        gap: 4px;
      }

      .storage {
        color: #666;
      }
    }

    .card-actions {
      display: flex;
      gap: 8px;
      opacity: 0;
      visibility: hidden;
      transition: all 0.3s;
      position: absolute;
      bottom: 20px;
      left: 20px;
      right: 20px;

      .test-btn {
        flex: 1;
        height: 36px;
        font-size: 15px;
        gap: 4px;
        border-radius: 10px;
      }

      .test-btn {
        background: $primary-color;
        border: 1px solid $primary-color;
        color: #fff;

        &:hover {
          background: darken($primary-color, 10%);
          border-color: darken($primary-color, 10%);
          color: #fff;
        }
      }
    }
  }
}
</style>
