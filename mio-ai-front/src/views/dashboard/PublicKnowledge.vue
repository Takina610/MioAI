<template>
  <div class="public-knowledge">
    <div class="page-header">
      <h2>公共知识库</h2>
      <p class="desc">探索公开的知识库资源</p>
    </div>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in knowledgeList" :key="kb.id">
        <a-card hoverable class="kb-card" @click="viewKnowledge(kb)">
          <div class="card-header">
            <DatabaseOutlined class="header-icon" />
            <h3>{{ kb.name }}</h3>
          </div>
          <p class="card-desc">{{ kb.description || '暂无描述' }}</p>
          <div class="card-footer">
            <span class="author">
              <UserOutlined /> {{ kb.userName || '匿名' }}
            </span>
            <span class="docs">
              <FileOutlined /> {{ kb.docCount || 0 }} 文档
            </span>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-empty v-if="!loading && knowledgeList.length === 0" description="暂无公开知识库" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { DatabaseOutlined, UserOutlined, FileOutlined } from '@ant-design/icons-vue'
import { getPublicKnowledgeBases } from '@/api/knowledgeBase'

const loading = ref(false)
const knowledgeList = ref([])

onMounted(() => {
  fetchKnowledgeBases()
})

async function fetchKnowledgeBases() {
  loading.value = true
  try {
    const res = await getPublicKnowledgeBases()
    knowledgeList.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function viewKnowledge(kb) {
  console.log('查看知识库:', kb)
}
</script>

<style lang="scss" scoped>
.public-knowledge {
  .page-header {
    margin-bottom: 24px;

    h2 {
      font-size: 24px;
      font-weight: 600;
      color: #202124;
      margin-bottom: 8px;
    }

    .desc {
      color: #5f6368;
      font-size: 14px;
    }
  }

  .kb-card {
    border-radius: 12px;
    transition: all 0.3s;

    &:hover {
      transform: translateY(-4px);
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

      .author,
      .docs {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }
  }
}
</style>
