<template>
  <div class="dash-page">
    <PageHeader :title="`公共知识库 ${knowledgeList.length}`" />

    <div class="dash-page-content">
      <a-spin v-if="loading || knowledgeList.length > 0" :spinning="loading">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in knowledgeList" :key="kb.id">
            <KbCard
              :kb="kb"
              show-author
              @open="goToDetail(kb.id)"
              @hit-test="goToSimilaritySearch(kb.id)"
            />
          </a-col>
        </a-row>
      </a-spin>

      <div v-else class="dash-empty">
        <a-empty description="暂无公开知识库" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getPublicKnowledgeBases } from '@/api/knowledgeBase'
import type { KnowledgeBase, PageResponse } from '@/types'
import PageHeader from '../components/PageHeader.vue'
import KbCard from './components/KbCard.vue'

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
