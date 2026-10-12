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
            <KbCard
              :kb="kb"
              show-delete
              @open="goToDetail(kb.id)"
              @hit-test="goToSimilaritySearch(kb.id)"
              @delete="confirmDelete(kb)"
            />
          </a-col>
        </a-row>

        <div v-else class="dash-empty">
          <a-empty description="你还没有知识库" />
        </div>

        <KnowledgeBaseModal
          v-model:visible="createModalVisible"
          @success="fetchKnowledgeBases"
        />

        <!-- 刷新/重开页面后的向量化任务恢复：跳过前两步直接接管进度 -->
        <KnowledgeBaseModal
          v-model:visible="resumeModalVisible"
          :kb-id="resumeKbId ?? undefined"
          skip-first-step
          resume-vectorize
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
import { PlusOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/store/user'
import {
  queryKnowledgeBases,
  deleteKnowledgeBase,
  listActiveVectorizations,
  type ActiveVectorization
} from '@/api/knowledgeBase'
import type { KnowledgeBase, PageResponse } from '@/types'
import KnowledgeBaseModal from '@/components/KnowledgeBaseModal.vue'
import PageHeader from '../components/PageHeader.vue'
import LoginPrompt from '../components/LoginPrompt.vue'
import KbCard from './components/KbCard.vue'

const emit = defineEmits<{
  (e: 'login-required'): void
}>()

const userStore = useUserStore()
const router = useRouter()

const loading = ref(false)
const createModalVisible = ref(false)
const knowledgeList = ref<KnowledgeBase[]>([])
/** 有在途向量化任务的知识库：进入页面时自动弹出到向量化进度（第三步） */
const resumeKbId = ref<number | null>(null)
const resumeModalVisible = ref(false)

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

async function resumeActiveVectorization(): Promise<void> {
  if (!userStore.isLoggedIn) return
  try {
    const actives: ActiveVectorization[] = await listActiveVectorizations()
    if (actives && actives.length > 0) {
      resumeKbId.value = actives[0].kbId
      resumeModalVisible.value = true
      message.info(`「${actives[0].kbName}」的向量化仍在进行，已自动恢复进度`)
    }
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  fetchKnowledgeBases()
  resumeActiveVectorization()
})
</script>
